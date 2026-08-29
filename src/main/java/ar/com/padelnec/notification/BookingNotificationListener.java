package ar.com.padelnec.notification;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.BookingEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda el WhatsApp una vez que la reserva quedo escrita en la base.
 *
 * <p>Escucha despues del commit: asi el jugador nunca recibe un "turno confirmado"
 * de una reserva que despues se echo atras por un choque de horarios.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationListener {

    private final BookingRepository bookingRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingEvent event) {
        // El listener corre fuera del hilo de la peticion original, asi que el club
        // se vuelve a instalar a mano antes de tocar nada filtrado por tenant.
        TenantContext.runAs(event.clubId(), () -> deliver(event));
    }

    private void deliver(BookingEvent event) {
        Optional<Tenant> club = tenantRepository.findById(event.clubId());
        Optional<Booking> booking = bookingRepository.findById(event.bookingId());
        if (club.isEmpty() || booking.isEmpty()) {
            log.warn("No se pudo notificar {}: falta el club {} o la reserva {}",
                    event.kind(), event.clubId(), event.bookingId());
            return;
        }
        send(event.kind(), club.get(), booking.get());
    }

    private void send(BookingEvent.Kind kind, Tenant club, Booking booking) {
        switch (kind) {
            case CONFIRMATION_REQUEST -> notificationService.confirmationRequest(club, booking);
            case CONFIRMED_UNPAID -> notificationService.bookingConfirmedUnpaid(club, booking);
            case CONFIRMED_PAID -> notificationService.bookingConfirmedPaid(club, booking);
            case CONFIRMATION_EXPIRED -> notificationService.confirmationExpired(club, booking);
            case CANCELLED_BY_CLUB -> notificationService.cancelledByClub(club, booking);
            case REMINDER -> notificationService.reminder(club, booking);
        }
    }
}
