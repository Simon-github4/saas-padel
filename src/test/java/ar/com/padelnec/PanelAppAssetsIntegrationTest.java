package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.TenantStoryTemplate;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.TenantStoryTemplateRepository;
import ar.com.padelnec.web.PanelStoryController;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.time.Instant;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * La app instalable del club, separada de la del jugador, y lo que baja la historia
 * de turnos libres.
 *
 * <p>Lo de la app se pide sin sesion (el navegador ofrece instalarla desde el login);
 * lo de la historia es del club de quien la pide y no se entrega sin sesion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PanelAppAssetsIntegrationTest {

    private static final String PASSWORD = "unaClaveLarga123";
    /** Los primeros bytes de un JPG: alcanza para reconocer que se entrego la plantilla. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private ClubUserRepository clubUserRepository;
    @Autowired private TenantStoryTemplateRepository templates;
    @Autowired private PasswordEncoder passwordEncoder;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("El manifest del club usa sus propios iconos, no los de la app del jugador")
    void manifestUsesClubIcons() {
        String manifest = client.get().uri("/panel.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();

        assertThat(manifest)
                .contains("\"short_name\":\"TP Club\"", "\"scope\":\"/admin/\"",
                        "/panel/icons/icon-192.png", "/panel/icons/icon-maskable-512.png")
                .doesNotContain("\"src\":\"/icon-192.png\"");
    }

    @Test
    @DisplayName("Los iconos del club se sirven sin sesion, y solo los que existen")
    void clubIconsArePublic() {
        for (String icon : new String[] {"icon-192.png", "icon-512.png", "icon-maskable-512.png",
                "apple-touch-icon.png", "favicon-32.png"}) {
            client.get().uri("/panel/icons/" + icon)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().value(HttpHeaders.CONTENT_TYPE,
                            value -> assertThat(value).as(icon).isEqualTo("image/png"));
        }
        client.get().uri("/panel/icons/otro.png").exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("La plantilla y el dibujo de la historia no se entregan sin sesion del panel")
    void storyAssetsNeedThePanelSession() {
        for (String path : new String[] {PanelStoryController.templateUrl(1), PanelStoryController.SCRIPT_URL}) {
            EntityExchangeResult<byte[]> result = client.get().uri(path).exchange().returnResult(byte[].class);
            String type = result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            // O no contesta, o contesta con el login del panel: nunca la imagen ni el script.
            assertThat(result.getStatus().value() != 200 || (type != null && type.startsWith("text/html")))
                    .as(path + " -> " + result.getStatus() + " " + type).isTrue();
        }
    }

    @Test
    @DisplayName("Con la sesion del panel: la plantilla del club propio, el dibujo y el logo, con cache larga")
    void storyAssetsWithSession() {
        fixture.reset();
        Tenant club = fixture.club("club-necochea");
        Tenant other = fixture.club("otro-club");
        TenantStoryTemplate template = new TenantStoryTemplate();
        template.setTenantId(club.getId());
        template.setData(JPEG);
        template.setContentType("image/jpeg");
        template.setUpdatedAt(Instant.now());
        templates.saveAndFlush(template);

        RestTestClient panel = login(club);
        byte[] served = panel.get().uri(PanelStoryController.templateUrl(1))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("image/jpeg")
                .expectHeader().value(HttpHeaders.CACHE_CONTROL,
                        value -> assertThat(value).contains("max-age=31536000", "private", "immutable"))
                .returnResult(byte[].class).getResponseBody();
        assertThat(served).isEqualTo(JPEG);

        for (String path : new String[] {PanelStoryController.SCRIPT_URL, PanelStoryController.LOGO_URL}) {
            panel.get().uri(path)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().value(HttpHeaders.CACHE_CONTROL,
                            value -> assertThat(value).as(path).contains("max-age=31536000", "immutable"));
        }

        // Otro club, sin plantilla propia: no recibe la del primero.
        login(other).get().uri(PanelStoryController.templateUrl(1))
                .exchange()
                .expectStatus().isNotFound();
    }

    /** Entra por el formulario del panel con un cliente que guarda la sesion, como un navegador. */
    private RestTestClient login(Tenant club) {
        ClubUser owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setEmail("dueno-%s@test.com".formatted(UUID.randomUUID()));
        owner.setFullName("Dueño " + UUID.randomUUID());
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash(passwordEncoder.encode(PASSWORD));
        clubUserRepository.saveAndFlush(owner);

        HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        RestTestClient panel = RestTestClient.bindToServer(new JdkClientHttpRequestFactory(browser))
                .baseUrl("http://localhost:" + port).build();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", owner.getEmail());
        form.add("password", PASSWORD);
        panel.post().uri("/admin/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange();
        return panel;
    }
}
