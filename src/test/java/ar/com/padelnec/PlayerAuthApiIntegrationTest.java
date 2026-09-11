package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.EmailSender;
import ar.com.padelnec.repository.PendingPlayerSignupRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.security.GoogleIdTokenVerifier;
import ar.com.padelnec.web.api.BookingRateLimiter;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * El login del jugador sobre HTTP real, con el foco puesto en lo que lo justifica:
 * el historial tiene que cruzar clubes, algo que ninguna consulta JPA normal puede
 * hacer en este sistema (ver comentario de {@code findHistoryByPhone}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, PlayerAuthApiIntegrationTest.FakesConfig.class})
class PlayerAuthApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String EMAIL = "juana@example.com";
    private static final String PASSWORD = "unaClaveLarga123";

    @TestConfiguration(proxyBeanMethods = false)
    static class FakesConfig {
        @Bean
        @Primary
        GoogleIdTokenVerifier fakeGoogleIdTokenVerifier() {
            return new FakeGoogleIdTokenVerifier();
        }

        @Bean
        @Primary
        EmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static class FakeGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
        Optional<GoogleIdentity> nextResult = Optional.empty();

        @Override
        public Optional<GoogleIdentity> verify(String idToken) {
            return nextResult;
        }
    }

    /** Captura el ultimo email mandado, para sacar el codigo de 6 digitos como lo haria el jugador. */
    static class CapturingEmailSender implements EmailSender {
        String lastBody;

        @Override
        public String providerName() {
            return "capturing";
        }

        @Override
        public SendResult send(String toAddress, String subject, String plainBody) {
            lastBody = plainBody;
            return SendResult.ok();
        }
    }

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private PlayerAccountRepository playerAccountRepository;
    @Autowired private PendingPlayerSignupRepository pendingPlayerSignupRepository;
    @Autowired private FakeGoogleIdTokenVerifier googleVerifier;
    @Autowired private CapturingEmailSender emailSender;
    // Este archivo llama a /register en casi todos los tests, desde el mismo
    // remoteAddr de loopback: sin mockear, el limite compartido de 10 intentos
    // (BookingRateLimiter.java:29) revienta un test mas adelante, no el que de
    // verdad se pasa -- un falso negativo que depende del orden de ejecucion.
    @MockitoBean private BookingRateLimiter rateLimiter;

    private RestTestClient client;
    private LocalDate matchDay;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        fixture.reset();
        matchDay = LocalDate.now(ZONE).plusDays(2);
        googleVerifier.nextResult = Optional.empty();
        emailSender.lastBody = null;
    }

    @Test
    @DisplayName("El historial del jugador junta turnos de todos los clubes en los que reservo")
    void historySpansMultipleClubs() {
        String phone = "2262415000";
        JsonNode session = registerAndConfirm(EMAIL, PASSWORD);
        String token = session.get("token").asText();

        bookAt("club-a", "Turno en A", phone, token);
        bookAt("club-b", "Turno en B", phone, token);

        JsonNode history = historyOf(token);

        assertThat(history).hasSize(2);
        java.util.List<String> clubNames = java.util.List.of(
                history.get(0).get("clubName").asText(),
                history.get(1).get("clubName").asText());
        assertThat(clubNames).containsExactlyInAnyOrder("Club club-a", "Club club-b");
    }

    @Test
    @DisplayName("Poner el telefono de otro en la propia cuenta no muestra los turnos de esa persona")
    void anotherPersonsPhoneShowsNothing() {
        // Antes esto devolvia los dos turnos: el historial emparejaba telefonos y
        // nadie verifica el telefono que se carga en una cuenta. Con eso venia
        // ademas el management_token de cada turno, que es lo que permite
        // cancelarlo. Alcanzaba con saber el numero de alguien.
        String victimPhone = "2262415000";
        bookAt("club-a", "Turno de la victima", victimPhone);
        bookAt("club-b", "Otro turno de la victima", victimPhone);

        JsonNode session = registerAndConfirm(EMAIL, PASSWORD);
        String token = session.get("token").asText();

        client.put().uri("/api/public/player/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Atacante", "phoneNumber", victimPhone))
                .exchange()
                .expectStatus().isOk();

        assertThat(historyOf(token)).isEmpty();
    }

    private JsonNode historyOf(String token) {
        return client.get().uri("/api/public/player/bookings")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    @Test
    @DisplayName("Sin sesion, el historial devuelve 401")
    void bookingsRequireASession() {
        client.get().uri("/api/public/player/bookings")
                .header("Authorization", "Bearer token-inventado")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Registrarse no crea la cuenta todavia; confirmar el codigo si, y despues se puede loguear")
    void registerThenLoginRoundTrip() {
        registerAndConfirm(EMAIL, PASSWORD);

        JsonNode session = client.post().uri("/api/public/player/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(session.get("email").asText()).isEqualTo(EMAIL);
        assertThat(session.get("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Registrar con un email que ya tiene una cuenta confirmada da 422")
    void duplicateRegistrationIsRejected() {
        registerAndConfirm(EMAIL, PASSWORD);

        client.post().uri("/api/public/player/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL, "password", "otraClave123"))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    @DisplayName("Un codigo incorrecto no crea la cuenta")
    void wrongConfirmationCodeIsRejected() {
        register(EMAIL, PASSWORD);

        client.post().uri("/api/public/player/register/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL, "code", "000000"))
                .exchange()
                .expectStatus().isEqualTo(422);

        assertThat(playerAccountRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("Login con Google, con el verificador de prueba, abre sesion")
    void googleLoginOverHttp() {
        googleVerifier.nextResult = Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("sub-1", EMAIL, true, "Juana"));

        JsonNode session = client.post().uri("/api/public/player/login/google")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("idToken", "cualquiera"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(session.get("email").asText()).isEqualTo(EMAIL);
        assertThat(session.get("emailVerified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Reset de contrasena de punta a punta, sobre HTTP")
    void passwordResetOverHttp() {
        registerAndConfirm(EMAIL, PASSWORD);

        client.post().uri("/api/public/player/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL))
                .exchange()
                .expectStatus().isEqualTo(202);

        PlayerAccount account = playerAccountRepository.findByEmail(EMAIL).orElseThrow();
        String token = account.getPasswordResetToken();
        assertThat(token).isNotBlank();

        client.post().uri("/api/public/player/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token, "newPassword", "unaClaveNueva456"))
                .exchange()
                .expectStatus().isOk();

        client.post().uri("/api/public/player/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL, "password", PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(422);

        client.post().uri("/api/public/player/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL, "password", "unaClaveNueva456"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("El link de confirmacion crea la cuenta; un token invalido no rompe, solo avisa")
    void verifyEmailOverHttp() {
        register(EMAIL, PASSWORD);
        String token = pendingPlayerSignupRepository.findByEmail(EMAIL).orElseThrow().getConfirmToken();

        client.get().uri("/api/public/player/verify-email?token=" + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);

        assertThat(playerAccountRepository.findByEmail(EMAIL).orElseThrow().isEmailVerified()).isTrue();

        client.get().uri("/api/public/player/verify-email?token=token-inventado")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    @DisplayName("El perfil (nombre y telefono) viaja con la cuenta: se guarda y /me lo devuelve")
    void profileFollowsTheAccount() {
        JsonNode session = registerAndConfirm(EMAIL, PASSWORD);
        String token = session.get("token").asText();
        assertThat(session.get("displayName").isNull()).isTrue();

        client.put().uri("/api/public/player/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "  Juana Pérez  ", "phoneNumber", "2262415000"))
                .exchange()
                .expectStatus().isOk();

        JsonNode me = client.get().uri("/api/public/player/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
        assertThat(me.get("displayName").asText()).isEqualTo("Juana Pérez");
        // El teléfono viaja normalizado a E.164 (con prefijo), como se guarda en la cuenta.
        assertThat(me.get("phoneNumber").asText()).endsWith("2262415000");
    }

    // ------------------------------------------------------------ utilidades

    /** Solo arranca el alta: manda el codigo/link, todavia no crea la cuenta. */
    private void register(String email, String password) {
        client.post().uri("/api/public/player/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isEqualTo(202);
    }

    /** Registra y confirma con el codigo de una, como haria el jugador en la pantalla nueva. */
    private JsonNode registerAndConfirm(String email, String password) {
        register(email, password);
        Matcher matcher = Pattern.compile("\\d{6}").matcher(emailSender.lastBody);
        assertThat(matcher.find()).isTrue();

        return client.post().uri("/api/public/player/register/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "code", matcher.group()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    /** Reserva de invitado: sin sesion, como reserva la mayoria. */
    private void bookAt(String slug, String label, String phone) {
        bookAt(slug, label, phone, null);
    }

    /** Con {@code token}, la reserva queda atada a esa cuenta y entra al historial. */
    private void bookAt(String slug, String label, String phone, String token) {
        Tenant club = fixture.club(slug);
        TenantContext.set(club.getId());
        fixture.court("Cancha 1", 1);
        fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");
        TenantContext.clear();

        JsonNode slot = firstFreeSlot(slug);
        var request = client.post().uri("/api/public/" + slug + "/bookings")
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        request.body(Map.of(
                        "courtId", slot.get("available").get(0).get("courtId").asText(),
                        "startTime", Instant.parse(slot.get("startsAt").asText()).toString(),
                        "fullName", label,
                        "phoneNumber", phone,
                        "paymentChoice", "PAY_AT_CLUB"))
                .exchange()
                .expectStatus().isCreated();
    }

    private JsonNode firstFreeSlot(String slug) {
        JsonNode grid = client.get().uri("/api/public/" + slug + "/availability?date=" + matchDay)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
        for (JsonNode slot : grid.get("slots")) {
            if (!slot.get("available").isEmpty()) {
                return slot;
            }
        }
        throw new AssertionError("No hay ningun turno libre en " + slug);
    }
}
