package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PlayerAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerAccountRepository extends JpaRepository<PlayerAccount, UUID> {

    Optional<PlayerAccount> findByEmail(String email);

    /** Los telefonos de la lista que tienen una cuenta cargada con ese numero. */
    @Query("SELECT a.phoneNumber FROM PlayerAccount a WHERE a.phoneNumber IN :phones")
    List<String> findPhoneNumbersIn(@Param("phones") Collection<String> phones);

    Optional<PlayerAccount> findByGoogleSubject(String googleSubject);

    Optional<PlayerAccount> findByPasswordResetTokenHash(String passwordResetTokenHash);
}
