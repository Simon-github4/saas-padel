package ar.com.padelnec.repository;

import ar.com.padelnec.domain.MercadoPagoOAuthAttempt;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MercadoPagoOAuthAttemptRepository extends JpaRepository<MercadoPagoOAuthAttempt, UUID> {

    Optional<MercadoPagoOAuthAttempt> findByStateHash(String stateHash);

    /**
     * Barre, antes de crear un intento nuevo, los intentos previos del mismo
     * club (tocar "Conectar" dos veces no deberia dejar el anterior vivo) y
     * cualquier otro que ya haya vencido. Sin esto la tabla necesitaria un job
     * de limpieza propio para una fila que en la practica dura minutos.
     */
    @Modifying
    @Query("DELETE FROM MercadoPagoOAuthAttempt a WHERE a.tenantId = :tenantId OR a.expiresAt < :now")
    void deleteStaleFor(@Param("tenantId") UUID tenantId, @Param("now") Instant now);
}
