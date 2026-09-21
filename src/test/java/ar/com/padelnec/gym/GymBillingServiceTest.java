package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.gym.service.GymBillingService;
import ar.com.padelnec.gym.service.GymBillingService.Status;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymTariffService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * El ciclo mensual de las cuotas: los periodos salen del dia de corte (el ancla),
 * lo impago se acumula como deuda y la primera cuota fija el dia de corte del socio.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class})
class GymBillingServiceTest {

    @Autowired private GymBillingService billing;
    @Autowired private GymMembershipService membershipService;
    @Autowired private GymMembershipRepository membershipRepository;
    @Autowired private GymTariffService tariffService;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;

    private Tenant club;
    private GymSede sede;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        clubFixture.reset();
        club = clubFixture.club("los-troncos");
        gym.enable(club);
        sede = gym.sede(club, "Necochea");
        memberId = gym.member(club, "30111222", "Juan Pérez").id();
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** La primera cuota del socio, cobrada con el servicio de cobro del ciclo. */
    private void charge(int months, BigDecimal price, int daysPerWeek, LocalDate today) {
        membershipService.charge(memberId, months, price, daysPerWeek, ar.com.padelnec.gym.domain.PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, today);
    }

    @Test
    @DisplayName("La cuota va del dia del corte al dia anterior: mayo 15 es del 15/05 al 14/06")
    void periodMathKeepsTheCutoffDay() {
        LocalDate anchor = LocalDate.of(2026, 5, 15);
        assertThat(GymBillingService.periodStart(anchor, YearMonth.of(2026, 5))).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(GymBillingService.periodEnd(anchor, YearMonth.of(2026, 5))).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(GymBillingService.periodStart(anchor, YearMonth.of(2026, 6))).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("Un ancla en dia 31 baja al ultimo dia del mes corto")
    void anAnchorOnThe31stClampsToShortMonths() {
        LocalDate anchor = LocalDate.of(2026, 1, 31);
        // Febrero de 2026 tiene 28 dias: el periodo de febrero arranca y termina el 28.
        assertThat(GymBillingService.periodStart(anchor, YearMonth.of(2026, 2))).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(GymBillingService.periodEnd(anchor, YearMonth.of(2026, 2))).isEqualTo(LocalDate.of(2026, 3, 30));
    }

    @Test
    @DisplayName("En el dia del corte arranca el periodo nuevo, no el que termina ese mismo dia")
    void theCutoffDayBelongsToTheNewPeriod() {
        LocalDate anchor = LocalDate.of(2026, 5, 15);
        // El 14/06 cierra mayo, el 15/06 ya es del periodo de junio.
        assertThat(GymBillingService.currentYearMonth(anchor, LocalDate.of(2026, 6, 14)))
                .isEqualTo(YearMonth.of(2026, 5));
        assertThat(GymBillingService.currentYearMonth(anchor, LocalDate.of(2026, 6, 15)))
                .isEqualTo(YearMonth.of(2026, 6));
    }

    @Test
    @DisplayName("La primera cuota fija el ancla y arranca al dia: estado al día")
    void theFirstChargeSeedsTheAnchor() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        charge(1, new BigDecimal("30000"), 3, today);

        Status status = billing.status(memberId, today);
        assertThat(status.anchor()).isEqualTo(today);
        assertThat(status.periodStart()).isEqualTo(today);
        assertThat(status.periodEnd()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(status.paidCurrent()).isTrue();
        assertThat(status.monthsLate()).isZero();
        // Al dia, lo que queda por cobrar es la proxima cuota y hasta 4 adelantos.
        assertThat(status.pending()).hasSize(GymBillingService.LOOK_AHEAD);
        assertThat(status.pending().get(0).start()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(status.canEnter()).isTrue();
    }

    @Test
    @DisplayName("Sin pagar el mes, la deuda es de uno y el socio puede entrar igual (gracia)")
    void oneUnpaidMonthIsGrace() {
        charge(1, new BigDecimal("30000"), 3, LocalDate.of(2026, 6, 15));

        Status status = billing.status(memberId, LocalDate.of(2026, 7, 20));
        assertThat(status.paidCurrent()).isFalse();
        assertThat(status.monthsLate()).isEqualTo(1);
        assertThat(status.canEnter()).isTrue();
        // La deuda primero, y despues los adelantos a futuro.
        assertThat(status.pending().getFirst().start()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(status.pending()).hasSize(12);
        assertThat(status.paidUntil()).isNull();
    }

    @Test
    @DisplayName("Dos meses seguidos impagos bloquean el ingreso y suman la deuda")
    void twoUnpaidMonthsBlockAndOwe() {
        charge(1, new BigDecimal("30000"), 3, LocalDate.of(2026, 6, 15));

        Status status = billing.status(memberId, LocalDate.of(2026, 8, 20));
        assertThat(status.monthsLate()).isEqualTo(2);
        assertThat(status.canEnter()).isFalse();
        // La deuda se cobra de la mas vieja a la mas nueva: julio y agosto.
        assertThat(status.pending()).hasSize(13);
        assertThat(status.pending().get(0).start()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(status.pending().get(1).start()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("Cobrar dos meses con dos impagos llena los dos huecos y deja el ciclo al día")
    void chargingTwoDebtMonthsFillsThem() {
        charge(1, new BigDecimal("30000"), 3, LocalDate.of(2026, 6, 15));
        LocalDate payday = LocalDate.of(2026, 8, 20);

        charge(2, new BigDecimal("30000"), 3, payday);

        Status status = billing.status(memberId, payday);
        assertThat(status.paidCurrent()).isTrue();
        assertThat(status.monthsLate()).isZero();
        assertThat(status.canEnter()).isTrue();
        assertThat(membershipRepository.count()).isEqualTo(3);
        // Dos filas: una por mes, sin la superposicion de las ventas viejas de varios meses.
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId))
                .extracting(ar.com.padelnec.gym.domain.GymMembership::getStartsOn)
                .containsExactly(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("El que nunca pagó no tiene ancla todavía: lo fija la primera cuota")
    void aMemberWhoNeverPaidCannotEnter() {
        Status status = billing.status(memberId, LocalDate.of(2026, 9, 1));
        assertThat(status.anchor()).isNull();
        assertThat(status.plan()).isNull();
        assertThat(status.canEnter()).isFalse();
        // Ya se pueden adelantar de a varias cuotas aunque sea el primer cobro.
        assertThat(status.pending()).hasSize(GymBillingService.LOOK_AHEAD + 1);
        assertThat(status.pending().getFirst().start()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("Cobrar dos cuotas por adelantado deja la cobertura hasta el fin de la segunda")
    void prepaidMonthsShowPaidUntilAhead() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        charge(2, new BigDecimal("30000"), 3, today);

        Status status = billing.status(memberId, today);
        assertThat(status.paidCurrent()).isTrue();
        assertThat(status.monthsLate()).isZero();
        assertThat(status.canEnter()).isTrue();
        // Dos filas de un mes: la corriente y la que adelanta.
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId))
                .extracting(GymMembership::getStartsOn)
                .containsExactly(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 6, 15));
        // La cobertura llega al fin de la segunda cuota: el siguiente corte (15/08) menos un dia.
        assertThat(status.paidUntil()).isEqualTo(LocalDate.of(2026, 8, 14));
        // Las dos que ya se pagaron no se vuelven a ofrecer: el que viene despues es el corte nuevo.
        assertThat(status.pending().getFirst().start()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("Un socio con una cuota vieja de varios meses (antes de las cuotas por ciclo) queda cubierto")
    void legacyMultiMonthSaleCoversItsMonths() {
        gym.sell(club, memberId, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 8, 14), 3, sede);

        Status status = billing.status(memberId, LocalDate.of(2026, 8, 13));
        assertThat(status.anchor()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(status.paidCurrent()).isTrue();
        assertThat(status.monthsLate()).isZero();
        assertThat(status.canEnter()).isTrue();
    }

    @Test
    @DisplayName("La tarifa se fija por dias por semana y auto-completa el monto del cobro")
    void tariffsDriveTheAutomaticPrice() {
        tariffService.setPrice(3, new BigDecimal("35000"));

        assertThat(tariffService.priceOf(3)).isEqualByComparingTo("35000");
        assertThat(tariffService.priceOf(4)).isNull();

        // Sin monto a mano, la cuota del plan (3 dias) sale con la tarifa.
        membershipService.charge(memberId, 1, null, 3, ar.com.padelnec.gym.domain.PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, LocalDate.of(2026, 6, 15));

        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId).getFirst()
                .getPrice()).isEqualByComparingTo("35000");
    }
}