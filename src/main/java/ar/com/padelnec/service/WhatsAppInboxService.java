package ar.com.padelnec.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.PhoneVerification;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
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

    private final PhoneVerificationService phoneVerificationService;
    private final BookingService bookingService;
    private final PlayerAuthService playerAuthService;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;

    /**
     * Procesa un mensaje que llego y devuelve la respuesta para el jugador.
     *
     * @param rawFrom el remitente como lo manda Twilio ({@code whatsapp:+549...})
     */
    public String receive(String rawFrom, String body) {
        PhoneVerificationService.Inbound inbound = phoneVerificationService.handleInbound(rawFrom, body);
        PhoneVerification verification = inbound.verification();
        if (inbound.outcome() == InboundOutcome.VERIFIED && verification != null && verification.holdsBooking()) {
            return book(verification);
        }
        if (inbound.outcome() == InboundOutcome.ALREADY_VERIFIED && verification != null
                && verification.getBookingId() != null) {
            // Mando el mensaje de nuevo: el turno ya salio con el primero.
            return "Tu número ya está confirmado y tu turno ya quedó reservado: los datos están en el "
                    + "mensaje anterior.";
        }
        return reply(inbound.outcome());
    }

    /** Reserva el turno que esperaba el pedido recien confirmado, y cuenta como salio. */
    private String book(PhoneVerification verification) {
        Tenant club = TenantContext.callAs(verification.getClubId(),
                () -> tenantRepository.findById(verification.getClubId()).filter(Tenant::isActive).orElse(null));
        if (club == null) {
            return failed(verification, null, new BookingFailure(NOT_FOUND, null,
                    "El club ya no toma reservas por la web."));
        }
        try {
            Booking booking = TenantContext.callAs(club.getId(), () -> create(club, verification));
            phoneVerificationService.recordBooking(verification.getId(), booking.getId());
            log.info("Reserva {} hecha al confirmar el telefono {}", booking.getId(),
                    Masking.phone(verification.getPhoneNumber()));
            return booking.getStatus() == BookingStatus.AWAITING_CONFIRMATION
                    ? notificationService.confirmationRequestMessage(club, booking)
                    : notificationService.confirmedUnpaidMessage(club, booking);
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
        return bookingService.create(club, new NewBooking(
                verification.getCourtId(),
                verification.getStartTime(),
                verification.getFullName(),
                phone,
                PaymentChoice.PAY_AT_CLUB,
                verification.getPlayerAccountId()));
    }

    private String failed(PhoneVerification verification, Tenant club, BookingFailure failure) {
        phoneVerificationService.recordBookingFailure(verification.getId(), failure);
        log.info("La reserva del pedido de verificacion {} no salio: {}", verification.getId(), failure.code());
        String text = "Tu número quedó confirmado, pero el turno no se pudo reservar. " + failure.message();
        if (club == null) {
            return text;
        }
        String link = TenantContext.callAs(club.getId(),
                () -> notificationService.clubDayLink(club, verification.getStartTime()));
        return text + "\n\nYa no te vamos a pedir el código. Reservá otro horario acá:\n" + link;
    }

    private static String reply(InboundOutcome outcome) {
        return switch (outcome) {
            // El pedido no traia turno (una pagina vieja): la pagina reserva sola si
            // sigue abierta, y si no, el jugador tiene que volver a elegirlo.
            case VERIFIED -> "¡Listo! Tu número quedó confirmado. Volvé a la página para terminar tu reserva.";
            case ALREADY_VERIFIED -> "Tu número ya está confirmado. Volvé a la página para terminar tu reserva.";
            case WRONG_NUMBER -> "Ese código se pidió para otro número. En la página, revisá que el teléfono "
                    + "sea el de este WhatsApp y volvé a tocar el botón.";
            case UNKNOWN_CODE -> "Ese código venció o no es correcto. Volvé a la página y tocá de nuevo "
                    + "el botón para confirmar tu número.";
            case NO_CODE -> "Hola, este número es solo para confirmar teléfonos al reservar en TurnosPadel. "
                    + "Para consultas sobre tu turno, escribile directamente a tu club.";
        };
    }
}
