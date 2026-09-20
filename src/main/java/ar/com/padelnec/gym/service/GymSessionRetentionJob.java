package ar.com.padelnec.gym.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.gym.repository.GymSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borra las sesiones de la app del gimnasio que ya no sirven: vencidas, o
 * cerradas hace mas de un mes. Sin esto la tabla solo crece, con una fila por
 * cada login y cada cambio de clave.
 *
 * <p>Corre una vez por dia con el contexto de plataforma ({@code ROOT}), porque
 * el corte es el mismo para todos los clubes y no hace falta recorrerlos uno
 * por uno. Solo borra; nunca lee datos de un club.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GymSessionRetentionJob {

    private static final Duration KEEP_REVOKED = Duration.ofDays(30);

    private final GymSessionRepository sessionRepository;
    private final Clock clock;

    @Scheduled(cron = "0 50 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void deleteStaleSessions() {
        Instant now = clock.instant();
        int deleted = TenantContext.callAs(TenantContext.ROOT,
                () -> sessionRepository.deleteStale(now, now.minus(KEEP_REVOKED)));
        if (deleted > 0) {
            log.info("Se borraron {} sesiones vencidas de la app del gimnasio", deleted);
        }
    }
}
