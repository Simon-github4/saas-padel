package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
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
     */
    public record Status(GymMember member, LocalDate anchor, boolean started, LocalDate periodStart,
                         LocalDate periodEnd, LocalDate paidUntil, boolean paidCurrent, int monthsLate,
                         BigDecimal owedTotal, List<Period> pending, GymMembership plan, int planDaysPerWeek,
                         boolean canEnter) {
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
                    null, false, 0, BigDecimal.ZERO, pending, plan, plan != null ? plan.getDaysPerWeek() : 0, false);
        }
        if (anchor == null) {
            // Socios de antes de la migracion (o registros sin backfill): su primera cuota es el ancla.
            anchor = paid.get(paid.size() - 1).getStartsOn();
        }

        YearMonth anchorMonth = YearMonth.from(anchor);
        YearMonth currentMonth = currentYearMonth(anchor, today);
        boolean started = !today.isBefore(periodStart(anchor, anchorMonth));

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
        if (anchorMonth.isAfter(currentMonth)) {
            current = periodOf(anchor, currentMonth);
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
        BigDecimal unit = plan != null ? tariffService.priceOf(plan.getDaysPerWeek()) : null;
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

        boolean canEnter = started && plan != null && monthsLate <= 1;
        return new Status(member, anchor, started,
                current == null ? periodStart(anchor, currentMonth) : current.start(),
                current == null ? periodEnd(anchor, currentMonth) : current.end(),
                paidUntil, isPaid(paid, current), monthsLate, owedTotal, pending, plan,
                plan != null ? plan.getDaysPerWeek() : 0, canEnter);
    }

    private static Period periodOf(LocalDate anchor, YearMonth month) {
        return new Period(periodStart(anchor, month), periodEnd(anchor, month));
    }

    /** Un periodo esta pago si una cuota no anulada lo cubre entero (aunque sea una fila vieja de varios meses). */
    private static boolean isPaid(List<GymMembership> paid, Period period) {
        return paid.stream().anyMatch(m -> !m.getStartsOn().isAfter(period.start())
                && !m.getEndsOn().isBefore(period.end()));
    }
}