package ar.com.padelnec.web;

import ar.com.padelnec.repository.TenantStoryTemplateRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que baja el navegador para armar la historia de turnos libres del panel
 * ({@code StoryDialog}): el codigo del dibujo, la lupa de TurnosPadel y la plantilla
 * del club.
 *
 * <p>Los tres con la version en la direccion y cache de un año: cambian muy de vez en
 * cuando, y antes viajaban de nuevo cada vez que se abria el dialogo (la plantilla
 * pesa cientos de KB, y el panel se usa desde el celular con datos). Cuando cambian,
 * cambia la direccion y el navegador baja la nueva.
 *
 * <p>Fuera de {@code /admin/} porque eso lo toma entero el servlet de Vaadin (igual que
 * {@link PanelPwaController}), pero con la sesion del panel: cae en su cadena de
 * seguridad, y la plantilla es la del club de quien la pide.
 */
@RestController
@RequiredArgsConstructor
public class PanelStoryController {

    private static final byte[] SCRIPT = read("/panel/historia.js");

    /**
     * La lupa con medidas propias: un SVG con solo viewBox no tiene tamaño como imagen
     * y algunos navegadores no lo dibujan en un canvas.
     */
    private static final byte[] LOGO = new String(read("/brand/lupa.svg"), StandardCharsets.UTF_8)
            .replaceFirst("<svg ", "<svg width=\"96\" height=\"96\" ")
            .getBytes(StandardCharsets.UTF_8);

    public static final String SCRIPT_URL = "/panel/historia/dibujo.js?v=" + version(SCRIPT);
    public static final String LOGO_URL = "/panel/historia/lupa.svg?v=" + version(LOGO);

    /** Direccion de la plantilla del club en una version: cambia cuando el club sube otra. */
    public static String templateUrl(long version) {
        return "/panel/historia/plantilla/" + version;
    }

    private static final CacheControl FOR_A_YEAR = CacheControl.maxAge(Duration.ofDays(365)).immutable();

    private final TenantStoryTemplateRepository templates;

    @GetMapping("/panel/historia/dibujo.js")
    public ResponseEntity<byte[]> script() {
        return ResponseEntity.ok()
                .cacheControl(FOR_A_YEAR.cachePrivate())
                .contentType(new MediaType("text", "javascript", StandardCharsets.UTF_8))
                .body(SCRIPT);
    }

    @GetMapping("/panel/historia/lupa.svg")
    public ResponseEntity<byte[]> logo() {
        return ResponseEntity.ok()
                .cacheControl(FOR_A_YEAR.cachePrivate())
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(LOGO);
    }

    /**
     * La version de la direccion no se compara: solo sirve para que el navegador no
     * reuse una plantilla vieja. Se entrega siempre la que el club tiene ahora.
     */
    @GetMapping("/panel/historia/plantilla/{version}")
    public ResponseEntity<byte[]> template(@PathVariable long version,
                                           @AuthenticationPrincipal ClubUserPrincipal user) {
        if (user == null || user.clubId() == null) {
            return ResponseEntity.notFound().build();
        }
        return templates.findById(user.clubId())
                .map(template -> ResponseEntity.ok()
                        .cacheControl(FOR_A_YEAR.cachePrivate())
                        .contentType(MediaType.parseMediaType(template.getContentType()))
                        .body(template.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static byte[] read(String resource) {
        try (InputStream in = PanelStoryController.class.getResourceAsStream(resource)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String version(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
