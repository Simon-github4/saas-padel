package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Semana puntual en la que el grupo avisa que no juega y libera la cancha. */
@Entity
@Table(name = "recurring_booking_skip")
@Getter
@Setter
public class RecurringBookingSkip extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_booking_id", nullable = false)
    private RecurringBooking recurringBooking;

    @Column(name = "skip_date", nullable = false)
    private LocalDate skipDate;

    @Column(length = 160)
    private String reason;
}
