package ar.com.padelnec.scheduler;

import ar.com.padelnec.repository.WaitlistEntryRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra las anotaciones de la lista de espera de turnos que ya terminaron.
 *
 * <p>Una vez que el turno empieza, la anotacion no la usa nadie: el aviso
 * automatico y el panel solo miran horarios por venir. Sin este borrado, la
 * tabla juntaba para siempre una fila por cada vez que alguien se anoto.
 *
 * <p>Sin recorrer club por club, igual que {@link PageEventRetentionJob}: el
 * corte es el mismo para todos y el borrado es una sola sentencia.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class WaitlistRetentionJob {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final Clock clock;

    /** De madrugada, una vez por dia: no hay apuro, ya nadie las mira. */
    @Scheduled(cron = "0 45 4 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void deleteEndedEntries() {
        int deleted = waitlistEntryRepository.deleteEndedBefore(clock.instant());
        if (deleted > 0) {
            log.info("Se borraron {} anotaciones de lista de espera de turnos ya terminados", deleted);
        }
    }
}
