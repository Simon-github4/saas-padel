package ar.com.padelnec.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Devuelve la app del jugador para las rutas que resuelve el navegador.
 *
 * <p>La app es una SPA: {@code /necochea-padel} o {@code /manage/abc123} no existen
 * como recursos en el servidor, los arma React. Sin este reenvio, entrar directo a
 * uno de esos links, o refrescar la pagina, devolveria 404. Y esos links son
 * precisamente los que le llegan al jugador por WhatsApp.
 */
@Controller
public class SpaForwardingController {

    private static final String INDEX = "forward:/index.html";

    /** Portada. */
    @GetMapping("/")
    public String root() {
        return INDEX;
    }

    /**
     * Busqueda global, grilla del club y flujos por token.
     *
     * <p>Se enumeran las rutas en vez de usar un comodin general para no interceptar
     * {@code /api}, {@code /admin} ni los archivos estaticos de la propia app.
     */
    @GetMapping({"/buscar", "/club/{slug}", "/manage/{token}", "/confirm/{token}", "/turno/{token}",
            "/login", "/account", "/forgot-password", "/reset-password/{token}",
            "/privacidad", "/terminos"})
    public String appRoutes() {
        return INDEX;
    }
}
