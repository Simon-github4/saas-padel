package ar.com.padelnec.notification;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.NotificationLog;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.domain.enums.NotificationStatus;
import ar.com.padelnec.notification.whatsapp.NotificationTemplate;
import ar.com.padelnec.notification.whatsapp.WhatsAppSender;
import ar.com.padelnec.repository.NotificationLogRepository;
import ar.com.padelnec.service.SlotGenerator;
import ar.com.padelnec.support.Masking;
import ar.com.padelnec.support.PhoneNumbers;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
 *
 * <p>Todos los mensajes tienen la misma forma, para que se lean de un vistazo en
 * el celular: primero que paso, despues el turno en una linea (cancha, dia y
 * hora), la plata si hay, y al final un link a la pagina en su propia linea,
 * siempre, aunque el mensaje no lo necesite: si el jugador tiene una duda, ahi
 * esta todo. Negritas con asteriscos, que es como las marca WhatsApp.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_AR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", ES_AR);

    private final WhatsAppSender sender;
    private final EmailSender emailSender;
    private final NotificationLogRepository notificationLogRepository;
    private final AppProperties properties;
    private final SlotGenerator slotGenerator;
    private final PhoneNumbers phoneNumbers;

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
                ¡Hola %s! Tu turno en *%s* está casi listo:
                %s

                Confirmalo en los próximos %d minutos tocando acá:
                %s

                Si no lo confirmás, la cancha vuelve a quedar libre."""
                .formatted(firstName(booking), club.getName(), slotLine(club, booking),
                        club.getConfirmationTtlMinutes(), link));
    }

    /** Turno confirmado de palabra: se paga en el club. */
    public void bookingConfirmedUnpaid(Tenant club, Booking booking) {
        List<String> variables = List.of(
                firstName(booking),
                club.getName(),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                money(booking.getTotalPrice()),
                managementLink(booking));

        dispatch(club, booking, NotificationTemplate.BOOKING_CONFIRMED_UNPAID, variables,
                confirmedUnpaidMessage(club, booking));
    }

    /**
     * Mismo texto que manda el WhatsApp automatico de {@link #bookingConfirmedUnpaid}, expuesto
     * aparte para armar a mano un link de wa.me desde el panel mientras WhatsApp esta en stand by.
     */
    public String confirmedUnpaidMessage(Tenant club, Booking booking) {
        return """
                ✅ ¡Listo %s! Tu turno en *%s* quedó confirmado.
                %s
                💵 Se abona %s en el club.

                Ver o cancelar tu turno:
                %s"""
                .formatted(firstName(booking), club.getName(), slotLine(club, booking),
                        money(booking.getTotalPrice()), managementLink(booking));
    }

    /** Sena acreditada por MercadoPago. */
    public void bookingConfirmedPaid(Tenant club, Booking booking) {
        String link = managementLink(booking);
        List<String> variables = List.of(
                firstName(booking),
                club.getName(),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                money(booking.balanceDue()),
                link);

        dispatch(club, booking, NotificationTemplate.BOOKING_CONFIRMED_PAID, variables, """
                ✅ ¡Seña acreditada, %s! Tu turno en *%s* quedó confirmado.
                %s
                💵 %s

                Ver o cancelar tu turno:
                %s"""
                .formatted(firstName(booking), club.getName(), slotLine(club, booking),
                        balanceLine(booking), link));
    }

    /** Vencio el plazo de confirmacion y la cancha volvio a la grilla. */
    public void confirmationExpired(Tenant club, Booking booking) {
        String link = bookSlotLink(club, booking.getStartTime());
        List<String> variables = List.of(
                firstName(booking),
                club.getName(),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                link);

        dispatch(club, booking, NotificationTemplate.CONFIRMATION_EXPIRED, variables, """
                Hola %s, tu turno en *%s* no se confirmó a tiempo y la cancha volvió a quedar libre.
                %s

                Si todavía querés jugar, reservalo de nuevo acá:
                %s"""
                .formatted(firstName(booking), club.getName(), slotLine(club, booking), link));
    }

    /** El club dio de baja el turno desde el panel. */
    public void cancelledByClub(Tenant club, Booking booking) {
        String link = clubDayLink(club, booking.getStartTime());
        String phone = phoneNumbers.forDisplay(club.getWhatsappNumber());
        List<String> variables = List.of(
                firstName(booking),
                club.getName(),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                link,
                phone);

        dispatch(club, booking, NotificationTemplate.BOOKING_CANCELLED_BY_CLUB, variables, """
                Hola %s, tuvimos que dar de baja tu turno en *%s*. Disculpá las molestias.
                %s

                Elegí otro horario acá:
                %s

                O escribinos al %s y lo reprogramamos."""
                .formatted(firstName(booking), club.getName(), slotLine(club, booking), link, phone));
    }

    public void reminder(Tenant club, Booking booking) {
        List<String> variables = List.of(
                firstName(booking),
                club.getName(),
                booking.getCourt().getName(),
                date(club, booking),
                time(club, booking),
                money(booking.balanceDue()),
                managementLink(booking));

        dispatch(club, booking, NotificationTemplate.BOOKING_REMINDER, variables, reminderMessage(club, booking));
    }

    /**
     * Mismo texto que manda el WhatsApp automatico de {@link #reminder}, expuesto aparte para
     * armar a mano un link de wa.me desde el panel mientras WhatsApp esta en stand by.
     *
     * <p>Sin "mañana": desde el panel se manda cuando el mostrador quiere, y el dia
     * ya va escrito en la linea del turno.
     */
    public String reminderMessage(Tenant club, Booking booking) {
        return """
                🔔 Hola %s, te recordamos tu turno en *%s*:
                %s
                💵 %s

                Ver o cancelar tu turno:
                %s"""
                .formatted(firstName(booking), club.getName(), slotLine(club, booking),
                        balanceLine(booking), managementLink(booking));
    }

    /**
     * El saludo con el que el mostrador abre un chat por un turno (alertas y detalle
     * del turno en el panel): quien escribe, por que turno, y el link para verlo.
     */
    public String contactMessage(Tenant club, Booking booking) {
        return """
                Hola %s, te escribimos de *%s* por tu turno:
                %s

                Ver tu turno:
                %s"""
                .formatted(firstName(booking), club.getName(), slotLine(club, booking), managementLink(booking));
    }

    /**
     * Se libero un horario que alguien esperaba en la lista de espera.
     *
     * <p>Devuelve el resultado (a diferencia del resto de los mensajes) porque quien
     * llama necesita saber si de verdad se mando antes de marcar la entrada como
     * atendida: a diferencia de una reserva, un fallo aca si tiene que poder
     * reintentarse en la proxima pasada del job.
     */
    public WhatsAppSender.SendResult waitlistSlotFreed(Tenant club, WaitlistEntry entry) {
        String phone = entry.getCustomer().getPhoneNumber();
        String name = firstName(entry.getCustomer().getFullName());
        String link = waitlistLink(club, entry);
        List<String> variables = List.of(
                name, club.getName(), date(club, entry.getStartsAt()), time(club, entry.getStartsAt()), link);

        return dispatch(club, phone, null, NotificationTemplate.WAITLIST_SLOT_FREED, variables, """
                🎾 Hola %s, se liberó un turno en *%s*: %s a las %s hs.

                Reservalo antes que otro acá:
                %s"""
                .formatted(name, club.getName(), date(club, entry.getStartsAt()),
                        time(club, entry.getStartsAt()), link));
    }

    /**
     * El aviso de que se libero un horario, para que el club lo mande a mano por
     * wa.me desde la lista de espera del panel. Lleva el link para reservar ese
     * turno, igual que el automatico de {@link #waitlistSlotFreed}.
     */
    public String waitlistManualMessage(Tenant club, WaitlistEntry entry) {
        return """
                Hola %s, te escribimos de *%s*. Se liberó una cancha el %s a las %s hs, \
                el horario en el que te anotaste en la lista de espera.

                Si todavía la querés, reservala acá:
                %s"""
                .formatted(firstName(entry.getCustomer().getFullName()), club.getName(),
                        date(club, entry.getStartsAt()), time(club, entry.getStartsAt()),
                        waitlistLink(club, entry));
    }

    /**
     * Mismo aviso que {@link #waitlistSlotFreed}, pero por mail: el respaldo
     * para cuando el WhatsApp del club esta apagado o el envio real fallo (lo
     * decide {@link ar.com.padelnec.scheduler.WaitlistNotificationWorker}, no
     * este metodo -- aca solo se manda). Existe porque anotarse en la lista de
     * espera exige sesion de jugador, y esa cuenta siempre tiene un mail.
     */
    public EmailSender.SendResult waitlistSlotFreedEmail(Tenant club, WaitlistEntry entry) {
        EmailMessage message = EmailTemplates.waitlistSlotFreed(
                firstName(entry.getCustomer().getFullName()), club.getName(),
                date(club, entry.getStartsAt()), time(club, entry.getStartsAt()), waitlistLink(club, entry));
        try {
            return emailSender.send(entry.getEmail(), message);
        } catch (RuntimeException ex) {
            // Mismo criterio que el WhatsApp de al lado: un proveedor caido no
            // puede tumbar el barrido entero, la entrada queda pendiente para
            // el proximo minuto.
            log.warn("Fallo el email de lista de espera a {}", Masking.email(entry.getEmail()), ex);
            return EmailSender.SendResult.failed(ex.getMessage());
        }
    }

    // --------------------------------------------------------------- envio

    /** Reserva concreta: el telefono y el id salen del turno. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Tenant club, Booking booking, NotificationTemplate template,
                         List<String> variables, String plainBody) {
        dispatch(club, booking.getCustomer().getPhoneNumber(), booking.getId(), template, variables, plainBody);
    }

    /**
     * Manda el mensaje y registra el intento en su propia transaccion, para que el
     * rastro quede incluso si lo que dispara el aviso termina fallando.
     *
     * <p>{@code bookingId} puede ser nulo: el aviso de lista de espera no nace de un
     * turno, y la columna ya esta pensada para sobrevivir sin uno (ver
     * {@link NotificationLog#getBookingId()}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WhatsAppSender.SendResult dispatch(Tenant club, String phone, UUID bookingId,
                         NotificationTemplate template, List<String> variables, String plainBody) {
        WhatsAppSender.SendResult result;
        try {
            result = sender.send(phone, template, variables, plainBody);
        } catch (RuntimeException ex) {
            // Un proveedor caido no puede tumbar una reserva que ya esta tomada.
            log.warn("Fallo inesperado enviando {} a {}", template, Masking.phone(phone), ex);
            result = WhatsAppSender.SendResult.failed(ex.getMessage());
        }

        NotificationLog entry = new NotificationLog();
        entry.setBookingId(bookingId);
        entry.setPhoneNumber(phone);
        entry.setTemplate(template.name());
        entry.setBody(plainBody);
        entry.setStatus(result.skipped() ? NotificationStatus.SKIPPED
                : result.delivered() ? NotificationStatus.SENT : NotificationStatus.FAILED);
        entry.setProviderMessageId(result.providerMessageId());
        entry.setError(result.error());
        notificationLogRepository.save(entry);
        return result;
    }

    // ---------------------------------------------------------- utilidades

    public String managementLink(Booking booking) {
        return properties.getBaseUrl() + "/manage/" + booking.getManagementToken();
    }

    public String confirmationLink(Booking booking) {
        return properties.getBaseUrl() + "/confirm/" + booking.getConfirmationToken();
    }

    /** Link de solo lectura para que el jugador se lo mande a los demas. */
    public String shareLink(Booking booking) {
        return properties.getBaseUrl() + "/turno/" + booking.getShareToken();
    }

    /**
     * Link a la portada del club con el horario ya elegido: el mismo ?fecha=&hora=
     * que arma la busqueda global, y que lleva al jugador directo a sus datos.
     *
     * <p>La fecha es la del dia operativo y no la del calendario: en un club que
     * cierra a la 01:00, el turno de las 00:30 esta en la grilla del dia
     * anterior, y con la fecha de calendario la portada no lo encontraba.
     */
    public String bookSlotLink(Tenant club, Instant startsAt) {
        ZonedDateTime local = startsAt.atZone(club.zoneId());
        LocalDate day = slotGenerator.resolve(club, startsAt)
                .map(SlotGenerator.ResolvedSlot::operatingDate)
                .orElse(local.toLocalDate());
        return properties.getBaseUrl() + "/club/" + club.getSlug()
                + "?fecha=" + day + "&hora=" + local.format(TIME);
    }

    /**
     * La portada del club en el dia del turno, sin horario elegido: para cuando el
     * club dio de baja ese turno y el jugador tiene que buscar otro.
     */
    public String clubDayLink(Tenant club, Instant startsAt) {
        ZonedDateTime local = startsAt.atZone(club.zoneId());
        LocalDate day = slotGenerator.resolve(club, startsAt)
                .map(SlotGenerator.ResolvedSlot::operatingDate)
                .orElse(local.toLocalDate());
        return properties.getBaseUrl() + "/club/" + club.getSlug() + "?fecha=" + day;
    }

    private String waitlistLink(Tenant club, WaitlistEntry entry) {
        return bookSlotLink(club, entry.getStartsAt());
    }

    /** El turno en una linea: {@code 🎾 Cancha 2 · sábado 20 de septiembre · 21:00 hs}. */
    private String slotLine(Tenant club, Booking booking) {
        return "🎾 %s · %s · %s hs".formatted(booking.getCourt().getName(), date(club, booking), time(club, booking));
    }

    /** Lo que falta pagar, o que no falta nada: "Saldo a pagar en el club: $ 0" no dice nada. */
    private String balanceLine(Booking booking) {
        return booking.balanceDue().signum() > 0
                ? "Saldo a pagar en el club: " + money(booking.balanceDue())
                : "Ya está todo pago.";
    }

    private String firstName(Booking booking) {
        // El de quien reservo, no el del jugador dueño del telefono: si alguien
        // se equivoco de numero, el mensaje le llega al otro y el saludo con un
        // nombre ajeno es lo que le avisa que no es suyo.
        return firstName(booking.displayName());
    }

    private String firstName(String fullName) {
        String full = fullName.trim();
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private String date(Tenant club, Booking booking) {
        return localTime(club, booking).format(DATE);
    }

    private String time(Tenant club, Booking booking) {
        return localTime(club, booking).format(TIME);
    }

    private String date(Tenant club, Instant instant) {
        return instant.atZone(club.zoneId()).format(DATE);
    }

    private String time(Tenant club, Instant instant) {
        return instant.atZone(club.zoneId()).format(TIME);
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
