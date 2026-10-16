package ar.com.padelnec.notification.push;

import ar.com.padelnec.domain.PushSubscription;
import ar.com.padelnec.repository.PushSubscriptionRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.web.BusinessRuleException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Los navegadores de los usuarios del panel que pidieron los avisos de reservas
 * nuevas ("Avisarme en este celular").
 *
 * <p>Una suscripcion es de una persona en un dispositivo, y no depende de la sesion:
 * el aviso llega con el panel cerrado. La apaga la misma opcion, "Salir" en ese
 * dispositivo, un cambio de clave o deshabilitar al usuario.
 */
@Service
@RequiredArgsConstructor
public class PanelPushService {

    /**
     * Los servicios de push de los navegadores: Chrome, Samsung y Opera usan el de
     * Google; despues Firefox, Safari y Edge. El endpoint lo elige el navegador, pero
     * llega desde la pagina, y el servidor le va a mandar un POST: sin esta lista,
     * cualquiera con usuario del panel podria hacerlo pegarle a una direccion interna.
     */
    private static final List<String> PUSH_SERVICES = List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com", "notify.windows.com");

    private static final int MAX_ENDPOINT = 1024;
    private static final int MAX_USER_AGENT = 300;

    private final Optional<VapidKeys> vapidKeys;
    private final PushSubscriptionRepository repository;
    private final ObjectMapper objectMapper;

    /** Lo que devuelve {@code JSON.stringify(pushSubscription)} en el navegador. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BrowserSubscription(String endpoint, Keys keys) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Keys(String p256dh, String auth) {
        }
    }

    /** Sin claves VAPID no hay avisos, y la opcion no se muestra. */
    public boolean isEnabled() {
        return vapidKeys.isPresent();
    }

    /** La clave publica con la que se suscribe el navegador. */
    public Optional<String> applicationServerKey() {
        return vapidKeys.map(VapidKeys::publicKey);
    }

    /**
     * Guarda la suscripcion del navegador para este usuario. Si ese navegador ya
     * estaba suscripto con otra persona (el mismo celular, otro usuario), pasa a esta.
     */
    @Transactional
    public void subscribe(ClubUserPrincipal user, String subscriptionJson, String userAgent) {
        if (!isEnabled() || user.clubId() == null) {
            throw new BusinessRuleException("Los avisos no están disponibles para este usuario");
        }
        BrowserSubscription browser = parse(subscriptionJson);
        repository.deleteByEndpointInAnyClub(browser.endpoint());

        PushSubscription subscription = new PushSubscription();
        subscription.setClubUserId(user.userId());
        subscription.setEndpoint(browser.endpoint());
        subscription.setP256dh(browser.keys().p256dh());
        subscription.setAuth(browser.keys().auth());
        subscription.setUserAgent(userAgent == null || userAgent.length() <= MAX_USER_AGENT
                ? userAgent : userAgent.substring(0, MAX_USER_AGENT));
        repository.save(subscription);
    }

    /** Este navegador ya le avisa a este usuario. */
    @Transactional(readOnly = true)
    public boolean isSubscribed(UUID userId, String endpoint) {
        return endpoint != null && repository.existsByEndpointAndClubUserId(endpoint, userId);
    }

    /** La persona apago los avisos en este dispositivo, o salio desde el. */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        if (endpoint != null) {
            repository.deleteByEndpointAndUser(endpoint, userId);
        }
    }

    /** Cambio de clave o usuario deshabilitado: dejan de llegarle avisos en todos lados. */
    @Transactional
    public void forgetAll(UUID userId) {
        repository.deleteAllForUser(userId);
    }

    /** Los dispositivos del club en contexto. */
    @Transactional(readOnly = true)
    public List<PushSubscription> currentClubSubscriptions() {
        return repository.findAll();
    }

    @Transactional
    public void removeDead(UUID subscriptionId) {
        repository.deleteDead(subscriptionId);
    }

    private BrowserSubscription parse(String json) {
        BrowserSubscription browser;
        try {
            browser = objectMapper.readValue(json, BrowserSubscription.class);
        } catch (JacksonException ex) {
            throw unusable();
        }
        if (browser == null || browser.endpoint() == null || browser.keys() == null
                || browser.keys().p256dh() == null || browser.keys().auth() == null
                || browser.endpoint().length() > MAX_ENDPOINT || !isPushService(browser.endpoint())) {
            throw unusable();
        }
        try {
            P256.publicKey(P256.decode(browser.keys().p256dh()));
            if (P256.decode(browser.keys().auth()).length != 16) {
                throw unusable();
            }
        } catch (IllegalArgumentException ex) {
            throw unusable();
        }
        return browser;
    }

    static boolean isPushService(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && uri.getUserInfo() == null
                    && PUSH_SERVICES.stream().anyMatch(service -> host.equals(service) || host.endsWith("." + service));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static BusinessRuleException unusable() {
        return new BusinessRuleException("Este navegador no se puede usar para los avisos");
    }
}
