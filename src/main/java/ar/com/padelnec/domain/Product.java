package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Producto de kiosco que el club vende en el mostrador, ej. "Gatorade", "Agua". */
@Entity
@Table(name = "product")
@Getter
@Setter
public class Product extends TenantScopedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Un producto inactivo desaparece del catalogo pero conserva su historial de ventas. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
