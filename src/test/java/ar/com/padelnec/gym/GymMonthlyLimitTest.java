package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.MutableClock;
import ar.com.padelnec.TestClockConfig;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.EntryLimit;
import ar.com.padelnec.gym.domain.GymCheckin;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.service.GymAllowance.Window;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymCheckinService.CheckInResult;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymOverviewService.MemberRow;
import ar.com.padelnec.gym.service.GymStatusService;
import ar.com.padelnec.service.ClubUserService;
import ar.com.padelnec.web.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * El tope de ingresos por mes: el club lo elige en «Cuotas» y cada socio reparte sus ingresos como
 * quiera dentro del periodo de su cuota, con los dias por semana proporcionales a lo que dura.
 *
 * <p>Las cuotas se cobran como en el mostrador ({@code charge}): el primer cobro fija el ancla, el
 * dia de corte de todos los meses del socio. El 15/10/2026 es jueves.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, TestClockConfig.class})
class GymMonthlyLimitTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

    @Autowired private GymCheckinService checkinService;
    @Autowired private GymCheckinRepository checkinRepository;
    @Autowired private GymMembershipService membershipService;
    @Autowired private GymStatusService statusService;
    @Autowired private GymOverviewService overviewService;
    @Autowired private ClubUserService clubUserService;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;
    @Autowired private Clock clock;

    private Tenant club;
    private GymSede sede;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        at(OCT_15);
        clubFixture.reset();
        club = clubFixture.club("los-troncos");
        gym.enable(club);
        gym.entryLimit(club, EntryLimit.MONTH);
        sede = gym.sede(club, "Necochea");
        memberId = gym.member(club, "30111222", "Juan Pérez").id();
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------ helpers

    private void at(LocalDate day) {
        ((MutableClock) clock).set(day.atTime(10, 0).atZone(ZONE).toInstant());
    }

    /** Cobra {@code months} cuotas mensuales, como el mostrador; {@code newStart} null = sigue su ciclo. */
    private void month(UUID member, LocalDate today, int months, int daysPerWeek, LocalDate newStart) {
        membershipService.charge(member, months, new BigDecimal("30000"), daysPerWeek, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, today, newStart, null);
    }

    private void month(LocalDate today, int months, int daysPerWeek) {
        month(memberId, today, months, daysPerWeek, null);
    }

    private CheckInResult scanOn(UUID member, LocalDate day) {
        at(day);
        return checkinService.checkIn(member, sede.getQrToken(), null);
    }

    private CheckInResult scanOn(LocalDate day) {
        return scanOn(memberId, day);
    }

    // ------------------------------------------------------------- tests

    @Test
    @DisplayName("Reparte los ingresos del mes como quiera: 4 en una semana entran, y pasado el tope se rechaza")
    void monthlyLimitLetsTheMemberSpreadEntries() {
        // 2 dias por semana, del 15/10 al 14/11: 31 dias, 9 ingresos.
        month(OCT_15, 1, 2);

        // Lunes a jueves de la misma semana: con el tope semanal, el miercoles ya no entraria.
        for (int day = 19; day <= 22; day++) {
            assertThat(scanOn(LocalDate.of(2026, 10, day)).alreadyRegistered()).isFalse();
        }
        scanOn(LocalDate.of(2026, 10, 26));
        scanOn(LocalDate.of(2026, 10, 30));
        scanOn(LocalDate.of(2026, 11, 3));
        scanOn(LocalDate.of(2026, 11, 6));
        CheckInResult ninth = scanOn(LocalDate.of(2026, 11, 10));

        assertThat(ninth.used()).isEqualTo(9);
        assertThat(ninth.limit()).isEqualTo(9);
        assertThat(ninth.window()).isEqualTo(Window.MONTH);
        assertThat(ninth.windowEnd()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThatThrownBy(() -> scanOn(LocalDate.of(2026, 11, 12)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya usaste tus 9 ingresos de este mes. Tu mes va hasta el 14/11/2026");
    }

    @Test
    @DisplayName("El mes es el de la cuota del socio (del 15 al 14), no el mes calendario")
    void windowFollowsTheAnchorNotTheCalendarMonth() {
        month(OCT_15, 2, 2);

        scanOn(LocalDate.of(2026, 10, 31));
        // El 1/11 sigue siendo su mes del 15/10 al 14/11.
        assertThat(scanOn(LocalDate.of(2026, 11, 1)).used()).isEqualTo(2);

        // El 15/11 arranca su mes nuevo, de 30 dias: vuelve a cero y tiene 9.
        CheckInResult newMonth = scanOn(LocalDate.of(2026, 11, 15));
        assertThat(newMonth.used()).isEqualTo(1);
        assertThat(newMonth.limit()).isEqualTo(9);
        assertThat(newMonth.windowEnd()).isEqualTo(LocalDate.of(2026, 12, 14));

        // "Mi estado" cuenta lo mismo.
        GymStatusService.Status status = statusService.of(memberId);
        assertThat(status.used()).isEqualTo(1);
        assertThat(status.limit()).isEqualTo(9);
        assertThat(status.window()).isEqualTo(Window.MONTH);
        assertThat(status.windowEnd()).isEqualTo(LocalDate.of(2026, 12, 14));
    }

    @Test
    @DisplayName("En un periodo de 28 dias (el que arranca en febrero) 2 dias por semana son 8")
    void aShortPeriodHasFewerEntries() {
        LocalDate feb15 = LocalDate.of(2027, 2, 15);
        at(feb15);
        month(feb15, 1, 2);

        CheckInResult result = scanOn(feb15);

        assertThat(result.limit()).isEqualTo(8);
        assertThat(result.windowEnd()).isEqualTo(LocalDate.of(2027, 3, 14));
    }

    @Test
    @DisplayName("Con el mes impago (gracia), lo que entra cuenta para ese mes y no para el anterior")
    void graceEntriesCountForTheUnpaidMonth() {
        LocalDate sep15 = LocalDate.of(2026, 9, 15);
        at(sep15);
        // 4 dias por semana: del 15/09 al 14/10 (30 dias) son 17; del 15/10 al 14/11 (31), 18.
        month(sep15, 1, 4);
        scanOn(LocalDate.of(2026, 10, 13));
        CheckInResult lastPaidDay = scanOn(LocalDate.of(2026, 10, 14));
        assertThat(lastPaidDay.used()).isEqualTo(2);
        assertThat(lastPaidDay.limit()).isEqualTo(17);

        CheckInResult grace = scanOn(LocalDate.of(2026, 10, 20));

        assertThat(grace.used()).isEqualTo(1);
        assertThat(grace.limit()).isEqualTo(18);
        assertThat(grace.windowEnd()).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    @DisplayName("El pase de 15 dias se cuenta en sus 15 dias: con 2 dias por semana son 4")
    void passCountsItsFifteenDays() {
        LocalDate oct21 = LocalDate.of(2026, 10, 21);
        at(oct21);
        membershipService.chargePass(memberId, null, new BigDecimal("18000"), 2, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, oct21, null, false);

        scanOn(oct21);
        scanOn(LocalDate.of(2026, 10, 22));
        scanOn(LocalDate.of(2026, 10, 23));
        CheckInResult fourth = scanOn(LocalDate.of(2026, 10, 24));

        assertThat(fourth.window()).isEqualTo(Window.PASS);
        assertThat(fourth.limit()).isEqualTo(4);
        assertThat(fourth.windowEnd()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThatThrownBy(() -> scanOn(LocalDate.of(2026, 10, 25)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya usaste los 4 ingresos de tu pase, que vale hasta el 04/11/2026");
    }

    @Test
    @DisplayName("Con 7 dias por semana entra todos los dias del periodo")
    void sevenDaysPerWeekEntersEveryDay() {
        month(OCT_15, 1, 7);

        CheckInResult last = null;
        for (LocalDate day = OCT_15; !day.isAfter(LocalDate.of(2026, 11, 14)); day = day.plusDays(1)) {
            last = scanOn(day);
            assertThat(last.alreadyRegistered()).isFalse();
        }

        assertThat(last.used()).isEqualTo(31);
        assertThat(last.limit()).isEqualTo(31);
    }

    @Test
    @DisplayName("El mostrador puede dejar pasar por encima del tope del mes, y queda como excepcion")
    void staffCanOverrideTheMonthlyLimit() {
        // 1 dia por semana en 31 dias: 4 ingresos, y los usa en la primera semana.
        month(OCT_15, 1, 1);
        for (int day = 15; day <= 18; day++) {
            scanOn(LocalDate.of(2026, 10, day));
        }
        LocalDate oct19 = LocalDate.of(2026, 10, 19);
        UUID staff = clubUserService.createStaff(club, "Mostrador", "mostrador@troncos.test", "clave-larga-1").getId();

        assertThatThrownBy(() -> scanOn(oct19))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya usaste tus 4 ingresos de este mes");
        CheckInResult forced = checkinService.forceCheckIn(memberId, sede.getId(), staff);

        assertThat(forced.used()).isEqualTo(5);
        GymCheckin saved = checkinRepository.findByMemberIdAndLocalDate(memberId, oct19).orElseThrow();
        assertThat(saved.isOverride()).isTrue();
    }

    @Test
    @DisplayName("Cambiar de modo a mitad del periodo cuenta lo que el socio ya uso, y volver a semanal tambien")
    void switchingModeMidPeriodCountsWhatWasUsed() {
        gym.entryLimit(club, EntryLimit.WEEK);
        month(OCT_15, 1, 2);
        // Dos por semana, tres semanas: jueves y viernes, lunes y martes, lunes y martes.
        for (int day : new int[] {15, 16, 19, 20, 26, 27}) {
            scanOn(LocalDate.of(2026, 10, day));
        }

        gym.entryLimit(club, EntryLimit.MONTH);
        CheckInResult monthly = scanOn(LocalDate.of(2026, 10, 28));
        assertThat(monthly.used()).isEqualTo(7);
        assertThat(monthly.limit()).isEqualTo(9);

        // De vuelta a semanal: lunes, martes y miercoles de esta semana ya pasan el tope de 2.
        gym.entryLimit(club, EntryLimit.WEEK);
        assertThatThrownBy(() -> scanOn(LocalDate.of(2026, 10, 29)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya usaste tus 2 días de esta semana");
    }

    @Test
    @DisplayName("Con un nuevo inicio de mes a futuro, hasta que arranca cuenta la cuota de antes")
    void aFutureNewStartCountsTheFeeThatCoversToday() {
        LocalDate oct1 = LocalDate.of(2026, 10, 1);
        at(oct1);
        month(oct1, 1, 2);
        scanOn(LocalDate.of(2026, 10, 2));
        LocalDate oct20 = LocalDate.of(2026, 10, 20);
        month(memberId, oct20, 1, 2, LocalDate.of(2026, 11, 10));

        CheckInResult result = scanOn(oct20);

        assertThat(result.used()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(9);
        assertThat(result.window()).isEqualTo(Window.MONTH);
        assertThat(result.windowEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("La grilla del panel cuenta a cada socio en su propio mes")
    void panelCountsEachMemberInTheirOwnMonth() {
        // Ana arranco el 15/10; Beto el 01/10 (y no pago noviembre: esta en gracia).
        UUID ana = memberId;
        month(OCT_15, 1, 2);
        UUID beto = gym.member(club, "40222333", "Beto Gómez").id();
        LocalDate oct1 = LocalDate.of(2026, 10, 1);
        month(beto, oct1, 1, 2, null);
        // Carla vino con un pase en septiembre, que ya vencio: su ventana arranca hace mas de un mes.
        UUID carla = gym.member(club, "50333444", "Carla Paso").id();
        LocalDate sep1 = LocalDate.of(2026, 9, 1);
        membershipService.chargePass(carla, null, new BigDecimal("18000"), 2, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, sep1, null, false);
        scanOn(carla, LocalDate.of(2026, 9, 2));
        scanOn(carla, LocalDate.of(2026, 9, 3));

        for (UUID member : new UUID[] {ana, beto}) {
            scanOn(member, LocalDate.of(2026, 10, 16));
            scanOn(member, LocalDate.of(2026, 10, 20));
        }
        LocalDate nov3 = LocalDate.of(2026, 11, 3);
        scanOn(ana, nov3);
        scanOn(beto, nov3);

        Map<UUID, MemberRow> rows = overviewService.members().stream()
                .collect(Collectors.toMap(MemberRow::id, Function.identity()));

        // Ana: su mes va del 15/10 al 14/11 y entro 3 veces.
        assertThat(rows.get(ana).used()).isEqualTo(3);
        assertThat(rows.get(ana).allowance().to()).isEqualTo(LocalDate.of(2026, 11, 14));
        // Beto: su mes es noviembre (de 30 dias, 9 ingresos); lo de octubre era el mes anterior.
        assertThat(rows.get(beto).used()).isEqualTo(1);
        assertThat(rows.get(beto).allowance().limit()).isEqualTo(9);
        assertThat(rows.get(beto).allowance().to()).isEqualTo(LocalDate.of(2026, 11, 30));
        // Carla: su pase vencido se cuenta aparte (no entra en los ingresos recientes).
        assertThat(rows.get(carla).used()).isEqualTo(2);
        assertThat(rows.get(carla).allowance().window()).isEqualTo(Window.PASS);
    }
}
