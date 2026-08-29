package ar.com.padelnec.scheduler;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.RecurringBookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre el horizonte movil de los turnos fijos.
 *
 * <p>Una vez por dia alcanza: lo unico que cambia entre corridas es que se suma
 * una semana al final del horizonte.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RecurringBookingJob {

    private final TenantRepository tenantRepository;
    private final RecurringBookingService recurringBookingService;

    @Scheduled(cron = "0 30 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void extendHorizon() {
        for (Tenant club : tenantRepository.findAllByActiveTrue()) {
            try {
                TenantContext.runAs(club.getId(), () -> {
                    int created = recurringBookingService.materializeUpcoming(club);
                    if (created > 0) {
                        log.info("Se generaron {} turnos fijos del club {}", created, club.getSlug());
                    }
                });
            } catch (RuntimeException ex) {
                log.error("Fallo la generacion de turnos fijos del club {}", club.getSlug(), ex);
            }
        }
    }
}
