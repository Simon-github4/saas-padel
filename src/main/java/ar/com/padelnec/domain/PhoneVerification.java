package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un pedido de verificacion de telefono: el codigo que el jugador nos tiene que
 * mandar por WhatsApp desde ese numero antes de su primera reserva sin sena.
 *
 * <p>No es de ningun club: que un numero exista y sea de quien reserva vale para
 * toda la plataforma. El club que guarda es solo el del turno que espera el mensaje.
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

    // El turno que el jugador estaba reservando: sale solo cuando llega el mensaje
    // (ver V15). Nulos en los pedidos de paginas viejas, que reservan ellas.

    /** Un club cualquiera, sin filtro por tenant: el pedido no es de ningun club. */
    @Column(name = "club_id")
    private UUID clubId;

    @Column(name = "court_id")
    private UUID courtId;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(name = "player_account_id")
    private UUID playerAccountId;

    /** La reserva que salio al llegar el mensaje. */
    @Column(name = "booking_id")
    private UUID bookingId;

    /** Por que no salio: el codigo de la API ({@code SLOT_TAKEN}, {@code RULE_VIOLATION}...). */
    @Column(name = "booking_error_code", length = 40)
    private String bookingErrorCode;

    @Column(name = "booking_error_reason", length = 40)
    private String bookingErrorReason;

    @Column(name = "booking_error_message", length = 300)
    private String bookingErrorMessage;

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    /** Si trae un turno para reservar cuando llegue el mensaje. */
    public boolean holdsBooking() {
        return clubId != null && courtId != null && startTime != null && fullName != null;
    }

    /** Si la reserva que traia ya se intento, saliera o no. */
    public boolean isBookingSettled() {
        return bookingId != null || bookingErrorCode != null;
    }
}
