package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Tenant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** El club no esta alcanzado por el filtro de tenant: es el tenant. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlugIgnoreCase(String slug);

    Optional<Tenant> findBySlugIgnoreCaseAndActiveTrue(String slug);

    List<Tenant> findAllByActiveTrue();

    /** Los que se muestran en la busqueda general y el sitemap: activos y marcados visibles. */
    List<Tenant> findAllByActiveTrueAndListedInSearchTrue();

    boolean existsBySlugIgnoreCase(String slug);

    /** Clubes conectados por OAuth cuyo access token vence dentro de la ventana de renovacion. */
    List<Tenant> findAllByMpRefreshTokenIsNotNullAndMpTokenExpiresAtBefore(Instant threshold);

    /** El webhook unico de la app identifica al club por el user_id de MercadoPago, no por slug. */
    Optional<Tenant> findByMpUserId(String mpUserId);

    /**
     * Guarda solo el nombre y el email de la cuenta de MercadoPago.
     *
     * <p>Un update puntual y no {@code save(club)}: quien lo llama es Configuracion,
     * que tiene el club cargado desde que se abrio la pantalla y quizas con cambios
     * a medio editar en otra pestana. Guardar la entidad entera los persistiria.
     */
    @Modifying
    @Query("update Tenant t set t.mpAccountName = :name, t.mpAccountEmail = :email where t.id = :id")
    void updateMpAccount(@Param("id") UUID id, @Param("name") String name, @Param("email") String email);
}
