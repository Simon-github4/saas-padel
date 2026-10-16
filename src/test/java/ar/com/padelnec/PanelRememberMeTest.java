package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.security.PanelRememberMeServices;
import ar.com.padelnec.service.ClubUserService;
import ar.com.padelnec.service.PanelRememberService;
import ar.com.padelnec.support.TokenHash;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

/**
 * El "recordarme" del panel: despues de entrar con la clave, el dispositivo vuelve
 * a entrar solo durante 15 dias, y deja de hacerlo apenas sale, vence, le cambian
 * la clave o lo deshabilitan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, PanelRememberMeTest.FixedClock.class})
class PanelRememberMeTest {

    private static final String PASSWORD = "unaClaveLarga123";
    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW, ZoneId.of("UTC"));
        }
    }

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private ClubUserService clubUserService;
    @Autowired private PanelRememberMeServices rememberMe;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private Tenant club;
    private ClubUser staff;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        fixture.reset();
        club = fixture.club("club-necochea");
        String suffix = UUID.randomUUID().toString();
        staff = clubUserService.createStaff(club, "Ana " + suffix, "ana-%s@test.com".formatted(suffix), PASSWORD);
    }

    @Test
    @DisplayName("Entrar con la clave deja una cookie de 15 días y en la base solo su huella")
    void formLoginRemembersTheDevice() throws Exception {
        HttpResponse<String> login = formLogin(staff.getEmail(), PASSWORD);

        String setCookie = rememberCookieHeader(login);
        assertThat(setCookie)
                .contains("Max-Age=" + PanelRememberService.VALIDITY.toSeconds())
                .containsIgnoringCase("HttpOnly")
                .contains("SameSite=Lax");

        String token = decode(cookieValue(setCookie));
        String storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM panel_remember_token WHERE user_id = ?", String.class, staff.getId());
        assertThat(storedHash).isEqualTo(TokenHash.of(token)).isNotEqualTo(token);

        OffsetDateTime expiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM panel_remember_token WHERE user_id = ?", OffsetDateTime.class, staff.getId());
        assertThat(expiresAt.toInstant()).isEqualTo(NOW.plus(PanelRememberService.VALIDITY));
    }

    @Test
    @DisplayName("Con la cookie sola, sin sesión, se entra al panel sin pasar por el login")
    void cookieAloneGetsIn() throws Exception {
        String cookie = cookieValue(rememberCookieHeader(formLogin(staff.getEmail(), PASSWORD)));

        HttpResponse<String> anonymous = http.send(get("/admin/").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(anonymous.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).endsWith("/admin/login"));

        HttpResponse<String> remembered = http.send(
                get("/admin/").header("Cookie", PanelRememberMeServices.COOKIE_NAME + "=" + cookie).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(remembered.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("\"Salir\" olvida el dispositivo: la misma cookie ya no entra")
    void logoutForgetsTheDevice() {
        Cookie cookie = remember(staff);
        assertThat(autoLogin(cookie)).isEqualTo(staff.getId());

        MockHttpServletResponse response = new MockHttpServletResponse();
        rememberMe.logout(requestWith(cookie), response, null);

        assertThat(response.getCookie(PanelRememberMeServices.COOKIE_NAME).getMaxAge()).isZero();
        assertThat(rememberedDevices(staff)).isZero();
        assertThat(autoLogin(cookie)).isNull();
    }

    @Test
    @DisplayName("A los 15 días pide la clave aunque se haya usado todos los días")
    void expiresFifteenDaysAfterThePasswordLogin() {
        Cookie cookie = remember(staff);

        clock.advance(PanelRememberService.VALIDITY.minus(Duration.ofMinutes(1)));
        assertThat(autoLogin(cookie)).isEqualTo(staff.getId());

        clock.advance(Duration.ofMinutes(1));
        assertThat(autoLogin(cookie)).isNull();
        assertThat(rememberedDevices(staff)).isZero();
    }

    @Test
    @DisplayName("Cambiar la clave saca a todos los dispositivos recordados")
    void passwordChangeForgetsEveryDevice() {
        Cookie phone = remember(staff);
        Cookie desk = remember(staff);

        clubUserService.setPassword(club, staff.getId(), "otraClaveLarga456");

        assertThat(autoLogin(phone)).isNull();
        assertThat(autoLogin(desk)).isNull();
    }

    @Test
    @DisplayName("Deshabilitar al usuario lo saca, y volver a habilitarlo no devuelve los dispositivos")
    void disablingForgetsEveryDevice() {
        Cookie phone = remember(staff);

        clubUserService.setEnabled(club, staff.getId(), false);
        assertThat(autoLogin(phone)).isNull();

        clubUserService.setEnabled(club, staff.getId(), true);
        assertThat(autoLogin(phone)).isNull();
    }

    @Test
    @DisplayName("Volver a entrar con la clave en el mismo navegador reemplaza el token anterior")
    void loggingInAgainReplacesThePreviousToken() {
        Cookie first = remember(staff);

        MockHttpServletResponse response = new MockHttpServletResponse();
        rememberMe.loginSuccess(requestWith(first), response, authenticationOf(staff));

        assertThat(rememberedDevices(staff)).isEqualTo(1);
        assertThat(autoLogin(first)).isNull();
        assertThat(autoLogin(response.getCookie(PanelRememberMeServices.COOKIE_NAME))).isEqualTo(staff.getId());
    }

    // ------------------------------------------------------------------ ayudas

    private HttpResponse<String> formLogin(String username, String password) throws Exception {
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        return http.send(HttpRequest.newBuilder(uri("/admin/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String rememberCookieHeader(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(PanelRememberMeServices.COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El login no dejó la cookie de recordar: "
                        + response.headers().allValues("Set-Cookie")));
    }

    private static String cookieValue(String setCookieHeader) {
        String pair = setCookieHeader.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }

    /** Lo mismo que {@code AbstractRememberMeServices.decodeCookie}, para un solo token. */
    private static String decode(String cookieValue) {
        String padded = cookieValue + "=".repeat((4 - cookieValue.length() % 4) % 4);
        String plain = new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8);
        return URLDecoder.decode(plain, StandardCharsets.UTF_8);
    }

    private Cookie remember(ClubUser user) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        rememberMe.loginSuccess(new MockHttpServletRequest(), response, authenticationOf(user));
        return response.getCookie(PanelRememberMeServices.COOKIE_NAME);
    }

    /** El usuario con el que entra la cookie, o nulo si ya no entra. */
    private UUID autoLogin(Cookie cookie) {
        Authentication authentication = rememberMe.autoLogin(requestWith(cookie), new MockHttpServletResponse());
        return authentication == null ? null : ((ClubUserPrincipal) authentication.getPrincipal()).userId();
    }

    private static MockHttpServletRequest requestWith(Cookie cookie) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(cookie.getName(), cookie.getValue()));
        return request;
    }

    private static Authentication authenticationOf(ClubUser user) {
        ClubUserPrincipal principal = ClubUserPrincipal.of(user);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private int rememberedDevices(ClubUser user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM panel_remember_token WHERE user_id = ?", Integer.class, user.getId());
    }
}
