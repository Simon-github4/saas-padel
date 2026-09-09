package ar.com.padelnec.repository;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.enums.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubUserRepository extends JpaRepository<ClubUser, UUID> {

    Optional<ClubUser> findByEmailIgnoreCase(String email);

    List<ClubUser> findAllByClubIdOrderByFullNameAsc(UUID clubId);

    boolean existsByEmailIgnoreCase(String email);

    /** Un solo usuario de mostrador por club: alcanza con el primero que haya. */
    Optional<ClubUser> findFirstByClubIdAndRole(UUID clubId, UserRole role);
}
