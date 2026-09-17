package ar.com.padelnec.domain;

import ar.com.padelnec.support.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Intento de conexion OAuth con MercadoPago, entre que el dueno toca "Conectar"
 * en su panel y MercadoPago redirige de vuelta con el codigo de autorizacion.
 *
 * <p>No extiende {@link TenantScopedEntity} a proposito: el callback de
 * MercadoPago llega sin contexto de club instalado -- resolverlo es lo primero
 * que hay que hacer, a partir del {@code state} -- y el filtro automatico de
 * tenant de esa base lo bloquearia antes de poder leer la fila.
 */
@Entity
@Table(name = "mp_oauth_attempt")
@Getter
@Setter
public class MercadoPagoOAuthAttempt extends BaseEntity {

    /**
     * Huella SHA-256 del {@code state} que viaja por el navegador y vuelve en el
     * callback. Ver {@link ar.com.padelnec.support.TokenHash}: el valor que
     * MercadoPago devuelve no sirve de nada por si solo -- sin el {@code code},
     * que solo entrega MercadoPago, no hay intercambio posible -- pero tampoco
     * hay motivo para guardarlo en claro.
     */
    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Cifrado en reposo aunque vive minutos: nunca viaja al navegador, y es lo
     * unico que impide que un {@code code} interceptado a mitad de camino
     * alcance para completar el intercambio (RFC 7636).
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "code_verifier", nullable = false, columnDefinition = "text")
    private String codeVerifier;

    /** El {@code code} de MercadoPago dura 10 minutos y es de un solo uso. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
