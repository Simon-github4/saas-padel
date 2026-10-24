package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.MutableClock;
import ar.com.padelnec.TestClockConfig;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.domain.PlanLength;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.gym.service.GymBillingService;
import ar.com.padelnec.gym.service.GymBillingService.Status;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymCheckinService.CheckInResult;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMembershipService.VoidEffect;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymOverviewService.Standing;
import ar.com.padelnec.gym.service.GymStatusService;
import ar.com.padelnec.gym.service.GymTariffService;
import ar.com.padelnec.web.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * El pase de 15 dias: vale 15 dias calendario, vence sin deuda ni gracia, se renueva o se
 * pasa al plan mensual, y convive con el ciclo mensual sin cambiarle nada al socio que no
 * tiene pases.
 *
 * <p>Usa el reloj fijo compartido, {@link TestClockConfig}: los cobros reciben el dia a mano y
 * el check-in sale del reloj.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, TestClockConfig.class})
class GymPassTest {

    /** Miercoles 21 de octubre de 2026, 10:00 en Buenos Aires. */
    private static final LocalDate OCT_21 = LocalDate.of(2026, 10, 21);
    private static final Instant OCT_21_10AM = Instant.parse("2026-10-21T13:00:00Z");

    @Autowired private GymBillingService billing;
    @Autowired private GymMembershipService membershipService;
    @Autowired private GymMembershipRepository membershipRepository;
    @Autowired private GymTariffService tariffService;
    @Autowired private GymCheckinService checkinService;
    @Autowired private GymStatusService statusService;
    @Autowired private GymOverviewService overviewService;
    @Autowired private GymMemberService memberService;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    private Tenant club;
    private GymSede sede;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(OCT_21_10AM);
        clubFixture.reset();
        club = clubFixture.club("los-troncos");
        gym.enable(club);
        sede = gym.sede(club, "Necochea");
        memberId = gym.member(club, "30111222", "Tomás Paso").id();
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------ helpers

    /** Un pase de 3 dias por semana, por $18.000; {@code start} null = desde cuando se pueda. */
    private UUID pass(LocalDate today, LocalDate start) {
        return pass(today, start, false);
    }

    private UUID pass(LocalDate today, LocalDate start, boolean dropMonthlyDebt) {
        return membershipService.chargePass(memberId, start, new BigDecimal("18000"), 3, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, today, null, dropMonthlyDebt);
    }

    /** Cuotas mensuales de 3 dias por $30.000, como las cobra el mostrador. */
    private void month(LocalDate today, int months, LocalDate newStart) {
        membershipService.charge(memberId, months, new BigDecimal("30000"), 3, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, today, newStart, null);
    }

    private Status at(LocalDate day) {
        return billing.status(memberId, day);
    }

