package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.scheduler.BookingExpiryWorker;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

/**
 * Liberacion de canchas retenidas por reservas que nunca se completaron.
 *
 * <p>Es lo que sostiene la promesa de la grilla: si esto no corre, cada checkout
 * abandonado deja un turno fantasma y el club termina con la agenda llena de
 * horarios que en realidad estan libres.
 */
@SpringBootTest
@Import({TestDatabaseConfig.class, ClubFixture.class, BookingExpiryWorkerTest.FixedClockConfig.class})
class BookingExpiryWorkerTest {

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

    @Autowired private BookingExpiryWorker worker;
    @Autowired private BookingService bookingService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Pasados los 15 minutos, la reserva sin confirmar libera la cancha")
    void unconfirmedBookingsExpire() {
        Booking booking = reserve(LocalTime.of(18, 30));

        ((MutableClock) clock).advance(Duration.ofMinutes(16));
        int expired = worker.expireUnconfirmedBookings(club);

        assertThat(expired).isEqualTo(1);
        Booking after = reload(booking);
        assertThat(after.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(after.getCancellationReason()).isEqualTo(CancellationReason.CONFIRMATION_TIMEOUT);
    }

    @Test
    @DisplayName("Antes de que venza el plazo, la reserva sigue reteniendo la cancha")
    void bookingsWithinTheWindowAreLeftAlone() {
        Booking booking = reserve(LocalTime.of(18, 30));

        ((MutableClock) clock).advance(Duration.ofMinutes(14));

        assertThat(worker.expireUnconfirmedBookings(club)).isZero();
        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.AWAITING_CONFIRMATION);
    }

    @Test
    @DisplayName("Un turno ya confirmado no lo toca ningun vencimiento")
    void confirmedBookingsNeverExpire() {
        Booking booking = reserve(LocalTime.of(18, 30));
        bookingService.confirmByToken(booking.getConfirmationToken());

        ((MutableClock) clock).advance(Duration.ofHours(3));
        worker.expireUnconfirmedBookings(club);
        worker.expireUnpaidDrafts();

        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("La cancha liberada vuelve a aparecer en la grilla")
    void theReleasedSlotReturnsToTheGrid() {
        reserve(LocalTime.of(18, 30));
        assertThat(slotIsFree(LocalTime.of(18, 30))).isFalse();

        ((MutableClock) clock).advance(Duration.ofMinutes(16));
        worker.expireUnconfirmedBookings(club);

        assertThat(slotIsFree(LocalTime.of(18, 30))).isTrue();
    }

    @Test
    @DisplayName("Pasados los 10 minutos, el borrador sin pago libera la cancha")
    void unpaidDraftsExpire() {
        Booking draft = draftAt(LocalTime.of(20, 0));

        ((MutableClock) clock).advance(Duration.ofMinutes(11));
        int expired = worker.expireUnpaidDrafts();

        assertThat(expired).isEqualTo(1);
        assertThat(reload(draft).getCancellationReason())
                .isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
    }

    @Test
    @DisplayName("Los turnos ya jugados se cierran con una hora de gracia")
    void playedBookingsAreClosed() {
        Booking booking = reserve(LocalTime.of(18, 30));
        bookingService.confirmByToken(booking.getConfirmationToken());

        // Termina 20:00 local. Dos horas despues ya paso la hora de gracia.
        ((MutableClock) clock).set(TODAY.atTime(22, 0).atZone(ZONE).toInstant());

        assertThat(worker.closePlayedBookings()).isEqualTo(1);
        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("Un turno recien terminado queda abierto para poder marcar ausente")
    void recentlyFinishedBookingsStayOpen() {
        Booking booking = reserve(LocalTime.of(18, 30));
        bookingService.confirmByToken(booking.getConfirmationToken());

        // Termino hace 20 minutos: el club todavia esta a tiempo de marcar ausente.
        ((MutableClock) clock).set(TODAY.atTime(20, 20).atZone(ZONE).toInstant());

        assertThat(worker.closePlayedBookings()).isZero();
        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ------------------------------------------------------------ utilidades

    private Booking reserve(LocalTime time) {
        return bookingService.create(club, new NewBooking(court.getId(),
                TODAY.atTime(time).atZone(ZONE).toInstant(),
                "Simon Diaz", "2262415000", PaymentChoice.PAY_AT_CLUB));
    }

    /** Reserva en DRAFT, como la deja el camino de pago online. */
    private Booking draftAt(LocalTime time) {
        club.setMpAccessToken("APP_USR-token-de-prueba");
        club = fixture.save(club);
        return bookingService.create(club, new NewBooking(court.getId(),
                TODAY.atTime(time).atZone(ZONE).toInstant(),
                "Otro jugador", "2262415111", PaymentChoice.DEPOSIT_ONLINE));
    }

    private boolean slotIsFree(LocalTime time) {
        return availabilityService.availabilityFor(club, TODAY).slots().stream()
                .filter(slot -> slot.startTime().equals(time))
                .anyMatch(slot -> !slot.available().isEmpty());
    }

    private Booking reload(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }
}
