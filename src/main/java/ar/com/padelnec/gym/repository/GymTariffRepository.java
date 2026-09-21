package ar.com.padelnec.gym.repository;

import ar.com.padelnec.gym.domain.GymTariff;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymTariffRepository extends JpaRepository<GymTariff, UUID> {

    Optional<GymTariff> findByDaysPerWeek(int daysPerWeek);

    List<GymTariff> findAllByOrderByDaysPerWeekAsc();
}