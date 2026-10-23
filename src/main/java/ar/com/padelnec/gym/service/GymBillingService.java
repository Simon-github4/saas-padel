package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.PlanLength;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El ciclo mensual de cada socio y su deuda.
 *
 * <p>El dia de corte de todos los meses de un socio es el dia en que empezo su
 * ciclo (el "ancla"): quien arranco el dia 15, cada cuota va del 15 de un mes al
 * 14 del siguiente; el ancla en 31 baja al ultimo dia del mes corto. El mostrador
 * puede arrancar el ciclo de nuevo al cobrar: el ancla pasa a ser ese dia y lo
 * impago de antes deja de contar. Un
 * periodo esta pago si una cuota no anulada lo cubre; lo impago se acumula como
 * deuda, y se cobra la mas vieja primero.
 *
 * <p>El pase de 15 dias queda afuera del ciclo: si la ultima cuota paga es un pase, el
 * socio esta en "regimen de pases" y solo cuenta la cobertura (lo que vence no genera
 * deuda ni tiene gracia). Vender un pase no toca el ancla: si se anula, el socio vuelve
 * solo a su ciclo mensual. Volver al mensual desde los pases arranca un ciclo nuevo.
 *
 * <p>De aca sale todo lo que habla de cuotas: la grilla del panel, el dialogo de
 * cobro, el check-in y la pantalla "mi estado" de la app.
 */
@Service
@RequiredArgsConstructor
public class GymBillingService {

    public static final int MAX_SCAN_BACK = 400;

    /** Cuantas cuotas a futuro se pueden pagar por adelantado (ademas de la corriente y las vencidas). */
    public static final int LOOK_AHEAD = 4;

    private final GymMemberRepository memberRepository;
    private final GymMembershipRepository membershipRepository;
    private final GymTariffService tariffService;

    /** Un periodo del ciclo: desde {@code start} hasta {@code end} inclusive. */
    public record Period(LocalDate start, LocalDate end) {
    }

    /**
     * {@code paidUntil} es hasta cuando llega lo pagado por adelantado (el fin del periodo
     * corriente si esta pago, o mas alla si se adelantaron cuotas). {@code pending} son las
     * cuotas que se pueden cobrar ahora, de la mas vieja a la mas nueva: la deuda y hasta
     * {@link #LOOK_AHEAD} cuotas a futuro.
     *
     * <p>{@code plan} es la ultima cuota paga (la mas lejana, aunque sea un adelanto): de ahi
     * salen los valores por defecto del proximo cobro. {@code active} es la cuota que rige HOY:
     * la que cubre hoy, o si no la ultima que ya empezo (el mes impago con gracia), o si todavia
     * ninguna empezo la primera que va a empezar. Los dias por semana, las sedes y la cuota a la
     * que se asocia un ingreso salen siempre de {@code active}: un adelanto con otro plan no
     * cambia lo que vale hoy. Un pase vencido nunca rige (no tiene gracia).
     *
     * <p>{@code started} dice si ya rige alguna cuota: empezo el ciclo, o todavia no pero hoy lo
     * cubre una cuota de antes (el mostrador arranco el mes de nuevo para cuando termine lo pago,
     * o un pase que corre con el plan mensual por empezar). En ese caso {@code paidUntil} es hasta
     * donde llega lo pago desde hoy sin cortes, y si queda un hueco, {@code paidAhead} es lo pago
     * que vuelve despues: el hueco va del dia siguiente a {@code paidUntil} al dia antes de
     * {@code paidAhead.start()}, y esos dias no entra. En cualquier otro caso {@code paidAhead} es null.
     *
     * <p>{@code onPasses}: la ultima cuota paga es un pase. No hay ciclo mensual abierto: no hay
     * deuda ni gracia ({@code monthsLate} 0, {@code pending} vacio), {@code anchor} es null y
     * {@code active} es la que cubre hoy, si no la proxima que empieza, si no la ultima (vencida).
     */
    public record Status(GymMember member, LocalDate anchor, boolean started, LocalDate periodStart,
                         LocalDate periodEnd, LocalDate paidUntil, boolean paidCurrent, int monthsLate,
                         BigDecimal owedTotal, List<Period> pending, GymMembership plan, int planDaysPerWeek,
                         boolean canEnter, GymMembership active, boolean onPasses, Period paidAhead) {

        /**
         * Lo que vale hoy lo deciden las reglas del pase: solo la cobertura, sin ciclo ni gracia.
         * Pasa en el regimen de pases y cuando un pase cubre hoy con el plan mensual por empezar.
         * Para un socio sin pases es siempre false.
         */
        public boolean passRules() {
            return onPasses || (active != null && active.isPass());
        }

        /**
         * Tiene la cuota de hoy paga (mensual o pase): es el socio "activo" que cuenta el panel.
         * El que debe el mes no cuenta aunque entre por la gracia, ni el que todavia no empezo.
         */
        public boolean paidToday() {
            return passRules() ? canEnter : started && paidCurrent;
        }

        /** Los dias sin cuota entre lo pago hoy y {@code paidAhead}, o null si no hay hueco. */
        public Period gap() {
            return paidAhead == null ? null
                    : new Period(paidUntil.plusDays(1), paidAhead.start().minusDays(1));
        }
    }

