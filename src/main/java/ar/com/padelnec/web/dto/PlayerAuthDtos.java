package ar.com.padelnec.web.dto;

import ar.com.padelnec.repository.BookingRepository.PlayerBookingHistoryRow;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos del login del jugador. */
public final class PlayerAuthDtos {

    private PlayerAuthDtos() {
    }

    /** Mismo largo que Customer.email en la base: sin esto, un email gigante viaja entero hasta el error de columna. */
    private static final String EMAIL_TOO_LONG = "Ese email es demasiado largo";

    public record RegisterRequest(
            @NotBlank(message = "Necesitamos tu email") @Email(message = "Ese email no parece válido")
            @Size(max = 255, message = EMAIL_TOO_LONG) String email,
            @NotBlank(message = "Necesitamos una contraseña")
            @Size(min = 8, message = "La contraseña tiene que tener al menos 8 caracteres") String password,
            @Size(max = 100, message = "El nombre es muy largo") String displayName,
            String phoneNumber) {
    }

    public record ConfirmSignupRequest(
            @NotBlank(message = "Necesitamos tu email") @Email(message = "Ese email no parece válido")
            @Size(max = 255, message = EMAIL_TOO_LONG) String email,
            @NotBlank(message = "Necesitamos el código")
            @Pattern(regexp = "\\d{6}", message = "El código son 6 números") String code) {
    }

    public record LoginRequest(
            @NotBlank(message = "Necesitamos tu email") @Email(message = "Ese email no parece válido")
            @Size(max = 255, message = EMAIL_TOO_LONG) String email,
            @NotBlank(message = "Necesitamos tu contraseña") String password) {
    }

    /**
     * 4096 alcanza de sobra para un id_token real de Google (unos pocos KB como
     * mucho): sin este tope, un cuerpo gigante llegaba entero a la verificacion
     * criptografica antes de que nada lo rechazara.
     */
    public record GoogleLoginRequest(
            @NotBlank(message = "Falta el token de Google")
            @Size(max = 4096, message = "Ese token no es válido") String idToken) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Necesitamos tu email") @Email(message = "Ese email no parece válido")
            @Size(max = 255, message = EMAIL_TOO_LONG) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Falta el token") String token,
            @NotBlank(message = "Necesitamos una contraseña")
            @Size(min = 8, message = "La contraseña tiene que tener al menos 8 caracteres") String newPassword) {
    }

    public record ConfigResponse(String googleClientId) {
    }

    public record SessionResponse(
            String token,
            Instant expiresAt,
            UUID accountId,
            String email,
            boolean emailVerified,
            String phoneNumber,
            String displayName) {
    }

    public record MeResponse(
            UUID accountId,
            String email,
            boolean emailVerified,
            String phoneNumber,
            String displayName) {
    }

    /** Nombre y telefono de contacto del jugador. */
    /**
     * Turnos que este navegador reservo sin cuenta y ahora quiere guardar en una.
     *
     * <p>Lo que autoriza el reclamo es tener el token: ya alcanza para ver y
     * cancelar el turno, asi que atarlo a una cuenta no suma ningun poder.
     *
     * <p>El tope es el mismo que guarda el navegador, veinte.
     */
    public record ClaimBookingsRequest(
            @NotEmpty(message = "No hay turnos para guardar")
            @Size(max = 20, message = "Son demasiados turnos")
            List<@NotBlank @Size(max = 64) String> managementTokens) {
    }

    public record ClaimBookingsResponse(int claimed) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Necesitamos tu nombre")
            @Size(max = 100, message = "El nombre es muy largo") String name,
            String phoneNumber) {
    }

    /** Un turno del historial global, con el link para verlo y gestionarlo. */
    public record BookingHistoryItem(
            UUID bookingId,
            String clubName,
            String clubSlug,
            String courtName,
            Instant startTime,
            Instant endTime,
            String status,
            BigDecimal totalPrice,
            BigDecimal paidAmount,
            String managementToken) {

        public static BookingHistoryItem of(PlayerBookingHistoryRow row) {
            return new BookingHistoryItem(
                    row.getBookingId(),
                    row.getClubName(),
                    row.getClubSlug(),
                    row.getCourtName(),
                    row.getStartTime(),
                    row.getEndTime(),
                    row.getStatus(),
                    row.getTotalPrice(),
                    row.getPaidAmount(),
                    row.getManagementToken());
        }
    }
}
