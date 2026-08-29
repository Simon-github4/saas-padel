package ar.com.padelnec.web.api;

import ar.com.padelnec.web.BusinessRuleException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Techo de intentos de reserva por origen.
 *
 * <p>Crear una reserva bloquea una cancha sin haber cobrado nada, asi que el
 * endpoint es un vector de abuso trivial: un script puede dejar sin agenda a un
 * club en segundos. El cupo por jugador que valida {@code BookingService} no
 * alcanza, porque basta con variar el telefono.
 *
 * <p>Ventana deslizante en memoria. Alcanza para un despliegue de una sola
 * instancia; el dia que haya varias, esto tiene que mudarse a un almacen
 * compartido o quedar delante, en el proxy.
 */
@Component
@RequiredArgsConstructor
public class BookingRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_TRACKED_ORIGINS = 10_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public void check(String origin) {
        if (origin == null || origin.isBlank()) {
            return;
        }
        long now = clock.millis();
        long windowStart = now - WINDOW.toMillis();

        // Cota de memoria: sin esto, un atacante que rote direcciones hace crecer el
        // mapa hasta tumbar el proceso, que es justamente lo que se queria evitar.
        if (attempts.size() > MAX_TRACKED_ORIGINS) {
            attempts.entrySet().removeIf(entry -> {
                Deque<Long> timestamps = entry.getValue();
                synchronized (timestamps) {
                    return timestamps.isEmpty() || timestamps.peekLast() < windowStart;
                }
            });
        }

        Deque<Long> timestamps = attempts.computeIfAbsent(origin, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS) {
                throw new BusinessRuleException(
                        "Hiciste muchos intentos seguidos. Espera unos minutos y proba de nuevo.");
            }
            timestamps.addLast(now);
        }
    }
}