    /** El periodo que arranca en ese mes, dado el ancla. */
    public static LocalDate periodStart(LocalDate anchor, YearMonth month) {
        return month.atDay(Math.min(anchor.getDayOfMonth(), month.lengthOfMonth()));
    }

    /** El ultimo dia del periodo que arranca en {@code month}: el dia antes del proximo corte. */
    public static LocalDate periodEnd(LocalDate anchor, YearMonth month) {
        return periodStart(anchor, month.plusMonths(1)).minusDays(1);
    }

    /** El periodo de un socio que contiene a {@code today}, o el que sigue si es el dia del corte. */
    public static YearMonth currentYearMonth(LocalDate anchor, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        if (today.isBefore(periodStart(anchor, month))) {
            return month.minusMonths(1);
        }
        if (today.isAfter(periodEnd(anchor, month))) {
            return month.plusMonths(1);
        }
        return month;
    }

    /**
     * Los dias sin cuota que deja un nuevo inicio de mes: desde el dia siguiente a lo pago (o desde
     * hoy, si eso ya paso) hasta el dia antes de {@code newStart}. Esos dias el socio no entra: si el
     * mostrador corre el inicio es porque no vino. Null si no queda hueco, o si nunca pago (el que
     * arranca no tiene un hueco, tiene una primera cuota que empieza mas adelante).
     *
     * @param lastPaid la cuota paga que termina mas lejos ({@link Status#plan()})
     */
    public static Period gapBefore(GymMembership lastPaid, LocalDate today, LocalDate newStart) {
        if (lastPaid == null || newStart == null) {
            return null;
        }
        LocalDate afterPaid = lastPaid.getEndsOn().plusDays(1);
        LocalDate from = afterPaid.isAfter(today) ? afterPaid : today;
        LocalDate to = newStart.minusDays(1);
        return from.isAfter(to) ? null : new Period(from, to);
    }

    @Transactional(readOnly = true)
    public Status status(UUID memberId, LocalDate today) {
        GymMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión."));
        return status(member, today);
    }

    @Transactional(readOnly = true)
    public Status status(GymMember member, LocalDate today) {
        return status(member, today, null);
    }

