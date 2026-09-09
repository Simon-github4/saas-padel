package ar.com.padelnec.repository;

import ar.com.padelnec.domain.ClubAmenity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/** Servicios de un club, ordenados como se muestran en la portada. */
public interface ClubAmenityRepository extends JpaRepository<ClubAmenity, UUID> {

    List<ClubAmenity> findAllByOrderByDisplayOrderAscTitleAsc();

    @Modifying
    void deleteAllByClubId(UUID clubId);
}
