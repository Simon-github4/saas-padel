package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PricingRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Las dos consultas traen la cancha con {@code LEFT JOIN FETCH}.
 *
 * <p>Por dos motivos. En el panel, la entidad ya viene desconectada cuando Vaadin
 * pinta la grilla, y tocar la relacion perezosa ahi revienta. En el motor de
 * disponibilidad hay sesion abierta, pero resolver la cancha regla por regla seria
 * un N+1 en el camino mas caliente del sistema.
 *
 * <p>Es LEFT porque la cancha puede ser nula: esa es justamente la regla general
 * que aplica a todas.
 */
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    @Query("""
            SELECT r FROM PricingRule r
            LEFT JOIN FETCH r.court
            WHERE r.dayOfWeek = :dayOfWeek
            """)
    List<PricingRule> findAllByDayOfWeek(@Param("dayOfWeek") int dayOfWeek);

    @Query("""
            SELECT r FROM PricingRule r
            LEFT JOIN FETCH r.court
            ORDER BY r.dayOfWeek ASC, r.startTime ASC
            """)
    List<PricingRule> findAllByOrderByDayOfWeekAscStartTimeAsc();
}
