package ar.com.padelnec.notification;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingSource;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.BookingEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reservas que los jugadores acaban de hacer por la web, para que el panel las avise.
 *
 * <p>El panel no tiene push: cada pantalla abierta consulta esto en su poll y
 * muestra lo que paso desde la ultima vez. Vive en memoria a proposito -es un
 * aviso, no un registro-: si la app se reinicia se pierde lo de los ultimos
 * minutos, y la agenda igual tiene todos los turnos. Asume una sola instancia,
 * igual que {@code LoginRateLimiter}.
 */
@Component
@RequiredArgsConstructor
public class NewBookingFeed {

    private static final int MAX_PER_CLUB = 50;
    /** Un panel que estuvo cerrado mas que esto no necesita enterarse de lo viejo. */
    private static final long KEEP_SECONDS = 600;
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d/M HH:mm", Locale.forLanguageTag("es-AR"));

    private final BookingRepository bookingRepository;
    private final TenantRepository tenantRepository;
    private final Clock clock;

    private final Map<UUID, Deque<Entry>> byClub = new ConcurrentHashMap<>();

    public record Entry(Instant at, UUID bookingId, String text) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(BookingEvent event) {
        if (event.kind() != BookingEvent.Kind.CONFIRMATION_REQUEST
                && event.kind() != BookingEvent.Kind.CONFIRMED_UNPAID
                && event.kind() != BookingEvent.Kind.CONFIRMED_PAID) {
            return;
        }
        TenantContext.runAs(event.clubId(), () -> record(event));
    }

    private void record(BookingEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        Tenant club = tenantRepository.findById(event.clubId()).orElse(null);
        // Solo lo que entro por la web: lo que carga el club a mano ya lo sabe el club.
        if (booking == null || club == null || booking.getSource() != BookingSource.WEB) {
            return;
        }
        String text = "Nueva reserva: %s · %s · %s".formatted(
                booking.getCourt().getName(),
                WHEN.format(booking.getStartTime().atZone(club.zoneId())),
                booking.displayName());
        add(event.clubId(), new Entry(clock.instant(), booking.getId(), text));
    }

    private void add(UUID clubId, Entry entry) {
        Deque<Entry> entries = byClub.computeIfAbsent(clubId, id -> new ArrayDeque<>());
        synchronized (entries) {
            Instant oldest = entry.at().minusSeconds(KEEP_SECONDS);
            entries.removeIf(existing -> existing.at().isBefore(oldest));
            // Una reserva pasa por varios eventos (pedido de confirmacion y despues
            // confirmada): se avisa una sola vez.
            if (entries.stream().anyMatch(existing -> existing.bookingId().equals(entry.bookingId()))) {
                return;
            }
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
