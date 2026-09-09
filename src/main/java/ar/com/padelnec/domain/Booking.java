package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.BookingSource;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Turno reservado sobre una cancha. */
@Entity
@Table(name = "booking")
@Getter
@Setter
public class Booking extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** No nulo cuando el turno lo genero un turno fijo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_booking_id")
    private RecurringBooking recurringBooking;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingSource source = BookingSource.WEB;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    /** Sena exigida al reservar. Cero cuando el turno se paga entero en el club. */
    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    /** Suma de los pagos aprobados. Se mantiene al dia para no recalcularla en cada pantalla. */
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Token permanente con el que el jugador entra a ver y cancelar su turno. */
    @Column(name = "management_token", unique = true, length = 64)
    private String managementToken;

    /**
     * Token de solo lectura para compartir el turno con otros jugadores. A
     * proposito distinto de {@code managementToken}: compartirlo nunca puede
     * terminar dandole a un tercero la posibilidad de cancelar la reserva.
     */
    @Column(name = "share_token", unique = true, length = 64)
    private String shareToken;

    /** Token de un solo uso para el flujo sin pago anticipado. */
    @Column(name = "confirmation_token", unique = true, length = 64)
    private String confirmationToken;

    @Column(name = "confirmation_expires_at")
    private Instant confirmationExpiresAt;

    /** Momento en que se libera la cancha si no acredita el pago de MercadoPago. */
    @Column(name = "draft_expires_at")
    private Instant draftExpiresAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 40)
    private CancellationReason cancellationReason;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @Version
    @Column(nullable = false)
    private long version;

    // ------------------------------------------------------------- dinero

    public BigDecimal balanceDue() {
        return totalPrice.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    /**
     * Cuando lo pagado supera al total (ej. se sacó un producto ya cobrado), lo que
     * queda a favor del cliente. Cero cuando no hay excedente.
     */
    public BigDecimal creditBalance() {
        return paidAmount.subtract(totalPrice).max(BigDecimal.ZERO);
    }

    public boolean isPaidInFull() {
        return paidAmount.compareTo(totalPrice) >= 0;
    }

    /** Hubo dinero real de por medio: al cancelar hay que coordinar una devolucion. */
    public boolean hasMoneyIn() {
        return paidAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    // ------------------------------------------------------------- tiempo

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startTime.isBefore(otherEnd) && endTime.isAfter(otherStart);
    }

    /**
     * El jugador todavia esta a tiempo de cancelar por su cuenta. Por debajo del
     * limite la cancelacion pasa a ser una conversacion con el club.
     */
    public boolean isWithinCancellationWindow(int limitHours, Instant now) {
        return Duration.between(now, startTime).toMinutes() >= (long) limitHours * 60;
    }

    // -------------------------------------------------------- transiciones

    public void markCancelled(CancellationReason reason, Instant now) {
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = now;
        this.confirmationToken = null;
        this.confirmationExpiresAt = null;
        this.draftExpiresAt = null;
    }

    public void markConfirmed() {
        this.status = BookingStatus.CONFIRMED;
        this.confirmationToken = null;
        this.confirmationExpiresAt = null;
        this.draftExpiresAt = null;
    }
}
