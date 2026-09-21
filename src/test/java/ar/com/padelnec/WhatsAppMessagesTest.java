package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.EmailSender;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.notification.whatsapp.WhatsAppSender;
import ar.com.padelnec.repository.NotificationLogRepository;
import ar.com.padelnec.service.SlotGenerator;
import ar.com.padelnec.support.PhoneNumbers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Los WhatsApp que manda el sistema, y los que el panel deja listos para mandar
 * a mano por WhatsApp: todos dicen el turno en una linea y terminan con un link
 * a la pagina, para que el jugador tenga a donde ir si le queda una duda.
 */
class WhatsAppMessagesTest {

    private static final String BASE = "https://turnospadel.com.ar";

    private final WhatsAppSender sender = mock(WhatsAppSender.class);
    private NotificationService notifications;
    private Tenant club;
    private Booking booking;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl(BASE);
        when(sender.send(anyString(), any(), any(), anyString())).thenReturn(WhatsAppSender.SendResult.skipped("test"));
        notifications = new NotificationService(sender, mock(EmailSender.class),
                mock(NotificationLogRepository.class), properties, mock(SlotGenerator.class), new PhoneNumbers());

        club = new Tenant();
        club.setName("Pádel Necochea");
        club.setSlug("padel-necochea");
        club.setWhatsappNumber("+5492262415000");

        Court court = new Court();
        court.setName("Cancha 2");
        Customer customer = new Customer();
        customer.setFullName("Juana Pérez");
        customer.setPhoneNumber("+5492262111222");

        booking = new Booking();
        booking.setCourt(court);
        booking.setCustomer(customer);
        // Domingo 20 de septiembre de 2026, 21:00 en Argentina.
        booking.setStartTime(Instant.parse("2026-09-21T00:00:00Z"));
        booking.setTotalPrice(new BigDecimal("36000"));
        booking.setManagementToken("gestion123");
        booking.setConfirmationToken("confirma123");
        booking.setShareToken("comparte123");
    }

    @Test
    @DisplayName("Confirmado de palabra: el turno en una linea, lo que se paga y el link del turno")
    void confirmedUnpaid() {
        assertThat(notifications.confirmedUnpaidMessage(club, booking))
                .startsWith("✅ ¡Listo Juana! Tu turno en *Pádel Necochea* quedó confirmado.")
                .contains("🎾 Cancha 2 · domingo 20 de septiembre · 21:00 hs", "Se abona $")
                .endsWith(BASE + "/manage/gestion123");
    }

    @Test
    @DisplayName("Recordatorio: sin 'mañana', con saldo y link; pagado entero dice que no falta nada")
    void reminder() {
        String pending = notifications.reminderMessage(club, booking);
        assertThat(pending).doesNotContain("mañana").contains("Saldo a pagar en el club: $")
                .endsWith(BASE + "/manage/gestion123");

        booking.setPaidAmount(new BigDecimal("36000"));
        assertThat(notifications.reminderMessage(club, booking)).contains("Ya está todo pago.");
    }

    @Test
    @DisplayName("El saludo del panel dice el turno y lleva su link")
    void contact() {
        assertThat(notifications.contactMessage(club, booking))
                .startsWith("Hola Juana, te escribimos de *Pádel Necochea* por tu turno:")
                .endsWith(BASE + "/manage/gestion123");
    }

    @Test
    @DisplayName("Los automaticos llevan todos un link a la pagina, en su propia linea")
    void automaticMessagesCarryALink() {
        assertSent(n -> n.confirmationRequest(club, booking), BASE + "/confirm/confirma123");
        assertSent(n -> n.bookingConfirmedPaid(club, booking), BASE + "/manage/gestion123");
        // Vencido: a la portada del club con el horario elegido, para reservarlo de nuevo.
        assertSent(n -> n.confirmationExpired(club, booking), BASE + "/club/padel-necochea?fecha=2026-09-20&hora=21:00");
        assertSent(n -> n.reminder(club, booking), BASE + "/manage/gestion123");
    }

    @Test
    @DisplayName("Baja del club: link para elegir otro horario ese dia y el telefono legible")
    void cancelledByClub() {
        notifications.cancelledByClub(club, booking);

        String body = lastBody();
        assertThat(body).contains(BASE + "/club/padel-necochea?fecha=2026-09-20", "+54 9 2262 41-5000")
                .doesNotContain("+5492262415000");
    }

    @Test
    @DisplayName("El link de WhatsApp lleva los espacios como %20, no como '+'")
    void whatsappLinkEncodesSpacesAsPercent20() {
        String link = new PhoneNumbers().whatsappLink("+5492262111222", "Hola Juana, 1+1");

        assertThat(link)
                .isEqualTo("https://api.whatsapp.com/send?phone=5492262111222&text=Hola%20Juana%2C%201%2B1");
    }

    private void assertSent(Consumer<NotificationService> send, String link) {
        send.accept(notifications);
        String body = lastBody();
        // En su propia linea: WhatsApp arma la vista previa y el link no se pega al texto.
        assertThat(body.lines()).as("el mensaje tiene que llevar el link en una linea sola").contains(link);
        assertThat(body).contains("🎾 Cancha 2 · domingo 20 de septiembre · 21:00 hs");
    }

    private String lastBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).send(anyString(), any(), any(), body.capture());
        return body.getValue();
    }
}
