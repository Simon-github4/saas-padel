package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.web.api.BookingRateLimiter;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * La garantia de fondo del sistema no es que la app valide bien: es que la
 * restriccion de exclusion GiST de la base (`ex_booking_no_overlap`,
 * {@code V1__baseline.sql}) gana la carrera cuando dos requests le pegan al
 * mismo turno de verdad al mismo tiempo, no en orden. {@link
 * PublicApiIntegrationTest#theSecondBookingGetsAConflict()} ya prueba el
 * caso secuencial (la segunda request llega despues de que la primera ya
 * confirmo); esto agrega el caso simultaneo real, con hilos liberados a la
 * vez por un {@link CountDownLatch}.
 *
 * <p>{@link BookingRateLimiter} queda mockeado (no-op): el limite de 10
 * intentos por 5 minutos por origen frenaria un burst de 10 requests del
 * mismo loopback, y ademas Spring cachea el {@code ApplicationContext} entre
 * clases de test con la misma configuracion -- sin este mock, cuanto haya
 * gastado {@code PublicApiIntegrationTest} del mismo cupo antes en la misma
 * corrida decidiria si este test pasa, no la restriccion GiST que en
 * realidad se quiere probar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class BookingConcurrencyTest {

    private static final int RACERS = 10;
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @MockitoBean private BookingRateLimiter rateLimiter;

    private RestTestClient client;
    private LocalDate matchDay;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        client = RestTestClient.bindToServer().baseUrl(baseUrl).build();
        fixture.reset();

        Tenant club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        fixture.court("Cancha 1", 1);

        matchDay = LocalDate.now(ZONE).plusDays(2);
        fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");
        TenantContext.clear();
    }

    @Test
    @DisplayName("De diez intentos simultaneos por el mismo turno, gana exactamente uno")
    void onlyOneRacerWinsTheSameSlot() throws Exception {
        JsonNode slot = firstFreeSlot();
        String courtId = slot.get("available").get(0).get("courtId").asText();
        String startsAt = slot.get("startsAt").asText();
        URI uri = URI.create(baseUrl + "/api/public/club-necochea/bookings");

        HttpClient http = HttpClient.newHttpClient();
        CountDownLatch ready = new CountDownLatch(RACERS);
        CountDownLatch go = new CountDownLatch(1);

        // Future por racer (no una lista compartida): si alguno revienta antes de
        // llegar al POST, .get() lo saca a la luz con su causa real, en vez de
        // perderse en un Runnable y dejar un resultado vacio sin explicacion.
        List<Future<Integer>> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(RACERS)) {
            List<Callable<Integer>> racers = new ArrayList<>();
            for (int racer = 0; racer < RACERS; racer++) {
                String phone = "22624150%02d".formatted(racer);
                racers.add(() -> {
                    ready.countDown();
                    go.await();
                    HttpRequest request = HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    bookingBody(courtId, startsAt, phone)))
                            .build();
                    return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                });
            }
            results = racers.stream().map(pool::submit).toList();
            ready.await();
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(10, TimeUnit.SECONDS));
        }

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(RACERS - 1);
    }

    /** Los valores son propios del test (uuid/instant/digitos), arman JSON valido sin mapper. */
    private String bookingBody(String courtId, String startsAt, String phone) {
        return """
                {"courtId":"%s","startTime":"%s","fullName":"Racer %s","phoneNumber":"%s","paymentChoice":"PAY_AT_CLUB"}"""
                .formatted(courtId, startsAt, phone, phone);
    }

    private JsonNode firstFreeSlot() {
        JsonNode grid = client.get()
                .uri("/api/public/club-necochea/availability?date=" + matchDay)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
        for (JsonNode slot : grid.get("slots")) {
            if (slot.get("available").size() == 1) {
                return slot;
            }
        }
        throw new AssertionError("No hay ningun turno con la cancha libre");
    }
}
