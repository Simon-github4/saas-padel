package ar.com.padelnec.gym.web;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.ThemeMode;
import ar.com.padelnec.service.TenantService;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import lombok.RequiredArgsConstructor;

/**
 * Sirve la app del socio ({@code gym-app}) y su manifest.
 *
 * <p>Las rutas son de una SPA: las resuelve React y el backend solo devuelve el
 * index, que vive bajo {@code /gym-app/}. Se enumeran a mano (el slug sin puntos, para
 * no capturar archivos) para no interceptar la API, el panel ni los estaticos.
 *
 * <p>El index se sirve aunque el club no tenga el modulo prendido: es la app la que
 * pregunta a la API y muestra "gimnasio no disponible" ante el 404.
 */
@Controller
@RequiredArgsConstructor
public class GymSpaController {

    private static final String SLUG = "{slug:[a-z0-9-]+}";

    private final TenantService tenantService;
    private final GymModule gymModule;

    /** El deep link del QR ({@code /in/{token}}) abre la app y la app hace el check-in. */
    @GetMapping({"/gym/" + SLUG, "/gym/" + SLUG + "/in/{token}"})
    public String app() {
        return "forward:/gym-app/index.html";
    }

    /** Service worker de la PWA: vive bajo /gym/ para poder controlar cada club. */
    @GetMapping(value = "/gym/sw.js", produces = "application/javascript")
    @ResponseBody
    public ResponseEntity<String> serviceWorker() {
        String script = """
                self.addEventListener('install', event => {
                  self.skipWaiting();
                });

                self.addEventListener('activate', event => {
                  event.waitUntil(self.clients.claim());
                });

                self.addEventListener('fetch', event => {
                  if (event.request.method !== 'GET') {
                    return;
                  }
                  event.respondWith(fetch(event.request));
                });
                """;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("Service-Worker-Allowed", "/gym/")
                .body(script);
    }

    /**
     * Manifest con el nombre del club y la URL de inicio de SU gimnasio: por eso es
     * dinamico y no un archivo estatico. Si el modulo esta apagado, 404.
     */
    @GetMapping(value = "/gym/" + SLUG + "/manifest.webmanifest", produces = "application/manifest+json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manifest(@PathVariable String slug) {
        Tenant club = tenantService.activate(slug);
        String clubName = club.getName();
        gymModule.requireEnabled();

        String shortName = clubName.length() <= 12 ? clubName : clubName.substring(0, 12).trim();
        String icon = hasText(club.getHeroImageUrl()) ? club.getHeroImageUrl() : "/gym-app/icon-512.png";
        String primary = colorOr(club.getPrimaryColor(), "#ea580c");
        String background = club.getThemeMode() == ThemeMode.LIGHT ? "#ffffff" : "#0a0a0a";
        Map<String, Object> manifest = Map.of(
                "name", clubName + " · Gimnasio",
                "short_name", shortName,
                "start_url", "/gym/" + slug,
                "scope", "/gym/",
                "display", "standalone",
                "background_color", background,
                "theme_color", primary,
                "lang", "es-AR",
                "icons", List.of(
                        Map.of("src", icon, "sizes", "192x192", "purpose", "any"),
                        Map.of("src", icon, "sizes", "512x512", "purpose", "any"),
                        Map.of("src", icon, "sizes", "512x512", "purpose", "maskable")));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/manifest+json"))
                .cacheControl(CacheControl.noCache())
                .body(manifest);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String colorOr(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
