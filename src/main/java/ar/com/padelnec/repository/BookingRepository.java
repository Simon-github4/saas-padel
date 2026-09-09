package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.enums.BookingStatus;
import java.math.BigDecimal;
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

    /**
     * Una reserva con cancha y cliente ya cargados, para pantallas que la vuelven
     * a mostrar despues de una accion sin arrastrar sus proxies lazy fuera de la
     * transaccion (eso tira {@code LazyInitializationException} al renderizar).
     */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court
            JOIN FETCH b.customer
            WHERE b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") UUID id);

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
     * Vista de solo lectura para compartir el turno. Deliberadamente separada
     * de {@link #findByManagementToken}: ningun otro metodo del servicio busca
     * una reserva por este token, asi que compartirlo no puede terminar
     * habilitando una cancelacion.
     */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.court
            JOIN FETCH b.customer
            WHERE b.shareToken = :token
            """)
    Optional<Booking> findByShareToken(@Param("token") String token);

    /**
     * Resuelve a que club pertenece un token de gestion, confirmacion o de
     * compartir.
     *
     * <p>Consulta nativa a proposito: los links que viajan por WhatsApp no llevan el
     * slug del club, asi que hace falta averiguar el tenant antes de poder
     * establecerlo. Es la unica lectura del sistema que esquiva el filtro por
     * club_id, y devuelve solo el identificador: nunca datos de la reserva.
     */
    @Query(value = """
            SELECT club_id FROM booking
            WHERE management_token = :token OR confirmation_token = :token OR share_token = :token
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findClubIdByAnyToken(@Param("token") String token);

    /**
     * Historial de un jugador en todos los clubes de la plataforma.
     *
     * <p>Nativa a proposito, igual que {@link #findClubIdByAnyToken}: el filtro
     * por club_id de Hibernate no distingue "sin filtro" de "vacio" en JPQL, asi
     * que cruzar clubes solo se puede esquivandolo con SQL nativo.
     */
    @Query(value = """
            SELECT b.id AS bookingId, t.name AS clubName, t.slug AS clubSlug,
                   co.name AS courtName, b.start_time AS startTime, b.end_time AS endTime,
                   b.status AS status, b.total_price AS totalPrice, b.paid_amount AS paidAmount,
                   b.management_token AS managementToken
            FROM booking b
            JOIN customer cu ON cu.id = b.customer_id
            JOIN tenant t ON t.id = b.club_id
            JOIN court co ON co.id = b.court_id
            WHERE cu.phone_number = :phone
            ORDER BY b.start_time DESC
            """, nativeQuery = true)
    List<PlayerBookingHistoryRow> findHistoryByPhone(@Param("phone") String phone);

    /** Proyeccion de {@link #findHistoryByPhone}. */
    interface PlayerBookingHistoryRow {
        UUID getBookingId();

        String getClubName();

        String getClubSlug();

        String getCourtName();

        Instant getStartTime();

        Instant getEndTime();

        String getStatus();

        BigDecimal getTotalPrice();

        BigDecimal getPaidAmount();

        String getManagementToken();
    }

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

    /**
     * Turnos que arrancan dentro de un rango, para agregar estadisticas del club.
     *
     * <p>Proyeccion, no la entidad completa: {@link BookingStatsRow} trae
     * exactamente lo que {@code BookingStatsService} lee (ver esa clase). Sin
     * cancha (nunca se usa ahi) y sin el resto de {@code Customer}, en vez de
     * arrastrar ambas entidades enteras por cada turno de un año.
     */
    @Query("""
            SELECT new ar.com.padelnec.repository.BookingStatsRow(
                b.id, b.status, b.startTime, b.endTime, b.totalPrice, b.paidAmount, b.cancellationReason,
                b.customer.id, b.customer.fullName, b.customer.phoneNumber)
            FROM Booking b
            WHERE b.startTime >= :from AND b.startTime < :until
            ORDER BY b.startTime ASC
            """)
    List<BookingStatsRow> findStatsBetween(@Param("from") Instant from, @Param("until") Instant until);
}
