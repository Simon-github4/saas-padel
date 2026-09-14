package ar.com.padelnec.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cache larga para los archivos que arma Vite para la app del jugador.
 *
 * <p>Vite pone un hash del contenido en el nombre de todo lo que deja en
 * {@code assets/} ({@code index-DkN3WvEd.js}): si el archivo cambia, cambia la URL.
 * Lo que hay detras de una URL de ahi no varia nunca, asi que el navegador puede
 * guardarlo un anio sin volver a preguntar. Sin esto, Spring Security le pone
 * {@code no-store} a todo y el jugador vuelve a bajar el JS, el CSS y las fuentes
 * en cada visita. Spring Security no pisa un {@code Cache-Control} que ya puso la
 * aplicacion.
 *
 * <p>Solo {@code /assets/**}. El {@code index.html}, que tambien devuelven
 * {@code /club/...} y el resto de las rutas de la SPA, sigue sin cache: es el que
 * apunta a los nombres nuevos despues de un deploy, y cachearlo dejaria al jugador
 * pegado a la version anterior.
 */
@Configuration
public class PlayerAssetsCacheConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
