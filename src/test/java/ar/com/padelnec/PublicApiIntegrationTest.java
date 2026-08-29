package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

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

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;

    private RestTestClient client;
    private LocalDate matchDay;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
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
    @DisplayName("El circuito completo del jugador: reservar, ver el turno y cancelar")
    void fullPlayerJourney() {
        JsonNode slot = firstFreeSlot();

        JsonNode booking = client.post().uri("/api/public/club-necochea/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bookingBody(slot))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(booking.get("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
        String token = tokenFrom(booking.get("managementUrl").asText());

        // El turno ya retiene la cancha.
        assertThat(freeCourtsAt(slot.get("startTime").asText())).isEqualTo(1);

        // Este es el paso que fallaba: el link no lleva el slug del club, asi que el
        // tenant tiene que salir del propio token antes de abrir ninguna transaccion.
        JsonNode detail = client.get().uri("/api/public/manage/" + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(detail.get("courtName").asText()).isNotBlank();
        assertThat(detail.get("cancellableOnline").asBoolean()).isTrue();

        JsonNode cancelled = client.post().uri("/api/public/manage/" + token + "/cancel")
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("refundNeeded").asBoolean()).isFalse();

        // La cancha volvio al mercado.
        assertThat(freeCourtsAt(slot.get("startTime").asText())).isEqualTo(2);
    }

    @Test
    @DisplayName("Dos jugadores sobre el mismo turno: el segundo recibe 409")
    void theSecondBookingGetsAConflict() {
        JsonNode slot = firstFreeSlot();
        book(slot).expectStatus().isCreated();

        JsonNode error = book(slot)
                .expectStatus().isEqualTo(409)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        // Codigo propio para que la app refresque la grilla en vez de mostrar un
        // cartel de error generico.
        assertThat(error.get("code").asText()).isEqualTo("SLOT_TAKEN");
    }

    @Test
    @DisplayName("Un token inventado devuelve 404 y no filtra nada")
    void unknownTokensReturnNotFound() {
        client.get().uri("/api/public/manage/token-que-no-existe")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Un club inexistente devuelve 404")
    void unknownClubReturnsNotFound() {
        client.get().uri("/api/public/club-que-no-existe/availability?date=" + matchDay)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Un formulario incompleto devuelve 400 con el mensaje para el jugador")
    void invalidPayloadIsRejected() {
        JsonNode error = client.post().uri("/api/public/club-necochea/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("fullName", "", "phoneNumber", "", "paymentChoice", "PAY_AT_CLUB"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(error.get("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("El webhook de MercadoPago sin firma valida se rechaza")
    void unsignedWebhooksAreRejected() {
        // Sin esta barrera, cualquiera confirmaria turnos que nadie pago.
        client.post().uri("/api/webhooks/mercadopago/club-necochea?type=payment&data.id=123")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ------------------------------------------------------------ utilidades

    private RestTestClient.ResponseSpec book(JsonNode slot) {
        return client.post().uri("/api/public/club-necochea/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bookingBody(slot))
                .exchange();
    }

    private JsonNode availability() {
        return client.get().uri("/api/public/club-necochea/availability?date=" + matchDay)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
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

    private String tokenFrom(String managementUrl) {
        return managementUrl.substring(managementUrl.indexOf("/manage/") + "/manage/".length());
    }
}
