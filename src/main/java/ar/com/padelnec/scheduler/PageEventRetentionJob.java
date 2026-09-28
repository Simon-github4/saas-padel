package ar.com.padelnec.scheduler;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.repository.PageEventRepository;
import ar.com.padelnec.repository.PanelLoginRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le pone fecha de vencimiento a la bitacora de visitas.
 *
 * <p>Es la tabla que mas rapido crece del sistema -- una visita deja decenas de
 * filas y una reserva deja una -- sobre una base chica. Y es la unica cuyo valor
 * caduca: nadie va a mirar el embudo de hace dos años para decidir algo hoy.
 *
 * <p>Sin recorrer club por club, a diferencia de los otros jobs: la bitacora no
 * esta filtrada por club y el corte es el mismo para todos.
 *
 * <p>Los ingresos al panel siguen el mismo corte: son pocos, pero guardan la IP
 * de quien entro, y ese dato no tiene por que quedar para siempre.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PageEventRetentionJob {

    private final PageEventRepository pageEventRepository;
    private final PanelLoginRepository panelLoginRepository;
    private final AppProperties properties;
    private final Clock clock;

    /** De madrugada, y una vez por dia: no hay ninguna urgencia en este borrado. */
    @Scheduled(cron = "0 40 4 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void deleteOldEvents() {
        int months = properties.getAnalytics().getRetentionMonths();
        // Meses de calendario, no bloques de 30 dias: el corte se explica solo
        // ("un año para atras") cuando alguien lo audite.
        Instant cutoff = clock.instant().atZone(ZoneOffset.UTC).minusMonths(months).toInstant();
        int deleted = pageEventRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Se borraron {} eventos de visita con mas de {} meses", deleted, months);
        }
        int logins = panelLoginRepository.deleteOlderThan(cutoff);
        if (logins > 0) {
            log.info("Se borraron {} ingresos al panel con mas de {} meses", logins, months);
        }
    }
}
