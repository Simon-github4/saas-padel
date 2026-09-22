package ar.com.padelnec.payment;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.MercadoPagoOAuthAttempt;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.payment.MercadoPagoOAuthClient.AccountResponse;
import ar.com.padelnec.payment.MercadoPagoOAuthClient.TokenResponse;
import ar.com.padelnec.repository.MercadoPagoOAuthAttemptRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.TenantService;
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
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    /** Cuantas veces se intenta guardar la conexion si se cruza con otro guardado del club. */
    private static final int SAVE_ATTEMPTS = 3;

    private final AppProperties properties;
    private final MercadoPagoOAuthClient client;
    private final MercadoPagoOAuthAttemptRepository attemptRepository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
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
     *
     * <p>Sin transaccion alrededor: las dos llamadas a MercadoPago pueden tardar
     * segundos, y no tienen por que tener una conexion a la base tomada. El
     * guardado va aparte, en {@link #saveConnection}.
     */
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

        if (!tenantRepository.existsById(attempt.getTenantId())) {
            throw new IllegalStateException("El club del intento OAuth " + attempt.getId() + " ya no existe");
        }

        TokenResponse token = client.exchangeCode(code, attempt.getCodeVerifier(), redirectUri());
        AccountResponse account = fetchAccountOrNull(attempt.getTenantId(), token.accessToken());
        Instant connectedAt = clock.instant();
        Tenant club = saveConnection(attempt.getTenantId(), fresh -> {
            applyToken(fresh, token);
            fresh.setMpConnectedAt(connectedAt);
            applyAccount(fresh, account);
        });
        log.info("Club {} conecto su cuenta de MercadoPago", club.getSlug());
        return true;
    }

    /**
     * Pide un access token y refresh token nuevos antes de que venzan los actuales. Job de renovacion.
     *
     * <p>Primero MercadoPago y despues la base, y el guardado no puede perderse:
     * una vez que MercadoPago entrego tokens nuevos, los viejos pueden dejar de
     * servir. Por eso va con {@link #saveConnection}, que reintenta en vez de
     * rendirse si justo se cruza un guardado de Configuracion.
     */
    public void refresh(Tenant club) {
        TokenResponse token = client.refresh(club.getMpRefreshToken());
        saveConnection(club.getId(), fresh -> applyToken(fresh, token));
        log.info("Se renovo el access token de MercadoPago del club {}", club.getSlug());
    }

    /**
     * Guarda lo que trajo OAuth sobre el club recien leido (TenantService.update).
     *
     * <p>Configuracion nunca toca estas columnas, asi que si su guardado se cruza
     * con este y la version lo detecta, volver a leer y aplicar es siempre
     * correcto: no hay nada de ella que pisar. Se reintenta unas veces antes de
     * rendirse, porque lo que se perderia son credenciales recien emitidas.
     */
    private Tenant saveConnection(UUID clubId, Consumer<Tenant> changes) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tenantService.update(clubId, changes);
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt == SAVE_ATTEMPTS) {
                    throw ex;
                }
                log.info("Se cruzo otro guardado del club {} al guardar la conexion con MercadoPago; "
                        + "se reintenta", clubId);
            }
        }
    }

    /**
     * Borra todo lo que trajo OAuth. Las reservas nuevas vuelven a "pagar en el club".
     *
     * <p>Lee el club de nuevo en vez de guardar el que recibe: quien llama es
     * Configuracion, con el club cargado desde que se abrio la pantalla, y
     * guardar esa copia volveria a escribir el resto del club como estaba
     * entonces (ver TenantService.update).
     *
     * @return el club como quedo guardado
     */
    @Transactional
    public Tenant disconnect(Tenant stale) {
        Tenant club = tenantRepository.findById(stale.getId()).orElseThrow();
        club.setMpAccessToken(null);
        club.setMpRefreshToken(null);
        club.setMpUserId(null);
        club.setMpAccountName(null);
        club.setMpAccountEmail(null);
        club.setMpTokenExpiresAt(null);
        club.setMpConnectedAt(null);
        return tenantRepository.saveAndFlush(club);
    }

    /**
     * Completa nombre y email de la cuenta conectada si todavia no los tiene: las
     * conexiones hechas antes de que se guardaran. Lo llama Configuracion al abrir
     * la pestana de cobros, asi que se pide una sola vez por club.
     *
     * <p>Actualiza el {@code club} que recibe y la base con un update puntual, sin
     * guardar la entidad entera (ver {@link TenantRepository#updateMpAccount}).
     */
    @Transactional
    public void loadMissingAccount(Tenant club) {
        if (!club.acceptsOnlinePayments()
                || club.getMpAccountName() != null || club.getMpAccountEmail() != null) {
            return;
        }
        applyAccount(club, fetchAccountOrNull(club.getId(), club.getMpAccessToken()));
        tenantRepository.updateMpAccount(club.getId(), club.getMpAccountName(), club.getMpAccountEmail());
    }

    /** Clubes cuyo access token vence dentro de la ventana de renovacion. */
    @Transactional(readOnly = true)
    public List<Tenant> dueForRenewal() {
        return tenantRepository.findAllByMpRefreshTokenIsNotNullAndMpTokenExpiresAtBefore(
                clock.instant().plus(RENEWAL_WINDOW));
    }

    /**
     * Pregunta a MercadoPago de quien es la cuenta. Null si falla.
     *
     * <p>Si falla, la conexion sigue adelante igual: el nombre es para que el dueno
     * reconozca su cuenta, no hace falta para cobrar. Configuracion muestra
     * entonces el numero de cuenta, como antes.
     */
    private AccountResponse fetchAccountOrNull(UUID clubId, String accessToken) {
        try {
            return client.fetchAccount(accessToken);
        } catch (PaymentGatewayException ex) {
            log.warn("No se pudo leer la cuenta de MercadoPago del club {}: {}", clubId, ex.getMessage());
            return null;
        }
    }

    /** Deja en el club nombre y email de la cuenta; sin cuenta (fallo la lectura), no toca nada. */
    private static void applyAccount(Tenant club, AccountResponse account) {
        if (account == null) {
            return;
        }
        club.setMpAccountName(displayName(account));
        club.setMpAccountEmail(blankToNull(account.email()));
    }

    /** Nombre y apellido; si la cuenta no los tiene, el apodo de MercadoPago. */
    private static String displayName(AccountResponse account) {
        String fullName = ((account.firstName() == null ? "" : account.firstName().trim()) + " "
                + (account.lastName() == null ? "" : account.lastName().trim())).trim();
        String name = fullName.isEmpty() ? blankToNull(account.nickname()) : fullName;
        return name == null || name.length() <= 160 ? name : name.substring(0, 160);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
