package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PlayerSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerSessionRepository extends JpaRepository<PlayerSession, UUID> {

    Optional<PlayerSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    /** Cierra toda sesion activa de la cuenta, para que un cambio de contrasena no deje una robada viva. */
    @Modifying
    @Query("UPDATE PlayerSession s SET s.revokedAt = :now WHERE s.player.id = :playerId AND s.revokedAt IS NULL")
    void revokeAllForPlayer(@Param("playerId") UUID playerId, @Param("now") Instant now);
}
