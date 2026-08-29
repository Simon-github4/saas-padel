package ar.com.padelnec.scheduler;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.service.BookingEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trabajo transaccional de vencimientos, club por club.
 *
 * <p>Vive en un bean aparte del job a proposito: si estos metodos estuvieran en la
 * misma clase que el {@code @Scheduled} que los invoca, la llamada seria interna,
 * se saltearia el proxy de Spring y las transacciones no existirian. Ademas, una
 * transaccion por club evita que el problema de uno revierta el trabajo de todos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryWorker {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Reservas que se fueron a MercadoPago y nunca volvieron con un pago. */
    @Transactional
    public int expireUnpaidDrafts() {
        List<Booking> expired = bookingRepository.findExpiredDrafts(clock.instant());
        for (Booking booking : expired) {
            booking.markCancelled(CancellationReason.PAYMENT_TIMEOUT, clock.instant());
            log.info("Se libera la cancha {} del {} por falta de pago",
                    booking.getCourt().getName(), booking.getStartTime());
        }
        bookingRepository.saveAll(expired);
        // Al jugador no se le avisa: abandono el checkout a proposito, y un WhatsApp
        // que le recuerde que no pago es ruido, no servicio.
        return expired.size();
    }

    /** Reservas de palabra en las que el jugador nunca toco el link de confirmacion. */
    @Transactional
    public int expireUnconfirmedBookings(Tenant club) {
        List<Booking> expired = bookingRepository.findExpiredConfirmations(clock.instant());
        for (Booking booking : expired) {
            booking.markCancelled(CancellationReason.CONFIRMATION_TIMEOUT, clock.instant());
            log.info("Se libera la cancha {} del {} por falta de confirmacion",
                    booking.getCourt().getName(), booking.getStartTime());
        }
        bookingRepository.saveAll(expired);

        // Aca si se avisa: el jugador dejo su telefono creyendo que estaba reservando,
        // y merece enterarse de que la cancha volvio a quedar libre.
        expired.forEach(booking -> events.publishEvent(
                BookingEvent.of(club.getId(), booking.getId(),
                        BookingEvent.Kind.CONFIRMATION_EXPIRED)));
        return expired.size();
    }

    /**
     * Cierra los turnos que ya se jugaron.
     *
     * <p>Con una hora de gracia despues del final, para que el club alcance a marcar
     * un ausente antes de que el turno quede dado por jugado.
     */
    @Transactional
    public int closePlayedBookings() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(1));
        List<Booking> played = bookingRepository.findPlayedButOpen(cutoff);
        played.forEach(booking -> booking.setStatus(BookingStatus.COMPLETED));
        bookingRepository.saveAll(played);
        return played.size();
    }
}