    private GymMembership latest() {
        return membershipRepository.findFirstByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId).orElseThrow();
    }

    private CheckInResult scanOn(LocalDate day) {
        ((MutableClock) clock).set(day.atTime(10, 0).atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                .toInstant());
        return checkinService.checkIn(memberId, sede.getQrToken(), null);
    }

    // ------------------------------------------------- regresion del mensual

    @Test
    @DisplayName("Un socio sin pases nunca entra en las reglas del pase: al dia, con gracia, con deuda o por empezar")
    void aMonthlyMemberNeverFollowsPassRules() {
        month(LocalDate.of(2026, 6, 15), 1, null);

        Status paid = at(LocalDate.of(2026, 6, 20));
        assertThat(paid.onPasses()).isFalse();
        assertThat(paid.passRules()).isFalse();
        assertThat(paid.canEnter()).isTrue();
        assertThat(paid.paidUntil()).isEqualTo(LocalDate.of(2026, 7, 14));

        Status grace = at(LocalDate.of(2026, 7, 20));
        assertThat(grace.passRules()).isFalse();
        assertThat(grace.monthsLate()).isEqualTo(1);
        assertThat(grace.canEnter()).isTrue();
        assertThat(grace.active().getStartsOn()).isEqualTo(LocalDate.of(2026, 6, 15));

        Status owing = at(LocalDate.of(2026, 8, 20));
        assertThat(owing.passRules()).isFalse();
        assertThat(owing.monthsLate()).isEqualTo(2);
        assertThat(owing.canEnter()).isFalse();
        assertThat(owing.pending()).hasSize(2 + GymBillingService.LOOK_AHEAD);
    }

    @Test
    @DisplayName("La cuota mensual que todavia no empezo sigue igual: no entra y lo pago es su mes")
    void aFutureMonthlyFeeIsUnchanged() {
        month(OCT_21, 1, LocalDate.of(2026, 10, 26));

        Status status = at(OCT_21);
        assertThat(status.passRules()).isFalse();
        assertThat(status.started()).isFalse();
        assertThat(status.canEnter()).isFalse();
        assertThat(status.paidUntil()).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    @DisplayName("Las tarifas mensuales quedan como estaban y la deuda se cuenta con la mensual, no con la de 15 dias")
    void monthlyTariffsAndDebtAreUnchanged() {
        tariffService.setPrice(3, new BigDecimal("30000"));
        tariffService.setPrice(PlanLength.FIFTEEN_DAYS, 3, new BigDecimal("18000"));
        membershipService.charge(memberId, 1, null, 3, PayMethod.CASH, sede.getId(), Set.of(sede.getId()), null,
                LocalDate.of(2026, 6, 15), null, null);

        assertThat(tariffService.priceOf(3)).isEqualByComparingTo("30000");
        assertThat(tariffService.priceOf(PlanLength.MONTH, 3)).isEqualByComparingTo("30000");
        assertThat(tariffService.priceOf(PlanLength.FIFTEEN_DAYS, 3)).isEqualByComparingTo("18000");
        assertThat(latest().getPrice()).isEqualByComparingTo("30000");
        assertThat(latest().getPlanLength()).isEqualTo(PlanLength.MONTH);
        assertThat(at(LocalDate.of(2026, 8, 20)).owedTotal()).isEqualByComparingTo("60000");
        assertThat(tariffService.tariffs()).singleElement().satisfies(row -> {
            assertThat(row.monthPrice()).isEqualByComparingTo("30000");
            assertThat(row.fifteenDaysPrice()).isEqualByComparingTo("18000");
        });
    }

    // ----------------------------------------------------------- pase basico

    @Test
    @DisplayName("El pase vale 15 dias contando el primero y el ultimo: del 21/10 al 04/11")
    void aPassLastsFifteenCalendarDays() {
        pass(OCT_21, null);

        GymMembership pass = latest();
        assertThat(pass.getPlanLength()).isEqualTo(PlanLength.FIFTEEN_DAYS);
        assertThat(pass.getStartsOn()).isEqualTo(OCT_21);
        assertThat(pass.getEndsOn()).isEqualTo(LocalDate.of(2026, 11, 4));

        Status lastDay = at(LocalDate.of(2026, 11, 4));
        assertThat(lastDay.onPasses()).isTrue();
        assertThat(lastDay.canEnter()).isTrue();
        assertThat(lastDay.paidUntil()).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    @DisplayName("Vencido, el pase no genera deuda ni tiene gracia")
    void anExpiredPassOwesNothing() {
        pass(OCT_21, null);

        Status expired = at(LocalDate.of(2026, 11, 5));
        assertThat(expired.onPasses()).isTrue();
        assertThat(expired.started()).isTrue();
        assertThat(expired.canEnter()).isFalse();
        assertThat(expired.monthsLate()).isZero();
        assertThat(expired.owedTotal()).isEqualByComparingTo("0");
        assertThat(expired.pending()).isEmpty();
        assertThat(expired.paidUntil()).isNull();
        assertThat(expired.active().isPass()).isTrue();
        // Meses despues sigue sin deber nada.
        assertThat(at(LocalDate.of(2027, 3, 1)).monthsLate()).isZero();
    }

    @Test
    @DisplayName("Un pase que empieza en unos dias todavia no deja entrar")
    void aFuturePassIsNotYetValid() {
        pass(OCT_21, LocalDate.of(2026, 10, 24));

        Status status = at(OCT_21);
        assertThat(status.started()).isFalse();
        assertThat(status.canEnter()).isFalse();
        assertThat(status.active().getStartsOn()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(status.paidUntil()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("Sin fechas para atras, ni a mas de un año, ni pisando lo pago")
    void passDatesAreValidated() {
        assertThatThrownBy(() -> pass(OCT_21, OCT_21.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("antes de hoy");
        assertThatThrownBy(() -> pass(OCT_21, OCT_21.plusYears(1).plusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("próximo año");

        pass(OCT_21, null);
        assertThatThrownBy(() -> pass(OCT_21, LocalDate.of(2026, 10, 30)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("04/11/2026");
        assertThatThrownBy(() -> pass(OCT_21, LocalDate.of(2026, 11, 4)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("La base no deja guardar un pase que no dure 15 dias")
    void theDatabaseEnforcesFifteenDays() {
        String insert = """
                INSERT INTO gym_membership (club_id, member_id, starts_on, ends_on, days_per_week, price,
                                            pay_method, collected_sede_id, paid_on, plan_length)
                VALUES (?, ?, ?, ?, 3, 18000, 'CASH', ?, ?, 'FIFTEEN_DAYS')
                """;
        assertThatThrownBy(() -> jdbc.update(insert, club.getId(), memberId, OCT_21, OCT_21.plusDays(9),
                sede.getId(), OCT_21)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(insert, club.getId(), memberId, OCT_21, OCT_21.plusDays(14), sede.getId(), OCT_21);
        assertThat(latest().isPass()).isTrue();
    }

    @Test
    @DisplayName("El pase toma la tarifa de 15 dias; sin tarifa ni monto, se avisa")
    void aPassUsesItsOwnTariff() {
        tariffService.setPrice(PlanLength.MONTH, 3, new BigDecimal("30000"));
        tariffService.setPrice(PlanLength.FIFTEEN_DAYS, 3, new BigDecimal("18000"));

        membershipService.chargePass(memberId, null, null, 3, PayMethod.CASH, sede.getId(), Set.of(sede.getId()),
                null, OCT_21, null, false);
        assertThat(latest().getPrice()).isEqualByComparingTo("18000");

        assertThatThrownBy(() -> membershipService.chargePass(memberId, null, null, 2, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, OCT_21, null, false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cuota de 15 días");
    }

    // ------------------------------------------------- renovaciones y huecos

    @Test
    @DisplayName("Renovar arranca el dia despues del pase y la cobertura se estira")
    void renewingAPassChainsTheCoverage() {
        pass(OCT_21, null);
        pass(OCT_21, null);

        assertThat(latest().getStartsOn()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(latest().getEndsOn()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(at(OCT_21).paidUntil()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(at(LocalDate.of(2026, 11, 10)).canEnter()).isTrue();
    }

    @Test
    @DisplayName("Con un hueco entre dos pases, la cobertura no lo saltea y en el hueco no entra")
    void aGapBetweenPassesIsNotCovered() {
        pass(OCT_21, null);
        pass(OCT_21, LocalDate.of(2026, 11, 10));

        assertThat(at(OCT_21).paidUntil()).isEqualTo(LocalDate.of(2026, 11, 4));

        Status inTheGap = at(LocalDate.of(2026, 11, 7));
        assertThat(inTheGap.canEnter()).isFalse();
        assertThat(inTheGap.started()).isFalse();
        assertThat(inTheGap.active().getStartsOn()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(inTheGap.monthsLate()).isZero();
    }

    // ---------------------------------------------------------- transiciones

    @Test
    @DisplayName("Mensual al dia que pasa a pase: el mes rige hasta que termina, y despues del pase no debe nada")
    void fromAPaidMonthToAPass() {
        month(OCT_21, 1, OCT_21);
        pass(OCT_21, null);

        GymMembership pass = latest();
        assertThat(pass.getStartsOn()).isEqualTo(LocalDate.of(2026, 11, 21));

        Status today = at(OCT_21);
        assertThat(today.onPasses()).isTrue();
        assertThat(today.active().isPass()).isFalse();
        assertThat(today.canEnter()).isTrue();
        assertThat(today.paidUntil()).isEqualTo(LocalDate.of(2026, 12, 5));

        assertThat(at(LocalDate.of(2026, 11, 25)).active().isPass()).isTrue();

        Status after = at(LocalDate.of(2026, 12, 6));
        assertThat(after.canEnter()).isFalse();
        assertThat(after.monthsLate()).isZero();
        assertThat(after.owedTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Mensual con deuda que pasa a pase: sin confirmar se rechaza; confirmado, la deuda deja de contar")
    void fromAMonthWithDebtToAPassNeedsConfirmation() {
        month(LocalDate.of(2026, 9, 1), 1, LocalDate.of(2026, 9, 1));
        assertThat(at(OCT_21).monthsLate()).isEqualTo(1);

        assertThatThrownBy(() -> pass(OCT_21, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("una cuota mensual");

        pass(OCT_21, null, true);
        Status status = at(OCT_21);
        assertThat(status.onPasses()).isTrue();
        assertThat(status.monthsLate()).isZero();
        assertThat(status.owedTotal()).isEqualByComparingTo("0");
        assertThat(status.canEnter()).isTrue();
    }

    @Test
    @DisplayName("Al que nunca pago o ya esta con pases no se le pide confirmar nada")
    void noDebtConfirmationWithoutMonthlyDebt() {
        pass(OCT_21, null);
        pass(LocalDate.of(2026, 11, 20), null);

        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId)).hasSize(2);
    }

    @Test
    @DisplayName("Pase que pasa a mensual: el mes arranca despues del pase, entra por el pase y esos dias no son deuda")
    void fromAPassToAMonth() {
        pass(OCT_21, null);
        month(OCT_21, 1, null);

        GymMembership month = latest();
        assertThat(month.getPlanLength()).isEqualTo(PlanLength.MONTH);
        assertThat(month.getStartsOn()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(month.getEndsOn()).isEqualTo(LocalDate.of(2026, 12, 4));

        Status duringThePass = at(OCT_21);
        assertThat(duringThePass.onPasses()).isFalse();
        // Ya rige una cuota (el pase), aunque el ciclo mensual todavia no empezo.
        assertThat(duringThePass.started()).isTrue();
        assertThat(duringThePass.canEnter()).isTrue();
        assertThat(duringThePass.active().isPass()).isTrue();
        assertThat(duringThePass.passRules()).isTrue();
        assertThat(duringThePass.paidUntil()).isEqualTo(LocalDate.of(2026, 12, 4));

        Status monthly = at(LocalDate.of(2026, 11, 20));
        assertThat(monthly.anchor()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(monthly.passRules()).isFalse();
        assertThat(monthly.monthsLate()).isZero();

        Status nextMonth = at(LocalDate.of(2026, 12, 10));
        assertThat(nextMonth.monthsLate()).isEqualTo(1);
        assertThat(nextMonth.pending().getFirst().start()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    @DisplayName("Mes, pase, mes, pase, mes: ninguna deuda de un ciclo abandonado vuelve a aparecer")
    void manyTransitionsNeverBringBackOldDebt() {
        month(LocalDate.of(2026, 9, 1), 1, LocalDate.of(2026, 9, 1));
        pass(LocalDate.of(2026, 9, 25), null);
        assertThat(latest().getStartsOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        month(LocalDate.of(2026, 10, 10), 1, null);
        assertThat(latest().getStartsOn()).isEqualTo(LocalDate.of(2026, 10, 16));
        pass(LocalDate.of(2026, 11, 10), null);
        assertThat(latest().getStartsOn()).isEqualTo(LocalDate.of(2026, 11, 16));
        month(LocalDate.of(2026, 11, 20), 1, null);
        assertThat(latest().getStartsOn()).isEqualTo(LocalDate.of(2026, 12, 1));

        Status december = at(LocalDate.of(2026, 12, 10));
        assertThat(december.anchor()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(december.monthsLate()).isZero();
        assertThat(december.owedTotal()).isEqualByComparingTo("0");

        Status january = at(LocalDate.of(2027, 1, 10));
        assertThat(january.monthsLate()).isEqualTo(1);
        assertThat(january.pending().getFirst().start()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    // ------------------------------------------------------- socios activos

    @Test
    @DisplayName("Activo es el que tiene la cuota de hoy paga: mensual o pase, no el que debe el mes ni el que no empezo")
    void activeMeansTodaysFeeIsPaid() {
        month(LocalDate.of(2026, 6, 15), 1, null);
        assertThat(at(LocalDate.of(2026, 6, 20)).paidToday()).isTrue();
        // En la gracia entra, pero no tiene el mes pago.
        assertThat(at(LocalDate.of(2026, 7, 20)).paidToday()).isFalse();
        assertThat(at(LocalDate.of(2026, 8, 20)).paidToday()).isFalse();
    }

    @Test
    @DisplayName("Con pases, activo es el que tiene un pase (o un mes) que lo cubre hoy")
    void activeWithPasses() {
        UUID first = pass(OCT_21, null);
        assertThat(at(OCT_21).paidToday()).isTrue();
        assertThat(at(LocalDate.of(2026, 11, 4)).paidToday()).isTrue();
        assertThat(at(LocalDate.of(2026, 11, 5)).paidToday()).isFalse();

        // Pase que corre con el mensual por empezar: activo hoy por el pase.
        month(OCT_21, 1, null);
        assertThat(at(OCT_21).paidToday()).isTrue();
        assertThat(at(LocalDate.of(2026, 11, 20)).paidToday()).isTrue();

        // Un pase que todavia no empieza no cuenta.
        membershipService.voidMembership(latest().getId());
        membershipService.voidMembership(first);
        pass(OCT_21, LocalDate.of(2026, 10, 25));
        assertThat(at(OCT_21).paidToday()).isFalse();
    }

    @Test
    @DisplayName("La cuota mensual que empieza en unos dias todavia no lo hace activo")
    void aFutureMonthIsNotActiveYet() {
        month(OCT_21, 1, LocalDate.of(2026, 10, 26));
        assertThat(at(OCT_21).paidToday()).isFalse();
        assertThat(at(LocalDate.of(2026, 10, 26)).paidToday()).isTrue();
    }

    @Test
    @DisplayName("El filtro del panel pone a cada socio en una sola situacion")
    void eachMemberHasOneStanding() {
        UUID owingOne = gym.member(club, "41000002", "Debe el mes").id();
        UUID owingTwo = gym.member(club, "41000003", "Debe dos").id();
        UUID futureMonth = gym.member(club, "41000004", "Mes por empezar").id();
        UUID futurePass = gym.member(club, "41000005", "Pase por empezar").id();
        UUID expiredPass = gym.member(club, "41000006", "Pase vencido").id();
        gym.member(club, "41000007", "Sin cuota");
        UUID disabled = gym.member(club, "41000008", "Deshabilitado al dia").id();
        UUID passThenMonth = gym.member(club, "41000009", "Pase y mes despues").id();

        month(OCT_21, 1, LocalDate.of(2026, 10, 15));                       // Tomás Paso: al dia
        charge(owingOne, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
        charge(owingTwo, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));
        charge(futureMonth, OCT_21, LocalDate.of(2026, 10, 25));
        charge(disabled, OCT_21, LocalDate.of(2026, 10, 15));
        memberService.setEnabled(disabled, false);
        passFor(futurePass, OCT_21, LocalDate.of(2026, 10, 23));
        passFor(expiredPass, LocalDate.of(2026, 10, 1), null);
        passFor(passThenMonth, OCT_21, null);
        charge(passThenMonth, OCT_21, null);

        Map<String, Standing> standings = overviewService.members().stream()
                .collect(Collectors.toMap(GymOverviewService.MemberRow::fullName,
                        GymOverviewService.MemberRow::standing));
        assertThat(standings).containsExactlyInAnyOrderEntriesOf(Map.of(
                "Tomás Paso", Standing.ACTIVE,
                "Pase y mes despues", Standing.ACTIVE,
                "Debe el mes", Standing.OWING,
                "Debe dos", Standing.OWING,
                "Mes por empezar", Standing.UPCOMING,
                "Pase por empezar", Standing.UPCOMING,
                "Pase vencido", Standing.EXPIRED_PASS,
                "Sin cuota", Standing.NO_FEE,
                "Deshabilitado al dia", Standing.DISABLED));
    }

    private void charge(UUID member, LocalDate today, LocalDate newStart) {
        membershipService.charge(member, 1, new BigDecimal("30000"), 3, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, today, newStart, null);
    }

    private void passFor(UUID member, LocalDate today, LocalDate start) {
        membershipService.chargePass(member, start, new BigDecimal("18000"), 3, PayMethod.CASH,
                sede.getId(), Set.of(sede.getId()), null, today, null, false);
    }

    // ------------------------------------------------------------ anulacion

    @Test
    @DisplayName("Anular un pase vendido a un mensual al dia lo devuelve a su ciclo, sin deuda de mas")
    void voidingAPassAfterAPaidMonthRestoresTheCycle() {
        month(OCT_21, 1, OCT_21);
        UUID pass = pass(OCT_21, null);

        VoidEffect effect = membershipService.voidEffect(pass);
        assertThat(effect.allowed()).isTrue();
        assertThat(effect.message()).contains("plan mensual").contains("21/11/2026");

        membershipService.voidMembership(pass);
        Status status = at(OCT_21);
        assertThat(status.onPasses()).isFalse();
        assertThat(status.anchor()).isEqualTo(OCT_21);
        assertThat(status.monthsLate()).isZero();
        assertThat(status.paidUntil()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(at(LocalDate.of(2026, 11, 25)).monthsLate()).isEqualTo(1);
    }

    @Test
    @DisplayName("Anular el pase que reemplazo un mensual con deuda devuelve la deuda, con el tiempo del pase")
    void voidingAPassBringsBackTheDroppedDebt() {
        month(LocalDate.of(2026, 9, 1), 1, LocalDate.of(2026, 9, 1));
        UUID pass = pass(OCT_21, null, true);
        assertThat(at(OCT_21).monthsLate()).isZero();

        membershipService.voidMembership(pass);

        assertThat(at(OCT_21).monthsLate()).isEqualTo(1);
        Status duringWhatWasThePass = at(LocalDate.of(2026, 11, 3));
        assertThat(duringWhatWasThePass.monthsLate()).isEqualTo(2);
        assertThat(duringWhatWasThePass.canEnter()).isFalse();
    }

    @Test
    @DisplayName("Anular el mensual cobrado despues de un pase lo deja otra vez con pases, sin deuda")
    void voidingTheMonthAfterAPassGoesBackToPasses() {
        pass(OCT_21, null);
        month(OCT_21, 1, null);

        membershipService.voidMembership(latest().getId());

        Status today = at(OCT_21);
        assertThat(today.onPasses()).isTrue();
        assertThat(today.canEnter()).isTrue();
        assertThat(today.paidUntil()).isEqualTo(LocalDate.of(2026, 11, 4));
        Status after = at(LocalDate.of(2026, 11, 10));
        assertThat(after.canEnter()).isFalse();
        assertThat(after.monthsLate()).isZero();
        // Y el proximo mensual arranca otro ciclo, despues de lo pago.
        month(OCT_21, 1, null);
        assertThat(latest().getStartsOn()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    @DisplayName("Si despues del pase se arranco y se anulo otro mensual, el pase ya no se puede anular")
    void aPassCannotBeVoidedWhenThePreviousCycleIsLost() {
        month(LocalDate.of(2026, 9, 1), 1, LocalDate.of(2026, 9, 1));
        UUID pass = pass(LocalDate.of(2026, 9, 25), null);
        month(LocalDate.of(2026, 10, 10), 1, null);
        membershipService.voidMembership(latest().getId());

        assertThat(membershipService.voidEffect(pass).allowed()).isFalse();
        assertThatThrownBy(() -> membershipService.voidMembership(pass))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no se puede anular");
        assertThat(membershipRepository.findById(pass).orElseThrow().getVoidedAt()).isNull();
    }

    @Test
    @DisplayName("Anular el unico pase lo deja como el que nunca pago")
    void voidingTheOnlyPassResetsTheMember() {
        UUID pass = pass(OCT_21, null);

        membershipService.voidMembership(pass);

        Status status = at(OCT_21);
        assertThat(status.plan()).isNull();
        assertThat(status.anchor()).isNull();
        assertThat(status.onPasses()).isFalse();
        assertThat(status.pending().getFirst().start()).isEqualTo(OCT_21);
    }

    @Test
    @DisplayName("Anular un pase entre otros pases no cambia nada mas")
    void voidingAPassBetweenPasses() {
        UUID first = pass(OCT_21, null);
        pass(OCT_21, null);

        assertThat(membershipService.voidEffect(first)).isEqualTo(new VoidEffect(true, null));
        membershipService.voidMembership(first);
        assertThat(at(OCT_21).canEnter()).isFalse();
        assertThat(at(OCT_21).started()).isFalse();
        assertThat(at(LocalDate.of(2026, 11, 6)).canEnter()).isTrue();
    }

    // ------------------------------------------------------------ concurrencia

    @Test
    @DisplayName("Dos pases a la vez: se guarda uno y el otro recibe el aviso de superposicion")
    void twoPassesAtOnce() throws Exception {
        List<Throwable> failures = race(
                () -> pass(OCT_21, OCT_21),
                () -> pass(OCT_21, OCT_21));

        assertThat(failures).singleElement().isInstanceOf(BusinessRuleException.class);
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("Un pase y un mes a la vez: se guarda uno solo y no quedan cuotas pisadas")
    void aPassAndAMonthAtOnce() throws Exception {
        List<Throwable> failures = race(
                () -> pass(OCT_21, OCT_21),
                () -> {
                    month(OCT_21, 1, OCT_21);
                    return null;
                });

        assertThat(failures).singleElement().isInstanceOf(BusinessRuleException.class);
        assertThat(membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(memberId)).hasSize(1);
    }

    /** Corre las dos tareas a la vez, cada una en su transaccion, y devuelve lo que fallo. */
    private List<Throwable> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        UUID clubId = club.getId();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return TenantContext.callAs(clubId, () -> {
                        try {
                            return task.call();
                        } catch (RuntimeException ex) {
                            throw ex;
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
                }));
            }
            go.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------- check-in y app

    @Test
    @DisplayName("Con el pase vencido, el check-in lo dice: no habla de deuda")
    void checkInWithAnExpiredPass() {
        pass(OCT_21, null);

        assertThat(scanOn(LocalDate.of(2026, 11, 4)).validUntil()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThatThrownBy(() -> scanOn(LocalDate.of(2026, 11, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tu pase de 15 días venció el 04/11/2026");
    }

    @Test
    @DisplayName("Con un pase que corre y el mensual por empezar, entra, y vale hasta el fin del mes")
    void checkInWithAPassAndAMonthAhead() {
        pass(OCT_21, null);
        month(OCT_21, 1, null);

        CheckInResult result = scanOn(LocalDate.of(2026, 10, 22));
        assertThat(result.limit()).isEqualTo(3);
        assertThat(result.validUntil()).isEqualTo(LocalDate.of(2026, 12, 4));
    }

    @Test
    @DisplayName("Con un pase que todavia no empieza, el check-in dice cuando empieza")
    void checkInWithAFuturePass() {
        pass(OCT_21, LocalDate.of(2026, 10, 24));

        assertThatThrownBy(() -> scanOn(OCT_21))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tu cuota empieza el 24/10/2026");
    }

    @Test
    @DisplayName("La pantalla del socio sabe que esta con pases y que su cuota es de 15 dias")
    void theAppStatusTellsItIsAPass() {
        pass(OCT_21, null);

        GymStatusService.Status status = statusService.of(memberId);
        assertThat(status.onPasses()).isTrue();
        assertThat(status.membership().planLength()).isEqualTo(PlanLength.FIFTEEN_DAYS);
        assertThat(status.canEnter()).isTrue();
        assertThat(status.paidUntil()).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    @DisplayName("Las cuotas cobradas dicen cual fue pase y cual mensual, las anuladas incluidas")
    void feesShowTheirPlan() {
        pass(OCT_21, null);
        month(OCT_21, 1, null);
        membershipService.voidMembership(latest().getId());

        assertThat(membershipService.feesOf(memberId))
                .extracting(GymMembershipService.Fee::planLength, GymMembershipService.Fee::voided)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PlanLength.MONTH, true),
                        org.assertj.core.groups.Tuple.tuple(PlanLength.FIFTEEN_DAYS, false));
    }
}
