package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PanelRememberToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PanelRememberTokenRepository extends JpaRepository<PanelRememberToken, UUID> {

    Optional<PanelRememberToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM PanelRememberToken t WHERE t.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /** Olvida todos los dispositivos del usuario: cambio de clave o usuario deshabilitado. */
    @Modifying
    @Query("DELETE FROM PanelRememberToken t WHERE t.userId = :userId")
    void deleteAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PanelRememberToken t WHERE t.userId = :userId AND t.expiresAt <= :now")
    void deleteExpiredForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
