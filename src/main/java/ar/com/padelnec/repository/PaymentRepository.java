package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByMpPaymentId(String mpPaymentId);

    Optional<Payment> findByMpPreferenceId(String mpPreferenceId);

    List<Payment> findAllByBookingIdOrderByCreatedAtAsc(UUID bookingId);

    /**
     * Averigua el club de una preferencia de MercadoPago.
     *
     * <p>Nativa a proposito: el webhook llega sin ninguna pista del tenant, asi que
     * primero hay que resolverlo y recien despues consultar con el filtro puesto.
     */
    @Query(value = """
            SELECT club_id FROM payment
            WHERE mp_preference_id = :preferenceId OR mp_payment_id = :preferenceId
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findClubIdByMercadoPagoReference(@Param("preferenceId") String preferenceId);
}
