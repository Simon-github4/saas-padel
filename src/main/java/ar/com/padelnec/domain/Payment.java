package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.domain.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Movimiento de dinero asociado a una reserva.
 *
 * <p>Una reserva puede tener varios: la sena por MercadoPago y el saldo cobrado
 * en el mostrador.
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
public class Payment extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "mp_preference_id", length = 120)
    private String mpPreferenceId;

    /**
     * Identificador del pago en MercadoPago. Es unico en la base: si el webhook
     * llega repetido, el segundo intento choca contra la restriccion y no
     * duplica la acreditacion.
     */
    @Column(name = "mp_payment_id", unique = true, length = 60)
    private String mpPaymentId;

    /** Payload crudo de MercadoPago, guardado para poder auditar una acreditacion. */
    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    /** Usuario del panel que registro un cobro en efectivo. Nulo si lo genero MercadoPago. */
    @Column(name = "registered_by")
    private UUID registeredBy;

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }
}
