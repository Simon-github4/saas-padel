package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.GoogleMapsLinkResolver;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que el dueño del club realmente tiene para pegar: el link de "Compartir"
 * de Google Maps, no un par de decimales que nadie memoriza.
 */
class GoogleMapsLinkResolverTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Lee las coordenadas del codigo de \"Insertar un mapa\", sin salir a la red")
    void readsCoordinatesFromTheEmbedSnippet() {
        String snippet = "<iframe src=\"https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3120.41"
                + "!2d-58.7639738!3d-38.5473366!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2"
                + "!1s0x958fbdca6b2fe4cf%3A0x5f37c8b10e063905!2sNucleo%20p%C3%A1del!5e0!3m2!1ses!2sar"
                + "!4v1789044997563!5m2!1ses!2sar\" width=\"600\" height=\"450\" style=\"border:0;\" "
                + "allowfullscreen=\"\" loading=\"lazy\" referrerpolicy=\"strict-origin-when-cross-origin\">"
                + "</iframe>";

        Optional<GoogleMapsLinkResolver.Coordinates> found = new GoogleMapsLinkResolver().resolve(snippet);

        assertThat(found).isPresent();
        assertThat(found.get().latitude()).isEqualByComparingTo("-38.547337");
        assertThat(found.get().longitude()).isEqualByComparingTo("-58.763974");
    }

    @Test
    @DisplayName("Lee las coordenadas de una URL de lugar ya completa, del pin y no del centro del mapa")
    void readsCoordinatesFromAFullPlaceUrl() {
        String url = "https://www.google.com/maps/place/Nucleo+p%C3%A1del/@-38.5480000,-58.7650000,17z/"
                + "data=!3m1!4b1!4m6!3m5!1s0x958fbdca6b2fe4cf!8m2!3d-38.5473366!4d-58.7639738";

        Optional<GoogleMapsLinkResolver.Coordinates> found = new GoogleMapsLinkResolver().resolve(url);

        // El pin (!3d..!4d..) es mas preciso que el centro de lo que se ve en
        // pantalla (@lat,lng): tienen que ganar los del pin.
        assertThat(found).isPresent();
        assertThat(found.get().latitude()).isEqualByComparingTo("-38.547337");
        assertThat(found.get().longitude()).isEqualByComparingTo("-58.763974");
    }

    @Test
    @DisplayName("Sin pin, usa el centro del mapa como ultimo recurso")
    void fallsBackToTheMapCenterWhenThereIsNoPin() {
        Optional<GoogleMapsLinkResolver.Coordinates> found = new GoogleMapsLinkResolver()
                .resolve("https://www.google.com/maps/@-38.547337,-58.763974,15z");

        assertThat(found).isPresent();
        assertThat(found.get().latitude()).isEqualByComparingTo("-38.547337");
        assertThat(found.get().longitude()).isEqualByComparingTo("-58.763974");
    }

    @Test
    @DisplayName("Un link corto sigue la redireccion y lee las coordenadas del destino")
    void followsAShortLinkToItsDestination() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/short", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://localhost:" + server.getAddress().getPort()
                            + "/maps/place/Nucleo/@-38.5,-58.7,17z/data=!3d-38.5473366!4d-58.7639738");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        GoogleMapsLinkResolver resolver = new GoogleMapsLinkResolver(Set.of("localhost"));
        String shortLink = "http://localhost:" + server.getAddress().getPort() + "/short";

        Optional<GoogleMapsLinkResolver.Coordinates> found = resolver.resolve(shortLink);

        assertThat(found).isPresent();
        assertThat(found.get().latitude()).isEqualByComparingTo("-38.547337");
        assertThat(found.get().longitude()).isEqualByComparingTo("-58.763974");
    }

    @Test
    @DisplayName("Una cadena de redirecciones mas larga que el limite se abandona, no se cuelga")
    void givesUpAfterTooManyRedirects() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://localhost:" + server.getAddress().getPort() + "/loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        GoogleMapsLinkResolver resolver = new GoogleMapsLinkResolver(Set.of("localhost"));

        Optional<GoogleMapsLinkResolver.Coordinates> found = resolver
                .resolve("http://localhost:" + server.getAddress().getPort() + "/loop");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("No sale a la red por un link que no es de Google")
    void neverCallsOutForAnUntrustedHost() {
        // "localhost" no esta en la lista de hosts confiables del resolver
        // real -solo goo.gl y maps.app.goo.gl-, asi que esto tiene que volver
        // vacio sin intentar conectarse a nada.
        Optional<GoogleMapsLinkResolver.Coordinates> found = new GoogleMapsLinkResolver()
                .resolve("http://localhost:1/en-que-club-jugamos");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Texto que no es un link no revienta")
    void handlesGarbageWithoutThrowing() {
        assertThat(new GoogleMapsLinkResolver().resolve(null)).isEmpty();
        assertThat(new GoogleMapsLinkResolver().resolve("")).isEmpty();
        assertThat(new GoogleMapsLinkResolver().resolve("necochea, buenos aires")).isEmpty();
    }

    @Test
    @DisplayName("Un numero fuera de rango de latitud/longitud se descarta")
    void rejectsOutOfRangeNumbers() {
        // 200 no es una latitud posible: tiene que fallar en vez de guardar
        // cualquier cosa.
        Optional<GoogleMapsLinkResolver.Coordinates> found = new GoogleMapsLinkResolver()
                .resolve("https://www.google.com/maps/@200.0,-58.7639738,15z");

        assertThat(found).isEmpty();
    }
}
