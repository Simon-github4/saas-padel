package ar.com.padelnec.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saca latitud y longitud de lo que el dueño del club tiene a mano de verdad:
 * el link que Google Maps le da al tocar "Compartir", no un par de decimales
 * que nadie memoriza. Pedirle esos decimales a mano -como se le pedia antes,
 * ver {@code SettingsView}- es la clase de friccion que hace que la portada se
 * quede sin mapa para siempre.
 *
 * <p>No hay una API gratuita de Google para esto -la oficial (Geocoding API)
 * pide tarjeta y un costo mensual, injustificado para un dato que se carga una
 * sola vez por club-. En cambio, todo link de Google Maps trae las coordenadas
 * codificadas en la URL misma, en uno de un puñado de formatos conocidos. Es
 * una convencion, no un contrato: si Google cambia el formato el dia de mañana
 * esto deja de reconocer links nuevos, pero nunca deja al club sin poder
 * cargar su ubicacion -las coordenadas manuales siguen ahi al lado, como red
 * de contencion.
 */
@Component
@Slf4j
public class GoogleMapsLinkResolver {

    /** Cuanto vale cada tipo de link. */
    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }

    // "!3d<lat>!4d<lng>": el pin de un lugar puntual, en las URL de tipo
    // ".../maps/place/...". Es el mas preciso cuando esta: apunta al comercio
    // exacto, no al centro de lo que se ve en pantalla.
    private static final Pattern PIN_LAT_LNG = Pattern.compile("!3d(-?\\d{1,3}\\.\\d+)!4d(-?\\d{1,3}\\.\\d+)");

    // "!2d<lng>!3d<lat>": el bloque pb= del iframe que Google arma con
    // "Insertar un mapa". Ojo, el orden es al reves que el de arriba.
    private static final Pattern EMBED_LNG_LAT = Pattern.compile("!2d(-?\\d{1,3}\\.\\d+)!3d(-?\\d{1,3}\\.\\d+)");

    // "@<lat>,<lng>": el centro de lo que se ve en pantalla, en cualquier URL
    // de Google Maps. Menos preciso que el pin -es la camara, no el comercio-
    // pero esta en todos lados, asi que es el ultimo recurso antes de rendirse.
    private static final Pattern VIEWPORT_LAT_LNG = Pattern.compile("@(-?\\d{1,3}\\.\\d+),(-?\\d{1,3}\\.\\d+)");

    private static final Pattern FIRST_URL = Pattern.compile("https?://[^\\s\"'<>]+");

    /**
     * Unicos hosts por los que este resolver sale a la red. Todo lo demas -un
     * link mal pegado, una URL de otro sitio, texto suelto- se descarta local,
     * sin pedirle nada a nadie: alcanza con dos nombres exactos porque son los
     * unicos dos dominios de Google que devuelven un link corto (no una URL de
     * mapa ya completa), y son exactos y no un patron de sufijo a proposito --
     * "google.com.algo-que-registro-cualquiera" no tiene por que ser Google.
     */
    private static final Set<String> SHORT_LINK_HOSTS = Set.of("goo.gl", "maps.app.goo.gl");

    private static final int MAX_REDIRECTS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; PadelSaaS/1.0)";

    private final Set<String> shortLinkHosts;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            // A mano: hay que mirar el header Location de cada salto para
            // frenar en cuanto el propio link ya trae las coordenadas, en vez
            // de terminar de bajar una pagina entera de Google Maps.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public GoogleMapsLinkResolver() {
        this(SHORT_LINK_HOSTS);
    }

    /**
     * Para el test: reemplaza los hosts reales por el de un server de prueba,
     * asi el redirect se puede probar sin salir a la red de verdad.
     */
    public GoogleMapsLinkResolver(Set<String> shortLinkHosts) {
        this.shortLinkHosts = shortLinkHosts;
    }

    /**
     * Lee latitud y longitud de lo que el dueño pegó: un link de Google Maps
     * (largo o acortado), o el codigo entero de "Insertar un mapa".
     *
     * @return vacio si no se pudo leer nada -el dueño se entera con un mensaje
     *         y le queda la carga manual, nunca un error de servidor.
     */
    public Optional<Coordinates> resolve(String pasted) {
        if (pasted == null || pasted.isBlank()) {
            return Optional.empty();
        }
        // El pb= de un iframe pegado tal cual a veces trae "&amp;" en vez de
        // "&"; no cambia nada en este caso puntual (pb es un solo parametro,
        // sin mas "&"), pero es gratis cubrirlo por si el dia de mañana lo
        // tiene.
        String normalized = pasted.replace("&amp;", "&");

        // El caso comun: una URL de Google Maps ya completa, o el iframe de
        // "Insertar un mapa". Las coordenadas viven en el texto mismo, sin
        // pedirle nada a la red.
        Optional<Coordinates> direct = extract(normalized);
        if (direct.isPresent()) {
            return direct;
        }

        // Lo unico que le falta a un link corto (goo.gl, maps.app.goo.gl) es
        // seguirle el redirect: Google no manda las coordenadas en el link
        // corto en si, las manda en el header Location de la redireccion.
        Matcher urlMatcher = FIRST_URL.matcher(normalized);
        if (!urlMatcher.find()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(urlMatcher.group());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!shortLinkHosts.contains(host)) {
            // No es un link corto de Google que sepamos seguir: mejor no
            // salir a la red por un host que el club escribio a mano y podria
            // ser cualquier cosa.
            return Optional.empty();
        }
        return followShortLink(uri);
    }

    // ------------------------------------------------------------ internos

    private Optional<Coordinates> followShortLink(URI start) {
        URI current = start;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            Optional<URI> next = locationOf(current);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            Optional<Coordinates> found = extract(next.get().toString());
            if (found.isPresent()) {
                return found;
            }
            current = next.get();
        }
        return Optional.empty();
    }

    /** El destino de un salto de redireccion, o vacio si no redirige mas. */
    private Optional<URI> locationOf(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .build();
            // Se descarta el cuerpo a proposito: una redireccion no necesita
            // que se lea nada mas que su header, y no hace falta bajar la
            // pagina de Google Maps para leer el Location.
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 300 || status >= 400) {
                return Optional.empty();
            }
            return response.headers().firstValue("Location").map(uri::resolve);
        } catch (Exception ex) {
            // Cualquier falla de red -timeout, DNS, lo que sea- no rompe el
            // flujo: el dueño se entera con un mensaje y carga a mano, no con
            // un error 500.
            log.debug("No se pudo resolver el link corto de Google Maps {}", uri, ex);
            return Optional.empty();
        }
    }

    /** Prueba los tres formatos conocidos, del mas preciso al mas generico. */
    private Optional<Coordinates> extract(String text) {
        Matcher pin = PIN_LAT_LNG.matcher(text);
        if (pin.find()) {
            Optional<Coordinates> found = coordinates(pin.group(1), pin.group(2));
            if (found.isPresent()) {
                return found;
            }
        }
        Matcher embed = EMBED_LNG_LAT.matcher(text);
        if (embed.find()) {
            // Grupo 1 es la longitud y el 2 la latitud en este formato -al
            // reves que en los otros dos-, por eso van cruzados aca.
            Optional<Coordinates> found = coordinates(embed.group(2), embed.group(1));
            if (found.isPresent()) {
                return found;
            }
        }
        Matcher viewport = VIEWPORT_LAT_LNG.matcher(text);
        if (viewport.find()) {
            return coordinates(viewport.group(1), viewport.group(2));
        }
        return Optional.empty();
    }

    private Optional<Coordinates> coordinates(String latRaw, String lngRaw) {
        try {
            BigDecimal lat = new BigDecimal(latRaw).setScale(6, RoundingMode.HALF_UP);
            BigDecimal lng = new BigDecimal(lngRaw).setScale(6, RoundingMode.HALF_UP);
            if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
                // Un numero fuera de rango es una señal de que el patron
                // matcheo el pedazo equivocado de la URL, no unas coordenadas.
                return Optional.empty();
            }
            return Optional.of(new Coordinates(lat, lng));
        } catch (NumberFormatException | ArithmeticException ex) {
            return Optional.empty();
        }
    }
}
