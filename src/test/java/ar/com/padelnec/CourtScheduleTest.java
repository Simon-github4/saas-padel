package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.dto.AvailabilityResponse.CourtAvailability;
import ar.com.padelnec.web.dto.AvailabilityResponse.SlotView;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Horario propio de una cancha: le gana al del club en los dias que cubre, y la
 * grilla se estira con el mismo paso cuando la cancha abre antes o cierra despues.
 *
 * <p>El club de prueba abre de 8 a 23 con turnos de 90 minutos: 08:00, 09:30, 11:00,
 * 12:30, 14:00, 15:30, 17:00, 18:30, 20:00 y 21:30.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, CourtScheduleTest.FixedClockConfig.class})
class CourtScheduleTest {

    /** Martes 01/09/2026, 07:00 hora de Necochea (10:00 UTC): el club todavia no abrio. */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 1);
    private static final LocalDate WEDNESDAY = TUESDAY.plusDays(1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @Autowired private AvailabilityService availabilityService;
    @Autowired private BookingService bookingService;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court1;
    private Court court2;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();
        club = fixture.setGeneralPricePerPerson(fixture.club("club-necochea"), "5000");
        TenantContext.set(club.getId());
        court1 = fixture.court("Cancha 1", 1);
        court2 = fixture.court("Cancha 2", 2);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Sin horarios propios, la grilla es la del club en todas las canchas")
    void withoutSchedulesEveryCourtFollowsTheClub() {
        List<SlotView> slots = slotsOf(TUESDAY);

        assertThat(slots).hasSize(10);
        assertThat(slots).allSatisfy(slot -> assertThat(courtNames(slot)).containsExactly("Cancha 1", "Cancha 2"));
    }

    @Test
    @DisplayName("La cancha con horario propio solo se ofrece adentro de ese horario, y las demas siguen con el del club")
    void courtHoursNarrowThatCourtOnly() {
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(20, 0));

        List<SlotView> slots = slotsOf(TUESDAY);

        assertThat(slots).hasSize(10);
        assertThat(courtsAt(slots, 12, 30)).containsExactly("Cancha 2");
        assertThat(courtsAt(slots, 14, 0)).containsExactly("Cancha 1", "Cancha 2");
        // 18:30 a 20:00 entra justo antes del cierre de la cancha.
        assertThat(courtsAt(slots, 18, 30)).containsExactly("Cancha 1", "Cancha 2");
        assertThat(courtsAt(slots, 20, 0)).containsExactly("Cancha 2");
    }

    @Test
    @DisplayName("El horario propio es solo para los dias que cubre")
    void courtHoursOnlyApplyOnTheirDays() {
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(20, 0));

        assertThat(courtsAt(slotsOf(WEDNESDAY), 8, 0)).containsExactly("Cancha 1", "Cancha 2");
    }

    @Test
    @DisplayName("Una cancha que cierra de madrugada estira la grilla, y esos turnos son solo suyos")
    void laterClosingExtendsTheGridForThatCourt() {
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(20, 0), LocalTime.of(1, 0));

        List<SlotView> slots = slotsOf(TUESDAY);

        assertThat(slots.getLast().startTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(courtsAt(slots, 23, 0)).containsExactly("Cancha 1");
        assertThat(courtsAt(slots, 21, 30)).containsExactly("Cancha 1", "Cancha 2");
        assertThat(courtsAt(slots, 8, 0)).containsExactly("Cancha 2");
    }

    @Test
    @DisplayName("Una cancha que abre antes suma turnos antes de la apertura, con el mismo paso de la grilla")
    void earlierOpeningExtendsTheGridBackwards() {
        fixture.courtHours(court2, DayOfWeek.WEDNESDAY, LocalTime.of(6, 30), LocalTime.of(12, 0));

        List<SlotView> slots = slotsOf(WEDNESDAY);

        assertThat(slots.getFirst().startTime()).isEqualTo(LocalTime.of(6, 30));
        assertThat(courtsAt(slots, 6, 30)).containsExactly("Cancha 2");
        assertThat(courtsAt(slots, 9, 30)).containsExactly("Cancha 1", "Cancha 2");
        // 11:00 a 12:30 se pasa del cierre de la cancha 2.
        assertThat(courtsAt(slots, 11, 0)).containsExactly("Cancha 1");
    }

    @Test
    @DisplayName("Cerrada todo el dia le gana a cualquier franja de ese dia")
    void closedWinsOverHours() {
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(23, 0));
        fixture.courtClosed(court1, DayOfWeek.TUESDAY);

        assertThat(slotsOf(TUESDAY)).allSatisfy(slot -> assertThat(courtNames(slot)).containsExactly("Cancha 2"));
        assertThat(courtsAt(slotsOf(WEDNESDAY), 8, 0)).containsExactly("Cancha 1", "Cancha 2");
    }

    @Test
    @DisplayName("Con varias franjas el mismo dia, la cancha abre en todas")
    void severalWindowsOnTheSameDay() {
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(11, 0));
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(18, 30), LocalTime.of(23, 0));

        List<SlotView> slots = slotsOf(TUESDAY);

        assertThat(courtsAt(slots, 9, 30)).containsExactly("Cancha 1", "Cancha 2");
        assertThat(courtsAt(slots, 12, 30)).containsExactly("Cancha 2");
        assertThat(courtsAt(slots, 20, 0)).containsExactly("Cancha 1", "Cancha 2");
    }

    @Test
    @DisplayName("Si ninguna cancha abre en un turno, el turno no aparece")
    void slotWithoutOpenCourtsDisappears() {
        fixture.courtClosed(court1, DayOfWeek.TUESDAY);
        fixture.courtHours(court2, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(20, 0));

        assertThat(slotsOf(TUESDAY)).extracting(SlotView::startTime).containsExactly(
                LocalTime.of(14, 0), LocalTime.of(15, 30), LocalTime.of(17, 0), LocalTime.of(18, 30));
    }

    @Test
    @DisplayName("No se reserva una cancha fuera de su horario, ni por la web ni desde el panel")
    void cannotBookACourtOutsideItsHours() {
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(20, 0));
        Instant morning = at(TUESDAY, 12, 30);

        assertThatThrownBy(() -> bookingService.create(club, new NewBooking(court1.getId(), morning,
                "Jugador", "2262415000", PaymentChoice.PAY_AT_CLUB)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cancha 1 no abre");
        assertThatThrownBy(() -> bookingService.createManual(club, court1.getId(), morning,
                "Jugador", "2262415000", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cancha 1 no abre");

        Booking afternoon = bookingService.create(club, new NewBooking(court1.getId(), at(TUESDAY, 14, 0),
                "Jugador", "2262415000", PaymentChoice.PAY_AT_CLUB));
        assertThat(afternoon.getId()).isNotNull();
    }

    @Test
    @DisplayName("El turno de madrugada de una cancha que cierra tarde se reserva en esa cancha y no en las otras")
    void bookingTheExtendedSlot() {
        fixture.courtHours(court1, DayOfWeek.TUESDAY, LocalTime.of(20, 0), LocalTime.of(1, 0));
        Instant lateNight = at(TUESDAY, 23, 0);

        Booking booked = bookingService.create(club, new NewBooking(court1.getId(), lateNight,
                "Jugador", "2262415000", PaymentChoice.PAY_AT_CLUB));
        assertThat(booked.getEndTime()).isEqualTo(at(WEDNESDAY, 0, 30));

        assertThatThrownBy(() -> bookingService.create(club, new NewBooking(court2.getId(), lateNight,
                "Otro", "2262415111", PaymentChoice.PAY_AT_CLUB)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cancha 2 no abre");
    }

    // ------------------------------------------------------------- helpers

    private List<SlotView> slotsOf(LocalDate date) {
        return availabilityService.availabilityFor(club, date).slots();
    }

    private List<String> courtsAt(List<SlotView> slots, int hour, int minute) {
        return slots.stream()
                .filter(slot -> slot.startTime().equals(LocalTime.of(hour, minute)))
                .findFirst()
                .map(this::courtNames)
                .orElseThrow(() -> new AssertionError("No hay turno a las %02d:%02d".formatted(hour, minute)));
    }

    private List<String> courtNames(SlotView slot) {
        return slot.available().stream().map(CourtAvailability::courtName).toList();
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ZONE).toInstant();
    }
}
