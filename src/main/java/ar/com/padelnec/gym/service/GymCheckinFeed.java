package ar.com.padelnec.gym.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Socios que acaban de entrar escaneando el QR, para el cartel con sonido del panel
 * (cada pantalla consulta esto en su poll y muestra lo que paso desde la ultima vez).
 *
 * <p>Solo los escaneos: lo que carga el mostrador a mano ya lo sabe el mostrador, y
 * escanear dos veces el mismo dia no es un ingreso nuevo. Tampoco va al celular: en
 * hora pico son decenas por hora.
 *
 * <p>Vive en memoria a proposito -es un aviso, no un registro-: si la app se reinicia
 * se pierde lo de los ultimos minutos, y "Ingresos de hoy" igual los tiene a todos.
 * Asume una sola instancia, igual que el aviso de reservas web del panel.
 */
@Component
public class GymCheckinFeed {

    private static final int MAX_PER_CLUB = 50;
    /** Un panel que estuvo cerrado mas que esto no necesita enterarse de lo viejo. */
    private static final long KEEP_SECONDS = 600;

    private final Clock clock;
    private final Map<UUID, Deque<Entry>> byClub = new ConcurrentHashMap<>();

    public GymCheckinFeed(Clock clock) {
        this.clock = clock;
    }

    public record Entry(Instant at, String text) {
    }

    /**
     * Con la hora de ahora y no la del escaneo: dos ingresos simultaneos pueden terminar
     * de grabarse en el orden inverso, y el panel, que pide "lo posterior al ultimo que
     * vi", se perderia el que quedo con la hora mas vieja.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GymCheckinEvent event) {
        String text = "Ingreso: %s · %s".formatted(event.memberName(), event.sedeName())
                + (event.owesTheMonth() ? " · Debe el mes" : "");
        add(event.clubId(), new Entry(clock.instant(), text));
    }

    private void add(UUID clubId, Entry entry) {
        Deque<Entry> entries = byClub.computeIfAbsent(clubId, id -> new ArrayDeque<>());
        synchronized (entries) {
            Instant oldest = entry.at().minusSeconds(KEEP_SECONDS);
            entries.removeIf(existing -> existing.at().isBefore(oldest));
            entries.addLast(entry);
            while (entries.size() > MAX_PER_CLUB) {
                entries.removeFirst();
            }
        }
    }

    /** Lo que entro despues de {@code after}, de mas viejo a mas nuevo. */
    public List<Entry> since(UUID clubId, Instant after) {
        Deque<Entry> entries = byClub.get(clubId);
        if (entries == null) {
            return List.of();
        }
        synchronized (entries) {
            return entries.stream().filter(entry -> entry.at().isAfter(after)).toList();
        }
    }
}
