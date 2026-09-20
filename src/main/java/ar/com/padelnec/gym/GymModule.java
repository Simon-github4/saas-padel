package ar.com.padelnec.gym;

import ar.com.padelnec.gym.domain.GymClubConfig;
import ar.com.padelnec.gym.repository.GymClubConfigRepository;
import ar.com.padelnec.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puerta de entrada del modulo de gimnasio para el resto de la aplicacion.
 *
 * <p>Es lo unico de este paquete que el panel de padel conoce (ver
 * {@code MainLayout}): dice si el club en contexto tiene el modulo prendido. El
 * modulo se prende insertando una fila en {@code gym_club_config}; ver
 * {@code gym/README.md}.
 */
@Component
@RequiredArgsConstructor
public class GymModule {

    static final String NOT_AVAILABLE = "El gimnasio no está disponible.";

    private final GymClubConfigRepository configRepository;

    /** Del club en contexto; sin club, o con el modulo apagado, es false. */
    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return configRepository.existsByEnabledTrue();
    }

    /**
     * Si el club pide clave ademas del DNI. Lo normal es que no: el socio entra con solo el
     * DNI. Sin el modulo prendido, false.
     */
    @Transactional(readOnly = true)
    public boolean isPasswordRequired() {
        return configRepository.findFirstByEnabledTrue().map(GymClubConfig::isPasswordRequired).orElse(false);
    }

    /** Para la API: un club sin el modulo responde como si la ruta no existiera (404). */
    public void requireEnabled() {
        if (!isEnabled()) {
            throw new ResourceNotFoundException(NOT_AVAILABLE);
        }
    }
}
