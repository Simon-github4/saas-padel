package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.gym.service.GymBillingService;
import ar.com.padelnec.gym.service.GymBillingService.Status;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymTariffService;
import ar.com.padelnec.web.BusinessRuleException;
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
    @Autowired private GymOverviewService overviewService;
    @Autowired private GymMemberRepository memberRepository;
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
                sede.getId(), Set.of(sede.getId()), null, today, null, null);
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
        assertThat(status.pending()).hasSize(GymBillingService.LOOK_AHEAD + 1);
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
        assertThat(status.pending()).hasSize(GymBillingService.LOOK_AHEAD + 2);
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
    @DisplayName("Al cobrar con más días por semana, la cuota nueva cambia el plan del socio")
    void planCanChangeItsDaysPerWeek() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        charge(1, new BigDecimal("30000"), 3, today);

        // Pagó 3 días y ahora quiere ir 4: la próxima cuota sale con el plan nuevo.
        membershipService.charge(memberId, 1, new BigDecimal("40000"), 4,
                ar.com.padelnec.gym.domain.PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, LocalDate.of(2026, 7, 20), null, null);

        Status status = billing.status(memberId, LocalDate.of(2026, 7, 20));
        assertThat(status.planDaysPerWeek()).isEqualTo(4);
        // El plan lo marca la última cuota: la de 4 días es la más nueva.
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId))
                .extracting(GymMembership::getDaysPerWeek)
                .containsExactly(4, 3);
    }

    @Test
    @DisplayName("La tarifa se fija por dias por semana y auto-completa el monto del cobro")
    void tariffsDriveTheAutomaticPrice() {
        tariffService.setPrice(3, new BigDecimal("35000"));

        assertThat(tariffService.priceOf(3)).isEqualByComparingTo("35000");
        assertThat(tariffService.priceOf(4)).isNull();

        // Sin monto a mano, la cuota del plan (3 dias) sale con la tarifa.
        membershipService.charge(memberId, 1, null, 3, ar.com.padelnec.gym.domain.PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, LocalDate.of(2026, 6, 15), null, null);

        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId).getFirst()
                .getPrice()).isEqualByComparingTo("35000");
    }

    /** Un cobro con las fechas del mostrador: si arranca el mes de nuevo y cuando se cobro. */
    private void chargeWithDates(int months, LocalDate today, LocalDate newStart, LocalDate paidOn) {
        membershipService.charge(memberId, months, new BigDecimal("30000"), 3, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, today, newStart, paidOn);
    }

    @Test
    @DisplayName("Un socio que ya venia se carga con su mes real: arranco el 01/09, se lo carga el 23/09")
    void aMemberWhoAlreadyCameKeepsTheirRealCycle() {
        LocalDate today = LocalDate.of(2026, 9, 23);
        LocalDate sept1 = LocalDate.of(2026, 9, 1);

        Status before = billing.status(loadMember(), today, sept1);
        assertThat(before.pending().getFirst().start()).isEqualTo(sept1);
        assertThat(before.pending().getFirst().end()).isEqualTo(LocalDate.of(2026, 9, 30));

        chargeWithDates(1, today, sept1, LocalDate.of(2026, 9, 5));

        Status status = billing.status(memberId, today);
        assertThat(status.anchor()).isEqualTo(sept1);
        assertThat(status.periodStart()).isEqualTo(sept1);
        assertThat(status.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(status.paidCurrent()).isTrue();
        assertThat(status.canEnter()).isTrue();
        // La renovacion sigue su ciclo: del 01/10 al 31/10.
        assertThat(status.pending().getFirst().start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(status.pending().getFirst().end()).isEqualTo(LocalDate.of(2026, 10, 31));

        GymMembership cuota = membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId)
                .getFirst();
        assertThat(cuota.getStartsOn()).isEqualTo(sept1);
        assertThat(cuota.getPaidOn()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("Si el primer mes arranco hace meses, se ofrecen todos hasta el corriente y los adelantos")
    void aFirstStartMonthsAgoOffersEveryMonthUpToNow() {
        Status status = billing.status(loadMember(), LocalDate.of(2026, 9, 23), LocalDate.of(2026, 6, 1));

        assertThat(status.pending()).hasSize(4 + GymBillingService.LOOK_AHEAD);
        assertThat(status.pending().getFirst().start()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(status.periodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(status.canEnter()).isFalse();
    }

    @Test
    @DisplayName("Pagar antes no adelanta el periodo: cobrado el 25/09, la cuota es del 01/10 al 31/10")
    void anEarlyPaymentDoesNotMoveThePeriod() {
        chargeWithDates(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null);

        chargeWithDates(1, LocalDate.of(2026, 9, 25), null, LocalDate.of(2026, 9, 25));

        GymMembership renewal = membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId)
                .getFirst();
        assertThat(renewal.getStartsOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(renewal.getEndsOn()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(renewal.getPaidOn()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    @DisplayName("El que vuelve despues de meses arranca su mes de nuevo: sin deuda de lo que no vino")
    void aNewStartRestartsTheCycleWithoutDebt() {
        chargeWithDates(1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), null);
        LocalDate today = LocalDate.of(2026, 9, 23);
        assertThat(billing.status(memberId, today).monthsLate()).isEqualTo(3);

        chargeWithDates(1, today, LocalDate.of(2026, 9, 18), null);

        Status status = billing.status(memberId, today);
        assertThat(status.anchor()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(status.periodStart()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(status.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(status.monthsLate()).isZero();
        assertThat(status.canEnter()).isTrue();
        // Las renovaciones siguen desde la fecha nueva: 18/10 al 17/11.
        assertThat(status.pending().getFirst().start()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(status.pending().getFirst().end()).isEqualTo(LocalDate.of(2026, 11, 17));
        // Julio y agosto no se generaron como cuotas.
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId)).hasSize(2);
    }

    @Test
    @DisplayName("Sin fecha nueva el ciclo sigue como venia; con una, no puede pisar lo ya pagado")
    void aNewStartCannotOverlapWhatIsPaid() {
        chargeWithDates(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null);

        assertThatThrownBy(() -> chargeWithDates(1, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("30/09/2026");

        chargeWithDates(1, LocalDate.of(2026, 9, 20), null, null);
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId).getFirst()
                .getStartsOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(billing.status(memberId, LocalDate.of(2026, 9, 20)).anchor()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("Sin fecha de cobro, se cobro hoy; una fecha de cobro futura se rechaza")
    void paidOnDefaultsToTodayAndCannotBeInTheFuture() {
        LocalDate today = LocalDate.of(2026, 9, 23);

        assertThatThrownBy(() -> chargeWithDates(1, today, null, today.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("futura");

        chargeWithDates(1, today, null, null);
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId).getFirst()
                .getPaidOn()).isEqualTo(today);
    }

    @Test
    @DisplayName("Un inicio a mas de un año (un error de tipeo) se rechaza")
    void aFirstStartTooFarAwayIsRejected() {
        LocalDate today = LocalDate.of(2026, 9, 23);

        assertThatThrownBy(() -> chargeWithDates(1, today, LocalDate.of(2025, 9, 1), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> chargeWithDates(1, today, LocalDate.of(2027, 10, 1), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Los cobros del dia van por fecha de cobro: el de ayer cargado hoy es de ayer")
    void dayPaymentsFollowThePaidDate() {
        LocalDate today = overviewService.today();
        LocalDate yesterday = today.minusDays(1);

        chargeWithDates(1, today, today, yesterday);

        assertThat(overviewService.paymentsOf(yesterday)).hasSize(1);
        assertThat(overviewService.paymentsOf(yesterday).getFirst().paidOn()).isEqualTo(yesterday);
        assertThat(overviewService.paymentsOf(today)).isEmpty();
    }

    private GymMember loadMember() {
        return memberRepository.findById(memberId).orElseThrow();
    }

    @Test
    @DisplayName("Corregir los dias de una cuota cobrada no cambia sus fechas ni agrega otra")
    void correctingAFeeKeepsItsPeriod() {
        chargeWithDates(1, LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 2), null);
        UUID fee = membershipService.feesOf(memberId).getFirst().id();

        membershipService.correct(fee, 2, new BigDecimal("25000"));

        assertThat(membershipService.feesOf(memberId)).singleElement().satisfies(corrected -> {
            assertThat(corrected.startsOn()).isEqualTo(LocalDate.of(2026, 9, 2));
            assertThat(corrected.endsOn()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(corrected.daysPerWeek()).isEqualTo(2);
            assertThat(corrected.price()).isEqualByComparingTo("25000");
        });
        assertThat(billing.status(memberId, LocalDate.of(2026, 9, 23)).planDaysPerWeek()).isEqualTo(2);
        assertThatThrownBy(() -> membershipService.correct(fee, 8, new BigDecimal("25000")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Anular la cuota cobrada de mas deja al socio al dia hasta la anterior")
    void voidingTheExtraFeeShortensThePaidPeriod() {
        LocalDate today = LocalDate.of(2026, 9, 23);
        chargeWithDates(1, today, LocalDate.of(2026, 9, 2), null);
        chargeWithDates(1, today, null, null);
        assertThat(billing.status(memberId, today).paidUntil()).isEqualTo(LocalDate.of(2026, 11, 1));

        membershipService.voidMembership(membershipService.feesOf(memberId).getFirst().id());

        // La anulada queda en la lista, de constancia.
        assertThat(membershipService.feesOf(memberId)).extracting(GymMembershipService.Fee::voided)
                .containsExactly(true, false);
        Status status = billing.status(memberId, today);
        assertThat(status.paidUntil()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(status.anchor()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(overviewService.paymentsOf(today)).hasSize(1);
    }

    @Test
    @DisplayName("Si se anula la unica cuota, el socio vuelve a estar como el que nunca pago")
    void voidingTheOnlyFeeResetsTheCycle() {
        LocalDate today = LocalDate.of(2026, 9, 23);
        chargeWithDates(1, today, LocalDate.of(2026, 9, 2), null);

        membershipService.voidMembership(membershipService.feesOf(memberId).getFirst().id());

        Status status = billing.status(memberId, today);
        assertThat(status.anchor()).isNull();
        assertThat(status.plan()).isNull();
        assertThat(status.pending().getFirst().start()).isEqualTo(today);
    }
}
