package ar.com.padelnec.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.PhoneVerification;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.PhoneVerificationService.BookingFailure;
import ar.com.padelnec.service.PhoneVerificationService.InboundOutcome;
import ar.com.padelnec.support.Masking;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import ar.com.padelnec.web.SlotUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Los WhatsApp que nos escriben los jugadores, y lo que les contestamos.
 *
 * <p>Hoy solo sirven para verificar telefonos ({@link PhoneVerificationService}).
 * Cuando el pedido que se confirma trae el turno que el jugador estaba reservando,
 * la reserva sale aca mismo y la respuesta ya es la confirmacion del turno, con el
 * link para verlo o cancelarlo. Antes la hacia la pagina al enterarse, y desde el
 * navegador de Instagram la pagina ya no existe cuando el jugador vuelve de
 * WhatsApp: el numero quedaba confirmado y la reserva sin hacer.
 *
 * <p>Ninguna respuesta le dice "volve a la pagina" a quien ya tiene su turno, ni le
 * da a entender que tiene turno a quien todavia no lo tiene: con "volve a la pagina
 * para ver tu reserva" los jugadores daban el turno por hecho y no volvian.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppInboxService {

    // Los mismos codigos que devuelve la API (ApiExceptionHandler): la pagina trata
    // la reserva que no salio aca igual que una que no salio al reservar ella.
    private static final String SLOT_TAKEN = "SLOT_TAKEN";
    private static final String RULE_VIOLATION = "RULE_VIOLATION";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final String NOT_BOOKED_YET = "Tu turno todavía no está reservado: volvé a la página "
            + "y tocá de nuevo el botón para terminar la reserva.";

    private final PhoneVerificationService phoneVerificationService;
    private final BookingService bookingService;
    private final PlayerAuthService playerAuthService;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    /** Cada cuanto se le repite la explicacion a quien escribe sin codigo. */
    static final Duration EXPLANATION_EVERY = Duration.ofDays(1);
    /** A quien se le explico para que es el numero, y cuando. */
    private final Map<String, Instant> explained = new ConcurrentHashMap<>();

    /**
     * Procesa un mensaje que llego y devuelve la respuesta para el jugador, o nulo
     * si no hay que contestarle.
     *
     * @param rawFrom el remitente como lo manda Twilio ({@code whatsapp:+549...})
     */
    public String receive(String rawFrom, String body) {
        PhoneVerificationService.Inbound inbound = phoneVerificationService.handleInbound(rawFrom, body);
        PhoneVerification verification = inbound.verification();
        return switch (inbound.outcome()) {
            case VERIFIED -> verification != null && verification.holdsBooking()
                    ? book(verification)
                    // El pedido no traia turno (una pagina vieja): la pagina reserva
                    // sola si sigue abierta, y si no, tiene que volver a elegirlo.
                    : "¡Listo! Tu número quedó confirmado. " + NOT_BOOKED_YET;
            case ALREADY_VERIFIED -> verification == null
                    ? "Tu número ya está confirmado: podés reservar en la página sin mandarnos ningún código."
                    : again(verification);
            case WRONG_NUMBER -> "Ese código se pidió para otro número. En la página, revisá que el teléfono "
                    + "sea el de este WhatsApp y volvé a tocar el botón.";
            case UNKNOWN_CODE -> "Ese código venció o no es correcto. Volvé a la página y tocá de nuevo "
                    + "el botón para confirmar tu número.";
            case NO_CODE -> explainedRecently(rawFrom)
                    ? null
                    : "Hola, este número es solo para confirmar teléfonos al reservar en TurnosPadel. "
                            + "Para consultas sobre tu turno, escribile directamente a tu club.";
        };
    }

    /**
     * Si a este numero ya se le explico para que es el WhatsApp en el ultimo dia, y
     * si no, lo anota. Al "gracias", "dale" o "👍" que siguen no se les contesta: la
     * misma explicacion tres veces seguidas cansa, y cada respuesta se paga.
     *
     * <p>En memoria, como los avisos de {@code WebBookingFeed}: si el servidor se
     * reinicia, a lo sumo se explica una vez de mas.
     */
    private boolean explainedRecently(String rawFrom) {
        if (rawFrom == null || rawFrom.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        explained.values().removeIf(at -> at.isBefore(now.minus(EXPLANATION_EVERY)));
        return explained.putIfAbsent(rawFrom.trim(), now) != null;
    }

    /**
     * Mando el mensaje de nuevo, o Twilio reintento el aviso: lo que paso con el turno
     * que traia el pedido, que puede haber salido, no haber salido o estar saliendo.
     */
    private String again(PhoneVerification verification) {
        if (verification.getBookingId() != null) {
            return "Tu número ya está confirmado y tu turno ya quedó reservado: los datos y el link están "
                    + "en el mensaje anterior.";
        }
        if (verification.getBookingErrorCode() != null) {
            Tenant club = activeClub(verification);
            return failureText(verification, club, verification.getBookingErrorMessage());
        }
        if (verification.holdsBooking() && verification.getConfirmedAt() != null
                && verification.getConfirmedAt().plus(PhoneVerificationService.BOOKING_GRACE)
                        .isAfter(clock.instant())) {
            return "Estamos terminando tu reserva. En un momento te llega la confirmación por acá.";
        }
        return "Tu número ya está confirmado. " + NOT_BOOKED_YET;
    }

    /** Reserva el turno que esperaba el pedido recien confirmado, y cuenta como salio. */
    private String book(PhoneVerification verification) {
        Tenant club = activeClub(verification);
        if (club == null) {
            return failed(verification, null, new BookingFailure(NOT_FOUND, null,
                    "El club ya no toma reservas por la web."));
        }
        try {
            Booking booking = TenantContext.callAs(club.getId(), () -> create(club, verification));
            String saveCode = phoneVerificationService.recordBooking(verification.getId(), booking.getId());
            log.info("Reserva {} hecha al confirmar el telefono {}", booking.getId(),
                    Masking.phone(verification.getPhoneNumber()));
            return TenantContext.callAs(club.getId(),
                    () -> notificationService.confirmedByMessageReply(club, booking, saveCode));
        } catch (SlotUnavailableException ex) {
            return failed(verification, club, new BookingFailure(SLOT_TAKEN, null, ex.getMessage()));
        } catch (BusinessRuleException ex) {
            return failed(verification, club, new BookingFailure(RULE_VIOLATION,
                    ex.getReason() == null ? null : ex.getReason().name(), ex.getMessage()));
        } catch (ResourceNotFoundException ex) {
            return failed(verification, club, new BookingFailure(NOT_FOUND, null, ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("No se pudo hacer la reserva del pedido de verificacion {}", verification.getId(), ex);
            return failed(verification, club, new BookingFailure(INTERNAL_ERROR, null,
                    "Tuvimos un problema tomando tu reserva. Probá de nuevo en un momento."));
        }
    }

    /** Lo mismo que hace el endpoint de reserva sin sena, con el numero ya probado. */
    private Booking create(Tenant club, PhoneVerification verification) {
        String phone = verification.getPhoneNumber();
        if (verification.getPlayerAccountId() != null) {
            playerAuthService.requireAccountPhone(verification.getPlayerAccountId(), phone);
            // Antes de reservar: si le ganan el turno, el numero ya quedo corregido.
            playerAuthService.adoptVerifiedPhone(verification.getPlayerAccountId(), phone);
        }
        return bookingService.createFromPlayerMessage(club, new NewBooking(
                verification.getCourtId(),
                verification.getStartTime(),
                verification.getFullName(),
                phone,
                PaymentChoice.PAY_AT_CLUB,
                verification.getPlayerAccountId()));
    }

    /** El club del turno, si sigue tomando reservas. */
    private Tenant activeClub(PhoneVerification verification) {
        if (verification.getClubId() == null) {
            return null;
        }
        return TenantContext.callAs(verification.getClubId(),
                () -> tenantRepository.findById(verification.getClubId()).filter(Tenant::isActive).orElse(null));
    }

    private String failed(PhoneVerification verification, Tenant club, BookingFailure failure) {
        phoneVerificationService.recordBookingFailure(verification.getId(), failure);
        log.info("La reserva del pedido de verificacion {} no salio: {}", verification.getId(), failure.code());
        return failureText(verification, club, failure.message());
    }

    private String failureText(PhoneVerification verification, Tenant club, String reason) {
        String text = "Tu número quedó confirmado, pero el turno no se pudo reservar. " + reason;
        if (club == null) {
            return text;
        }
        String link = TenantContext.callAs(club.getId(),
                () -> notificationService.clubDayLink(club, verification.getStartTime()));
        return text + "\n\nYa no te vamos a pedir el código. Reservá otro horario acá:\n" + link;
    }
}
