package ar.com.padelnec.scheduler;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Libera las canchas que quedaron tomadas por reservas que nunca se completaron.
 *
 * <p>Es lo que sostiene la promesa de la grilla: una reserva bloquea el horario
 * desde el primer clic, asi que si el jugador abandona el checkout o no toca el
 * link de WhatsApp, alguien tiene que devolver esa cancha al mercado. Sin este
 * job, un sabado a la tarde se llena de turnos fantasma.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryJob {

    private final TenantRepository tenantRepository;
    private final BookingExpiryWorker worker;

    /**
     * Corre cada minuto porque los plazos son de 10 y 15 minutos: revisar cada cinco
     * dejaria la cancha figurando ocupada varios minutos despues de que en los
     * hechos ya se libero.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void releaseExpiredHolds() {
        for (Tenant club : tenantRepository.findAllByActiveTrue()) {
            try {
                TenantContext.runAs(club.getId(), () -> {
                    worker.expireUnpaidDrafts();
                    worker.expireUnconfirmedBookings(club);
                });
            } catch (RuntimeException ex) {
                // Un club con un problema no puede dejar sin mantenimiento a los demas.
                log.error("Fallo la liberacion de turnos vencidos del club {}", club.getSlug(), ex);
            }
        }
    }

    /** De madrugada, cuando ya no queda nadie jugando. */
    @Scheduled(cron = "0 15 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void closePlayedBookings() {
        for (Tenant club : tenantRepository.findAllByActiveTrue()) {
            try {
                TenantContext.runAs(club.getId(), () -> {
                    int closed = worker.closePlayedBookings();
                    if (closed > 0) {
                        log.info("Se cerraron {} turnos jugados del club {}", closed, club.getSlug());
                    }
                });
            } catch (RuntimeException ex) {
                log.error("Fallo el cierre de turnos jugados del club {}", club.getSlug(), ex);
            }
        }
    }
}
