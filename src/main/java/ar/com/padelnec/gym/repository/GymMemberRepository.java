package ar.com.padelnec.gym.repository;

import ar.com.padelnec.gym.domain.GymMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymMemberRepository extends JpaRepository<GymMember, UUID> {

    Optional<GymMember> findByDni(String dni);

    List<GymMember> findAllByOrderByFullNameAsc();
}
