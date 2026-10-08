package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Un pedido de verificacion de telefono: el codigo que el jugador nos tiene que
 * mandar por WhatsApp desde ese numero antes de su primera reserva sin sena.
 *
 * <p>No es de ningun club: que un numero exista y sea de quien reserva vale para
 * toda la plataforma.
 */
@Entity
@Table(name = "phone_verification")
@Getter
@Setter
public class PhoneVerification extends BaseEntity {

    /** En E.164, igual que {@link Customer}. Tiene que ser el que manda el mensaje. */
    @Column(name = "phone_number", nullable = false, length = 25)
    private String phoneNumber;

    /**
     * Los 6 digitos que van en el mensaje, en claro: no son una credencial. Lo que
     * prueba que el numero es del jugador es que el mensaje llegue desde ese numero
     * (ver V14).
     */
    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Cuando llego el mensaje que lo confirma. Nulo mientras esta pendiente. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
