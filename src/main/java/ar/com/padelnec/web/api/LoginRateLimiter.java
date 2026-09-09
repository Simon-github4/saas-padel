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
 * Techo de intentos de login y de pedidos de recuperar contrasena, por email.
 *
 * <p>A diferencia de {@link BookingRateLimiter} (que limita por origen), aca el
 * abuso es hostigar la cuenta -o el mail- de otra persona (fuerza bruta contra
 * su contrasena, o bombardearla de links de reset que no pidio), asi que la
 * clave es el email destino y no quien hace el pedido.
 *
 * <p>Ventana deslizante en memoria, mismo alcance y misma limitacion que
 * {@link BookingRateLimiter}: sirve para una sola instancia.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_EMAILS = 10_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public void check(String email) {
        long now = clock.millis();
        long windowStart = now - WINDOW.toMillis();

        if (attempts.size() > MAX_TRACKED_EMAILS) {
            attempts.entrySet().removeIf(entry -> {
                Deque<Long> timestamps = entry.getValue();
                synchronized (timestamps) {
                    return timestamps.isEmpty() || timestamps.peekLast() < windowStart;
                }
            });
        }

        Deque<Long> timestamps = attempts.computeIfAbsent(email, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS) {
                throw new BusinessRuleException(
                        "Hiciste muchos intentos seguidos. Esperá unos minutos y probá de nuevo.");
            }
            timestamps.addLast(now);
        }
    }
}
