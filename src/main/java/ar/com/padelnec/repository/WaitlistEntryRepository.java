package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.WaitlistEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Todos los anotados de los horarios que todavia no empezaron, hayan recibido
     * el aviso automatico o no: es lo que el club revisa en el panel. Por horario
     * y, dentro de cada uno, por orden de llegada.
     */
    @Query("""
            SELECT w FROM WaitlistEntry w
            JOIN FETCH w.customer
            WHERE w.startsAt > :now
            ORDER BY w.startsAt ASC, w.createdAt ASC
            """)
    List<WaitlistEntry> findUpcoming(@Param("now") Instant now);

    /** Anotados cuyo horario se pisa con la franja de un turno que se libero. */
    @Query("""
            SELECT COUNT(w) FROM WaitlistEntry w
            WHERE w.startsAt < :end
              AND w.endsAt > :start
            """)
    long countOverlapping(@Param("start") Instant start, @Param("end") Instant end);

    Optional<WaitlistEntry> findByCustomerAndStartsAt(Customer customer, Instant startsAt);

    /**
     * Saca de la lista a un jugador que acaba de reservar ese horario: ya no
     * espera nada, y en el panel seguia figurando como anotado.
     */
    @Modifying
    @Query("""
            DELETE FROM WaitlistEntry w
            WHERE w.customer.id = :customerId
              AND w.startsAt < :end
              AND w.endsAt > :start
            """)
    int deleteForCustomerOverlapping(@Param("customerId") UUID customerId,
                                     @Param("start") Instant start, @Param("end") Instant end);

    /**
     * Anotaciones de un jugador en todos los clubes, de horarios que todavia no
     * empezaron. Nativa por lo mismo que {@code BookingRepository#findHistoryByAccount}:
     * cruzar clubes solo se puede esquivando el filtro por club de Hibernate.
     */
    @Query(value = """
            SELECT w.id AS entryId, t.name AS clubName, t.slug AS clubSlug,
                   t.time_zone AS timeZone, w.starts_at AS startsAt, w.ends_at AS endsAt
            FROM waitlist_entry w
            JOIN tenant t ON t.id = w.club_id
            WHERE w.player_account_id = :accountId
              AND w.starts_at > :now
            ORDER BY w.starts_at ASC
            """, nativeQuery = true)
    List<PlayerWaitlistRow> findUpcomingForAccount(@Param("accountId") UUID accountId,
                                                   @Param("now") Instant now);

    /** Proyeccion de {@link #findUpcomingForAccount}. */
    interface PlayerWaitlistRow {
        UUID getEntryId();

        String getClubName();

        String getClubSlug();

        String getTimeZone();

        Instant getStartsAt();

        Instant getEndsAt();
    }

    /**
     * Baja pedida por el jugador. La condicion por cuenta es la autorizacion: con
     * el id de una anotacion ajena no borra nada.
     */
    @Modifying
    @Query(value = "DELETE FROM waitlist_entry WHERE id = :entryId AND player_account_id = :accountId",
            nativeQuery = true)
    int deleteForAccount(@Param("entryId") UUID entryId, @Param("accountId") UUID accountId);

    /** Anotaciones de turnos que ya terminaron, en todos los clubes: ya no sirven para nada. */
    @Modifying
    @Query(value = "DELETE FROM waitlist_entry WHERE ends_at < :now", nativeQuery = true)
    int deleteEndedBefore(@Param("now") Instant now);
}
