package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Un registro todavia no confirmado. No es una {@link PlayerAccount}: si el
 * jugador no confirma con el codigo o el link, esta fila se descarta (o la
 * reemplaza un segundo intento) y nunca llega a existir una cuenta de verdad.
 */
@Entity
@Table(name = "player_signup_pending")
@Getter
@Setter
public class PendingPlayerSignup extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "phone_number", length = 25)
    private String phoneNumber;

    /** Hash BCrypt del codigo de 6 digitos, mismo {@code PasswordEncoder} que las contrasenas. */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    /** Token del link alternativo, en texto plano -- misma logica que el resto de los links del sistema. */
    @Column(name = "confirm_token", nullable = false, unique = true, length = 64)
    private String confirmToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Intentos fallidos de codigo. A los 5, hay que registrarse de nuevo en vez de esperar el vencimiento. */
    @Column(name = "attempts", nullable = false)
    private int attempts;
}
