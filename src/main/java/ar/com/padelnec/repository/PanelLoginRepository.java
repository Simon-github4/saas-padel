package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PanelLogin;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PanelLoginRepository extends JpaRepository<PanelLogin, UUID> {

    /** Borrado por lote para el job de retencion. */
    @Modifying
    @Query("delete from PanelLogin l where l.createdAt < ?1")
    int deleteOlderThan(Instant cutoff);
}
