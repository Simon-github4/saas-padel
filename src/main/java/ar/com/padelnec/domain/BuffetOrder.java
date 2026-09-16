package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Pedido de buffet sin turno: lo que consume alguien que no está jugando, por
 * ejemplo el que vino a mirar.
 *
 * <p>Una cuenta se abre con un nombre -a nombre de quién es- y los productos se
 * le suman después, a medida que los pide. Una venta rápida, que se cobra en el
 * momento, puede no tener nombre. Se cobra igual que un turno: uno o varios
 * cobros en el mostrador, y una devolución si se saca algo ya cobrado.
 */
@Entity
@Table(name = "buffet_order")
@Getter
@Setter
public class BuffetOrder extends TenantScopedEntity {

    /** Cómo se muestra un pedido sin nombre. */
    public static final String QUICK_SALE = "Venta rápida";

    /**
     * A nombre de quién es el pedido; null en una venta rápida. No es un jugador
     * del club: no pide teléfono.
     */
    @Column(name = "customer_name", length = 120)
    private String customerName;

    /** Suma de los productos cargados. Se mantiene al día para no recalcularla en cada pantalla. */
    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    /** Suma de los cobros, con las devoluciones restadas. */
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Usuario del panel que abrió el pedido. */
    @Column(name = "registered_by")
    private UUID registeredBy;

    @Version
    @Column(nullable = false)
    private long version;

    public BigDecimal balanceDue() {
        return totalPrice.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    /** Lo cobrado de más, ej. después de sacar un producto que ya se había pagado. */
    public BigDecimal creditBalance() {
        return paidAmount.subtract(totalPrice).max(BigDecimal.ZERO);
    }

    /** El nombre, o "Venta rápida" si se cobró sin uno. */
    public String displayName() {
        return customerName == null ? QUICK_SALE : customerName;
    }

    public boolean hasMoneyIn() {
        return paidAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
