package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** El club no esta alcanzado por el filtro de tenant: es el tenant. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlugIgnoreCase(String slug);

    Optional<Tenant> findBySlugIgnoreCaseAndActiveTrue(String slug);

    List<Tenant> findAllByActiveTrue();

    boolean existsBySlugIgnoreCase(String slug);
}
