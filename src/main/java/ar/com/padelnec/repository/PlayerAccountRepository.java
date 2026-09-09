package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PlayerAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerAccountRepository extends JpaRepository<PlayerAccount, UUID> {

    Optional<PlayerAccount> findByEmail(String email);

    Optional<PlayerAccount> findByGoogleSubject(String googleSubject);

    Optional<PlayerAccount> findByPasswordResetToken(String passwordResetToken);
}
