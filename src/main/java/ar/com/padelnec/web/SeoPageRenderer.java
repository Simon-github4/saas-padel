package ar.com.padelnec.web;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Tenant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Arma el {@code index.html} de la app del jugador con los metadatos de la
 * pagina ya puestos, para las rutas publicas que un buscador o un link compartido
 * tienen que poder leer.
 *
 * <p>La app es una SPA: titulo y descripcion los pone React recien despues de
 * cargar. Google ejecuta el JS, pero los que arman la vista previa de un link
 * -- WhatsApp, Instagram, Facebook -- no, y ven solo lo que trae el HTML. Sin
 * esto, el link de un club se comparte pelado, sin nombre ni foto, y es justo el
 * canal por el que el jugador llega. La app en el navegador no cambia: es el mismo
 * index con mas cosas en el {@code <head>}, y React sigue poniendo lo suyo encima.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeoPageRenderer {

    /** Nombre del producto, igual a {@code BRAND} en player-app/src/pages/marketing/config.ts. */
    static final String SITE_NAME = "TurnosPadel";

    private static final String LOGO_PATH = "/apple-touch-icon.png";

    /**
     * Ciudades pegadas que para el jugador son un mismo lugar, igual que {@code ZONAS} en
     * SearchPage.tsx: ahi agrupa las secciones de resultados, aca le da a esa zona su propio
     * titulo, descripcion y URL ({@code /buscar?localidad=<clave>}) en vez de competir con el
     * texto generico de "todos los clubes" para una busqueda como "turnos padel necochea".
     * Es una decision de negocio, no algo que salga de los datos: sumar una ciudad nueva a una
     * zona va aca y alla.
     */
    private static final Map<String, String> ZONE_KEY_BY_CITY_SLUG =
            Map.of("necochea", "necochea-quequen", "quequen", "necochea-quequen");
    private static final Map<String, String> ZONE_NAME_BY_KEY = Map.of("necochea-quequen", "Necochea y Quequén");

    /** El build de Vite puede dejar la etiqueta en una linea o en varias: por eso {@code [^>]*}. */
    private static final Pattern TITLE = Pattern.compile("(?s)<title>.*?</title>");
    private static final Pattern DESCRIPTION = Pattern.compile("(?s)<meta\\s+name=\"description\"[^>]*>");

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    /** El index solo cambia con un deploy, asi que se lee una vez. */
    private volatile String template;

    /**
     * @param path        ruta canonica, ej. {@code /club/necochea-padel}
     * @param image       URL de la imagen para la vista previa, absoluta o relativa al sitio; puede ser nula
     * @param largeImage  si la imagen es una foto apaisada (portada de club) y no un icono
     * @param noindex     que los buscadores no indexen esta respuesta
     * @param structured  datos estructurados JSON-LD, o nulo
     * @param noscript    contenido en HTML para quien no ejecuta JS (ver {@code <noscript>}), o nulo
     */
    public record PageMeta(
            String title,
            String description,
            String path,
            String image,
            boolean largeImage,
            boolean noindex,
            Map<String, Object> structured,
            String noscript) {}

    /** Devuelve vacio si el build de la app del jugador no esta en el classpath. */
    public Optional<String> render(PageMeta meta) {
        String html = loadTemplate();
        if (html == null) {
            return Optional.empty();
        }
        html = TITLE.matcher(html).replaceFirst(Matcher.quoteReplacement(
                "<title>" + escape(meta.title()) + "</title>"));
        String descriptionTag = "<meta name=\"description\" content=\"" + escape(meta.description()) + "\" />";
        Matcher description = DESCRIPTION.matcher(html);
        if (description.find()) {
            html = description.replaceFirst(Matcher.quoteReplacement(descriptionTag));
        } else {
            html = insertBeforeHeadEnd(html, descriptionTag);
        }
        html = insertBeforeHeadEnd(html, extraTags(meta));
        if (meta.noscript() != null) {
            html = insertBeforeBodyEnd(html, "    <noscript>" + meta.noscript() + "</noscript>\n  ");
        }
        return Optional.of(html);
    }

    /** Metadatos de la pagina de un club. Los textos son los mismos que pone ClubPage.tsx. */
    public PageMeta clubMeta(Tenant club) {
        String where = hasText(club.getCity()) ? " en " + club.getCity() : "";
        String pitch = hasText(club.getTagline()) ? club.getTagline() + ". " : "";
        String description = pitch + "Reservá tu cancha de pádel online" + where + " con " + club.getName()
                + ", sin llamar ni escribir por WhatsApp.";
        String path = "/club/" + club.getSlug();
        String image = hasText(club.getHeroImageUrl()) ? absolute(club.getHeroImageUrl()) : null;

        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        // SportsClub y no SportsActivityLocation: Google solo reconoce LocalBusiness y
        // sus subtipos para resultados enriquecidos. Detalle en ClubPage.tsx.
        ld.put("@type", "SportsClub");
        ld.put("name", club.getName());
        ld.put("url", absolute(path));
        ld.put("telephone", club.getWhatsappNumber());
        if (hasText(club.getTagline())) {
            ld.put("description", club.getTagline());
        }
        if (image != null) {
            ld.put("image", image);
        }
        if (hasText(club.getAddress()) || hasText(club.getCity())) {
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("@type", "PostalAddress");
            if (hasText(club.getAddress())) {
                address.put("streetAddress", club.getAddress());
            }
            if (hasText(club.getCity())) {
                address.put("addressLocality", club.getCity());
            }
            address.put("addressCountry", "AR");
            ld.put("address", address);
        }
        if (club.getLatitude() != null && club.getLongitude() != null) {
            Map<String, Object> geo = new LinkedHashMap<>();
            geo.put("@type", "GeoCoordinates");
            geo.put("latitude", club.getLatitude());
            geo.put("longitude", club.getLongitude());
            ld.put("geo", geo);
        }
        String instagram = club.instagramUrl();
        if (hasText(instagram)) {
            ld.put("sameAs", List.of(instagram));
        }

        StringBuilder text = new StringBuilder("<h1>").append(escape(club.getName())).append("</h1>")
                .append("<p>").append(escape(description)).append("</p>");
        if (hasText(club.getAddress()) || hasText(club.getCity())) {
            String place = hasText(club.getAddress()) && hasText(club.getCity())
                    ? club.getAddress() + ", " + club.getCity()
                    : hasText(club.getAddress()) ? club.getAddress() : club.getCity();
            text.append("<p>").append(escape(place)).append("</p>");
        }
        String zoneKey = zoneKeyForCity(club.getCity());
        String zoneName = zoneKey == null ? null : ZONE_NAME_BY_KEY.get(zoneKey);
        if (zoneName != null) {
            text.append("<p><a href=\"/buscar?localidad=").append(zoneKey).append("\">Buscar más canchas de pádel en ")
                    .append(escape(zoneName)).append("</a></p>");
        } else {
            text.append("<p><a href=\"/buscar\">Buscar canchas de pádel libres en todos los clubes</a></p>");
        }

        return new PageMeta(club.getName() + " — Reservá tu cancha de pádel", description, path,
                image, image != null, false, ld, text.toString());
    }

    /** Portada comercial: la que ve el dueno de un club que evalua el sistema. */
    public PageMeta landingMeta() {
        String description = "Sistema de reservas online para clubes de pádel: tus jugadores reservan y pagan la seña "
                + "solos, con Mercado Pago, y vos manejás la agenda desde un panel. Con 7 días de prueba.";

        Map<String, Object> organization = new LinkedHashMap<>();
        organization.put("@type", "Organization");
        organization.put("name", SITE_NAME);
        organization.put("url", absolute("/"));
        organization.put("logo", absolute(LOGO_PATH));

        Map<String, Object> software = new LinkedHashMap<>();
        software.put("@type", "SoftwareApplication");
        software.put("name", SITE_NAME);
        software.put("url", absolute("/"));
        software.put("description", description);
        software.put("applicationCategory", "BusinessApplication");
        software.put("operatingSystem", "Web");

        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@graph", List.of(organization, software));

        String text = "<h1>Reservas online para clubes de pádel</h1>"
                + "<p>" + escape(description) + "</p>"
                + "<ul>"
                + "<li>Tus jugadores ven las canchas libres y reservan desde un link, sin escribirte por WhatsApp.</li>"
                + "<li>Cobrás la seña online con Mercado Pago, directo en tu cuenta.</li>"
                + "<li>Manejás la agenda, las tarifas y la caja desde un panel.</li>"
                + "</ul>"
                + "<p><a href=\"/buscar\">Buscar canchas de pádel libres</a></p>";

        return new PageMeta("Reservas online para tu club — " + SITE_NAME, description, "/",
                LOGO_PATH, false, false, ld, text);
    }

    /** Buscador global de canchas libres. Mismos textos que SearchPage.tsx. */
    public PageMeta searchMeta() {
        return new PageMeta("Buscar cancha de pádel — todos los clubes",
                "Buscá canchas de pádel libres hoy en todos los clubes a la vez, por día y horario, sin elegir club primero.",
                "/buscar", LOGO_PATH, false, false, null, null);
    }

    /**
     * Buscador filtrado a una zona reconocida ({@code ?localidad=<clave>}, ver
     * {@link #ZONE_KEY_BY_CITY_SLUG}): mismo buscador, con titulo, descripcion, canonical y
     * texto propios que nombran la zona -- sin esto, "turnos padel necochea" compite contra el
     * titulo generico de /buscar, que no dice en que ciudad. Una clave que no se reconoce cae al
     * buscador generico, igual que hace SearchPage.tsx cuando el parametro no matchea ninguna zona.
     */
    public PageMeta searchMeta(String zoneKey) {
        String zoneName = zoneKey == null ? null : ZONE_NAME_BY_KEY.get(zoneKey);
        if (zoneName == null) {
            return searchMeta();
        }
        String description = "Buscá y reservá una cancha de pádel libre en " + zoneName
                + " hoy, por día y horario, en todos los clubes a la vez.";
        String text = "<h1>Canchas de pádel en " + escape(zoneName) + "</h1><p>" + escape(description) + "</p>";
        return new PageMeta("Canchas de pádel en " + zoneName + " — turnos online", description,
                "/buscar?localidad=" + zoneKey, LOGO_PATH, false, false, null, text);
    }

    /**
     * Zonas con al menos un club activo: las unicas que tiene sentido indexar, en el sitemap y
     * como link desde la ficha de un club. En el orden en que aparecen los clubes.
     */
    public List<String> activeSearchZoneKeys(List<Tenant> activeClubs) {
        return activeClubs.stream()
                .map(club -> zoneKeyForCity(club.getCity()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** La zona de una ciudad ("Necochea, Buenos Aires" → "necochea-quequen"), o nulo si no es una zona reconocida. */
    private static String zoneKeyForCity(String city) {
        if (!hasText(city)) {
            return null;
        }
        String firstTown = city.split(",")[0].trim();
        return ZONE_KEY_BY_CITY_SLUG.get(slugify(firstTown));
    }

    /** Igual a {@code slugify} en SearchPage.tsx: sin tildes, en minuscula, separado por guiones. */
    private static String slugify(String text) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /**
     * Club que no existe o esta dado de baja. La SPA muestra su propio "no
     * encontrado"; esto solo evita que un buscador indexe la ruta como si fuera
     * una pagina real.
     */
    public PageMeta unknownClubMeta() {
        return new PageMeta("Club no encontrado — " + SITE_NAME,
                "Este club no existe o ya no está disponible.", null, null, false, true, null, null);
    }

    private String extraTags(PageMeta meta) {
        StringBuilder tags = new StringBuilder();
        String url = meta.path() == null ? null : absolute(meta.path());
        if (url != null) {
            tags.append("    <link rel=\"canonical\" href=\"").append(escape(url)).append("\" />\n");
        }
        if (meta.noindex()) {
            tags.append("    <meta name=\"robots\" content=\"noindex\" />\n");
        }
        tags.append("    <meta property=\"og:type\" content=\"website\" />\n");
        tags.append("    <meta property=\"og:site_name\" content=\"").append(SITE_NAME).append("\" />\n");
        tags.append("    <meta property=\"og:locale\" content=\"es_AR\" />\n");
        tags.append("    <meta property=\"og:title\" content=\"").append(escape(meta.title())).append("\" />\n");
        tags.append("    <meta property=\"og:description\" content=\"").append(escape(meta.description())).append("\" />\n");
        if (url != null) {
            tags.append("    <meta property=\"og:url\" content=\"").append(escape(url)).append("\" />\n");
        }
        String image = meta.image() == null ? null : absolute(meta.image());
        if (image != null) {
            tags.append("    <meta property=\"og:image\" content=\"").append(escape(image)).append("\" />\n");
        }
        tags.append("    <meta name=\"twitter:card\" content=\"")
                .append(image != null && meta.largeImage() ? "summary_large_image" : "summary").append("\" />\n");
        tags.append("    <meta name=\"twitter:title\" content=\"").append(escape(meta.title())).append("\" />\n");
        tags.append("    <meta name=\"twitter:description\" content=\"").append(escape(meta.description())).append("\" />\n");
        if (image != null) {
            tags.append("    <meta name=\"twitter:image\" content=\"").append(escape(image)).append("\" />\n");
        }
        if (meta.structured() != null) {
            // data-server-seo: el cliente lo reemplaza por el suyo en vez de duplicarlo.
            tags.append("    <script type=\"application/ld+json\" data-server-seo>")
                    .append(jsonForScript(meta.structured())).append("</script>\n");
        }
        return tags.toString();
    }

    private String loadTemplate() {
        String cached = template;
        if (cached != null) {
            return cached;
        }
        try {
            cached = new ClassPathResource("static/index.html").getContentAsString(StandardCharsets.UTF_8);
            template = cached;
            return cached;
        } catch (IOException e) {
            log.warn("No se pudo leer static/index.html para armar los metadatos SEO: {}", e.toString());
            return null;
        }
    }

    private static String insertBeforeBodyEnd(String html, String tags) {
        int end = html.indexOf("</body>");
        if (end < 0) {
            return html;
        }
        return html.substring(0, end) + tags + html.substring(end);
    }

    private static String insertBeforeHeadEnd(String html, String tags) {
        int end = html.indexOf("</head>");
        if (end < 0) {
            return html;
        }
        return html.substring(0, end) + tags + (tags.endsWith("\n") ? "" : "\n") + "  " + html.substring(end);
    }

    /** Ruta o URL a URL absoluta sobre la base publica; las que ya son absolutas quedan como estan. */
    private String absolute(String pathOrUrl) {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + (pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl);
    }

    /** JSON para meter dentro de un {@code <script>}: un {@code </script>} en un dato lo cerraria. */
    private String jsonForScript(Map<String, Object> data) {
        return objectMapper.writeValueAsString(data)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
