package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "court")
@Getter
@Setter
public class Court extends TenantScopedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    /** Orden en el que aparece la cancha en la grilla del panel y de la app. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Una cancha inactiva desaparece de la grilla pero conserva su historial. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
