package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.RecurringBooking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.AlertType;
import ar.com.padelnec.domain.enums.BookingSource;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.OperationalAlertRepository;
import ar.com.padelnec.repository.RecurringBookingRepository;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.service.RecurringBookingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Turnos fijos: el grupo que juega todas las semanas a la misma hora.
 *
 * <p>En los clubes de Necochea es la mayor parte de la ocupacion, asi que si esto
 * genera de menos el club vende dos veces la misma cancha, y si genera de mas le
 * tapa horarios que en realidad podria estar vendiendo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class,
        RecurringBookingServiceTest.FixedClockConfig.class})
class RecurringBookingServiceTest {

    /** Martes 01/09/2026. El horizonte de los tests son 4 semanas. */
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

    @Autowired private RecurringBookingService recurringBookingService;
    @Autowired private RecurringBookingRepository recurringBookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private OperationalAlertRepository alertRepository;
    @Autowired private CustomerService customerService;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court;
    private Customer group;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
        group = customerService.findOrCreate("2262415000", "Grupo del martes");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Un turno fijo genera un turno por semana dentro del horizonte")
    void weeklyOccurrencesAreMaterialized() {
        fixedBooking(LocalTime.of(20, 0), null);

        int created = recurringBookingService.materializeUpcoming(club);

        // Martes 1, 8, 15, 22 y 29 de septiembre: 4 semanas de horizonte.
        assertThat(created).isEqualTo(5);
        assertThat(occurrences()).hasSize(5)
                .allSatisfy(booking -> {
                    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
                    assertThat(booking.getSource()).isEqualTo(BookingSource.RECURRING);
                    assertThat(booking.getTotalPrice()).isEqualByComparingTo("20000");
                });
    }

    @Test
    @DisplayName("Correr el job de nuevo no duplica los turnos ya generados")
    void rerunningTheJobIsIdempotent() {
        fixedBooking(LocalTime.of(20, 0), null);

        recurringBookingService.materializeUpcoming(club);
        int secondRun = recurringBookingService.materializeUpcoming(club);

        assertThat(secondRun).isZero();
        assertThat(occurrences()).hasSize(5);
    }

    @Test
    @DisplayName("El turno fijo ocupa la grilla publica como cualquier otro")
    void fixedBookingsBlockThePublicGrid() {
        fixedBooking(LocalTime.of(20, 0), null);
        recurringBookingService.materializeUpcoming(club);

        assertThat(availabilityService.availabilityFor(club, TODAY).slots().stream()
                .filter(slot -> slot.startTime().equals(LocalTime.of(20, 0)))
                .findFirst().orElseThrow()
                .available()).isEmpty();
    }

    @Test
    @DisplayName("El precio pactado con el grupo le gana a la tarifa de la franja")
    void agreedPriceOverridesTheTariff() {
        fixedBooking(LocalTime.of(20, 0), new BigDecimal("15000"));

        recurringBookingService.materializeUpcoming(club);

        assertThat(occurrences()).allSatisfy(booking ->
                assertThat(booking.getTotalPrice()).isEqualByComparingTo("15000"));
    }

    @Test
    @DisplayName("Cuando el grupo avisa que no juega, esa semana vuelve a la grilla")
    void skippedWeeksAreReleased() {
        RecurringBooking fixed = fixedBooking(LocalTime.of(20, 0), null);
        recurringBookingService.materializeUpcoming(club);

        LocalDate skipped = TODAY.plusWeeks(2);
        recurringBookingService.skipDate(club, fixed.getId(), skipped, "El grupo avisa que no va");

        assertThat(activeOccurrencesOn(skipped)).isEmpty();
        // Las demas semanas siguen en pie.
        assertThat(activeOccurrencesOn(TODAY.plusWeeks(1))).hasSize(1);
    }

    @Test
    @DisplayName("Una semana salteada no se regenera en la proxima corrida")
    void skippedWeeksAreNotRegenerated() {
        RecurringBooking fixed = fixedBooking(LocalTime.of(20, 0), null);
        recurringBookingService.materializeUpcoming(club);
        recurringBookingService.skipDate(club, fixed.getId(), TODAY.plusWeeks(2), "No juegan");

        recurringBookingService.materializeUpcoming(club);

        assertThat(activeOccurrencesOn(TODAY.plusWeeks(2))).isEmpty();
    }

    @Test
    @DisplayName("Si la franja ya se vendio online, se avisa al club en vez de pisar al jugador")
    void conflictsRaiseAnAlertInsteadOfOverwriting() {
        // Un jugador compro por la web el martes que viene a las 20:00.
        bookingService.create(club, new NewBooking(court.getId(),
                TODAY.plusWeeks(1).atTime(20, 0).atZone(ZONE).toInstant(),
                "Jugador web", "2262415111", PaymentChoice.PAY_AT_CLUB));

        fixedBooking(LocalTime.of(20, 0), null);
        int created = recurringBookingService.materializeUpcoming(club);

        // Se pierde solo esa semana; las otras cuatro se generan igual.
        assertThat(created).isEqualTo(4);
        assertThat(alertRepository.findPending())
                .singleElement()
                .satisfies(alert ->
                        assertThat(alert.getType()).isEqualTo(AlertType.RECURRING_CONFLICT));
    }

    @Test
    @DisplayName("Dar de baja el turno fijo cancela las semanas que faltan")
    void deactivatingCancelsUpcomingOccurrences() {
        RecurringBooking fixed = fixedBooking(LocalTime.of(20, 0), null);
        recurringBookingService.materializeUpcoming(club);

        int cancelled = recurringBookingService.deactivate(fixed.getId());

        assertThat(cancelled).isEqualTo(5);
        assertThat(occurrences()).allSatisfy(booking ->
                assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED));
    }

    @Test
    @DisplayName("Un turno fijo con fecha de corte deja de generar despues de esa fecha")
    void endDatedFixturesStopGenerating() {
        RecurringBooking fixed = fixedBooking(LocalTime.of(20, 0), null);
        fixed.setValidUntil(TODAY.plusWeeks(2));
        recurringBookingService.save(fixed);

        int created = recurringBookingService.materializeUpcoming(club);

        // Solo los martes 1, 8 y 15.
        assertThat(created).isEqualTo(3);
    }

    // ------------------------------------------------------------ utilidades

    private RecurringBooking fixedBooking(LocalTime start, BigDecimal priceOverride) {
        RecurringBooking fixed = new RecurringBooking();
        fixed.setCourt(court);
        fixed.setCustomer(group);
        fixed.setDay(DayOfWeek.TUESDAY);
        fixed.setStartTime(start);
        fixed.setDurationMinutes(90);
        fixed.setValidFrom(TODAY);
        fixed.setPriceOverride(priceOverride);
        return recurringBookingRepository.saveAndFlush(fixed);
    }

    private List<Booking> occurrences() {
        return bookingRepository.findAll().stream()
                .filter(booking -> booking.getSource() == BookingSource.RECURRING)
                .toList();
    }

    private List<Booking> activeOccurrencesOn(LocalDate date) {
        return occurrences().stream()
                .filter(booking -> booking.getStartTime().atZone(ZONE).toLocalDate().equals(date))
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
                .toList();
    }
}
