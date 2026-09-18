package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Lo que ve un buscador, o WhatsApp al armar la vista previa de un link, en las
 * rutas publicas de la app del jugador: nada de esto depende de que corra el JS.
 *
 * <p>El index de base es el fixture de {@code src/test/resources/static}, porque el
 * build de la app del jugador no corre en los tests del backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class SeoPagesIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private TenantRepository tenantRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        fixture.reset();
    }

    private String body(String path) {
        return client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    @DisplayName("La pagina de un club sale con su titulo, vista previa, canonical y datos estructurados")
    void clubPageCarriesItsOwnMetadata() {
        Tenant club = fixture.club("necochea-padel");
        club.setName("Necochea & Padel");
        club.setCity("Necochea");
        club.setTagline("Canchas de cristal");
        club.setHeroImageUrl("/api/public/necochea-padel/hero-image");
        tenantRepository.saveAndFlush(club);

        String html = body("/club/necochea-padel");

        assertThat(html)
                .contains("<title>Necochea &amp; Padel — Reservá tu cancha de pádel</title>")
                .contains("<link rel=\"canonical\" href=\"http://localhost:8080/club/necochea-padel\" />")
                .contains("<meta property=\"og:title\" content=\"Necochea &amp; Padel — Reservá tu cancha de pádel\" />")
                .contains("<meta property=\"og:image\" content=\"http://localhost:8080/api/public/necochea-padel/hero-image\" />")
                .contains("<meta name=\"twitter:card\" content=\"summary_large_image\" />")
                .contains("Canchas de cristal. Reservá tu cancha de pádel online en Necochea con Necochea &amp; Padel")
                .contains("<script type=\"application/ld+json\" data-server-seo>")
                .contains("\"@type\":\"SportsClub\"")
                .doesNotContain("<title>test</title>");
        assertThat(html).containsOnlyOnce("<title>").containsOnlyOnce("name=\"description\"");
    }

    @Test
    @DisplayName("Un dato con </script> no puede cerrar el bloque de datos estructurados")
    void structuredDataCannotBreakOutOfItsScriptTag() {
        Tenant club = fixture.club("hostil");
        club.setTagline("</script><script>alert(1)</script>");
        tenantRepository.saveAndFlush(club);

        String html = body("/club/hostil");

        assertThat(html).doesNotContain("<script>alert(1)");
        assertThat(html).containsOnlyOnce("application/ld+json");
    }

    @Test
    @DisplayName("El slug se resuelve sin importar mayusculas, y el canonical usa el del club")
    void slugIsCaseInsensitive() {
        fixture.club("necochea-padel");

        assertThat(body("/club/NECOCHEA-Padel"))
                .contains("href=\"http://localhost:8080/club/necochea-padel\"");
    }

    @Test
    @DisplayName("Un club que no existe responde 404 y no se indexa, pero devuelve la app")
    void unknownClubIs404AndNoindex() {
        client.get().uri("/club/no-existe")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .value(html -> assertThat(html)
                        .contains("<meta name=\"robots\" content=\"noindex\" />")
                        .doesNotContain("rel=\"canonical\"")
                        .contains("</html>"));
    }

    @Test
    @DisplayName("Un club dado de baja responde igual que uno que no existe")
    void inactiveClubIs404() {
        Tenant club = fixture.club("cerrado");
        club.setActive(false);
        tenantRepository.saveAndFlush(club);

        client.get().uri("/club/cerrado").exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("La portada y el buscador tienen su propio titulo y canonical")
    void landingAndSearchHaveTheirOwnMetadata() {
        assertThat(body("/"))
                .contains("<title>Reservas online para tu club — TurnosPadel</title>")
                .contains("<link rel=\"canonical\" href=\"http://localhost:8080/\" />")
                .contains("\"@type\":\"SoftwareApplication\"");
        assertThat(body("/buscar"))
                .contains("<title>Buscar cancha de pádel — todos los clubes</title>")
                .contains("<link rel=\"canonical\" href=\"http://localhost:8080/buscar\" />");
    }

    @Test
    @DisplayName("El sitemap trae lastmod en los clubes, y robots.txt libera la foto de portada")
    void sitemapAndRobots() {
        fixture.club("necochea-padel");

        client.get().uri("/sitemap.xml").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(xml -> assertThat(xml)
                        .containsPattern("<loc>http://localhost:8080/club/necochea-padel</loc><lastmod>\\d{4}-\\d{2}-\\d{2}T[^<]+Z</lastmod>")
                        .contains("<url><loc>http://localhost:8080/buscar</loc></url>"));
        client.get().uri("/robots.txt").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(txt -> assertThat(txt)
                        .contains("Disallow: /api/")
                        .contains("Allow: /api/public/*/hero-image"));
    }
}
