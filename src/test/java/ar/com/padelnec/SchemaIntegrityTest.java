package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica sobre la base real las garantias que el esquema promete.
 *
 * <p>El doble booking es el unico fallo verdaderamente inaceptable de este
 * producto: si dos jugadores pagan la misma cancha a la misma hora, el club queda
 * expuesto en el mostrador. La validacion aplicativa no alcanza porque dos
 * requests concurrentes la pasan las dos; la garantia vive en la base.
 */
@SpringBootTest
@Import(TestDatabaseConfig.class)
class SchemaIntegrityTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID clubId;
    private UUID courtId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM booking");
        jdbc.update("DELETE FROM customer");
        jdbc.update("DELETE FROM court");
        jdbc.update("DELETE FROM tenant");

        clubId = jdbc.queryForObject(
                "INSERT INTO tenant (name, slug, whatsapp_number) VALUES (?, ?, ?) RETURNING id",
                UUID.class, "Club Necochea", "club-necochea", "+542262400000");
        courtId = jdbc.queryForObject(
                "INSERT INTO court (club_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, clubId, "Cancha 1");
        customerId = jdbc.queryForObject(
                "INSERT INTO customer (club_id, phone_number, full_name) VALUES (?, ?, ?) RETURNING id",
                UUID.class, clubId, "+5492262415000", "Jugador de prueba");
    }

    @Test
    @DisplayName("Flyway aplica el baseline y habilita btree_gist")
    void schemaIsInPlace() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class);
        assertThat(tables).isGreaterThanOrEqualTo(12);

        Integer extension = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
        assertThat(extension).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos turnos que se pisan en la misma cancha no pueden coexistir")
    void overlappingBookingsAreRejected() {
        insertBooking("CONFIRMED", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");

        // Se solapa por media hora con el turno anterior.
        assertThatThrownBy(() ->
                insertBooking("CONFIRMED", "2026-09-01 19:00:00+00", "2026-09-01 20:30:00+00"))
                .hasMessageContaining("ex_booking_no_overlap");
    }

    @Test
    @DisplayName("Un turno pendiente de pago tambien bloquea la franja")
    void draftBookingsAlsoHoldTheSlot() {
        insertBooking("DRAFT", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");

        assertThatThrownBy(() ->
                insertBooking("AWAITING_CONFIRMATION", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00"))
                .hasMessageContaining("ex_booking_no_overlap");
    }

    @Test
    @DisplayName("Turnos consecutivos que se tocan en el borde conviven")
    void backToBackBookingsAreAllowed() {
        insertBooking("CONFIRMED", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");
        insertBooking("CONFIRMED", "2026-09-01 19:30:00+00", "2026-09-01 21:00:00+00");

        assertThat(countBookings()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cancelar un turno libera la franja para revenderla")
    void cancelledBookingsReleaseTheSlot() {
        insertBooking("CANCELLED", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");
        insertBooking("NO_SHOW", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");
        insertBooking("CONFIRMED", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");

        assertThat(countBookings()).isEqualTo(3);
    }

    @Test
    @DisplayName("El mismo horario en otra cancha es una reserva valida")
    void sameSlotOnAnotherCourtIsFine() {
        UUID otherCourt = jdbc.queryForObject(
                "INSERT INTO court (club_id, name) VALUES (?, ?) RETURNING id",
                UUID.class, clubId, "Cancha 2");

        insertBooking(courtId, "CONFIRMED", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");
        insertBooking(otherCourt, "CONFIRMED", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");

        assertThat(countBookings()).isEqualTo(2);
    }

    @Test
    @DisplayName("El id de pago de MercadoPago es unico, asi el webhook es idempotente")
    void mercadoPagoPaymentIdIsUnique() {
        UUID bookingId = insertBooking("CONFIRMED", "2026-09-01 18:00:00+00", "2026-09-01 19:30:00+00");

        insertPayment(bookingId, "112233");
        assertThatThrownBy(() -> insertPayment(bookingId, "112233"))
                .hasMessageContaining("mp_payment_id");
    }

    // ------------------------------------------------------------ utilidades

    private UUID insertBooking(String status, String start, String end) {
        return insertBooking(courtId, status, start, end);
    }

    private UUID insertBooking(UUID court, String status, String start, String end) {
        return jdbc.queryForObject("""
                INSERT INTO booking (club_id, court_id, customer_id, start_time, end_time, status, total_price)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, ?, 20000)
                RETURNING id
                """, UUID.class, clubId, court, customerId, start, end, status);
    }

    private void insertPayment(UUID bookingId, String mpPaymentId) {
        jdbc.update("""
                INSERT INTO payment (club_id, booking_id, amount, method, status, mp_payment_id)
                VALUES (?, ?, 10000, 'MERCADOPAGO', 'APPROVED', ?)
                """, clubId, bookingId, mpPaymentId);
    }

    private Integer countBookings() {
        return jdbc.queryForObject("SELECT count(*) FROM booking", Integer.class);
    }
}
