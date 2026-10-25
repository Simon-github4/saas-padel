package ar.com.padelnec.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeoControllerTest {

    private static String robots(boolean indexable) {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://saas-padel-staging.onrender.com");
        properties.getSeo().setIndexable(indexable);
        return new SeoController(mock(TenantRepository.class), properties, mock(SeoPageRenderer.class)).robots();
    }

    @Test
    @DisplayName("En staging robots.txt cierra todo y no ofrece el sitemap")
    void stagingClosesEverything() {
        assertThat(robots(false))
                .contains("Disallow: /\n")
                .doesNotContain("Allow:")
                .doesNotContain("Sitemap:");
    }

    @Test
    @DisplayName("Por defecto deja recorrer el sitio y apunta al sitemap de la base publica")
    void indexableByDefault() {
        assertThat(new AppProperties().getSeo().isIndexable()).isTrue();
        assertThat(robots(true))
                .contains("Allow: /\n")
                .contains("Sitemap: https://saas-padel-staging.onrender.com/sitemap.xml");
    }
}
