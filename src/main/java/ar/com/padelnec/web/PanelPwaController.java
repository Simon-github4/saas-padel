package ar.com.padelnec.web;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * El panel del club como app instalable ("TurnosPadel Club") y su service worker.
 *
 * <p>Viven en la raiz y no bajo {@code /admin/} porque todo lo de {@code /admin/*}
 * lo toma el servlet de Vaadin. El service worker igual se registra con alcance
 * {@code /admin/}: {@code Service-Worker-Allowed} lo deja ahi y no mas arriba, donde
 * chocaria con el de la app del jugador ({@code /sw.js}, alcance {@code /}).
 *
 * <p>No se usa el {@code @PWA} de Vaadin: arma un service worker de Workbox que
 * guarda la app en cache, y el proyecto decidio no cachear (un deploy tiene que
 * llegar al instante). Este solo muestra los avisos de reservas nuevas.
 */
@RestController
public class PanelPwaController {

    /** El ladrillo de marca del panel ({@code --lumo-primary-color} del tema). */
    private static final String THEME_COLOR = "#c2410c";

    /**
     * Sin handler de {@code fetch}: no intercepta ningun pedido del panel, asi que
     * el panel funciona igual que sin service worker.
     *
     * <p>El click lleva a la agenda en el dia del turno, marcado ({@code ?reserva=}): si
     * el panel ya estaba abierto lo enfoca y lo lleva ahi, y si no, lo abre. Si ese panel
     * no es de este service worker ({@code navigate} falla), queda enfocado donde estaba.
     */
    private static final String SERVICE_WORKER = """
            /* Service worker del panel del club: solo avisos de reservas nuevas, sin cache. */

            self.addEventListener('install', () => {
              self.skipWaiting();
            });

            self.addEventListener('activate', (event) => {
              event.waitUntil(self.clients.claim());
            });

            self.addEventListener('push', (event) => {
              let data = {};
              try {
                data = event.data ? event.data.json() : {};
              } catch (e) {
                data = {};
              }
              event.waitUntil(self.registration.showNotification(data.title || 'Nueva reserva', {
                body: data.body || 'Entró una reserva por la página.',
                tag: data.tag,
                icon: '/icon-192.png',
                lang: 'es-AR',
                data: {url: data.url || '/admin/'}
              }));
            });

            self.addEventListener('notificationclick', (event) => {
              event.notification.close();
              const target = new URL((event.notification.data && event.notification.data.url) || '/admin/',
                  self.location.origin).href;
              event.waitUntil(self.clients.matchAll({type: 'window', includeUncontrolled: true})
                  .then((windows) => {
                    const panel = windows.find((client) => new URL(client.url).pathname.startsWith('/admin'));
                    if (!panel) {
                      return self.clients.openWindow(target);
                    }
                    return panel.focus()
                        .then((focused) => (focused || panel).navigate(target))
                        .catch(() => undefined);
                  }));
            });
            """;

    @GetMapping(value = "/panel-sw.js", produces = "application/javascript")
    public ResponseEntity<String> serviceWorker() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("Service-Worker-Allowed", "/admin/")
                .body(SERVICE_WORKER);
    }

    /** Los iconos propios de la app del club (ver {@link #icon}). */
    private static final Set<String> ICONS = Set.of(
            "icon-192.png", "icon-512.png", "icon-maskable-512.png", "apple-touch-icon.png", "favicon-32.png");

    /**
     * Fijo para todos los clubes: se pide sin sesion, y el que lo instala es quien
     * atiende, no el jugador. Nombre e icono distintos de los de "TurnosPadel" para
     * que en el inicio del celular y en la barra de la compu no se confundan: la del
     * jugador es la lupa naranja sobre negro, la del club la misma lupa en blanco
     * sobre el azul de una cancha.
     */
    @GetMapping(value = "/panel.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Map<String, Object>> manifest() {
        Map<String, Object> manifest = Map.ofEntries(
                Map.entry("id", "/admin/"),
                Map.entry("name", "TurnosPadel Club"),
                Map.entry("short_name", "TP Club"),
                Map.entry("description", "Panel del club: agenda, caja y avisos de reservas nuevas."),
                Map.entry("lang", "es-AR"),
                Map.entry("start_url", "/admin/"),
                Map.entry("scope", "/admin/"),
                Map.entry("display", "standalone"),
                Map.entry("background_color", "#ffffff"),
                Map.entry("theme_color", THEME_COLOR),
                Map.entry("categories", List.of("business", "sports")),
                Map.entry("icons", List.of(
                        Map.of("src", "/panel/icons/icon-192.png", "sizes", "192x192", "type", "image/png",
                                "purpose", "any"),
                        Map.of("src", "/panel/icons/icon-512.png", "sizes", "512x512", "type", "image/png",
                                "purpose", "any"),
                        Map.of("src", "/panel/icons/icon-maskable-512.png", "sizes", "512x512",
                                "type", "image/png", "purpose", "maskable"))),
                // Accesos directos al mantener apretado el icono (o clic derecho en la compu).
                Map.entry("shortcuts", List.of(
                        shortcut("Agenda", "/admin/"),
                        shortcut("Caja", "/admin/caja"),
                        shortcut("Buffet", "/admin/buffet"))));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/manifest+json"))
                .cacheControl(CacheControl.noCache())
                .body(manifest);
    }

    private static Map<String, Object> shortcut(String name, String url) {
        return Map.of("name", name, "url", url,
                "icons", List.of(Map.of("src", "/panel/icons/icon-192.png", "sizes", "192x192")));
    }

    /**
     * Sin sesion, como el manifest: el navegador los pide tambien desde el login, para
     * ofrecer instalar la app antes de entrar.
     */
    @GetMapping("/panel/icons/{name}")
    public ResponseEntity<byte[]> icon(@PathVariable String name) throws IOException {
        if (!ICONS.contains(name)) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream in = PanelPwaController.class.getResourceAsStream("/panel/icons/" + name)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                    .body(in.readAllBytes());
        }
    }
}
