package ar.com.padelnec.gym.repository;

import ar.com.padelnec.gym.domain.GymSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GymSessionRepository extends JpaRepository<GymSession, UUID> {

    /** Con el socio ya cargado: quien la recibe lo lee fuera de la transaccion. */
    @EntityGraph(attributePaths = "member")
    Optional<GymSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    /** Cierra toda sesion activa del socio: cambiar o resetear la clave no deja una vieja viva. */
    @Modifying
    @Transactional
    @Query("UPDATE GymSession s SET s.revokedAt = :now WHERE s.member.id = :memberId AND s.revokedAt IS NULL")
    int revokeAllForMember(@Param("memberId") UUID memberId, @Param("now") Instant now);

    /** Sesiones vencidas, o cerradas hace mas de lo que hace falta guardarlas. */
    @Modifying
    @Transactional
    @Query("DELETE FROM GymSession s WHERE s.expiresAt < :now OR s.revokedAt < :revokedBefore")
    int deleteStale(@Param("now") Instant now, @Param("revokedBefore") Instant revokedBefore);
}
