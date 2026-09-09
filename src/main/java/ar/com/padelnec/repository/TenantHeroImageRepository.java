package ar.com.padelnec.repository;

import ar.com.padelnec.domain.TenantHeroImage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantHeroImageRepository extends JpaRepository<TenantHeroImage, UUID> {
}
