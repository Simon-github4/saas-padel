package ar.com.padelnec.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CanonicalHostTest {

    private static Optional<String> target(String host, String base, String path, String query) {
        return CanonicalHost.redirectTarget(host, base, path, query);
    }

    @Test
    @DisplayName("Un pedido por el host de Render se manda al dominio propio, con ruta y query")
    void renderHostRedirectsToOwnDomain() {
        assertThat(target("saas-padel.onrender.com", "https://turnospadel.com.ar", "/club/simon", "fecha=2026-09-20"))
                .contains("https://turnospadel.com.ar/club/simon?fecha=2026-09-20");
        assertThat(target("SAAS-padel.onrender.com", "https://turnospadel.com.ar/", "/", null))
                .contains("https://turnospadel.com.ar/");
    }

    @Test
    @DisplayName("En el dominio propio, en localhost o sin dominio propio configurado no redirige")
    void otherHostsAreLeftAlone() {
        assertThat(target("turnospadel.com.ar", "https://turnospadel.com.ar", "/", null)).isEmpty();
        assertThat(target("localhost", "https://turnospadel.com.ar", "/", null)).isEmpty();
        assertThat(target(null, "https://turnospadel.com.ar", "/", null)).isEmpty();
        // Base publica en el propio host de Render: no hay a donde mandarlo (evita un bucle).
        // Y sin dominio propio (localhost, el default) tampoco: no se deja el sitio inaccesible.
        assertThat(target("saas-padel.onrender.com", "https://saas-padel.onrender.com", "/", null)).isEmpty();
        assertThat(target("saas-padel.onrender.com", "http://localhost:8080", "/", null)).isEmpty();
    }
}
