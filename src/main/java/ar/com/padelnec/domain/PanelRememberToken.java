package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un dispositivo que quedo recordado en el panel: vuelve a entrar sin la clave
 * hasta {@link #expiresAt}.
 *
 * <p>No extiende {@link TenantScopedEntity}, igual que {@link ClubUser}: se
 * resuelve en el filtro de seguridad, antes de que haya un club en contexto, y
 * el club sale del usuario.
 */
@Entity
@Table(name = "panel_remember_token")
@Getter
@Setter
public class PanelRememberToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * Huella SHA-256 del token, nunca el token: el valor que abre el panel solo
     * existe en la cookie del navegador. Ver {@link ar.com.padelnec.support.TokenHash}.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    /** Fijo desde el ingreso con clave: usarlo no lo estira. */
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
