package ar.com.padelnec.notification;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingSource;
import ar.com.padelnec.notification.push.PanelPushNotifier;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.service.BookingEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * Lo que los jugadores acaban de hacer con sus turnos por la web, para que el club se
 * entere: las reservas nuevas y las que cancelaron desde su link.
 *
 * <p>Es el unico lugar que decide "entro una reserva web nueva" o "un jugador cancelo", y
 * de ahi salen los dos avisos: el cartel con sonido del panel abierto (cada pantalla
 * consulta esto en su poll y muestra lo que paso desde la ultima vez) y el aviso al
 * celular de quien lo activo ({@link PanelPushNotifier}).
 *
 * <p>Vive en memoria a proposito -es un aviso, no un registro-: si la app se
 * reinicia se pierde lo de los ultimos minutos, y la agenda igual tiene todos los
 * turnos. Asume una sola instancia, igual que {@code LoginRateLimiter}.
 */
@Component
@RequiredArgsConstructor
public class WebBookingFeed {

    private static final int MAX_PER_CLUB = 50;
    /** Un panel que estuvo cerrado mas que esto no necesita enterarse de lo viejo. */
    private static final long KEEP_SECONDS = 600;
    /**
     * Cuanto se recuerda que una reserva ya se aviso. Mas que el plazo mas largo para
     * confirmar una reserva sin seña (120 minutos): si el jugador confirma a ultimo
     * momento, no vuelve a sonar.
     */
    private static final Duration ANNOUNCED_KEEP = Duration.ofHours(3);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d/M HH:mm", Locale.forLanguageTag("es-AR"));

    private final BookingRepository bookingRepository;
    private final TenantRepository tenantRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final PanelPushNotifier panelPushNotifier;
    private final Clock clock;

    private final Map<UUID, Deque<Entry>> byClub = new ConcurrentHashMap<>();
    /** Reservas nuevas ya avisadas, con el momento del aviso. */
    private final Map<UUID, Instant> announced = new ConcurrentHashMap<>();

    /** Que le paso al turno: en el panel cada uno tiene su sonido. */
    public enum Kind { NEW, CANCELLED }

    public record Entry(Instant at, Kind kind, UUID bookingId, String text) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(BookingEvent event) {
        switch (event.kind()) {
            case CONFIRMATION_REQUEST, CONFIRMED_UNPAID, CONFIRMED_PAID ->
                    TenantContext.runAs(event.clubId(), () -> recordNew(event));
            case CANCELLED_BY_CUSTOMER -> TenantContext.runAs(event.clubId(), () -> recordCancelled(event));
            // Lo demas lo hizo el club o el sistema: no hay nada de que avisarle.
            default -> { }
        }
    }

    private void recordNew(BookingEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        Tenant club = tenantRepository.findById(event.clubId()).orElse(null);
        // Solo lo que entro por la web: lo que carga el club a mano ya lo sabe el club.
        if (booking == null || club == null || booking.getSource() != BookingSource.WEB) {
            return;
        }
        // Una reserva pasa por varios eventos (pedido de confirmacion y despues
        // confirmada): se avisa una sola vez.
        Instant now = clock.instant();
        announced.values().removeIf(at -> at.isBefore(now.minus(ANNOUNCED_KEEP)));
        if (announced.putIfAbsent(booking.getId(), now) != null) {
            return;
        }
        String slot = slot(club, booking);
        add(event.clubId(), new Entry(now, Kind.NEW, booking.getId(), "Nueva reserva: " + slot));
        panelPushNotifier.newWebBooking(event.clubId(), booking.getId(), slot + " · " + payment(booking));
    }

    /**
     * Sin mirar el origen: un turno que cargo el club tambien se puede cancelar desde el
     * link del jugador, y de eso el club no se entero. Tampoco pasa por {@code announced}:
     * un turno se cancela una sola vez, y esa lista tiene las reservas que ya sonaron como
     * nuevas, que son justamente las que despues se cancelan.
     */
    private void recordCancelled(BookingEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        Tenant club = tenantRepository.findById(event.clubId()).orElse(null);
        if (booking == null || club == null) {
            return;
        }
        String slot = slot(club, booking);
        add(event.clubId(), new Entry(clock.instant(), Kind.CANCELLED, booking.getId(), "Turno cancelado: " + slot));
        panelPushNotifier.webCancellation(event.clubId(), booking.getId(), slot + afterCancelling(booking));
    }

    private static String slot(Tenant club, Booking booking) {
        return "%s · %s · %s".formatted(booking.getCourt().getName(),
                WHEN.format(booking.getStartTime().atZone(club.zoneId())), booking.displayName());
    }

    /** Lo que mas le importa al que atiende despues del horario: si ya hay plata adentro. */
    private static String payment(Booking booking) {
        if (!booking.hasMoneyIn()) {
            return "Paga en el club";
        }
        return booking.isPaidInFull() ? "Pagada" : "Seña pagada";
    }

    /**
     * Lo que le queda por hacer al club con el turno cancelado. En el panel lo dicen las
     * alertas; el celular no las ve, asi que va en el aviso.
     */
    private String afterCancelling(Booking booking) {
        List<String> todo = new ArrayList<>();
        if (booking.hasMoneyIn()) {
            todo.add("Hay que devolver la seña");
        }
        long waiting = waitlistEntryRepository.countOverlapping(booking.getStartTime(), booking.getEndTime());
        if (waiting > 0) {
            todo.add(waiting == 1 ? "1 en lista de espera" : waiting + " en lista de espera");
        }
        return todo.isEmpty() ? "" : " · " + String.join(" · ", todo);
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
