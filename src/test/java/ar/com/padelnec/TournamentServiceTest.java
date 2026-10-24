package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BlackoutKind;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.gym.GymFixture;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.service.TournamentService;
import ar.com.padelnec.service.TournamentService.MarkResult;
import ar.com.padelnec.service.TournamentService.TournamentSlot;
import ar.com.padelnec.web.BusinessRuleException;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Turnos que el club marca en la agenda para un torneo. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, TestClockConfig.class})
class TournamentServiceTest {

    /** Martes 01/09/2026, 07:00 hora de Necochea (10:00 UTC). */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired private TournamentService tournamentService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BlackoutRepository blackoutRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court1;
    private Court court2;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = new Tenant();
        club.setName("Club Necochea");
        club.setSlug("club-necochea");
        club.setWhatsappNumber("+542262400000");
        club.setOpenTime(LocalTime.of(8, 0));
        club.setCloseTime(LocalTime.of(23, 0));
        club.setDefaultSlotDuration(90);
        club = tenantRepository.saveAndFlush(club);

        TenantContext.set(club.getId());

        court1 = saveCourt("Cancha 1", 1);
        court2 = saveCourt("Cancha 2", 2);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Los turnos seguidos de una cancha quedan como una sola suspension de torneo")
    void consecutiveSlotsOfACourtBecomeOneBlackout() {
        MarkResult result = tournamentService.mark(club, "  Torneo Apertura ", List.of(
                slot(court1, 18, 30), slot(court1, 17, 0), slot(court1, 20, 0)));

        assertThat(result.marked()).isEqualTo(3);
        assertThat(result.leftOut()).isEmpty();
        assertThat(blackouts()).singleElement().satisfies(blackout -> {
            assertThat(blackout.getKind()).isEqualTo(BlackoutKind.TOURNAMENT);
            assertThat(blackout.getReason()).isEqualTo("Torneo Apertura");
            assertThat(blackout.getCourt().getId()).isEqualTo(court1.getId());
            assertThat(blackout.getStartTime()).isEqualTo(at(17, 0));
            assertThat(blackout.getEndTime()).isEqualTo(at(21, 30));
        });
    }

    @Test
    @DisplayName("Turnos salteados de la misma cancha quedan como suspensiones separadas")
    void slotsWithAGapStaySeparate() {
        tournamentService.mark(club, "Torneo", List.of(slot(court1, 14, 0), slot(court1, 17, 0)));

        assertThat(blackouts()).extracting(Blackout::getStartTime, Blackout::getEndTime)
                .containsExactly(tuple(at(14, 0), at(15, 30)), tuple(at(17, 0), at(18, 30)));
    }

    @Test
    @DisplayName("Cada cancha del torneo tiene su propia suspension")
    void eachCourtGetsItsOwnBlackout() {
        tournamentService.mark(club, "Torneo", List.of(slot(court1, 18, 30), slot(court2, 18, 30)));

        assertThat(blackouts()).extracting(blackout -> blackout.getCourt().getName())
                .containsExactlyInAnyOrder("Cancha 1", "Cancha 2");
    }

    @Test
    @DisplayName("Un turno reservado o que ya empezo queda afuera sin frenar el resto")
    void takenAndPastSlotsAreLeftOut() {
        // Son las 18:00 de Necochea: el turno de las 17:00 ya empezo.
        ((MutableClock) clock).set(Instant.parse("2026-09-01T21:00:00Z"));
        bookAt(court1, 18, 30);

        MarkResult result = tournamentService.mark(club, "Torneo", List.of(
                slot(court1, 20, 0), slot(court1, 18, 30), slot(court1, 17, 0)));

        assertThat(result.marked()).isEqualTo(1);
        assertThat(result.leftOut()).containsExactly(slot(court1, 17, 0), slot(court1, 18, 30));
        assertThat(blackouts()).singleElement()
                .satisfies(blackout -> assertThat(blackout.getStartTime()).isEqualTo(at(20, 0)));
    }

