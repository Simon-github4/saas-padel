package ar.com.padelnec.repository;

import ar.com.padelnec.domain.TenantStoryTemplate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantStoryTemplateRepository extends JpaRepository<TenantStoryTemplate, UUID> {

    /** Si el club tiene plantilla y de cuando es, sin traer la imagen. */
    @Query("select t.updatedAt from TenantStoryTemplate t where t.tenantId = :tenantId")
    Optional<Instant> findUpdatedAt(@Param("tenantId") UUID tenantId);
}
