package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** Precio del turno completo para una franja horaria de un dia de la semana. */
@Entity
@Table(name = "pricing_rule")
@Getter
@Setter
public class PricingRule extends TenantScopedEntity {

    /** Nulo = la regla aplica a todas las canchas. Una regla con cancha explicita tiene prioridad. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id")
    private Court court;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    public DayOfWeek day() {
        return DayOfWeek.of(dayOfWeek);
    }

    public void setDay(DayOfWeek day) {
        this.dayOfWeek = day.getValue();
    }

    /** La regla cubre el horario si el inicio del turno cae dentro de la franja. */
    public boolean covers(DayOfWeek day, LocalTime slotStart) {
        return dayOfWeek == day.getValue()
                && !slotStart.isBefore(startTime)
                && slotStart.isBefore(endTime);
    }

    /** Una regla especifica de cancha le gana a la regla general del club. */
    public boolean isCourtSpecific() {
        return court != null;
    }
}
