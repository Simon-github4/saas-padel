package ar.com.padelnec.web.api;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.PlayerSession;
import ar.com.padelnec.service.BookingClaimService;
import ar.com.padelnec.service.PlayerAuthService;
import ar.com.padelnec.service.PlayerAuthService.IssuedSession;
import ar.com.padelnec.web.UnauthorizedSessionException;
import ar.com.padelnec.web.dto.PlayerAuthDtos.BookingHistoryItem;
import ar.com.padelnec.web.dto.PlayerAuthDtos.ConfigResponse;
import ar.com.padelnec.web.dto.PlayerAuthDtos.ClaimBookingsRequest;
import ar.com.padelnec.web.dto.PlayerAuthDtos.ClaimBookingsResponse;
import ar.com.padelnec.web.dto.PlayerAuthDtos.ConfirmSignupRequest;
import ar.com.padelnec.web.dto.PlayerAuthDtos.ForgotPasswordRequest;
import ar.com.padelnec.web.dto.PlayerAuthDtos.GoogleLoginRequest;
import ar.com.padelnec.web.dto.PlayerAuthDtos.LoginRequest;
import ar.com.padelnec.web.dto.PlayerAuthDtos.MeResponse;
import ar.com.padelnec.web.dto.PlayerAuthDtos.RegisterRequest;
import ar.com.padelnec.web.dto.PlayerAuthDtos.ResetPasswordRequest;
import ar.com.padelnec.web.dto.PlayerAuthDtos.SessionResponse;
import ar.com.padelnec.web.dto.PlayerAuthDtos.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login del jugador y su cuenta global.
 *
 * <p>Cuelga de {@code /api/public}, que ya es de acceso libre: la autenticacion se
 * resuelve a mano contra el token de sesion, igual que {@link PublicBookingController}
 * ya resuelve el token de gestion de un turno. No hace falta un filtro de Spring
 * Security nuevo.
 */
@RestController
@RequestMapping("/api/public/player")
@RequiredArgsConstructor
public class PlayerAuthController {

    private final PlayerAuthService playerAuthService;
    private final BookingClaimService bookingClaimService;
    private final LoginRateLimiter loginRateLimiter;
    private final BookingRateLimiter bookingRateLimiter;
    private final AppProperties properties;

    /** Datos publicos que necesita el frontend antes de mostrar el boton de Google. */
    @GetMapping("/config")
    public ConfigResponse config() {
        return new ConfigResponse(properties.getGoogle().getClientId());
    }

    /** Arranca el alta: manda el codigo/link de confirmacion, todavia no crea la cuenta. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        bookingRateLimiter.check(httpRequest.getRemoteAddr());
        playerAuthService.register(
                request.email(), request.password(), request.displayName(), request.phoneNumber());
    }

    /** Confirma el alta con el codigo de 6 digitos: recien aca se crea la cuenta y se abre sesion. */
    @PostMapping("/register/confirm")
    public SessionResponse confirmSignup(@Valid @RequestBody ConfirmSignupRequest request,
                                         HttpServletRequest httpRequest) {
        bookingRateLimiter.check(httpRequest.getRemoteAddr());
        return toResponse(playerAuthService.confirmSignup(request.email(), request.code()));
    }

    /** Login con email y contrasena. */
    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        loginRateLimiter.check(request.email());
        return toResponse(playerAuthService.login(request.email(), request.password()));
    }

    /**
     * Login con Google: el frontend ya obtuvo el ID token con el SDK de Identity
     * Services.
     *
     * <p>No hay email todavia para limitar por destinatario (recien sale de
     * adentro del token, despues de verificarlo) -por eso por origen, como
     * {@code register}, y no por {@link LoginRateLimiter}: sin esto, nada frenaba
     * mandar tokens basura en bucle solo para gastar la verificacion criptografica.
     */
    @PostMapping("/login/google")
    public SessionResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                           HttpServletRequest httpRequest) {
        bookingRateLimiter.check(httpRequest.getRemoteAddr());
        return toResponse(playerAuthService.loginWithGoogle(request.idToken()));
    }

    /** Pide el link para resetear la contrasena. Responde igual exista o no ese email. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        loginRateLimiter.check(request.email());
        playerAuthService.requestPasswordReset(request.email());
    }

    /** Aplica la contrasena nueva a partir del token del link. */
    @PostMapping("/password/reset")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        playerAuthService.resetPassword(request.token(), request.newPassword());
    }

    /** Confirma el alta a partir del link. Devuelve HTML: es un click de un solo uso, no hace falta el SPA. */
    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        boolean confirmed = playerAuthService.confirmSignupByToken(token);
        String message = confirmed
                ? "Listo, confirmamos tu cuenta. Volvé a la pestaña donde te registraste e iniciá sesión."
                : "Ese link venció o no es válido. Registrate de nuevo.";
        String html = "<!doctype html><html lang=\"es\"><meta charset=\"utf-8\">"
                + "<title>Pádel</title><body style=\"font-family: sans-serif; padding: 2rem;\">"
                + "<p>" + message + "</p></body></html>";
        return ResponseEntity.ok(html);
    }

    /** Identidad de la sesion vigente. */
    @GetMapping("/me")
    public MeResponse me(@RequestHeader("Authorization") String authorization) {
        PlayerAccount account = playerAuthService.resolveSession(bearerToken(authorization));
        return new MeResponse(account.getId(), account.getEmail(), account.isEmailVerified(),
                account.getPhoneNumber(), account.getDisplayName());
    }

    /** Nombre y telefono de contacto del jugador. */
    @PutMapping("/profile")
    public void updateProfile(@RequestHeader("Authorization") String authorization,
                              @Valid @RequestBody UpdateProfileRequest request) {
        playerAuthService.updateProfile(bearerToken(authorization), request.name(), request.phoneNumber());
    }

    /** Cierra la sesion. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) {
        playerAuthService.logout(bearerToken(authorization));
    }

    /**
     * Guarda en la cuenta los turnos que este navegador reservo sin ella.
     *
     * <p>Lo llama la app apenas se abre sesion, con los tokens que tenga
     * guardados. Lo que autoriza cada reclamo es tener ese token, que ya alcanza
     * para cancelar el turno -- no el telefono, que no prueba nada.
     */
    @PostMapping("/bookings/claim")
    public ClaimBookingsResponse claimBookings(@RequestHeader("Authorization") String authorization,
                                               @Valid @RequestBody ClaimBookingsRequest request) {
        UUID accountId = playerAuthService.resolveSession(bearerToken(authorization)).getId();
        return new ClaimBookingsResponse(
                bookingClaimService.claim(accountId, request.managementTokens()));
    }

    /** Turnos del jugador en todos los clubes de la plataforma. */
    @GetMapping("/bookings")
    public List<BookingHistoryItem> bookings(@RequestHeader("Authorization") String authorization) {
        return playerAuthService.history(bearerToken(authorization)).stream()
                .map(BookingHistoryItem::of)
                .toList();
    }

    private SessionResponse toResponse(IssuedSession issued) {
        PlayerAccount account = issued.session().getPlayer();
        return new SessionResponse(issued.token(), issued.session().getExpiresAt(), account.getId(),
                account.getEmail(), account.isEmailVerified(), account.getPhoneNumber(), account.getDisplayName());
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión.");
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
