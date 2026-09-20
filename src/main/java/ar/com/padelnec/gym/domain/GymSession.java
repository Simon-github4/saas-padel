package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Sesion abierta de un {@link GymMember} en la app. El token es opaco y solo se
 * guarda su huella ({@link ar.com.padelnec.support.TokenHash}): una copia de la
 * tabla no sirve para entrar como nadie.
 */
@Entity
@Table(name = "gym_session")
@Getter
@Setter
public class GymSession extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private GymMember member;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** No nulo cuando la sesion se cerro antes de vencer (logout, cambio o reseteo de clave). */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
