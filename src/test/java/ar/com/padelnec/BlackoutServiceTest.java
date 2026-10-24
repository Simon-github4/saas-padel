package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BlackoutKind;
import ar.com.padelnec.gym.GymFixture;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.service.BlackoutService;
import ar.com.padelnec.web.BusinessRuleException;
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

/** Liberar un turno de una suspension o de un torneo, recortandola. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, TestClockConfig.class})
class BlackoutServiceTest {

    /** Martes 01/09/2026, 07:00 hora de Necochea (10:00 UTC). */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired private BlackoutService blackoutService;
    @Autowired private AvailabilityService availabilityService;
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

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court1 = fixture.court("Cancha 1", 1);
        court2 = fixture.court("Cancha 2", 2);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Liberar un turno del medio parte el torneo en dos, con el mismo nombre y tipo")
    void releasingAMiddleSlotSplitsTheTournament() {
        tournament(court1, at(17, 0), at(21, 30));

        blackoutService.release(club, court1.getId(), at(18, 30));

        assertThat(blackouts())
                .extracting(Blackout::getStartTime, Blackout::getEndTime, Blackout::getKind, Blackout::getReason)
                .containsExactly(
                        tuple(at(17, 0), at(18, 30), BlackoutKind.TOURNAMENT, "Torneo Apertura"),
                        tuple(at(20, 0), at(21, 30), BlackoutKind.TOURNAMENT, "Torneo Apertura"));
    }

    @Test
    @DisplayName("Liberar el primer o el ultimo turno acorta la suspension")
    void releasingAnEdgeSlotTrimsIt() {
        tournament(court1, at(17, 0), at(21, 30));

        blackoutService.release(club, court1.getId(), at(17, 0));
        blackoutService.release(club, court1.getId(), at(20, 0));

        assertThat(blackouts()).singleElement().satisfies(blackout -> {
            assertThat(blackout.getStartTime()).isEqualTo(at(18, 30));
            assertThat(blackout.getEndTime()).isEqualTo(at(20, 0));
        });
    }

    @Test
    @DisplayName("Liberar el unico turno que cubria la borra")
    void releasingTheOnlySlotDeletesIt() {
        tournament(court1, at(18, 30), at(20, 0));

        blackoutService.release(club, court1.getId(), at(18, 30));

        assertThat(blackouts()).isEmpty();
    }

    @Test
    @DisplayName("Una suspension de todo el club pasa a una por cancha, y solo se libera la elegida")
    void clubWideSuspensionIsSplitByCourt() {
        Blackout rain = new Blackout();
        rain.setStartTime(at(17, 0));
        rain.setEndTime(at(21, 30));
        rain.setReason("Lluvia");
        blackoutRepository.saveAndFlush(rain);

        blackoutService.release(club, court1.getId(), at(18, 30));

        assertThat(blackouts())
                .extracting(blackout -> blackout.getCourt().getName(), Blackout::getStartTime, Blackout::getEndTime,
                        Blackout::getKind)
                .containsExactlyInAnyOrder(
                        tuple("Cancha 1", at(17, 0), at(18, 30), BlackoutKind.SUSPENSION),
                        tuple("Cancha 1", at(20, 0), at(21, 30), BlackoutKind.SUSPENSION),
                        tuple("Cancha 2", at(17, 0), at(21, 30), BlackoutKind.SUSPENSION));
        assertThat(availabilityService.availabilityFor(club, TODAY).slots().stream()
                .filter(slot -> slot.startTime().equals(LocalTime.of(18, 30)))
                .findFirst().orElseThrow().available())
                .singleElement()
                .satisfies(available -> assertThat(available.courtName()).isEqualTo("Cancha 1"));
    }

    @Test
    @DisplayName("Un turno que ya empezo no se libera")
    void pastSlotIsNotReleased() {
        tournament(court1, at(17, 0), at(21, 30));
        // Son las 19:00 de Necochea: el turno de 18:30 ya empezo.
        ((MutableClock) clock).set(Instant.parse("2026-09-01T22:00:00Z"));

        assertThatThrownBy(() -> blackoutService.release(club, court1.getId(), at(18, 30)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya empezó");
        assertThat(blackouts()).hasSize(1);
    }

    @Test
    @DisplayName("Un turno que no estaba suspendido avisa que ya estaba libre")
    void freeSlotSaysSo() {
        tournament(court1, at(17, 0), at(18, 30));

        assertThatThrownBy(() -> blackoutService.release(club, court2.getId(), at(17, 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya estaba libre");
        assertThat(blackouts()).hasSize(1);
    }

    // ------------------------------------------------------------ utilidades

    private void tournament(Court court, Instant from, Instant until) {
        Blackout blackout = new Blackout();
        blackout.setCourt(court);
        blackout.setKind(BlackoutKind.TOURNAMENT);
        blackout.setStartTime(from);
        blackout.setEndTime(until);
        blackout.setReason("Torneo Apertura");
        blackoutRepository.saveAndFlush(blackout);
    }

    private Instant at(int hour, int minute) {
        return TODAY.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    /** Con la cancha ya cargada, ordenadas por inicio. */
    private List<Blackout> blackouts() {
        return blackoutRepository.findUpcoming(Instant.EPOCH);
    }
}
