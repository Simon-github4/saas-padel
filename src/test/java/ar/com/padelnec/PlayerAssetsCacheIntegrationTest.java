package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Que archivos de la app del jugador puede guardar el navegador y cuales no.
 *
 * <p>Los dos lados importan igual: sin cache en los assets el jugador vuelve a bajar
 * el JS y las fuentes en cada visita, y con cache en el index queda pegado a la
 * version anterior despues de un deploy. Los archivos que se piden viven en
 * {@code src/test/resources/static}, porque el build de la app del jugador no corre
 * en los tests del backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class PlayerAssetsCacheIntegrationTest {

    @LocalServerPort private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("Los assets con hash se cachean un anio, sin revalidar")
    void hashedAssetsAreCachedForAYear() {
        client.get().uri("/assets/cache-test-D5mQ2pXa.js")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CACHE_CONTROL, value -> assertThat(value)
                        .contains("max-age=31536000", "public", "immutable")
                        .doesNotContain("no-store"));
    }

    @Test
    @DisplayName("Los iconos y logos de public/ se sirven sin sesion, no el login del panel")
    void iconsArePublic() {
        String[][] icons = {
                {"/favicon.ico", "image/"},
                {"/favicon.svg", "image/svg+xml"},
                {"/apple-touch-icon.png", "image/png"},
                {"/marcas/mercado-pago-blanco.svg", "image/svg+xml"},
                {"/capturas/cargar-turno.webp", "image/webp"},
                {"/icon-192.png", "image/png"},
                {"/icon-512.png", "image/png"},
                {"/icon-maskable-512.png", "image/png"},
        };
        for (String[] icon : icons) {
            client.get().uri(icon[0])
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().value(HttpHeaders.CONTENT_TYPE,
                            value -> assertThat(value).as(icon[0]).startsWith(icon[1]));
        }
    }

    @Test
    @DisplayName("El manifest y el service worker de la app instalable se sirven sin sesion")
    void installableAppFilesArePublic() {
        client.get().uri("/manifest.webmanifest")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CONTENT_TYPE,
                        value -> assertThat(value).startsWith("application/manifest+json"));
        client.get().uri("/sw.js")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CONTENT_TYPE,
                        value -> assertThat(value).contains("javascript"));
    }

    @Test
    @DisplayName("El index y las rutas de la SPA siguen sin cache")
    void indexIsNeverCached() {
        for (String path : new String[] {"/", "/buscar", "/login", "/instalar"}) {
            client.get().uri(path)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().value(HttpHeaders.CACHE_CONTROL,
                            value -> assertThat(value).contains("no-store"));
        }
    }
}
