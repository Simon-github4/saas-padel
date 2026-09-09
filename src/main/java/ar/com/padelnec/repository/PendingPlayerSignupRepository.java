package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PendingPlayerSignup;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingPlayerSignupRepository extends JpaRepository<PendingPlayerSignup, UUID> {

    Optional<PendingPlayerSignup> findByEmail(String email);

    Optional<PendingPlayerSignup> findByConfirmToken(String confirmToken);

    void deleteByEmail(String email);

    /** Limpieza oportunista de intentos abandonados, disparada desde {@code register()}. */
    void deleteAllByExpiresAtBefore(Instant instant);
}
