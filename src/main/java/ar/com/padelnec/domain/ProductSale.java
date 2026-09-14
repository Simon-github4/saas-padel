package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Linea de venta de un producto del buffet, durante un turno o en un pedido suelto.
 *
 * <p>Nombre y precio quedan "congelados" al momento de la venta: si despues el
 * club edita el catalogo, esta linea ya cargada no cambia de valor con retroactividad.
 *
 * <p>Es de un turno o de un pedido de buffet, nunca de los dos: lo exige la base
 * ({@code ck_product_sale_owner}).
 */
@Entity
@Table(name = "product_sale")
@Getter
@Setter
public class ProductSale extends TenantScopedEntity {

    /** Nulo cuando la venta es de un pedido de buffet sin turno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    /** Nulo cuando la venta es de un turno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buffet_order_id")
    private BuffetOrder buffetOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 80)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    /** Usuario del panel que cargo la venta. */
    @Column(name = "registered_by")
    private UUID registeredBy;

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
