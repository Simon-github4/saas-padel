package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Court;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourtRepository extends JpaRepository<Court, UUID> {

    List<Court> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Court> findAllByOrderByDisplayOrderAscNameAsc();
}