    @Test
    @DisplayName("Un turno ya suspendido queda afuera")
    void alreadySuspendedSlotIsLeftOut() {
        Blackout rain = new Blackout();
        rain.setCourt(court2);
        rain.setStartTime(at(18, 30));
        rain.setEndTime(at(20, 0));
        rain.setReason("Arreglo de la red");
        blackoutRepository.saveAndFlush(rain);

        MarkResult result = tournamentService.mark(club, "Torneo", List.of(slot(court2, 18, 30)));

        assertThat(result.marked()).isZero();
        assertThat(result.leftOut()).containsExactly(slot(court2, 18, 30));
        assertThat(blackouts()).singleElement()
                .satisfies(blackout -> assertThat(blackout.getKind()).isEqualTo(BlackoutKind.SUSPENSION));
    }

    @Test
    @DisplayName("Un horario que no es el inicio de un turno de la grilla queda afuera")
    void slotOutsideTheGridIsLeftOut() {
        MarkResult result = tournamentService.mark(club, "Torneo", List.of(slot(court1, 18, 7)));

        assertThat(result.marked()).isZero();
        assertThat(result.leftOut()).hasSize(1);
        assertThat(blackouts()).isEmpty();
    }

    @Test
    @DisplayName("Sin nombre no se guarda nada")
    void blankNameIsRejected() {
        assertThatThrownBy(() -> tournamentService.mark(club, "   ", List.of(slot(court1, 18, 30))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nombre del torneo");
        assertThat(blackouts()).isEmpty();
    }

    @Test
    @DisplayName("Un torneo que sigue pasada la medianoche queda en una sola suspension")
    void tournamentAcrossMidnightStaysTogether() {
        // 08:00 a 02:00 son 18 horas: el ultimo turno es 00:30 a 02:00 del miercoles.
        club.setCloseTime(LocalTime.of(2, 0));
        club = tenantRepository.saveAndFlush(club);
        Instant afterMidnight = TODAY.plusDays(1).atTime(0, 30).atZone(ZONE).toInstant();

        MarkResult result = tournamentService.mark(club, "Nocturno", List.of(
                slot(court1, 23, 0), new TournamentSlot(court1.getId(), afterMidnight)));

        assertThat(result.marked()).isEqualTo(2);
        assertThat(blackouts()).singleElement().satisfies(blackout -> {
            assertThat(blackout.getStartTime()).isEqualTo(at(23, 0));
            assertThat(blackout.getEndTime())
                    .isEqualTo(TODAY.plusDays(1).atTime(2, 0).atZone(ZONE).toInstant());
        });
    }

    @Test
    @DisplayName("La cancha del torneo no se ofrece en la grilla publica en esos turnos")
    void tournamentHidesTheCourtFromThePublicGrid() {
        PricingRule rule = new PricingRule();
        rule.addDay(DayOfWeek.TUESDAY);
        rule.setStartTime(LocalTime.of(8, 0));
        rule.setEndTime(LocalTime.of(23, 59));
        rule.setPrice(new BigDecimal("20000"));
        pricingRuleRepository.saveAndFlush(rule);

        tournamentService.mark(club, "Torneo", List.of(slot(court1, 18, 30)));

        assertThat(availabilityService.availabilityFor(club, TODAY).slots().stream()
                .filter(slot -> slot.startTime().equals(LocalTime.of(18, 30)))
                .findFirst().orElseThrow().available())
                .singleElement()
                .satisfies(available -> assertThat(available.courtName()).isEqualTo("Cancha 2"));
    }

    // ------------------------------------------------------------ utilidades

    private Court saveCourt(String name, int order) {
        Court court = new Court();
        court.setName(name);
        court.setDisplayOrder(order);
        return courtRepository.saveAndFlush(court);
    }

    private void bookAt(Court court, int hour, int minute) {
        Customer customer = new Customer();
        customer.setFullName("Jugador de prueba");
        customer.setPhoneNumber("+5492262415000");
        customer = customerRepository.saveAndFlush(customer);

        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setCustomer(customer);
        booking.setStartTime(at(hour, minute));
        booking.setEndTime(at(hour, minute).plusSeconds(90 * 60));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalPrice(new BigDecimal("20000"));
        bookingRepository.saveAndFlush(booking);
    }

    private TournamentSlot slot(Court court, int hour, int minute) {
        return new TournamentSlot(court.getId(), at(hour, minute));
    }

    private Instant at(int hour, int minute) {
        return TODAY.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    /** Con la cancha ya cargada, ordenadas por inicio. */
    private List<Blackout> blackouts() {
        return blackoutRepository.findUpcoming(Instant.EPOCH);
    }
}
