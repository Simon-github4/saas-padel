package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Un codigo mandado por WhatsApp para verificar un telefono antes de su primera
 * reserva.
 *
 * <p>No es de ningun club: que un numero exista y sea de quien reserva vale para
 * toda la plataforma.
 */
@Entity
@Table(name = "phone_verification")
@Getter
@Setter
public class PhoneVerification extends BaseEntity {

    /** En E.164, igual que {@link Customer}. */
    @Column(name = "phone_number", nullable = false, length = 25)
    private String phoneNumber;

    /** Hash BCrypt del codigo de 6 digitos, mismo {@code PasswordEncoder} que las contrasenas. */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Intentos fallidos. Al llegar al tope, el codigo deja de servir y hay que pedir otro. */
    @Column(name = "attempts", nullable = false)
    private int attempts;
}
