package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.notification.EmailSender;
import ar.com.padelnec.notification.whatsapp.NotificationTemplate;
import ar.com.padelnec.notification.whatsapp.WhatsAppSender;
import ar.com.padelnec.repository.NotificationLogRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.scheduler.WaitlistNotificationWorker;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.WaitlistService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Barrido que avisa a la lista de espera cuando se libera una cancha.
 *
 * <p>No se dispara desde la cancelacion misma (ver el javadoc de
 * {@link WaitlistNotificationWorker}), asi que lo que hay que probar es que el
 * barrido encuentra el hueco despues de la cancelacion, no en el momento.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class,
        WaitlistNotificationWorkerTest.FixedClockConfig.class})
class WaitlistNotificationWorkerTest {

    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @Autowired private WaitlistNotificationWorker worker;
    @Autowired private WaitlistService waitlistService;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private BookingService bookingService;
    @Autowired private PlayerAccountRepository playerAccountRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;
    @MockitoBean private WhatsAppSender sender;
    @MockitoBean private EmailSender emailSender;

    private Tenant club;
    private Court court;
    private PlayerAccount player;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();
        // Por defecto se comporta como el canal apagado de siempre (WHATSAPP_PROVIDER=off
        // en test): "manejado" sin mandar nada de verdad. Los tests de fallo pisan esto.
        when(sender.send(any(), any(), any(), any())).thenReturn(WhatsAppSender.SendResult.skipped("test"));
        // Sin proveedor de verdad tampoco: asi el WhatsApp "apagado" sigue dando
        // "atendido" en los tests de siempre, igual que antes de que existiera
        // este respaldo. Los tests del propio respaldo pisan esto.
        when(emailSender.send(any(), any(), any())).thenReturn(EmailSender.SendResult.failed("sin smtp en el test"));

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
        player = player("jugador@test.com");
    }

    /** Anotarse exige sesion (ver WaitlistService): la cuenta es global, no del club. */
    private PlayerAccount player(String email) {
        PlayerAccount account = new PlayerAccount();
        account.setEmail(email);
        account.setEmailVerified(true);
        account.setDisplayName("Jugador de prueba");
        return playerAccountRepository.saveAndFlush(account);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Cuando se cancela el turno que tapaba el horario, el barrido avisa")
    void notifiesOnceTheSlotIsFreedByACancellation() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        Booking taking = fillTheOnlyCourt(startTime);
        WaitlistEntry entry = waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);

        bookingService.cancelByClub(club, taking.getId(), "Se cayo el grupo");
        int notified = worker.notifyFreedSlots(club);

        assertThat(notified).isEqualTo(1);
        WaitlistEntry reloaded = waitlistEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.isNotified()).isTrue();
        assertThat(reloaded.getNotifiedAt()).isNotNull();

        assertThat(notificationLogRepository.findTop50ByOrderByCreatedAtDesc())
                .anySatisfy(log -> {
                    assertThat(log.getTemplate()).isEqualTo(NotificationTemplate.WAITLIST_SLOT_FREED.name());
                    assertThat(log.getPhoneNumber()).isEqualTo("+5492262415111");
                });
    }

    @Test
    @DisplayName("Mientras la cancha siga tomada, el barrido no avisa")
    void doesNotNotifyWhileTheSlotIsStillFull() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(startTime);
        waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);

        int notified = worker.notifyFreedSlots(club);

        assertThat(notified).isZero();
        assertThat(waitlistEntryRepository.findPending()).hasSize(1);
    }

    @Test
    @DisplayName("Una vez avisado, el mismo anotado no vuelve a aparecer pendiente")
    void notifiedEntriesDropOutOfThePendingList() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        Booking taking = fillTheOnlyCourt(startTime);
        waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);
        bookingService.cancelByClub(club, taking.getId(), "Se cayo el grupo");

        worker.notifyFreedSlots(club);
        int secondPass = worker.notifyFreedSlots(club);

        assertThat(secondPass).isZero();
        assertThat(waitlistEntryRepository.findPending()).isEmpty();
    }

    @Test
    @DisplayName("Si el WhatsApp y el mail fallan los dos de verdad, la entrada sigue pendiente para reintentar")
    void aFailedSendLeavesTheEntryPendingForRetry() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        Booking taking = fillTheOnlyCourt(startTime);
        WaitlistEntry entry = waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);
        bookingService.cancelByClub(club, taking.getId(), "Se cayo el grupo");
        when(sender.send(any(), any(), any(), any())).thenReturn(WhatsAppSender.SendResult.failed("boom"));
        // Explicito y no solo el default de setUp: es la condicion exacta que
        // este test quiere probar, no una casualidad heredada.
        when(emailSender.send(any(), any(), any())).thenReturn(EmailSender.SendResult.failed("smtp caido"));

        int notified = worker.notifyFreedSlots(club);

        assertThat(notified).isZero();
        WaitlistEntry reloaded = waitlistEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.isNotified()).isFalse();
        assertThat(waitlistEntryRepository.findPending()).hasSize(1);
    }

    @Test
    @DisplayName("Si el WhatsApp del club esta apagado, el mail de la cuenta rescata el aviso")
    void emailRescuesTheNoticeWhenWhatsappIsOff() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        Booking taking = fillTheOnlyCourt(startTime);
        WaitlistEntry entry = waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);
        bookingService.cancelByClub(club, taking.getId(), "Se cayo el grupo");
        // sender ya esta "skipped" por el default de setUp (WhatsApp apagado).
        when(emailSender.send(any(), any(), any())).thenReturn(EmailSender.SendResult.ok());

        int notified = worker.notifyFreedSlots(club);

        assertThat(notified).isEqualTo(1);
        WaitlistEntry reloaded = waitlistEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.isNotified()).isTrue();
        verify(emailSender).send(eq("jugador@test.com"), any(), any());
    }

    @Test
    @DisplayName("Si el WhatsApp falla de verdad, el mail de la cuenta rescata el aviso igual")
    void emailRescuesTheNoticeWhenWhatsappReallyFails() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        Booking taking = fillTheOnlyCourt(startTime);
        WaitlistEntry entry = waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);
        bookingService.cancelByClub(club, taking.getId(), "Se cayo el grupo");
        when(sender.send(any(), any(), any(), any())).thenReturn(WhatsAppSender.SendResult.failed("boom"));
        when(emailSender.send(any(), any(), any())).thenReturn(EmailSender.SendResult.ok());

        int notified = worker.notifyFreedSlots(club);

        assertThat(notified).isEqualTo(1);
        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().isNotified()).isTrue();
    }

    private Booking fillTheOnlyCourt(Instant startTime) {
        return bookingService.create(club, new NewBooking(court.getId(), startTime,
                "El que llego primero", "2262415000", PaymentChoice.PAY_AT_CLUB));
    }
}
