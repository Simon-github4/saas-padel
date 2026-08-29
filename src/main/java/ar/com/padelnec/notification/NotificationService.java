package ar.com.padelnec.notification;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.NotificationLog;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.NotificationStatus;
import ar.com.padelnec.repository.NotificationLogRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma y despacha los WhatsApps del sistema, y deja constancia de cada intento.
 *
 * <p>Ningun fallo de notificacion se propaga: si el proveedor esta caido, el turno
 * ya reservado no se cae con el. Lo que si queda es el registro, porque cuando el
 * jugador dice que nunca le llego el link, esa tabla es la unica forma de saber si
 * el mensaje salio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_AR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", ES_AR);

    private final WhatsAppSender sender;
    private final NotificationLogRepository notificationLogRepository;
    private final AppProperties properties;

    // ------------------------------------------------------------- mensajes

    /** Link de 15 minutos del flujo sin pago anticipado. */
    public void confirmationRequest(Tenant club, Booking booking) {
        String link = confirmationLink(booking);
        List<String> variables = List.of(
                firstName(booking),
                club.getName(),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                String.valueOf(club.getConfirmationTtlMinutes()),
                link);

        dispatch(club, booking, NotificationTemplate.CONFIRMATION_REQUEST, variables, """
                ¡Hola %s! Estás por reservar %s en %s para el %s a las %s hs.
                Confirmá tocando este link, tenés %d minutos: %s
                Si no confirmás, la cancha vuelve a quedar libre."""
                .formatted(firstName(booking), booking.getCourt().getName(), club.getName(),
                        date(club, booking), time(club, booking),
                        club.getConfirmationTtlMinutes(), link));
    }

    /** Turno confirmado de palabra: se paga en el club. */
    public void bookingConfirmedUnpaid(Tenant club, Booking booking) {
        String link = managementLink(booking);
        List<String> variables = List.of(
                firstName(booking),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                money(booking.getTotalPrice()),
                link);

        dispatch(club, booking, NotificationTemplate.BOOKING_CONFIRMED_UNPAID, variables, """
                Listo %s, tu turno quedó confirmado.
                %s - %s a las %s hs.
                Se abona %s en el club.
                Podés ver o cancelar tu turno acá: %s"""
                .formatted(firstName(booking), booking.getCourt().getName(), date(club, booking),
                        time(club, booking), money(booking.getTotalPrice()), link));
    }

    /** Sena acreditada por MercadoPago. */
    public void bookingConfirmedPaid(Tenant club, Booking booking) {
        String link = managementLink(booking);
        List<String> variables = List.of(
                firstName(booking),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                money(booking.balanceDue()),
                link);

        dispatch(club, booking, NotificationTemplate.BOOKING_CONFIRMED_PAID, variables, """
                Pago acreditado, %s. Tu turno quedó confirmado.
                %s - %s a las %s hs.
                Saldo a pagar en el club: %s
                Podés ver o cancelar tu turno acá: %s"""
                .formatted(firstName(booking), booking.getCourt().getName(), date(club, booking),
                        time(club, booking), money(booking.balanceDue()), link));
    }

    /** Vencio el plazo de confirmacion y la cancha volvio a la grilla. */
    public void confirmationExpired(Tenant club, Booking booking) {
        List<String> variables = List.of(
                firstName(booking),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking));

        dispatch(club, booking, NotificationTemplate.CONFIRMATION_EXPIRED, variables, """
                Hola %s, no llegamos a confirmar tu turno de %s el %s a las %s hs,
                así que la cancha volvió a quedar disponible.
                Si todavía querés jugar, podés reservarla de nuevo."""
                .formatted(firstName(booking), booking.getCourt().getName(),
                        date(club, booking), time(club, booking)));
    }

    /** El club dio de baja el turno desde el panel. */
    public void cancelledByClub(Tenant club, Booking booking) {
        List<String> variables = List.of(
                firstName(booking),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                club.getWhatsappNumber());

        dispatch(club, booking, NotificationTemplate.BOOKING_CANCELLED_BY_CLUB, variables, """
                Hola %s, tuvimos que dar de baja tu turno de %s el %s a las %s hs.
                Escribinos a %s y lo reprogramamos."""
                .formatted(firstName(booking), booking.getCourt().getName(), date(club, booking),
                        time(club, booking), club.getWhatsappNumber()));
    }

    public void reminder(Tenant club, Booking booking) {
        List<String> variables = List.of(
                firstName(booking),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                money(booking.balanceDue()));

        dispatch(club, booking, NotificationTemplate.BOOKING_REMINDER, variables, """
                Hola %s, te esperamos mañana en %s, %s a las %s hs.
                Saldo a pagar en el club: %s"""
                .formatted(firstName(booking), booking.getCourt().getName(), date(club, booking),
                        time(club, booking), money(booking.balanceDue())));
    }

    // --------------------------------------------------------------- envio

    /**
     * Manda el mensaje y registra el intento en su propia transaccion, para que el
     * rastro quede incluso si lo que dispara el aviso termina fallando.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Tenant club, Booking booking, NotificationTemplate template,
                         List<String> variables, String plainBody) {
        String phone = booking.getCustomer().getPhoneNumber();

        WhatsAppSender.SendResult result;
        try {
            result = sender.send(phone, template, variables, plainBody);
        } catch (RuntimeException ex) {
            // Un proveedor caido no puede tumbar una reserva que ya esta tomada.
            log.warn("Fallo inesperado enviando {} a {}", template, phone, ex);
            result = WhatsAppSender.SendResult.failed(ex.getMessage());
        }

        NotificationLog entry = new NotificationLog();
        entry.setBookingId(booking.getId());
        entry.setPhoneNumber(phone);
        entry.setTemplate(template.name());
        entry.setBody(plainBody);
        entry.setStatus(result.delivered() ? NotificationStatus.SENT : NotificationStatus.FAILED);
        entry.setProviderMessageId(result.providerMessageId());
        entry.setError(result.error());
        notificationLogRepository.save(entry);
    }

    // ---------------------------------------------------------- utilidades

    public String managementLink(Booking booking) {
        return properties.getBaseUrl() + "/manage/" + booking.getManagementToken();
    }

    public String confirmationLink(Booking booking) {
        return properties.getBaseUrl() + "/confirm/" + booking.getConfirmationToken();
    }

    private String firstName(Booking booking) {
        String full = booking.getCustomer().getFullName().trim();
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private String date(Tenant club, Booking booking) {
        return localTime(club, booking).format(DATE);
    }

    private String time(Tenant club, Booking booking) {
        return localTime(club, booking).format(TIME);
    }

    private ZonedDateTime localTime(Tenant club, Booking booking) {
        return booking.getStartTime().atZone(club.zoneId());
    }

    private String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(ES_AR);
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }
}
