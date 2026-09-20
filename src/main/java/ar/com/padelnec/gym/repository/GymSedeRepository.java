package ar.com.padelnec.gym.repository;

import ar.com.padelnec.gym.domain.GymSede;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymSedeRepository extends JpaRepository<GymSede, UUID> {

    /**
     * La sede a la que corresponde el QR escaneado. Como la entidad esta filtrada
     * por club, el QR de otro club no la encuentra aunque el token exista.
     */
    Optional<GymSede> findByQrTokenAndActiveTrue(String qrToken);

    List<GymSede> findAllByOrderByNameAsc();

    List<GymSede> findAllByActiveTrueOrderByNameAsc();

    /** Alguna sede activa verifica la ubicacion del socio: la app tiene que pedirsela. */
    boolean existsByActiveTrueAndLatitudeIsNotNull();
}
