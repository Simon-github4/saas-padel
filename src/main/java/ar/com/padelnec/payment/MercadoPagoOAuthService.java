package ar.com.padelnec.payment;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.MercadoPagoOAuthAttempt;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.payment.MercadoPagoOAuthClient.TokenResponse;
import ar.com.padelnec.repository.MercadoPagoOAuthAttemptRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.support.Pkce;
import ar.com.padelnec.support.TokenHash;
import ar.com.padelnec.support.Tokens;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta la conexion OAuth de cada club con MercadoPago: autorizacion con PKCE,
 * intercambio del codigo, renovacion y desconexion.
 *
 * <p>Todos los clubes cuelgan de la misma aplicacion de MercadoPago (un solo
 * {@code client_id}/{@code client_secret} en {@code AppProperties}), pero cada
 * uno autoriza por separado y termina con su propio access token: el consentimiento
 * es del vendedor, no de la aplicacion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoOAuthService {

    /** Lo que dura el "code" de MercadoPago: pasado esto, no sirve ni intentar el intercambio. */
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    /**
     * Cuanto antes del vencimiento el job de renovacion ya intenta refrescar.
     * Frente a los 180 dias reales de vida del token, un mes de margen deja de
     * sobra para que reintentos por una falla transitoria de MercadoPago no
     * terminen justo en la fecha de corte.
     */
    private static final Duration RENEWAL_WINDOW = Duration.ofDays(30);

    private final AppProperties properties;
    private final MercadoPagoOAuthClient client;
    private final MercadoPagoOAuthAttemptRepository attemptRepository;
    private final TenantRepository tenantRepository;
    private final Clock clock;

    /**
     * Arma la URL a la que hay que mandar al navegador del dueno para autorizar,
     * y deja guardado el {@code code_verifier} a la espera del callback.
     */
    @Transactional
    public String startAuthorization(Tenant club) {
        Instant now = clock.instant();
        // Tocar "Conectar" de nuevo no deberia dejar vivo un intento anterior del
        // mismo club, y de paso barre cualquier otro ya vencido.
        attemptRepository.deleteStaleFor(club.getId(), now);

        String state = Tokens.generate();
        String codeVerifier = Tokens.generate();

        MercadoPagoOAuthAttempt attempt = new MercadoPagoOAuthAttempt();
        attempt.setStateHash(TokenHash.of(state));
        attempt.setTenantId(club.getId());
        attempt.setCodeVerifier(codeVerifier);
        attempt.setExpiresAt(now.plus(CODE_TTL));
        attemptRepository.save(attempt);

        String codeChallenge = Pkce.codeChallengeS256(codeVerifier);
        return "https://auth.mercadopago.com/authorization"
                + "?response_type=code"
                + "&client_id=" + encode(properties.getMercadopago().getClientId())
                + "&redirect_uri=" + encode(redirectUri())
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256"
                + "&state=" + encode(state);
    }

    /**
     * Completa el intercambio a partir del {@code code} y {@code state} que trae
     * el callback.
     *
     * @return false si el {@code state} no corresponde a ningun intento vivo (ya
     *         usado, vencido, o directamente falso) -- nunca lanza para ese caso,
     *         porque es una condicion esperada (el dueno cerro la pestana y
     *         volvio a tocar "Conectar", por ejemplo) y no un error de verdad.
     */
    @Transactional
    public boolean completeAuthorization(String code, String state) {
        Optional<MercadoPagoOAuthAttempt> found = attemptRepository.findByStateHash(TokenHash.of(state));
        if (found.isEmpty()) {
            log.warn("Llego un callback de MercadoPago con un state desconocido o ya consumido");
            return false;
        }

        MercadoPagoOAuthAttempt attempt = found.get();
        // De un solo uso: se borra la haya completado bien o mal.
        attemptRepository.delete(attempt);
        if (attempt.isExpired(clock.instant())) {
            log.warn("Llego un callback de MercadoPago para un intento ya vencido del club {}",
                    attempt.getTenantId());
            return false;
        }

        Tenant club = tenantRepository.findById(attempt.getTenantId())
                .orElseThrow(() -> new IllegalStateException(
                        "El club del intento OAuth " + attempt.getId() + " ya no existe"));

        TokenResponse token = client.exchangeCode(code, attempt.getCodeVerifier(), redirectUri());
        applyToken(club, token);
        club.setMpConnectedAt(clock.instant());
        tenantRepository.save(club);
        log.info("Club {} conecto su cuenta de MercadoPago", club.getSlug());
        return true;
    }

    /** Pide un access token y refresh token nuevos antes de que venzan los actuales. Job de renovacion. */
    @Transactional
    public void refresh(Tenant club) {
        TokenResponse token = client.refresh(club.getMpRefreshToken());
        applyToken(club, token);
        tenantRepository.save(club);
        log.info("Se renovo el access token de MercadoPago del club {}", club.getSlug());
    }

    /** Borra todo lo que trajo OAuth. Las reservas nuevas vuelven a "pagar en el club". */
    @Transactional
    public void disconnect(Tenant club) {
        club.setMpAccessToken(null);
        club.setMpRefreshToken(null);
        club.setMpUserId(null);
        club.setMpTokenExpiresAt(null);
        club.setMpConnectedAt(null);
        tenantRepository.save(club);
    }

    /** Clubes cuyo access token vence dentro de la ventana de renovacion. */
    @Transactional(readOnly = true)
    public List<Tenant> dueForRenewal() {
        return tenantRepository.findAllByMpRefreshTokenIsNotNullAndMpTokenExpiresAtBefore(
                clock.instant().plus(RENEWAL_WINDOW));
    }

    private void applyToken(Tenant club, TokenResponse token) {
        club.setMpAccessToken(token.accessToken());
        club.setMpRefreshToken(token.refreshToken());
        club.setMpUserId(String.valueOf(token.userId()));
        club.setMpTokenExpiresAt(clock.instant().plus(Duration.ofSeconds(token.expiresInSeconds())));
    }

    private String redirectUri() {
        return properties.getBaseUrl() + "/api/mercadopago/oauth/callback";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
