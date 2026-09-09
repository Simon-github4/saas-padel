package ar.com.padelnec.scheduler;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.notification.whatsapp.WhatsAppSender;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.service.AvailabilityService;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trabajo transaccional de la lista de espera, club por club.
 *
 * <p>Vive en un bean aparte del job, misma razon que separa a {@code BookingExpiryWorker}
 * de {@code BookingExpiryJob}: si el metodo estuviera en la clase con el
 * {@code @Scheduled}, la llamada seria interna, se saltearia el proxy de Spring y la
 * transaccion no existiria.
 *
 * <p>El aviso se manda en la misma pasada que marca la entrada como notificada, sin
 * pasar por un evento: a diferencia de una reserva nueva, marcar {@code notified} no
 * arriesga chocar contra ninguna restriccion de la base, asi que no hace falta
 * esperar a que la transaccion cierre para mandar el WhatsApp.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WaitlistNotificationWorker {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final AvailabilityService availabilityService;
    private final NotificationService notificationService;
    private final Clock clock;

    /**
     * Avisa a los anotados cuyo horario ya tiene alguna cancha libre.
     *
     * <p>Solo marca {@code notified} si el WhatsApp de verdad salio (o el canal esta
     * apagado a proposito, que tambien cuenta como atendido) - un fallo real deja la
     * entrada pendiente para que la proxima pasada del job la reintente, en vez de
     * perderse en silencio.
     */
    @Transactional
    public int notifyFreedSlots(Tenant club) {
        List<WaitlistEntry> pending = waitlistEntryRepository.findPending();
        int notified = 0;
        for (WaitlistEntry entry : pending) {
            if (!availabilityService.anyCourtFree(entry.getStartsAt(), entry.getEndsAt())) {
                continue;
            }
            WhatsAppSender.SendResult result = notificationService.waitlistSlotFreed(club, entry);
            if (!result.delivered() && !result.skipped()) {
                continue;
            }
            entry.markNotified(clock.instant());
            waitlistEntryRepository.save(entry);
            notified++;
        }
        return notified;
    }
}
