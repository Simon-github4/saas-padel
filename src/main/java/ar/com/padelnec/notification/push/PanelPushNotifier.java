package ar.com.padelnec.notification.push;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.PushSubscription;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Manda a los celulares del club el aviso de una reserva nueva o de un turno que el
 * jugador cancelo (Web Push).
 *
 * <p>En un hilo aparte: quien dispara esto es el pedido del jugador que reservo o
 * cancelo, o el webhook de MercadoPago que acredito la seña, y ninguno tiene por que
 * esperar a FCM o a Apple, ni enterarse si fallan. Un solo hilo alcanza: son un par
 * de avisos por reserva, y asi salen en el orden en que entraron.
 *
 * <p>Un aviso que no sale no se reintenta: el panel igual muestra la reserva, con su
 * propio cartel, y la agenda la tiene. Lo unico que se corrige solo es la
 * suscripcion muerta (404/410: el navegador la dio de baja), que se borra.
 */
@Component
@Slf4j
public class PanelPushNotifier {

    /** Si el celular esta apagado, el servicio de push lo guarda hasta 12 horas. */
    private static final Duration TTL = Duration.ofHours(12);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Optional<VapidKeys> vapidKeys;
    private final PanelPushService subscriptions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("panel-push-", 0).factory());

    public PanelPushNotifier(Optional<VapidKeys> vapidKeys, PanelPushService subscriptions,
                             ObjectMapper objectMapper, Clock clock) {
        this.vapidKeys = vapidKeys;
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    enum Outcome { DELIVERED, GONE, FAILED }

    /**
     * Encola el aviso para todos los dispositivos del club. El {@code tag} es la
     * reserva: si por algun motivo llegara dos veces, el celular muestra una sola.
     */
    public void newWebBooking(UUID clubId, UUID bookingId, String body) {
        notifyClub(clubId, "Nueva reserva", bookingId.toString(), bookingId, body);
    }

    /**
     * Un jugador cancelo desde su link: el horario quedo libre para revender. Lleva su
     * propio {@code tag} y no el de la reserva: con el mismo, el celular cambiaria en
     * silencio el aviso de la reserva nueva por este, sin sonar ni vibrar.
     */
    public void webCancellation(UUID clubId, UUID bookingId, String body) {
        notifyClub(clubId, "Turno cancelado", "cancelado-" + bookingId, bookingId, body);
    }

    /**
     * Tocar el aviso abre la agenda en el dia del turno, marcado: el {@code ?reserva=}
     * es el {@code AgendaView.BOOKING_PARAM} del panel, que este paquete no ve.
     */
    private void notifyClub(UUID clubId, String title, String tag, UUID bookingId, String body) {
        if (vapidKeys.isEmpty()) {
            return;
        }
        byte[] payload = objectMapper.writeValueAsBytes(Map.of(
                "title", title,
                "body", body,
                "tag", tag,
                "url", "/admin/?reserva=" + bookingId));
        executor.execute(() -> deliverToClub(clubId, payload));
    }

    private void deliverToClub(UUID clubId, byte[] payload) {
        try {
            TenantContext.runAs(clubId, () -> {
                for (PushSubscription subscription : subscriptions.currentClubSubscriptions()) {
                    if (send(subscription, payload) == Outcome.GONE) {
                        subscriptions.removeDead(subscription.getId());
                    }
                }
            });
        } catch (RuntimeException ex) {
            log.warn("No se pudieron mandar los avisos del club {}", clubId, ex);
        }
    }

    private Outcome send(PushSubscription subscription, byte[] payload) {
        URI endpoint = URI.create(subscription.getEndpoint());
        try {
            byte[] body = WebPushEncryption.encrypt(payload,
                    P256.decode(subscription.getP256dh()), P256.decode(subscription.getAuth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Authorization", vapidKeys.orElseThrow().authorization(endpoint, clock.instant()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(TTL.toSeconds()))
                    // Una reserva nueva o un turno libre son para ver ya: sin esto Android puede demorarlos
                    // hasta que el celular salga del ahorro de bateria.
                    .header("Urgency", "high")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Outcome.DELIVERED;
            }
            if (status == 404 || status == 410) {
                log.info("Suscripcion de push dada de baja por el navegador ({} en {}), se borra",
                        status, endpoint.getHost());
                return Outcome.GONE;
            }
            log.warn("El servicio de push {} rechazo el aviso: {} {}", endpoint.getHost(), status,
                    abbreviate(response.body()));
            return Outcome.FAILED;
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("No se pudo mandar el aviso a {}: {}", endpoint.getHost(), ex.toString());
            return Outcome.FAILED;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        }
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
