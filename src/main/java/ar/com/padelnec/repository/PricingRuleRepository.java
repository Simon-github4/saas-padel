package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PricingRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Las consultas traen la cancha y los dias con {@code LEFT JOIN FETCH} para que
 * la entidad llegue completa en un solo viaje.
 *
 * <p>Por dos motivos. En el panel, la entidad ya viene desconectada cuando Vaadin
 * pinta la grilla, y tocar una relacion perezosa ahi revienta. En el motor de
 * disponibilidad hay sesion abierta, pero resolver la cancha (o los dias) regla
 * por regla seria un N+1 en el camino mas caliente del sistema.
 *
 * <p>Es LEFT porque la cancha puede ser nula: esa es justamente la regla general
 * que aplica a todas.
 */
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    /**
     * Las reglas que cubren un dia de la semana, via la tabla puente de dias.
     *
     * <p>El filtro va en una subquery a proposito: un {@code JOIN r.days d WHERE
     * d = :dayOfWeek} sin fetch no deja a {@code days} poblado (queda EAGER pero
     * sin fetch join), y Hibernate termina disparando una consulta aparte por
     * cada regla devuelta para completarlo. Filtrando por subquery, el
     * {@code LEFT JOIN FETCH r.days} de afuera trae todo en una sola consulta.
     */
    @Query("""
            SELECT DISTINCT r FROM PricingRule r
            LEFT JOIN FETCH r.court
            LEFT JOIN FETCH r.days
            WHERE r.id IN (
                SELECT r2.id FROM PricingRule r2 JOIN r2.days d2 WHERE d2 = :dayOfWeek
            )
            """)
    List<PricingRule> findRulesForDay(@Param("dayOfWeek") int dayOfWeek);

    /** Todas las reglas del club, ordenadas por franja horaria (sin un dia unico). */
    @Query("""
            SELECT r FROM PricingRule r
            LEFT JOIN FETCH r.court
            ORDER BY r.startTime ASC, r.endTime ASC
            """)
    List<PricingRule> findAllByOrderByStartTimeAsc();
}
