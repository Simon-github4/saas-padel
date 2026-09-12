package ar.com.padelnec.repository;

import ar.com.padelnec.domain.WaitlistEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    /**
     * Anotados que todavia no recibieron el aviso y cuyo horario todavia no empezo.
     *
     * <p>Sin el corte por horario, una entrada nunca salia de la lista: un horario
     * que paso lleno quedaba pendiente para siempre y se volvia a revisar cada
     * minuto. Peor, un turno marcado ausente o cancelado despues de empezar deja
     * de ocupar la cancha, y el barrido avisaba "se libero un turno" para una hora
     * que ya no se puede reservar. Mismo corte que {@code BookingService} al
     * reservar: el turno tiene que arrancar despues de ahora.
     */
    @Query("""
            SELECT w FROM WaitlistEntry w
            JOIN FETCH w.customer
            WHERE w.notified = false
              AND w.startsAt > :now
            ORDER BY w.startsAt ASC
            """)
    List<WaitlistEntry> findPending(@Param("now") Instant now);
}
