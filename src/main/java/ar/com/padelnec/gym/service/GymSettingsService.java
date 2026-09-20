package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymClubConfig;
import ar.com.padelnec.gym.repository.GymClubConfigRepository;
import ar.com.padelnec.gym.repository.GymSedeRepository;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Los ajustes del gimnasio del club y lo que la app necesita saber antes de mostrar el login. */
@Service
@RequiredArgsConstructor
public class GymSettingsService {

    private final GymClubConfigRepository configRepository;
    private final GymSedeRepository sedeRepository;
    private final TenantService tenantService;

    /**
     * Lo publico del gimnasio, antes de que el socio entre: el nombre para el encabezado, si
     * tiene que escribir clave, y si la app le va a pedir la ubicacion (alguna sede la verifica).
     */
    public record PublicConfig(
            String clubName,
            boolean passwordRequired,
            boolean locationRequired,
            String heroImageUrl,
            String themeMode,
            String primaryColor,
            String secondaryColor) {
    }

    @Transactional(readOnly = true)
    public PublicConfig publicConfig() {
        GymClubConfig config = configRepository.findFirstByEnabledTrue()
                .orElseThrow(() -> new ResourceNotFoundException("El gimnasio no está disponible."));
        var club = tenantService.requireCurrent();
        return new PublicConfig(
                club.getName(),
                config.isPasswordRequired(),
                sedeRepository.existsByActiveTrueAndLatitudeIsNotNull(),
                club.getHeroImageUrl(),
                club.getThemeMode().name(),
                club.getPrimaryColor(),
                club.getSecondaryColor());
    }

    /**
     * Pide clave ademas del DNI (true) o solo el DNI (false). Cambiarlo no toca las sesiones abiertas
     * ni las claves: los socios que ya tenian una clave la conservan si el club vuelve a pedirla.
     * Los dados de alta sin clave conocida tendran que pasar por el mostrador ("Resetear clave").
     */
    @Transactional
    public void setPasswordRequired(boolean passwordRequired) {
        configRepository.findFirstByEnabledTrue()
                .orElseThrow(() -> new ResourceNotFoundException("El gimnasio no está disponible."))
                .setPasswordRequired(passwordRequired);
    }
}