    /**
     * Como {@link #status(GymMember, LocalDate)}, pero con el ciclo arrancando de nuevo
     * en {@code newStart}: lo que se cobre sale de ahi, y los meses de antes sin pagar
     * dejan de contar. Sirve para cargar a un socio que ya venia (su mes empezo el 01/09
     * aunque se lo cargue el 23/09) y para el que vuelve despues de un tiempo sin venir.
     * Con {@code newStart} null el ciclo sigue como venia (y el socio nuevo arranca hoy).
     */
    @Transactional(readOnly = true)
    public Status status(GymMember member, LocalDate today, LocalDate newStart) {
        List<GymMembership> paid =
                membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(member.getId());
        GymMembership plan = paid.isEmpty() ? null : paid.getFirst();
        if (newStart == null && plan != null && plan.isPass()) {
            return passStatus(member, paid, plan, today);
        }

        LocalDate anchor = member.getBillingAnchor();
        if (newStart != null || (anchor == null && paid.isEmpty())) {
            // Ciclo nuevo (el socio que nunca pago, o uno que arranca de nuevo): la cuota arranca
            // en newStart (o hoy) y fija el ancla al cobrarse. Hasta pagarla, el ciclo nuevo no
            // habilita a entrar. Si arranca antes de hoy, se ofrecen todos los meses hasta el
            // corriente y los adelantos de siempre.
            LocalDate start = newStart == null ? today : newStart;
            YearMonth first = YearMonth.from(start);
            List<Period> pending = new ArrayList<>();
            Period shown = null;
            int ahead = 0;
            for (int k = 0; ; k++) {
                Period period = periodOf(start, first.plusMonths(k));
                if (k > 0 && period.start().isAfter(today)) {
                    if (ahead == LOOK_AHEAD) {
                        break;
                    }
                    ahead++;
                }
                pending.add(period);
                if (shown == null || !period.start().isAfter(today)) {
                    shown = period;
                }
            }
            return new Status(member, null, false, shown.start(), shown.end(),
                    null, false, 0, BigDecimal.ZERO, pending, plan, plan != null ? plan.getDaysPerWeek() : 0, false,
                    activeOf(paid, today), false, null);
        }
        if (anchor == null) {
            // Socios de antes de la migracion (o registros sin backfill): su primera cuota es el ancla.
            anchor = paid.get(paid.size() - 1).getStartsOn();
        }

        YearMonth anchorMonth = YearMonth.from(anchor);
        boolean cycleStarted = !today.isBefore(periodStart(anchor, anchorMonth));
        // Si el ciclo todavia no empezo (una cuota que arranca en unos dias), su mes "corriente" es el
        // primero: el anterior al ancla no es del socio.
        YearMonth currentMonth = cycleStarted ? currentYearMonth(anchor, today) : anchorMonth;

        List<Period> owed = new ArrayList<>();
        int monthsLate = 0;
        Period current = null;
        for (YearMonth month = anchorMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            Period period = periodOf(anchor, month);
            current = period;
            if (!isPaid(paid, period)) {
                owed.add(period);
                if (month.equals(currentMonth)) {
                    monthsLate = 1;
                    // Cuotas impagas consecutivas terminando en la corriente: las que hacen bloqueo.
                    for (YearMonth back = currentMonth.minusMonths(1);
                            back.isAfter(anchorMonth.minusMonths(1)) && monthsLate <= MAX_SCAN_BACK;
                            back = back.minusMonths(1)) {
                        if (isPaid(paid, periodOf(anchor, back))) {
                            break;
                        }
                        monthsLate++;
                    }
                }
            }
        }

        List<Period> pending = new ArrayList<>(owed);
        for (YearMonth month = currentMonth.plusMonths(1);
                !month.isAfter(currentMonth.plusMonths(LOOK_AHEAD));
                month = month.plusMonths(1)) {
            Period period = periodOf(anchor, month);
            if (!isPaid(paid, period)) {
                pending.add(period);
            }
        }
        BigDecimal unit = plan != null ? tariffService.priceOf(PlanLength.MONTH, plan.getDaysPerWeek()) : null;
        BigDecimal owedTotal = unit == null ? BigDecimal.ZERO : unit.multiply(BigDecimal.valueOf(owed.size()));

        // Lo que el socio ya tiene pagado por adelantado: del periodo corriente hacia adelante,
        // mientras la cuota siguiente esté paga la cobertura se estira hasta su fin.
        LocalDate paidUntil = null;
        if (isPaid(paid, current)) {
            paidUntil = current.end();
            for (YearMonth month = currentMonth.plusMonths(1);
                    !month.isAfter(currentMonth.plusMonths(LOOK_AHEAD));
                    month = month.plusMonths(1)) {
                if (!isPaid(paid, periodOf(anchor, month))) {
                    break;
                }
                paidUntil = periodEnd(anchor, month);
            }
        }

        // El mostrador arranco el mes de nuevo para despues de lo que el socio ya tenia pago: hasta que
        // empiece el ciclo nuevo lo habilita la cuota de antes, y lo pago llega hasta el primer hueco.
        boolean coveredBefore = !cycleStarted && paid.stream().anyMatch(m -> m.covers(today));
        Period paidAhead = null;
        if (coveredBefore) {
            paidUntil = coveredUntil(paid, today);
            paidAhead = paidAfter(paid, paidUntil);
        }
        boolean started = cycleStarted || coveredBefore;
        boolean canEnter = started && plan != null && monthsLate <= 1;
        return new Status(member, anchor, started,
                current == null ? periodStart(anchor, currentMonth) : current.start(),
                current == null ? periodEnd(anchor, currentMonth) : current.end(),
                paidUntil, isPaid(paid, current) || coveredBefore, monthsLate, owedTotal, pending, plan,
                plan != null ? plan.getDaysPerWeek() : 0, canEnter, activeOf(paid, today), false, paidAhead);
    }

