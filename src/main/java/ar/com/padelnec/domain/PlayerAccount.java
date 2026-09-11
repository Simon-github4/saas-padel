package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuenta del jugador, global a toda la plataforma. Se entra con email y
 * contrasena, o con Google -- nunca con el telefono, que quedo solo como dato
 * de contacto.
 *
 * <p>No es un {@link Customer}: el customer es a proposito por club (el mismo
 * telefono en dos clubes son dos filas independientes, cada una con su propia
 * confianza y su propio historial). Esta cuenta vive al mismo nivel que
 * {@link Tenant} -sin club_id- porque el mismo jugador reserva en varios
 * clubes de la plataforma y su historial tiene que verse junto.
 */
@Entity
@Table(name = "player_account")
@Getter
@Setter
public class PlayerAccount extends BaseEntity {

    /**
     * Identidad de la cuenta. Normalizado a minusculas.
     *
     * <p>Nullable a nivel de columna porque legado pre-migracion podria no
     * tenerlo, pero todo alta nueva lo exige -- la obligatoriedad se valida en
     * el DTO de registro, no aca.
     */
    @Column(name = "email", unique = true, length = 255)
    private String email;

    /**
     * Si el jugador confirmo ser dueno de ese email. No bloquea el login: toda
     * cuenta creada por contrasena ya nace confirmada (ver
     * {@code PlayerAuthService#confirmSignup}, que es la unica forma de crear
     * una); esto solo puede dar false en una cuenta de Google cuyo propio
     * proveedor todavia no verifico ese mail.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** Hash BCrypt. Null en cuentas que solo entran con Google. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** Claim "sub" de Google. Null hasta que la cuenta se loguee con Google al menos una vez. */
    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    /**
     * Huella del token vigente para resetear la contrasena, nunca el token. Null
     * cuando no hay ningun pedido abierto. El valor real solo viaja en el mail.
     */
    @Column(name = "password_reset_token_hash", length = 64)
    private String passwordResetTokenHash;

    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    /**
     * Normalizado a E.164, igual que {@link Customer#getPhoneNumber()}.
     *
     * <p>Ya no es la identidad de la cuenta: queda como dato de contacto,
     * opcional, que se completa solo la primera vez que el jugador reserva
     * logueado (ver {@code PlayerAuthService#updateProfile}). Sigue siendo
     * necesario para el historial entre clubes ({@code findHistoryByPhone}),
     * que no tiene otra forma de cruzar reservas sin un vinculo real (FK)
     * entre reserva y cuenta.
     */
    @Column(name = "phone_number", unique = true, length = 25)
    private String phoneNumber;

    /** Nombre que el jugador uso al reservar con sesion iniciada. Precarga el checkout. */
    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
