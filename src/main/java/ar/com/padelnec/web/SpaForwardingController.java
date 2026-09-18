package ar.com.padelnec.web;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.TenantRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Devuelve la app del jugador para las rutas que resuelve el navegador.
 *
 * <p>La app es una SPA: {@code /necochea-padel} o {@code /manage/abc123} no existen
 * como recursos en el servidor, los arma React. Sin este reenvio, entrar directo a
 * uno de esos links, o refrescar la pagina, devolveria 404. Y esos links son
 * precisamente los que le llegan al jugador por WhatsApp.
 *
 * <p>Las rutas publicas -- portada, buscador y club -- devuelven ese mismo index
 * con titulo, descripcion y datos de vista previa ya puestos ({@link SeoPageRenderer}).
 * Las demas se reenvian tal cual: son privadas o de un solo uso y no se indexan.
 */
@Controller
@RequiredArgsConstructor
public class SpaForwardingController {

    private static final String INDEX = "forward:/index.html";
    private static final MediaType HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    private final TenantRepository tenantRepository;
    private final SeoPageRenderer seoPageRenderer;

    /** Portada. */
    @GetMapping("/")
    public ResponseEntity<String> root() {
        return page(HttpStatus.OK, seoPageRenderer.landingMeta());
    }

    /** Busqueda global de canchas libres. */
    @GetMapping("/buscar")
    public ResponseEntity<String> search() {
        return page(HttpStatus.OK, seoPageRenderer.searchMeta());
    }

    /**
     * Pagina de un club. Si el club no existe o esta dado de baja la respuesta es
     * 404 con el mismo index: la app muestra su aviso, y un buscador no toma la
     * ruta por una pagina real (sin esto, cualquier {@code /club/loquesea} era un 200).
     */
    @GetMapping("/club/{slug}")
    public ResponseEntity<String> club(@PathVariable String slug) {
        Optional<Tenant> club = tenantRepository.findBySlugIgnoreCaseAndActiveTrue(slug);
        return club
                .map(found -> page(HttpStatus.OK, seoPageRenderer.clubMeta(found)))
                .orElseGet(() -> page(HttpStatus.NOT_FOUND, seoPageRenderer.unknownClubMeta()));
    }

    /**
     * Flujos por token, cuenta y legales.
     *
     * <p>Se enumeran las rutas en vez de usar un comodin general para no interceptar
     * {@code /api}, {@code /admin} ni los archivos estaticos de la propia app.
     */
    @GetMapping({"/manage/{token}", "/confirm/{token}", "/turno/{token}",
            "/login", "/account", "/forgot-password", "/reset-password/{token}",
            "/privacidad", "/terminos"})
    public String appRoutes() {
        return INDEX;
    }

    private ResponseEntity<String> page(HttpStatus status, SeoPageRenderer.PageMeta meta) {
        return seoPageRenderer.render(meta)
                .map(html -> ResponseEntity.status(status)
                        .contentType(HTML_UTF8)
                        .body(html))
                // Sin el build de la app en el classpath no hay nada que servir: lo mismo
                // que daba el reenvio a /index.html.
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
