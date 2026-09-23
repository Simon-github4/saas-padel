package ar.com.padelnec.repository;

import ar.com.padelnec.domain.CourtSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Se traen siempre todas las reglas del club, con cancha y dias: son pocas, y
 * armar la grilla de varios dias seguidos (estadisticas, resolver un turno de
 * madrugada) las reusa en memoria en vez de consultar dia por dia.
 */
public interface CourtScheduleRepository extends JpaRepository<CourtSchedule, UUID> {

    @Query("""
            SELECT DISTINCT s FROM CourtSchedule s
            JOIN FETCH s.court c
            LEFT JOIN FETCH s.days
            ORDER BY c.displayOrder ASC, c.name ASC, s.startTime ASC
            """)
    List<CourtSchedule> findAllWithCourt();
}
