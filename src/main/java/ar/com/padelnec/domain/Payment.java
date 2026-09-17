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
 * Movimiento de dinero asociado a una reserva o a un pedido de buffet.
 *
 * <p>Una reserva puede tener varios: la sena por MercadoPago y el saldo cobrado
 * en el mostrador. Es de una reserva o de un pedido, nunca de los dos: lo exige
 * la base ({@code ck_payment_owner}).
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
public class Payment extends TenantScopedEntity {

    /** Nulo cuando el cobro es de un pedido de buffet sin turno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    /** Nulo cuando el cobro es de un turno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buffet_order_id")
    private BuffetOrder buffetOrder;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Id de la order de MercadoPago (Orders API) que genero este pago pendiente. */
    @Column(name = "mp_order_id", length = 120)
    private String mpOrderId;

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
