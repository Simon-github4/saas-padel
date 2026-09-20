package ar.com.padelnec.gym.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * La semana de conteo de dias: lunes a domingo, en fechas locales del club.
 *
 * <p>Lunes a domingo y no "los ultimos 7 dias": el socio piensa en "esta semana",
 * y un tope movil se le haria imposible de predecir.
 */
public record GymWeek(LocalDate monday, LocalDate sunday) {

    public static GymWeek of(LocalDate day) {
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new GymWeek(monday, monday.plusDays(6));
    }
}
