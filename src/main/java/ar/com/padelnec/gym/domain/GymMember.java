package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Socio del gimnasio, identificado por su DNI dentro de un club. Entra a la app
 * con una clave que le da el mostrador; la primera vez tiene que cambiarla.
 */
@Entity
@Table(name = "gym_member")
@Getter
@Setter
public class GymMember extends TenantScopedEntity {

    /** Solo digitos, sin puntos. */
    @Column(nullable = false, length = 12)
    private String dni;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(length = 25)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(columnDefinition = "text")
    private String notes;
}