    /**
     * El estado del socio cuya ultima cuota paga es un pase: no hay ciclo mensual, solo
     * cobertura. Rige la cuota que cubre hoy (puede ser un mes que todavia corre), si no la
     * proxima que empieza, si no la ultima, ya vencida. Lo que vence no es deuda. Si lo pago
     * de hoy tiene un hueco antes de otra cuota, {@code paidAhead} es esa cuota.
     */
    private static Status passStatus(GymMember member, List<GymMembership> paid, GymMembership plan,
                                     LocalDate today) {
        GymMembership covering = paid.stream().filter(m -> m.covers(today)).findFirst().orElse(null);
        GymMembership active = covering != null ? covering : firstUpcoming(paid, today).orElse(plan);
        boolean started = !active.getStartsOn().isAfter(today);
        boolean expired = covering == null && started;
        LocalDate paidUntil = expired ? null : coveredUntil(paid, active.getStartsOn());
        Period paidAhead = covering != null ? paidAfter(paid, paidUntil) : null;
        return new Status(member, null, started, active.getStartsOn(), active.getEndsOn(),
                paidUntil, covering != null, 0, BigDecimal.ZERO, List.of(),
                plan, plan.getDaysPerWeek(), covering != null, active, true, paidAhead);
    }

    /**
     * Desde cuando se puede cobrar lo proximo sin pisar lo pago: el dia despues de la ultima
     * cuota paga, o hoy si eso ya paso. Es el inicio por defecto del pase y del plan mensual
     * que arranca despues de los pases.
     */
    @Transactional(readOnly = true)
    public LocalDate nextStart(GymMember member, LocalDate today) {
        return membershipRepository.findFirstByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(member.getId())
                .map(last -> last.getEndsOn().plusDays(1))
                .filter(next -> next.isAfter(today))
                .orElse(today);
    }

    /**
     * Cuantas cuotas mensuales ya empezadas debe el socio: la deuda que dejaria de cobrarse si
     * pasa a un pase. Cero para el que nunca pago y para el que ya esta con pases.
     */
    public static int owedMonths(Status status, LocalDate today) {
        if (status.onPasses() || status.plan() == null) {
            return 0;
        }
        return (int) status.pending().stream().filter(period -> !period.start().isAfter(today)).count();
    }

    /**
     * La cuota que rige hoy, de las pagas ordenadas de la mas nueva a la mas vieja: la que cubre
     * hoy; si no, la ultima que ya empezo (el mes impago con gracia); si no, la primera que va a
     * empezar (null sin cuotas). Un pase vencido no tiene gracia: en su lugar rige la proxima que
     * empieza, y si no hay ninguna, el pase vencido.
     */
    private static GymMembership activeOf(List<GymMembership> paid, LocalDate today) {
        Optional<GymMembership> covering = paid.stream().filter(m -> m.covers(today)).findFirst();
        if (covering.isPresent()) {
            return covering.get();
        }
        Optional<GymMembership> lastStarted = paid.stream().filter(m -> !m.getStartsOn().isAfter(today)).findFirst();
        if (lastStarted.isPresent() && !lastStarted.get().isPass()) {
            return lastStarted.get();
        }
        return firstUpcoming(paid, today).orElse(lastStarted.orElse(null));
    }

    /** La primera cuota paga que todavia no empezo. */
    private static Optional<GymMembership> firstUpcoming(List<GymMembership> paid, LocalDate today) {
        return paid.stream().filter(m -> m.getStartsOn().isAfter(today))
                .min(Comparator.comparing(GymMembership::getStartsOn));
    }

    private static Period periodOf(LocalDate anchor, YearMonth month) {
        return new Period(periodStart(anchor, month), periodEnd(anchor, month));
    }

    /** Un periodo esta pago si una cuota no anulada lo cubre entero (aunque sea una fila vieja de varios meses). */
    private static boolean isPaid(List<GymMembership> paid, Period period) {
        return paid.stream().anyMatch(m -> !m.getStartsOn().isAfter(period.start())
                && !m.getEndsOn().isBefore(period.end()));
    }

    /** Hasta donde llega lo pago sin cortes desde {@code day}: cuota tras cuota, sin saltar huecos (null si nada lo cubre). */
    private static LocalDate coveredUntil(List<GymMembership> paid, LocalDate day) {
        LocalDate until = null;
        for (LocalDate next = day; ; ) {
            LocalDate probe = next;
            GymMembership covering = paid.stream().filter(m -> m.covers(probe)).findFirst().orElse(null);
            if (covering == null) {
                return until;
            }
            until = covering.getEndsOn();
            next = until.plusDays(1);
        }
    }

    /** Lo pago que vuelve despues de un hueco: desde la primera cuota que arranca despues de {@code until}, sin cortes (null si no hay). */
    private static Period paidAfter(List<GymMembership> paid, LocalDate until) {
        return paid.stream().map(GymMembership::getStartsOn).filter(start -> start.isAfter(until))
                .min(Comparator.naturalOrder())
                .map(start -> new Period(start, coveredUntil(paid, start)))
                .orElse(null);
    }
}