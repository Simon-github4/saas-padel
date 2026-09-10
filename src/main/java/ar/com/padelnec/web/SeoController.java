package ar.com.padelnec.web;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que necesita un buscador para encontrar la app: por que rutas puede
 * entrar y cuales son publicas.
 *
 * <p>La app del jugador es una SPA sin server-side rendering: sin un mapa
 * explicito, un buscador tiene que descubrir cada club por su cuenta (un link
 * externo, por ejemplo), y eso puede tardar mucho o no pasar nunca.
 */
@RestController
@RequiredArgsConstructor
public class SeoController {

    private final TenantRepository tenantRepository;
    private final AppProperties properties;

    /**
     * Bloquea lo que no es contenido publico: portales por token (nadie mas
     * tiene que ver esos links en un resultado de busqueda), cuenta y login.
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
                User-agent: *
                Allow: /
                Disallow: /manage/
                Disallow: /turno/
                Disallow: /confirm/
                Disallow: /account
                Disallow: /login
                Disallow: /forgot-password
                Disallow: /reset-password/
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(properties.getBaseUrl());
    }

    /** Un link por club activo, para que no dependan de que alguien los enlace primero. */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        String base = properties.getBaseUrl();
        List<Tenant> clubs = tenantRepository.findAllByActiveTrue();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, base + "/");
        appendUrl(xml, base + "/buscar");
        for (Tenant club : clubs) {
            appendUrl(xml, base + "/club/" + club.getSlug());
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void appendUrl(StringBuilder xml, String loc) {
        xml.append("  <url><loc>").append(loc).append("</loc></url>\n");
    }
}
