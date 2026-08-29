package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Aviso para el panel del club sobre algo que el sistema no puede resolver solo:
 * tipicamente una devolucion de sena que hay que coordinar por WhatsApp.
 */
@Entity
@Table(name = "operational_alert")
@Getter
@Setter
public class OperationalAlert extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertType type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    private boolean resolved;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    public void resolve(UUID userId, Instant now) {
        this.resolved = true;
        this.resolvedBy = userId;
        this.resolvedAt = now;
    }
}
