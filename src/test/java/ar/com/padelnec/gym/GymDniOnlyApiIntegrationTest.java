package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymRateLimits;
import ar.com.padelnec.gym.service.GymSettingsService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

/**
 * El modo normal del gimnasio: el socio entra con solo el DNI, y registra el ingreso
 * estando cerca de la sede.
 *
 * <p>El modo con clave temporal tiene su propia clase ({@link GymApiIntegrationTest}); aca se
 * prueba tambien que se puede pasar de uno al otro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class})
class GymDniOnlyApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Una sede real de Necochea; el radio de fabrica es de 200 m. */
    private static final double LAT = -38.5545;
    private static final double LON = -58.7396;

    @LocalServerPort private int port;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;
    @Autowired private GymMemberService memberService;
    @Autowired private GymSettingsService settingsService;
    @Autowired private GymCheckinRepository checkinRepository;
    @MockitoBean private GymRateLimits rateLimits;

    private RestTestClient client;
    private Tenant club;
    private GymSede located;
    private CreatedMember ana;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        clubFixture.reset();

        club = clubFixture.club("los-troncos");
        gym.enable(club);
        located = gym.sedeAt(club, "Necochea", LAT, LON, 200);
        ana = gym.member(club, "30111222", "Ana Gómez");
        LocalDate today = LocalDate.now(ZONE);
        gym.sell(club, ana.id(), today.minusDays(1), today.plusDays(29), 3, located);
    }

    // ------------------------------------------------------------ helpers

    private JsonNode post(String path, String token, Map<String, ?> body, int expectedStatus) {
        RestTestClient.RequestBodySpec request = client.post().uri("/api/public/los-troncos/gym" + path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.body(body)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    private JsonNode get(String path, String token, int expectedStatus) {
        RestTestClient.RequestHeadersSpec<?> request = client.get().uri("/api/public/los-troncos/gym" + path);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    private String loginWithDniOnly() {
        return post("/login", null, Map.of("dni", ana.dni()), 200).get("token").asText();
    }

    private JsonNode checkIn(String token, Double lat, Double lon, int expectedStatus) {
        Map<String, Object> body = new HashMap<>();
        body.put("qrToken", located.getQrToken());
        if (lat != null) {
            body.put("latitude", lat);
            body.put("longitude", lon);
        }
        return post("/checkin", token, body, expectedStatus);
    }

    // ------------------------------------------------------- acceso por DNI

    @Test
    @DisplayName("La app pregunta como se entra: sin clave, y con ubicacion porque la sede la verifica")
    void configTellsTheAppHowToLogIn() {
        JsonNode config = get("/config", null, 200);

        assertThat(config.get("passwordRequired").asBoolean()).isFalse();
        assertThat(config.get("locationRequired").asBoolean()).isTrue();
        assertThat(config.get("clubName").asText()).isEqualTo("Club los-troncos");
    }

    @Test
    @DisplayName("Si ninguna sede tiene ubicacion cargada, la app no pide la ubicacion")
    void locationIsNotRequestedWhenNoSedeVerifiesIt() {
        Tenant plain = clubFixture.club("sin-ubicacion");
        gym.enable(plain);
        gym.sede(plain, "Sede sin coordenadas");

        JsonNode config = client.get().uri("/api/public/sin-ubicacion/gym/config")
                .exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(config.get("locationRequired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Con solo el DNI se entra y se usa la app enseguida, sin cambiar ninguna clave")
    void dniAloneIsEnough() {
        JsonNode session = post("/login", null, Map.of("dni", "30.111.222"), 200);

        assertThat(session.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(session.get("fullName").asText()).isEqualTo("Ana Gómez");
        // El alta deja la clave temporal pendiente en la base, pero sin clave no cuenta.
        assertThat(get("/me", session.get("token").asText(), 200).get("valid").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Si el socio manda una clave igual, se ignora")
    void aPasswordIsIgnored() {
        post("/login", null, Map.of("dni", ana.dni(), "password", "cualquier-cosa"), 200);
    }

    @Test
    @DisplayName("Un DNI que no es de un socio se rechaza con un mensaje que ayuda; un socio deshabilitado tambien")
    void unknownAndDisabledDnisAreRejected() {
        JsonNode unknown = post("/login", null, Map.of("dni", "99999999"), 422);
        assertThat(unknown.get("message").asText()).contains("No encontramos un socio con ese DNI");

        TenantContext.runAs(club.getId(), () -> memberService.setEnabled(ana.id(), false));
        post("/login", null, Map.of("dni", ana.dni()), 422);
    }

    @Test
    @DisplayName("El club puede pasar a pedir clave, y volver: cada modo rechaza al otro")
    void theClubCanSwitchToPasswordAndBack() {
        TenantContext.runAs(club.getId(), () -> settingsService.setPasswordRequired(true));

        assertThat(get("/config", null, 200).get("passwordRequired").asBoolean()).isTrue();
        // Ahora el DNI solo no alcanza, y la clave temporal que se genero en el alta si sirve.
        assertThat(post("/login", null, Map.of("dni", ana.dni()), 422).get("message").asText())
                .isEqualTo("DNI o clave incorrectos");
        JsonNode withPassword = post("/login", null,
                Map.of("dni", ana.dni(), "password", ana.temporaryPassword()), 200);
        assertThat(withPassword.get("mustChangePassword").asBoolean()).isTrue();

        TenantContext.runAs(club.getId(), () -> settingsService.setPasswordRequired(false));
        assertThat(post("/login", null, Map.of("dni", ana.dni()), 200).get("mustChangePassword").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("Pasar a solo-DNI libera a quien tenia la clave temporal pendiente: ya no le bloquea la app")
    void switchingToDniOnlyUnblocksPendingTemporaryPasswords() {
        TenantContext.runAs(club.getId(), () -> settingsService.setPasswordRequired(true));
        String blocked = post("/login", null, Map.of("dni", ana.dni(), "password", ana.temporaryPassword()), 200)
                .get("token").asText();
        get("/me", blocked, 403);

        TenantContext.runAs(club.getId(), () -> settingsService.setPasswordRequired(false));

        get("/me", blocked, 200);
    }

    // --------------------------------------------------------- ubicacion

    @Test
    @DisplayName("En la sede, a unos 110 m, registra el ingreso y deja la distancia de constancia")
    void nearTheSedeChecksIn() {
        String token = loginWithDniOnly();

        JsonNode result = checkIn(token, LAT + 0.001, LON, 200);

        assertThat(result.get("sedeName").asText()).isEqualTo("Necochea");
        TenantContext.runAs(club.getId(), () -> {
            Integer meters = checkinRepository.findByMemberIdAndLocalDate(ana.id(), LocalDate.now(ZONE))
                    .orElseThrow().getDistanceMeters();
            assertThat(meters).isBetween(105, 118);
        });
    }

    @Test
    @DisplayName("Dentro del margen de 200 m todavia entra: el GPS falla adentro de un edificio")
    void insideTheMarginStillChecksIn() {
        checkIn(loginWithDniOnly(), LAT + 0.0017, LON, 200);
    }

    @Test
    @DisplayName("Desde lejos no registra, y el mensaje dice a que distancia esta")
    void farAwayIsRejected() {
        JsonNode error = checkIn(loginWithDniOnly(), LAT + 0.01, LON, 422);

        assertThat(error.get("message").asText())
                .contains("Parece que no estás en Necochea")
                .contains("unos 1,1 km")
                .contains("menos de 200 m");
        TenantContext.runAs(club.getId(), () ->
                assertThat(checkinRepository.findByMemberIdAndLocalDate(ana.id(), LocalDate.now(ZONE))).isEmpty());
    }

    @Test
    @DisplayName("Sin ubicacion (permiso negado), una sede que la verifica no registra")
    void noLocationIsRejected() {
        JsonNode error = checkIn(loginWithDniOnly(), null, null, 422);

        assertThat(error.get("message").asText()).contains("Necesitamos tu ubicación");
        // Con codigo propio: la app le explica al socio como activar el permiso.
        assertThat(error.get("code").asText()).isEqualTo("LOCATION_REQUIRED");
    }

    @Test
    @DisplayName("Con solo una de las dos coordenadas no hay ubicacion")
    void halfALocationIsNoLocation() {
        String token = loginWithDniOnly();

        JsonNode error = post("/checkin", token, Map.of("qrToken", located.getQrToken(), "latitude", LAT), 422);

        assertThat(error.get("message").asText()).contains("Necesitamos tu ubicación");
    }

    @Test
    @DisplayName("Coordenadas imposibles se rechazan como pedido mal armado")
    void impossibleCoordinatesAreABadRequest() {
        post("/checkin", loginWithDniOnly(),
                Map.of("qrToken", located.getQrToken(), "latitude", 200.0, "longitude", LON), 400);
    }

    @Test
    @DisplayName("Escanear de nuevo lo que ya estaba registrado no vuelve a pedir la ubicacion")
    void scanningAgainDoesNotNeedTheLocation() {
        String token = loginWithDniOnly();
        checkIn(token, LAT, LON, 200);

        JsonNode again = checkIn(token, null, null, 200);

        assertThat(again.get("alreadyRegistered").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Una sede sin ubicacion cargada no la pide: se registra igual")
    void aSedeWithoutLocationDoesNotAskForIt() {
        GymSede plain = gym.sede(club, "Sin coordenadas");
        LocalDate today = LocalDate.now(ZONE);
        CreatedMember beto = gym.member(club, "40222333", "Beto Ruiz");
        gym.sell(club, beto.id(), today.minusDays(1), today.plusDays(29), 3, plain);
        String token = post("/login", null, Map.of("dni", beto.dni()), 200).get("token").asText();

        JsonNode result = post("/checkin", token, Map.of("qrToken", plain.getQrToken()), 200);

        assertThat(result.get("sedeName").asText()).isEqualTo("Sin coordenadas");
        TenantContext.runAs(club.getId(), () -> assertThat(
                checkinRepository.findByMemberIdAndLocalDate(beto.id(), today).orElseThrow().getDistanceMeters())
                .isNull());
    }
}
