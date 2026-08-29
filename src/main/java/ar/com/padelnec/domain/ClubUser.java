package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Usuario del panel de administracion.
 *
 * <p>No extiende {@link TenantScopedEntity} a proposito: el login ocurre antes de
 * que exista contexto de tenant, asi que el club se resuelve a partir del usuario
 * y no al reves.
 */
@Entity
@Table(name = "club_user")
@Getter
@Setter
public class ClubUser extends BaseEntity {

    /** Nulo para usuarios de plataforma (SUPER_ADMIN). */
    @Column(name = "club_id")
    private UUID clubId;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
