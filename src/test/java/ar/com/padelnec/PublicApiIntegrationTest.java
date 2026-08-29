package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Recorre la API como la recorre la app del jugador, sobre HTTP real.
 *
 * <p>Existe porque los tests de servicio dejaban el club puesto a mano en el
 * arranque y tapaban un problema serio: por HTTP no hay nadie que lo haga, y
 * establecerlo dentro de la transaccion llega tarde, porque Hibernate fija el
 * tenant al abrir la sesion. Los flujos por token no funcionaban de verdad y ningun
 * test de servicio podia notarlo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PublicApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired private TestRestTemplate rest;
    @Autowired private ClubFixture fixture;

    private LocalDate matchDay;

    @BeforeEach
    void setUp() {
        fixture.reset();

        Tenant club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        fixture.court("Cancha 1", 1);
        fixture.court("Cancha 2", 2);

        // Se reserva pasado manana para quedar comodos frente al limite de
        // cancelacion de 12 horas.
        matchDay = LocalDate.now(ZONE).plusDays(2);
        fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");

        TenantContext.clear();
    }

    @Test
    @DisplayName("La grilla del club se sirve por slug, sin sesion ni login")
    void availabilityIsPublic() {
        JsonNode grid = availability();

        assertThat(grid.get("club").get("name").asText()).isEqualTo("Club club-necochea");
        assertThat(grid.get("courts")).hasSize(2);
        assertThat(grid.get("slots")).isNotEmpty();
    }

    @Test
    @DisplayName("El circuito completo del jugador: reservar, confirmar, ver y cancelar")
    void fullPlayerJourney() {
        JsonNode slot = firstFreeSlot();

        // --- reserva -------------------------------------------------------
        ResponseEntity<JsonNode> created = rest.postForEntity(
                "/api/public/club-necochea/bookings", bookingBody(slot), JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode booking = created.getBody();
        assertThat(booking.get("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
        String managementToken = tokenFrom(booking.get("managementUrl").asText(), "/manage/");

        // --- el turno ya bloquea la grilla ---------------------------------
        assertThat(freeCourtsAt(slot.get("startTime").asText())).isEqualTo(1);

        // --- portal de gestion ---------------------------------------------
        // Este es el paso que fallaba: el link no lleva el slug, asi que el club
        // tiene que salir del token antes de que se abra ninguna transaccion.
        ResponseEntity<JsonNode> detail = rest.getForEntity(
                "/api/public/manage/" + managementToken, JsonNode.class);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("courtName").asText()).isNotBlank();
        assertThat(detail.getBody().get("cancellableOnline").asBoolean()).isTrue();

        // --- cancelacion ----------------------------------------------------
        ResponseEntity<JsonNode> cancelled = rest.postForEntity(
                "/api/public/manage/" + managementToken + "/cancel", null, JsonNode.class);

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.getBody().get("refundNeeded").asBoolean()).isFalse();

        // --- la cancha volvio al mercado ------------------------------------
        assertThat(freeCourtsAt(slot.get("startTime").asText())).isEqualTo(2);
    }

    @Test
    @DisplayName("Dos jugadores sobre el mismo turno: el segundo recibe 409")
    void theSecondBookingGetsAConflict() {
        JsonNode slot = firstFreeSlot();
        rest.postForEntity("/api/public/club-necochea/bookings", bookingBody(slot), JsonNode.class);

        ResponseEntity<JsonNode> second = rest.postForEntity(
                "/api/public/club-necochea/bookings", bookingBody(slot), JsonNode.class);

        // 409 y un codigo propio para que la app sepa que tiene que refrescar la
        // grilla en vez de mostrar un cartel de error.
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code").asText()).isEqualTo("SLOT_TAKEN");
    }

    @Test
    @DisplayName("Un token inventado devuelve 404 y no filtra nada")
    void unknownTokensReturnNotFound() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                "/api/public/manage/token-que-no-existe", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Un club inexistente devuelve 404")
    void unknownClubReturnsNotFound() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                "/api/public/club-que-no-existe/availability?date=" + matchDay, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Un formulario incompleto devuelve 400 con el mensaje para el jugador")
    void invalidPayloadIsRejected() {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/api/public/club-necochea/bookings",
                Map.of("fullName", "", "phoneNumber", "", "paymentChoice", "PAY_AT_CLUB"),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("El webhook de MercadoPago sin firma valida se rechaza")
    void unsignedWebhooksAreRejected() {
        ResponseEntity<Void> response = rest.postForEntity(
                "/api/webhooks/mercadopago/club-necochea?type=payment&data.id=123",
                null, Void.class);

        // Sin esta barrera, cualquiera confirmaria turnos que nadie pago.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------ utilidades

    private JsonNode availability() {
        return rest.getForObject(
                "/api/public/club-necochea/availability?date=" + matchDay, JsonNode.class);
    }

    private JsonNode firstFreeSlot() {
        for (JsonNode slot : availability().get("slots")) {
            if (slot.get("available").size() == 2) {
                return slot;
            }
        }
        throw new AssertionError("No hay ningun turno con las dos canchas libres");
    }

    private int freeCourtsAt(String startTime) {
        for (JsonNode slot : availability().get("slots")) {
            if (slot.get("startTime").asText().equals(startTime)) {
                return slot.get("available").size();
            }
        }
        return 0;
    }

    private Map<String, Object> bookingBody(JsonNode slot) {
        return Map.of(
                "courtId", slot.get("available").get(0).get("courtId").asText(),
                "startTime", Instant.parse(slot.get("startsAt").asText()).toString(),
                "fullName", "Simon Diaz",
                "phoneNumber", "2262415000",
                "paymentChoice", "PAY_AT_CLUB");
    }

    private String tokenFrom(String url, String marker) {
        return url.substring(url.indexOf(marker) + marker.length());
    }
}
