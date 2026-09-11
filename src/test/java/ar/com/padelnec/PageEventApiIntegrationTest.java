package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.domain.PageEvent;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PageEventName;
import ar.com.padelnec.repository.PageEventRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * La bitacora de visitas, entrando por HTTP como entra de verdad.
 *
 * <p>Sobre el servidor real y no contra el servicio porque lo que mas importa de
 * este endpoint pasa en el borde: que la ruta llegue sin el token del turno, que
 * un lote repetido no duplique, y que nada de lo que mande un navegador
 * cualquiera -- que aca no se autentica con nada -- pueda escribir cualquier
 * cosa en la tabla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PageEventApiIntegrationTest {

    private static final String CHROME =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private PageEventRepository pageEventRepository;

    private RestTestClient client;
    private Tenant club;
    private UUID visit;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        fixture.reset();
        club = fixture.club("club-necochea");
        visit = UUID.randomUUID();
    }

    @Test
    @DisplayName("El recorrido de una visita queda guardado en orden y con su club")
    void visitIsRecorded() {
        send(CHROME,
                event(1, "view", "/buscar"),
                searchEvent(2),
                event(3, "view", "/club/club-necochea"),
                bookingEvent(4));

        List<PageEvent> events = stored();
        assertThat(events).hasSize(4);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getSessionId()).isEqualTo(visit);
            assertThat(event.getDevice()).isEqualTo("mobile");
        });

        // La busqueda global no es de ningun club; la ficha si.
        assertThat(events.get(0).getClubId()).isNull();
        assertThat(events.get(2).getClubId()).isEqualTo(club.getId());

        PageEvent search = events.get(1);
        assertThat(search.getName()).isEqualTo(PageEventName.SEARCH);
        assertThat(search.getResults()).isZero();
        assertThat(search.getTimeFrom()).hasToString("18:00");
        assertThat(search.getClubsFilter()).isEqualTo("club-necochea");

        assertThat(events.get(3).getName()).isEqualTo(PageEventName.BOOKING_CREATED);
        assertThat(events.get(3).getPaymentChoice()).isEqualTo("PAY_AT_CLUB");
    }

    @Test
    @DisplayName("El token del turno nunca llega a la bitacora, aunque el navegador lo mande")
    void managementTokenIsNeverStored() {
        send(CHROME, event(1, "view", "/manage/un-token-secreto-de-verdad"));

        assertThat(stored()).singleElement()
                .satisfies(event -> assertThat(event.getPath()).isEqualTo("/manage/:token"));
    }

    @Test
    @DisplayName("Cada ruta se guarda por su forma, y la desconocida no se guarda entera")
    void pathsAreStoredAsShapes() {
        send(CHROME,
                event(1, "view", "/"),
                event(2, "view", "/club/club-necochea"),
                event(3, "view", "/turno/token-de-solo-lectura"),
                event(4, "view", "/una-ruta-que-todavia-no-existe"));

        assertThat(stored()).extracting(PageEvent::getPath)
                .containsExactly("/", "/club/:slug", "/turno/:token", "/otro");
    }

    @Test
    @DisplayName("El mismo lote dos veces no duplica, y el evento nuevo igual entra")
    void repeatedBatchIsIdempotent() {
        Map<String, Object> first = event(1, "view", "/buscar");
        send(CHROME, first);
        send(CHROME, first, event(2, "view", "/club/club-necochea"));

        assertThat(stored()).hasSize(2);
    }

    @Test
    @DisplayName("Un evento que no existe en el catalogo se descarta sin arrastrar al resto")
    void unknownEventsAreDropped() {
        send(CHROME, event(1, "lo-que-sea", "/buscar"), event(2, "view", "/buscar"));

        assertThat(stored()).singleElement()
                .satisfies(event -> assertThat(event.getSeq()).isEqualTo(2));
    }

    @Test
    @DisplayName("El robot que indexa las fichas no cuenta como visita")
    void crawlersAreNotVisits() {
        send("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
                event(1, "view", "/club/club-necochea"));

        assertThat(stored()).isEmpty();
    }

    @Test
    @DisplayName("Un lote sin visita es una peticion invalida, no una fila a medias")
    void aBatchWithoutVisitIsRejected() {
        Map<String, Object> body = new HashMap<>();
        body.put("events", List.of(event(1, "view", "/buscar")));

        client.post().uri("/api/public/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(stored()).isEmpty();
    }

    // ----------------------------------------------------------------- helpers

    @SafeVarargs
    private void send(String userAgent, Map<String, Object>... events) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", visit.toString());
        body.put("referrer", "instagram.com");
        body.put("utmSource", "ig");
        body.put("events", List.of(events));

        client.post().uri("/api/public/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .body(body)
                .exchange()
                .expectStatus().isNoContent();
    }

    private Map<String, Object> event(int seq, String name, String path) {
        Map<String, Object> event = new HashMap<>();
        event.put("seq", seq);
        event.put("name", name);
        event.put("path", path);
        if (path.startsWith("/club/")) {
            event.put("clubSlug", path.substring("/club/".length()));
        }
        return event;
    }

    private Map<String, Object> searchEvent(int seq) {
        Map<String, Object> event = event(seq, "search", "/buscar");
        event.put("date", "2026-09-13");
        event.put("from", "18:00");
        event.put("to", "22:00");
        event.put("clubs", "club-necochea");
        event.put("results", 0);
        return event;
    }

    private Map<String, Object> bookingEvent(int seq) {
        Map<String, Object> event = event(seq, "booking_created", "/club/club-necochea");
        event.put("paymentChoice", "PAY_AT_CLUB");
        event.put("bookingId", UUID.randomUUID().toString());
        return event;
    }

    /** Los eventos de esta visita, en el orden en que los vivio el visitante. */
    private List<PageEvent> stored() {
        List<PageEvent> events = new ArrayList<>(pageEventRepository.findAll());
        events.sort((left, right) -> Integer.compare(left.getSeq(), right.getSeq()));
        return events;
    }
}
