package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.enums.BookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Turnos que ocupan la grilla en una ventana. Es la consulta del motor de disponibilidad. */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court
            WHERE b.status IN :statuses
              AND b.startTime < :until
              AND b.endTime > :from
            """)
    List<Booking> findOverlapping(@Param("from") Instant from,
                                  @Param("until") Instant until,
                                  @Param("statuses") Collection<BookingStatus> statuses);

    /** Agenda del dia para el panel del club, con todo lo que la pantalla necesita mostrar. */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court
            JOIN FETCH b.customer
            WHERE b.startTime < :until AND b.endTime > :from
            ORDER BY b.startTime ASC
            """)
    List<Booking> findAgenda(@Param("from") Instant from, @Param("until") Instant until);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court
            JOIN FETCH b.customer
            WHERE b.managementToken = :token
            """)
    Optional<Booking> findByManagementToken(@Param("token") String token);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court
            JOIN FETCH b.customer
            WHERE b.confirmationToken = :token
            """)
    Optional<Booking> findByConfirmationToken(@Param("token") String token);

    /**
     * Resuelve a que club pertenece un token de gestion o de confirmacion.
     *
     * <p>Consulta nativa a proposito: los links que viajan por WhatsApp no llevan el
     * slug del club, asi que hace falta averiguar el tenant antes de poder
     * establecerlo. Es la unica lectura del sistema que esquiva el filtro por
     * club_id, y devuelve solo el identificador: nunca datos de la reserva.
     */
    @Query(value = """
            SELECT club_id FROM booking
            WHERE management_token = :token OR confirmation_token = :token
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findClubIdByAnyToken(@Param("token") String token);

    /** Reservas de MercadoPago que nunca acreditaron y hay que liberar. */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = ar.com.padelnec.domain.enums.BookingStatus.DRAFT
              AND b.draftExpiresAt IS NOT NULL
              AND b.draftExpiresAt < :now
            """)
    List<Booking> findExpiredDrafts(@Param("now") Instant now);

    /** Reservas de palabra que el jugador nunca confirmo por WhatsApp. */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = ar.com.padelnec.domain.enums.BookingStatus.AWAITING_CONFIRMATION
              AND b.confirmationExpiresAt IS NOT NULL
              AND b.confirmationExpiresAt < :now
            """)
    List<Booking> findExpiredConfirmations(@Param("now") Instant now);

    /** Turnos ya jugados que siguen en CONFIRMED y hay que cerrar. */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = ar.com.padelnec.domain.enums.BookingStatus.CONFIRMED
              AND b.endTime < :cutoff
            """)
    List<Booking> findPlayedButOpen(@Param("cutoff") Instant cutoff);

    List<Booking> findAllByCustomerIdOrderByStartTimeDesc(UUID customerId);

    /** Turnos ya materializados de un turno fijo, para no duplicarlos al regenerar. */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.recurringBooking.id = :recurringId
              AND b.startTime >= :from
              AND b.status <> ar.com.padelnec.domain.enums.BookingStatus.CANCELLED
            """)
    List<Booking> findMaterialized(@Param("recurringId") UUID recurringId,
                                   @Param("from") Instant from);

    /**
     * Reservas activas del mismo telefono en el futuro. Sirve para frenar al que
     * bloquea media agenda sin intencion de pagar.
     */
    @Query("""
            SELECT count(b) FROM Booking b
            WHERE b.customer.id = :customerId
              AND b.startTime > :now
              AND b.status IN :statuses
            """)
    long countActiveUpcoming(@Param("customerId") UUID customerId,
                             @Param("now") Instant now,
                             @Param("statuses") Collection<BookingStatus> statuses);
}
