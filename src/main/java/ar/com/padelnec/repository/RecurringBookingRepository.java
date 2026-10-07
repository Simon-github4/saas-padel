package ar.com.padelnec.repository;

import ar.com.padelnec.domain.RecurringBooking;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringBookingRepository extends JpaRepository<RecurringBooking, UUID> {

    @Query("""
            SELECT DISTINCT r FROM RecurringBooking r
            JOIN FETCH r.court
            JOIN FETCH r.customer
            LEFT JOIN FETCH r.skips
            WHERE r.active = true
            ORDER BY r.dayOfWeek ASC, r.startTime ASC
            """)
    List<RecurringBooking> findAllActiveWithDetail();

    /**
     * Turnos fijos vigentes de un telefono en todos los clubes, para "Mis turnos",
     * con la proxima fecha que se juega de cada uno.
     *
     * <p>Nativa por lo mismo que {@code BookingRepository#findHistoryByAccount}:
     * cruzar clubes solo se puede esquivando el filtro por club de Hibernate.
     *
     * <p>La proxima fecha sale de los turnos ya generados y no de calcular la
     * regla: asi respeta las semanas salteadas, las que el club cancelo y las que
     * no se pudieron generar por un choque. COMPLETED cuenta porque el club cierra
     * el turno al cobrarlo, muchas veces antes del partido.
     *
     * <p>A proposito no devuelve ni el management_token ni el id de ningun turno:
     * el telefono no prueba quien pregunta (ver {@code PlayerAuthService#recurring}),
     * asi que lo que sale de aca tiene que servir para mirar y no para actuar.
     */
    @Query(value = """
            SELECT r.id AS recurringId, t.name AS clubName, t.slug AS clubSlug,
                   t.time_zone AS timeZone, co.name AS courtName, r.day_of_week AS dayOfWeek,
                   to_char(r.start_time, 'HH24:MI') AS startTime,
                   r.duration_minutes AS durationMinutes,
                   to_char(r.valid_until, 'YYYY-MM-DD') AS validUntil,
                   nxt.start_time AS nextStartTime, nxt.end_time AS nextEndTime,
                   nxt.status AS nextStatus
            FROM recurring_booking r
            JOIN customer c ON c.id = r.customer_id
            JOIN tenant t ON t.id = r.club_id
            JOIN court co ON co.id = r.court_id
            LEFT JOIN LATERAL (
                SELECT b.start_time, b.end_time, b.status
                FROM booking b
                WHERE b.recurring_booking_id = r.id
                  AND b.status IN ('CONFIRMED', 'COMPLETED')
                  AND b.end_time > :now
                ORDER BY b.start_time ASC
                LIMIT 1
            ) nxt ON TRUE
            WHERE c.phone_number = :phone
              AND r.active
              AND (r.valid_until IS NULL
                   OR r.valid_until >= CAST(timezone(t.time_zone, CAST(:now AS timestamptz)) AS date))
            ORDER BY r.day_of_week ASC, r.start_time ASC
            """, nativeQuery = true)
    List<PlayerRecurringRow> findActiveForPhone(@Param("phone") String phone, @Param("now") Instant now);

    /** Proyeccion de {@link #findActiveForPhone}. */
    interface PlayerRecurringRow {
        UUID getRecurringId();

        String getClubName();

        String getClubSlug();

        String getTimeZone();

        String getCourtName();

        /** 1 = lunes ... 7 = domingo, como {@link java.time.DayOfWeek}. */
        Integer getDayOfWeek();

        /** "20:00", en la hora del club. */
        String getStartTime();

        Integer getDurationMinutes();

        /** "2026-12-15", o nulo si el turno fijo no tiene fecha de corte. */
        String getValidUntil();

        /** Nulo si no queda ninguna fecha generada por jugar. */
        Instant getNextStartTime();

        Instant getNextEndTime();

        String getNextStatus();
    }
}
