package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.EntryLimit;
import ar.com.padelnec.gym.domain.GymMembership;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cuantos ingresos tiene el socio y en que dias se cuentan, segun como se cuenta el tope de su
 * cuota ({@link EntryLimit}). Los ingresos son uno por dia, sumando todas las sedes.
 *
 * <p>Por semana: de lunes a domingo ({@link GymWeek}), tantos ingresos como dias por semana. Por
 * mes: en el periodo de la cuota que rige hoy (del 15 al 14 si el socio arranco el 15; en un pase,
 * sus 15 dias), los dias por semana proporcionales a lo que dura el periodo: dias por semana x dias
 * del periodo / 7, redondeado. 2 dias por semana dan 8 en un periodo de 28 dias y 9 en uno de 31;
 * 7 dias por semana, todos los dias del periodo.
 *
 * <p>Los dias por semana y el modo son los de la cuota que rige hoy
 * ({@link GymBillingService.Status#active()}): un adelanto con otro plan, o un cambio del modo del
 * club, no cambian el tope de hoy. En la gracia rige la ultima cuota que empezo, asi que el que
 * debe el mes sigue con el modo de antes hasta que lo pague.
 *
 * @param window donde se cuenta: la semana, el mes de la cuota o el pase
 * @param from   el primer dia que cuenta
 * @param to     el ultimo dia que cuenta, inclusive
 * @param limit  cuantos ingresos tiene en esos dias (0 si nunca pago)
 */
public record GymAllowance(Window window, LocalDate from, LocalDate to, int limit) {

    /** Donde se cuenta el tope. {@code PASS} es el pase de 15 dias en un club que cuenta por mes. */
    public enum Window {
        WEEK,
        MONTH,
        PASS
    }

    public static GymAllowance of(GymBillingService.Status status, LocalDate today) {
        GymMembership active = status.active();
        int daysPerWeek = active == null ? 0 : active.getDaysPerWeek();
        if (active == null || active.getEntryLimit() != EntryLimit.MONTH) {
            GymWeek week = GymWeek.of(today);
            return new GymAllowance(Window.WEEK, week.monday(), week.sunday(), daysPerWeek);
        }

        Window window = Window.MONTH;
        LocalDate from = status.periodStart();
        LocalDate to = status.periodEnd();
        if (active != null && active.isPass() && (status.onPasses() || active.covers(today))) {
            // El pase que rige: sus 15 dias.
            window = Window.PASS;
            from = active.getStartsOn();
            to = active.getEndsOn();
        } else if (active != null && active.covers(today) && status.periodStart().isAfter(today)) {
            // El mostrador arranco el mes de nuevo mas adelante y hoy lo cubre una cuota de antes.
            from = active.getStartsOn();
            to = active.getEndsOn();
        }
        // Si no, el periodo corriente del ciclo, pago o no: lo que entra en la gracia cuenta para el
        // mes que debe.
        return new GymAllowance(window, from, to, proportional(daysPerWeek, ChronoUnit.DAYS.between(from, to) + 1));
    }

    /**
     * Los ingresos de un periodo de {@code periodDays} dias para {@code daysPerWeek} dias por
     * semana, redondeados. Nunca queda un ,5 que decidir: para eso 7 tendria que dividir a
     * {@code daysPerWeek * periodDays}, y entonces la cuenta da justa.
     */
    public static int proportional(int daysPerWeek, long periodDays) {
        return (int) Math.round(daysPerWeek * periodDays / 7.0);
    }
}
