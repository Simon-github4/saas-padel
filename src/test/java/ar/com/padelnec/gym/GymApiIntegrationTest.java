package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymRateLimits;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * La app del socio sobre HTTP real: login por DNI, clave temporal, sesion por
 * token y check-in. El foco esta en lo que una app de ingreso no puede hacer mal:
 * el socio sale de la sesion y nunca del pedido, y una sesion de un club no sirve
 * en otro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class})
class GymApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String NEW_PASSWORD = "una-clave-nueva-123";

    @LocalServerPort private int port;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;
    @Autowired private GymMemberService memberService;
    @Autowired private GymMembershipService membershipService;
    @Autowired private GymCheckinRepository checkinRepository;
    // El limitador es un singleton compartido con el resto de la suite y todos los pedidos salen del
    // mismo origen local: sin mockearlo, el resultado dependeria del orden en que corren los tests.
    @MockitoBean private GymRateLimits rateLimits;

    private RestTestClient client;
    private Tenant club;
    private GymSede sede;
    private CreatedMember ana;
    private CreatedMember beto;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        clubFixture.reset();

        // Este club pide clave: los tests de abajo son del flujo con clave temporal. El modo solo-DNI,
        // que es el normal, tiene su propia clase (GymDniOnlyApiIntegrationTest).
        club = clubFixture.club("los-troncos");
        gym.enable(club, true);
        sede = gym.sede(club, "Necochea");
        ana = gym.member(club, "30111222", "Ana Gómez");
        beto = gym.member(club, "40222333", "Beto Ruiz");
        LocalDate today = LocalDate.now(ZONE);
        gym.sell(club, ana.id(), today.minusDays(1), today.plusDays(29), 3, sede);
        gym.sell(club, beto.id(), today.minusDays(1), today.plusDays(29), 3, sede);
    }

    // ------------------------------------------------------------ helpers

    private JsonNode post(String slug, String path, String token, Object body, int expectedStatus) {
        RestTestClient.RequestBodySpec request = client.post().uri("/api/public/" + slug + "/gym" + path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.body(body == null ? Map.of() : body)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    private JsonNode get(String slug, String path, String token, int expectedStatus) {
        RestTestClient.RequestHeadersSpec<?> request = client.get().uri("/api/public/" + slug + "/gym" + path);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    private JsonNode login(CreatedMember member, String password) {
        return post("los-troncos", "/login", null, Map.of("dni", member.dni(), "password", password), 200);
    }

    /** Un socio que ya cambio la clave temporal, listo para usar la app. */
    private String readyToken(CreatedMember member) {
        String temporary = login(member, member.temporaryPassword()).get("token").asText();
        return post("los-troncos", "/password/change", temporary,
                Map.of("currentPassword", member.temporaryPassword(), "newPassword", NEW_PASSWORD), 200)
                .get("token").asText();
    }

    private String qr() {
        return sede.getQrToken();
    }

    // -------------------------------------------------------------- tests

    @Test
    @DisplayName("Un club sin el modulo prendido, o un club que no existe, responde 404 en JSON")
    void moduleOffAnswersNotFound() {
        clubFixture.club("sin-gimnasio");

        JsonNode off = post("sin-gimnasio", "/login", null, Map.of("dni", "30111222", "password", "x"), 404);
        JsonNode missing = get("club-inexistente", "/me", "token", 404);

        assertThat(off.get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(missing.get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("DNI inexistente y clave incorrecta dan el mismo mensaje: no se sabe cual fallo")
    void badCredentialsAreGeneric() {
        JsonNode wrongPassword = post("los-troncos", "/login", null,
                Map.of("dni", ana.dni(), "password", "clave-equivocada"), 422);
        JsonNode unknownDni = post("los-troncos", "/login", null,
                Map.of("dni", "99999999", "password", "clave-equivocada"), 422);

        assertThat(wrongPassword.get("message").asText()).isEqualTo("DNI o clave incorrectos");
        assertThat(unknownDni.get("message").asText()).isEqualTo(wrongPassword.get("message").asText());
    }

    @Test
    @DisplayName("El DNI se acepta con puntos: el socio lo escribe como quiere")
    void dniIsAcceptedWithDots() {
        JsonNode session = post("los-troncos", "/login", null,
                Map.of("dni", "30.111.222", "password", ana.temporaryPassword()), 200);

        assertThat(session.get("fullName").asText()).isEqualTo("Ana Gómez");
    }

    @Test
    @DisplayName("Con la clave temporal solo se puede cambiar la clave; todo lo demas da 403")
    void temporaryPasswordForcesAChange() {
        JsonNode session = login(ana, ana.temporaryPassword());
        String token = session.get("token").asText();

        assertThat(session.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(get("los-troncos", "/me", token, 403).get("code").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(post("los-troncos", "/checkin", token, Map.of("qrToken", qr()), 403).get("code").asText())
                .isEqualTo("PASSWORD_CHANGE_REQUIRED");

        JsonNode changed = post("los-troncos", "/password/change", token,
                Map.of("currentPassword", ana.temporaryPassword(), "newPassword", NEW_PASSWORD), 200);

        assertThat(changed.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(get("los-troncos", "/me", changed.get("token").asText(), 200).get("fullName").asText())
                .isEqualTo("Ana Gómez");
    }

    @Test
    @DisplayName("Cambiar la clave cierra la sesion vieja y la clave temporal deja de servir")
    void changingThePasswordClosesTheOldSession() {
        String temporaryToken = login(ana, ana.temporaryPassword()).get("token").asText();

        post("los-troncos", "/password/change", temporaryToken,
                Map.of("currentPassword", ana.temporaryPassword(), "newPassword", NEW_PASSWORD), 200);

        assertThat(get("los-troncos", "/me", temporaryToken, 401).get("code").asText()).isEqualTo("SESSION_EXPIRED");
        post("los-troncos", "/login", null, Map.of("dni", ana.dni(), "password", ana.temporaryPassword()), 422);
        post("los-troncos", "/login", null, Map.of("dni", ana.dni(), "password", NEW_PASSWORD), 200);
    }

    @Test
    @DisplayName("Cambiar la clave con la actual equivocada se rechaza")
    void wrongCurrentPasswordIsRejected() {
        String token = login(ana, ana.temporaryPassword()).get("token").asText();

        post("los-troncos", "/password/change", token,
                Map.of("currentPassword", "no-es-la-clave", "newPassword", NEW_PASSWORD), 422);
    }

    @Test
    @DisplayName("Resetear la clave desde el panel cierra todas las sesiones abiertas del socio")
    void resettingThePasswordRevokesEverySession() {
        String phone = readyToken(ana);
        String otherDevice = login(ana, NEW_PASSWORD).get("token").asText();
        get("los-troncos", "/me", phone, 200);
        get("los-troncos", "/me", otherDevice, 200);

        String reset = TenantContext.callAs(club.getId(), () -> memberService.resetPassword(ana.id()));

        assertThat(get("los-troncos", "/me", phone, 401).get("code").asText()).isEqualTo("SESSION_EXPIRED");
        assertThat(get("los-troncos", "/me", otherDevice, 401).get("code").asText()).isEqualTo("SESSION_EXPIRED");
        assertThat(login(ana, reset).get("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Un socio deshabilitado pierde la sesion al instante")
    void disablingAMemberClosesTheSession() {
        String token = readyToken(ana);

        TenantContext.runAs(club.getId(), () -> memberService.setEnabled(ana.id(), false));

        get("los-troncos", "/me", token, 401);
        post("los-troncos", "/login", null, Map.of("dni", ana.dni(), "password", NEW_PASSWORD), 422);
    }

    @Test
    @DisplayName("El ingreso es del socio de la sesion: un memberId en el pedido se ignora")
    void checkInIgnoresAMemberIdInTheBody() {
        String anaToken = readyToken(ana);

        // Ana intenta anotar el ingreso a nombre de Beto.
        JsonNode result = post("los-troncos", "/checkin", anaToken,
                Map.of("qrToken", qr(), "memberId", beto.id().toString(), "member", beto.id().toString()), 200);

        assertThat(result.get("alreadyRegistered").asBoolean()).isFalse();
        TenantContext.runAs(club.getId(), () -> {
            LocalDate today = LocalDate.now(ZONE);
            assertThat(checkinRepository.findByMemberIdAndLocalDate(ana.id(), today)).isPresent();
            assertThat(checkinRepository.findByMemberIdAndLocalDate(beto.id(), today)).isEmpty();
        });
    }

    @Test
    @DisplayName("Sin sesion, o con una sesion inventada, el check-in da 401")
    void checkInNeedsASession() {
        client.post().uri("/api/public/los-troncos/gym/checkin")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("qrToken", qr()))
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(post("los-troncos", "/checkin", "token-inventado", Map.of("qrToken", qr()), 401)
                .get("code").asText()).isEqualTo("SESSION_EXPIRED");
    }

    @Test
    @DisplayName("La sesion de un club no sirve en otro, aunque el token sea valido")
    void aSessionDoesNotWorkInAnotherClub() {
        String token = readyToken(ana);
        Tenant other = clubFixture.club("otro-club");
        gym.enable(other);

        assertThat(get("otro-club", "/me", token, 401).get("code").asText()).isEqualTo("SESSION_EXPIRED");
    }

    @Test
    @DisplayName("El estado del socio muestra los dias de la semana y se actualiza despues de entrar")
    void statusFollowsTheCheckIn() {
        String token = readyToken(ana);

        JsonNode before = get("los-troncos", "/me", token, 200);
        assertThat(before.get("valid").asBoolean()).isTrue();
        assertThat(before.get("canEnter").asBoolean()).isTrue();
        assertThat(before.get("paidCurrent").asBoolean()).isTrue();
        assertThat(before.get("monthsLate").asInt()).isZero();
        // Su ciclo arranco el dia de la primera cuota, y el mes corriente ya esta pagado.
        assertThat(before.get("cycleStart").asText()).isEqualTo(LocalDate.now(ZONE).minusDays(1).toString());
        assertThat(before.get("periodStart").asText()).isNotBlank();
        // Sin adelantos, la cobertura llega hasta el fin del mes corriente.
        assertThat(before.get("paidUntil").asText()).isEqualTo(before.get("periodEnd").asText());
        assertThat(before.get("weekUsed").asInt()).isZero();
        assertThat(before.get("weekLimit").asInt()).isEqualTo(3);
        assertThat(before.get("checkedInToday").asBoolean()).isFalse();
        assertThat(before.get("membership").get("sedes").get(0).asText()).isEqualTo("Necochea");

        JsonNode entry = post("los-troncos", "/checkin", token, Map.of("qrToken", qr()), 200);
        assertThat(entry.get("sedeName").asText()).isEqualTo("Necochea");
        assertThat(entry.get("weekUsed").asInt()).isEqualTo(1);

        JsonNode after = get("los-troncos", "/me", token, 200);
        assertThat(after.get("weekUsed").asInt()).isEqualTo(1);
        assertThat(after.get("checkedInToday").asBoolean()).isTrue();
        assertThat(after.get("recent")).hasSize(1);
    }

    @Test
    @DisplayName("Un QR que no es de la sede da un mensaje legible y no un error de servidor")
    void anInvalidQrIsAReadableError() {
        String token = readyToken(ana);

        JsonNode error = post("los-troncos", "/checkin", token, Map.of("qrToken", UUID.randomUUID().toString()), 422);

        assertThat(error.get("code").asText()).isEqualTo("RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("código QR no es válido");
    }

    @Test
    @DisplayName("Con dos meses impagos el /me baja canEnter y el check-in se rechaza con la deuda")
    void twoUnpaidMonthsBlockAtTheApi() {
        CreatedMember carla = gym.member(club, "50333444", "Carla Díaz");
        // Pagó una sola cuota hace tres meses: queden impagas al menos la que viene y la corriente,
        // haga lo que haga el calendario.
        LocalDate threeMonthsAgo = LocalDate.now(ZONE).minusDays(95);
        TenantContext.runAs(club.getId(), () -> membershipService.charge(carla.id(), 1,
                new BigDecimal("30000"), 3, PayMethod.CASH, sede.getId(), Set.of(sede.getId()), null, threeMonthsAgo));

        String token = readyToken(carla);
        JsonNode me = get("los-troncos", "/me", token, 200);
        assertThat(me.get("canEnter").asBoolean()).isFalse();
        assertThat(me.get("monthsLate").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(me.get("valid").asBoolean()).isFalse();
        assertThat(me.get("paidUntil").isNull()).isTrue();

        JsonNode error = post("los-troncos", "/checkin", token, Map.of("qrToken", qr()), 422);
        assertThat(error.get("code").asText()).isEqualTo("RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("Adeudás");
    }

    @Test
    @DisplayName("El manifest trae el nombre del club y su URL de inicio; sin el modulo, 404")
    void manifestIsPerClub() {
        JsonNode manifest = client.get().uri("/gym/los-troncos/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(manifest.get("start_url").asText()).isEqualTo("/gym/los-troncos");
        assertThat(manifest.get("name").asText()).contains("Club los-troncos");
        assertThat(manifest.get("display").asText()).isEqualTo("standalone");

        clubFixture.club("sin-gimnasio");
        client.get().uri("/gym/sin-gimnasio/manifest.webmanifest")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("La pagina de la app y el link del QR se sirven sin login, con camara permitida y sin scripts externos")
    void theAppIsServedWithItsOwnSecurityHeaders() {
        for (String path : new String[] {"/gym/los-troncos", "/gym/los-troncos/in/" + qr()}) {
            client.get().uri(path)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Permissions-Policy", "camera=(self), geolocation=(self)")
                    .expectHeader().value("Content-Security-Policy",
                            csp -> assertThat(csp).contains("script-src 'self';").doesNotContain("google"));
        }
    }
}
