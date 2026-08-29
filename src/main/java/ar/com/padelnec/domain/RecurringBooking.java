package ar.com.padelnec.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Turno fijo: el mismo grupo, la misma cancha y el mismo horario todas las semanas.
 *
 * <p>No es una reserva en si misma. Un job materializa {@link Booking} concretos
 * sobre un horizonte movil, de modo que la grilla, los precios y el panel siguen
 * viendo turnos normales.
 */
@Entity
@Table(name = "recurring_booking")
@Getter
@Setter
public class RecurringBooking extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 90;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Nulo = el turno fijo no tiene fecha de corte. */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    /** Precio pactado con el grupo. Nulo = se cobra la tarifa vigente de la franja. */
    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "recurringBooking", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RecurringBookingSkip> skips = new HashSet<>();

    public DayOfWeek day() {
        return DayOfWeek.of(dayOfWeek);
    }

    public void setDay(DayOfWeek day) {
        this.dayOfWeek = day.getValue();
    }

    /** Indica si el turno fijo debe materializarse en esa fecha. */
    public boolean appliesOn(LocalDate date) {
        if (!active || date.getDayOfWeek().getValue() != dayOfWeek) {
            return false;
        }
        if (date.isBefore(validFrom)) {
            return false;
        }
        if (validUntil != null && date.isAfter(validUntil)) {
            return false;
        }
        return skips.stream().noneMatch(skip -> skip.getSkipDate().equals(date));
    }

    public void skip(LocalDate date, String reason) {
        RecurringBookingSkip skip = new RecurringBookingSkip();
        skip.setRecurringBooking(this);
        skip.setSkipDate(date);
        skip.setReason(reason);
        skips.add(skip);
    }
}
