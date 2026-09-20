package ar.com.padelnec.gym.service;

import ar.com.padelnec.web.BusinessRuleException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Techo de intentos en una ventana deslizante, en memoria.
 *
 * <p>Sirve para una sola instancia de la aplicacion, igual que los limitadores
 * de padel de los que se copio la idea. Es una copia propia a proposito: el
 * modulo de gimnasio no depende de las clases de padel.
 */
final class SlidingWindowLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final String message;

    SlidingWindowLimiter(Clock clock, int maxAttempts, Duration window, String message) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.message = message;
    }

    void check(String key) {
        long now = clock.millis();
        long windowStart = now - window.toMillis();

        if (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.entrySet().removeIf(entry -> {
                Deque<Long> timestamps = entry.getValue();
                synchronized (timestamps) {
                    return timestamps.isEmpty() || timestamps.peekLast() < windowStart;
                }
            });
        }

        Deque<Long> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                throw new BusinessRuleException(message);
            }
            timestamps.addLast(now);
        }
    }
}
