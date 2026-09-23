package ar.com.padelnec.gym.web;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.service.GymAuthService;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymLocation;
import ar.com.padelnec.gym.service.GymRateLimits;
import ar.com.padelnec.gym.service.GymSettingsService;
import ar.com.padelnec.gym.service.GymStatusService;
import ar.com.padelnec.gym.web.GymDtos.ChangePasswordRequest;
import ar.com.padelnec.gym.web.GymDtos.CheckInRequest;
import ar.com.padelnec.gym.web.GymDtos.CheckInResponse;
import ar.com.padelnec.gym.web.GymDtos.ConfigResponse;
import ar.com.padelnec.gym.web.GymDtos.LoginRequest;
import ar.com.padelnec.gym.web.GymDtos.MeResponse;
import ar.com.padelnec.gym.web.GymDtos.SessionResponse;
import ar.com.padelnec.web.ClientIp;
import ar.com.padelnec.web.UnauthorizedSessionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de la app del gimnasio.
 *
 * <p>Cuelga de {@code /api/public/{slug}/gym}: esa ruta ya es de acceso libre y el
 * {@code TenantContextFilter} instala el club a partir del slug antes de que se
 * abra ninguna transaccion. Como en el login del jugador, la autenticacion se
 * resuelve a mano contra el token de sesion (Bearer).
 *
 * <p>Un club sin el modulo prendido responde 404 en todo, como si la ruta no
 * existiera.
 */
@RestController
@RequestMapping("/api/public/{slug}/gym")
@RequiredArgsConstructor
public class GymController {

    private final GymModule gymModule;
    private final GymAuthService authService;
    private final GymCheckinService checkinService;
    private final GymStatusService statusService;
    private final GymRateLimits rateLimits;
    private final GymSettingsService settingsService;

    /** Lo que la app necesita antes del login: el nombre, si pide clave y si va a pedir la ubicacion. */
    @GetMapping("/config")
    public ConfigResponse config() {
        gymModule.requireEnabled();
        GymSettingsService.PublicConfig config = settingsService.publicConfig();
        return new ConfigResponse(
                config.clubName(),
                config.passwordRequired(),
                config.locationRequired(),
                config.heroImageUrl(),
                config.themeMode(),
                config.primaryColor(),
                config.secondaryColor());
    }

    @PostMapping("/login")
    public SessionResponse login(@PathVariable String slug, @Valid @RequestBody LoginRequest request,
                                 HttpServletRequest http) {
        gymModule.requireEnabled();
        rateLimits.checkLogin(slug, request.dni(), ClientIp.of(http));
        return SessionResponse.of(authService.login(request.dni(), request.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        gymModule.requireEnabled();
        authService.logout(bearerToken(authorization));
    }

    /** Unica ruta, junto con el login, que se puede usar con la clave temporal. */
    @PostMapping("/password/change")
    public SessionResponse changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        gymModule.requireEnabled();
        return SessionResponse.of(authService.changePassword(
                bearerToken(authorization), request.currentPassword(), request.newPassword()));
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        gymModule.requireEnabled();
        GymMember member = authService.requireMember(bearerToken(authorization));
        return MeResponse.of(statusService.of(member.getId()));
    }

    /**
     * El socio que ingresa sale de la sesion, nunca del cuerpo del pedido: es lo que
     * impide registrar un ingreso a nombre de otro.
     */
    @PostMapping("/checkin")
    public CheckInResponse checkIn(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CheckInRequest request, HttpServletRequest http) {
        gymModule.requireEnabled();
        GymMember member = authService.requireMember(bearerToken(authorization));
        rateLimits.checkCheckIn(member.getId(), ClientIp.of(http));
        // Sin las dos coordenadas no hay ubicacion: la sede que la exige lo va a decir.
        GymLocation.Point location = request.latitude() != null && request.longitude() != null
                ? new GymLocation.Point(request.latitude(), request.longitude())
                : null;
        return CheckInResponse.of(checkinService.checkIn(member.getId(), request.qrToken(), location));
    }

    /** {@code Authorization: Bearer <token>}; un encabezado ausente o mal armado es una sesion vencida. */
    private static String bearerToken(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión.");
        }
        return header.substring(7).trim();
    }
}
