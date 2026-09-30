package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PhoneVerification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, UUID> {

    // Todo se ordena y se cuenta por expires_at y no por created_at: el vencimiento
    // sale del Clock de la aplicacion y created_at del reloj del sistema, y los
    // plazos tienen que poder probarse moviendo el Clock.

    /** El codigo vigente es el ultimo que se mando. */
    Optional<PhoneVerification> findFirstByPhoneNumberOrderByExpiresAtDesc(String phoneNumber);

    /** Codigos mandados a un numero, para el tope diario: los que vencen despues de {@code after}. */
    long countByPhoneNumberAndExpiresAtAfter(String phoneNumber, Instant after);

    @Transactional
    void deleteByPhoneNumber(String phoneNumber);

    /** Limpieza oportunista: pasado el dia, un codigo ya no cuenta para nada. */
    @Transactional
    void deleteAllByExpiresAtBefore(Instant instant);

    /**
     * Si el numero no necesita verificarse: ya lo verifico con el codigo (o estaba
     * en la lista de algun club al lanzar la verificacion), o ya reservo alguna vez
     * en cualquier club, tambien un turno cargado por el club. Las reservas de un
     * jugador bloqueado no cuentan: el club lo bloqueo por algo.
     *
     * <p>Nativa a proposito, como {@code BookingRepository.findHistoryByAccount}: el
     * filtro por club de Hibernate no deja cruzar clubes de otra forma.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM verified_phone WHERE phone_number = :phone)
                OR EXISTS (SELECT 1 FROM customer c JOIN booking b ON b.customer_id = c.id
                           WHERE c.phone_number = :phone AND NOT c.is_blocked)
            """, nativeQuery = true)
    boolean isRegistered(@Param("phone") String phone);

    /** Suma el numero a la lista. Dos verificaciones simultaneas del mismo numero no chocan. */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO verified_phone (phone_number) VALUES (:phone) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void markVerified(@Param("phone") String phone);
}
