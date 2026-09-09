package ar.com.padelnec.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Precio del turno completo para una franja horaria y un conjunto de dias de la
 * semana (ej. lunes, martes y miercoles de 8 a 12). La tarifa general del club
 * es el fallback cuando ninguna regla cubre el dia/franja.
 */
@Entity
@Table(name = "pricing_rule")
@Getter
@Setter
public class PricingRule extends TenantScopedEntity {

    /** Nulo = la regla aplica a todas las canchas. Una regla con cancha explicita tiene prioridad. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id")
    private Court court;

    /**
     * Dias de la semana que cubre, en la tabla puente {@code pricing_rule_day}.
     *
     * <p>{@code findRulesForDay} ya trae esto con {@code JOIN FETCH}, asi que el
     * camino caliente no pasa por aca. El {@code @BatchSize} es la red de
     * seguridad para las consultas que devuelven varias reglas sin fetch join
     * (hoy, {@code findAllByOrderByStartTimeAsc}): agrupa el relleno en tandas de
     * a 25 en vez de una consulta por regla.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pricing_rule_day", joinColumns = @JoinColumn(name = "pricing_rule_id"))
    @Column(name = "day_of_week", nullable = false)
    @BatchSize(size = 25)
    private Set<Integer> days = new HashSet<>();

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Marca explicita de promocion: controla el badge PROMO de la app. */
    @Column(nullable = false)
    private boolean promo;

    public Set<DayOfWeek> getDays() {
        Set<DayOfWeek> result = new HashSet<>();
        for (Integer value : days) {
            result.add(DayOfWeek.of(value));
        }
        return result;
    }

    public void setDays(Set<DayOfWeek> values) {
        days = new HashSet<>();
        for (DayOfWeek day : values) {
            days.add(day.getValue());
        }
    }

    public void addDay(DayOfWeek day) {
        days.add(day.getValue());
    }

    public boolean containsDay(DayOfWeek day) {
        return days.contains(day.getValue());
    }

    /** La regla cubre el horario si el inicio del turno cae dentro de la franja y el dia coincide. */
    public boolean covers(DayOfWeek day, LocalTime slotStart) {
        return days.contains(day.getValue())
                && !slotStart.isBefore(startTime)
                && slotStart.isBefore(endTime);
    }

    /** Una regla especifica de cancha le gana a la regla general del club. */
    public boolean isCourtSpecific() {
        return court != null;
    }
}
