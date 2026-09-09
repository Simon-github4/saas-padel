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
 * Barrido periodico de la lista de espera: revisa si algun horario anotado ya
 * tiene cancha libre y avisa.
 *
 * <p>No se dispara desde el punto exacto en que se cancela un turno porque hay
 * demasiados caminos por los que una cancha se libera (el jugador cancela, el club
 * da de baja, un turno fijo salta una fecha, vence sin pago) como para instrumentar
 * cada uno sin riesgo de que alguno quede afuera. El barrido re-deriva la verdad
 * desde la misma disponibilidad que ya usa la grilla publica, asi que la
 * correccion nunca depende de acordarse de nada.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class WaitlistNotificationJob {

    private final TenantRepository tenantRepository;
    private final WaitlistNotificationWorker worker;

    /** Cada minuto: si alguien esta esperando una cancha, un minuto de demora es poco. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void notifyFreedSlots() {
        for (Tenant club : tenantRepository.findAllByActiveTrue()) {
            try {
                TenantContext.runAs(club.getId(), () -> {
                    int notified = worker.notifyFreedSlots(club);
                    if (notified > 0) {
                        log.info("Se avisaron {} anotados en la lista de espera del club {}",
                                notified, club.getSlug());
                    }
                });
            } catch (RuntimeException ex) {
                log.error("Fallo el aviso de lista de espera del club {}", club.getSlug(), ex);
            }
        }
    }
}
