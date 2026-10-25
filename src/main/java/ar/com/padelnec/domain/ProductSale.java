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

    /**
     * Si esta línea ya se cobró: en un pedido de buffet, cuando alguien del
     * grupo paga lo suyo y el resto de la cuenta sigue abierta. Se marca sola
     * al cobrar, por lo que se eligió o por lo que alcanzó a cubrir un monto.
     */
    @Column(nullable = false)
    private boolean paid;

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Separa unidades de esta línea en una nueva, que es la que se marca
     * cobrada: para cobrar 3 de las 5 milanesas, la nueva lleva las 3 y esta se
     * queda con las 2 que siguen pendientes. Mismo producto, nombre y precio
     * congelado, y la misma fecha de venta: para la caja y las estadísticas se
     * vendieron cuando se vendieron.
     *
     * <p>Lo pendiente se queda en esta línea, en su lugar: el orden de las
     * líneas sin cobrar es el orden en que un pago por monto cubre la cuenta, y
     * una línea nueva quedaría detrás de lo que se cargó después.
     *
     * @param units cuántas unidades pasan a la línea nueva; menos que las que tiene esta
     */
    public ProductSale splitOff(int units) {
        if (units <= 0 || units >= quantity) {
            throw new IllegalArgumentException("Para partir una línea de " + quantity + " no sirven " + units);
        }
        ProductSale part = new ProductSale();
        part.setBooking(booking);
        part.setBuffetOrder(buffetOrder);
        part.setProduct(product);
        part.setProductName(productName);
        part.setUnitPrice(unitPrice);
        part.setQuantity(units);
        part.setRegisteredBy(registeredBy);
        part.setCreatedAt(getCreatedAt());
        quantity -= units;
        return part;
    }
}
