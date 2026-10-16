package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PushSubscription;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    boolean existsByEndpointAndClubUserId(String endpoint, UUID clubUserId);

    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint AND s.clubUserId = :userId")
    int deleteByEndpointAndUser(@Param("endpoint") String endpoint, @Param("userId") UUID userId);

    /**
     * Sin el filtro de club, a proposito: el endpoint es unico en toda la base, y si
     * el navegador ya estaba suscripto con otro usuario (el mismo celular, otra
     * persona) la suscripcion pasa a quien la pide ahora. Solo lo puede pedir quien
     * tiene ese navegador en la mano: el endpoint lo conoce el navegador y nadie mas.
     */
    @Modifying
    @Query(value = "DELETE FROM push_subscription WHERE endpoint = :endpoint", nativeQuery = true)
    void deleteByEndpointInAnyClub(@Param("endpoint") String endpoint);

    /** El servicio de push dijo que ya no existe (404 o 410). */
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.id = :id")
    void deleteDead(@Param("id") UUID id);

    /** Cambio de clave o usuario deshabilitado: ninguno de sus dispositivos sigue avisando. */
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.clubUserId = :userId")
    void deleteAllForUser(@Param("userId") UUID userId);
}
