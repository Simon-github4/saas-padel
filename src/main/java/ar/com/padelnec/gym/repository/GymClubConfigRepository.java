package ar.com.padelnec.gym.repository;

import ar.com.padelnec.gym.domain.GymClubConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymClubConfigRepository extends JpaRepository<GymClubConfig, UUID> {

    /** Filtrado por el club en contexto: dice si ESE club tiene el modulo prendido. */
    boolean existsByEnabledTrue();

    /** La configuracion del club en contexto, si tiene el modulo prendido. */
    Optional<GymClubConfig> findFirstByEnabledTrue();
}
