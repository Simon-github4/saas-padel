package ar.com.padelnec.web.api;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Techo de eventos de visita por origen.
 *
 * <p>Aparte de {@code BookingRateLimiter} por dos diferencias que importan. La
 * primera es de escala: una visita normal genera decenas de eventos, asi que el
 * cupo de una reserva dejaria sin medir al jugador que mas navega, que es
 * justamente el que mas dice. La segunda es de respuesta: pasarse del tope no es
 * un error que el jugador pueda corregir, asi que aca se descarta en silencio en
 * vez de tirar una excepcion -- la app manda los eventos con {@code sendBeacon} y
 * no lee la respuesta.
 *
 * <p>Ventana fija y no deslizante: sirve para cortar un abuso, no para medir con
 * precision, y cuesta un contador en vez de una cola por origen.
 */
@Component
@RequiredArgsConstructor
public class EventRateLimiter {

    private static final int MAX_EVENTS = 300;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_TRACKED_ORIGINS = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    /** {@code true} si el lote entra en el cupo del origen. */
    public boolean allow(String origin, int events) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        long now = clock.millis();

        // Cota de memoria: sin esto, quien rota direcciones hace crecer el mapa
        // hasta tumbar el proceso, que es lo que se queria evitar.
        if (windows.size() > MAX_TRACKED_ORIGINS) {
            windows.entrySet().removeIf(entry -> entry.getValue().expired(now));
        }

        Window window = windows.compute(origin, (key, current) ->
                current == null || current.expired(now) ? new Window(now) : current);
        return window.count.addAndGet(events) <= MAX_EVENTS;
    }

    private record Window(long startedAt, AtomicInteger count) {

        Window(long startedAt) {
            this(startedAt, new AtomicInteger());
        }

        boolean expired(long now) {
            return now - startedAt > WINDOW.toMillis();
        }
    }
}
