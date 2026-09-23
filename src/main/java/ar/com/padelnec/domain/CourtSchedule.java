package ar.com.padelnec.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Horario propio de una cancha en ciertos dias de la semana (ej. la cancha 3, que
 * no tiene luz, los domingos de 9 a 19).
 *
 * <p>Mismo criterio que las franjas de tarifa: en los dias que cubre, la regla de
 * la cancha le gana al horario general del club. Si una cancha tiene varias reglas
 * para el mismo dia, abre en todas esas franjas (ej. de 8 a 12 y de 17 a 23).
 * {@code closed} deja la cancha sin turnos esos dias y pisa a cualquier franja.
 */
@Entity
@Table(name = "court_schedule")
@Getter
@Setter
public class CourtSchedule extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "court_schedule_day", joinColumns = @JoinColumn(name = "court_schedule_id"))
    @Column(name = "day_of_week", nullable = false)
    @BatchSize(size = 25)
    private Set<Integer> days = new HashSet<>();

    /** Nulo solo si {@code closed}. */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** Si es menor o igual al inicio, cierra pasada la medianoche, igual que el club. */
    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean closed;

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

    public boolean containsDay(DayOfWeek day) {
        return days.contains(day.getValue());
    }

    public boolean closesAfterMidnight() {
        return !closed && !endTime.isAfter(startTime);
    }
}
