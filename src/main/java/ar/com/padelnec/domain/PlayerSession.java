package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Sesion activa de una {@link PlayerAccount}, identificada por un token opaco. */
@Entity
@Table(name = "player_session")
@Getter
@Setter
public class PlayerSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerAccount player;

    /** Credencial de la sesion. Generado con {@link ar.com.padelnec.support.Tokens#generate()}. */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** No nulo cuando la sesion se cerro antes de vencer (logout). */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
