package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.HeroVariant;
import ar.com.padelnec.domain.enums.ThemeMode;
import ar.com.padelnec.support.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;

/** Club. Raiz del multi-tenant: no lleva club_id porque es el tenant. */
@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    /** Identificador legible usado en la URL publica, ej. "necochea-padel". */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    /** Telefono oficial del club, en E.164. Se usa para derivar consultas y reembolsos. */
    @Column(name = "whatsapp_number", nullable = false, length = 25)
    private String whatsappNumber;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = "America/Argentina/Buenos_Aires";

    /** Token de produccion de MercadoPago. Cifrado en reposo. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mp_access_token", columnDefinition = "text")
    private String mpAccessToken;

    /** Clave secreta con la que MercadoPago firma los webhooks. Cifrada en reposo. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mp_webhook_secret", columnDefinition = "text")
    private String mpWebhookSecret;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(8, 0);

    /**
     * Hora de cierre. Si es menor o igual a openTime se interpreta que el club
     * cierra pasada la medianoche (ej. abre 08:00 y cierra 01:00).
     */
    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(23, 59);

    /** Duracion del turno en minutos. Define el tamano de los bloques de la grilla. */
    @Column(name = "default_slot_duration", nullable = false)
    private int defaultSlotDuration = 90;

    /** Antelacion minima, en horas, para que el jugador pueda cancelar desde la web. */
    @Column(name = "cancellation_limit_hours", nullable = false)
    private int cancellationLimitHours = 12;

    @Column(name = "deposit_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal depositPercentage = new BigDecimal("50.00");

    /** Si el club acepta reservas "de palabra" confirmadas por WhatsApp. */
    @Column(name = "allow_unpaid_booking", nullable = false)
    private boolean allowUnpaidBooking = true;

    /**
     * Si una reserva de palabra necesita que el jugador confirme por WhatsApp antes de quedar firme.
     *
     * <p>Default en false mientras WhatsApp esta en stand by: pedir confirmacion
     * por un link que nunca llega solo haria que la reserva se caiga sola a los
     * 15 minutos. Revisar este default el dia que WhatsApp vuelva a estar activo.
     */
    @Column(name = "requires_booking_confirmation", nullable = false)
    private boolean requiresBookingConfirmation = false;

    /** Cuantos dias hacia adelante puede reservar el jugador. */
    @Column(name = "booking_horizon_days", nullable = false)
    private int bookingHorizonDays = 21;

    /** Minutos que se sostiene un DRAFT esperando que acredite MercadoPago. */
    @Column(name = "draft_ttl_minutes", nullable = false)
    private int draftTtlMinutes = 10;

    /** Minutos que se sostiene un AWAITING_CONFIRMATION esperando el click de WhatsApp. */
    @Column(name = "confirmation_ttl_minutes", nullable = false)
    private int confirmationTtlMinutes = 15;

    /** Cuantos turnos futuros puede tener tomados un mismo telefono a la vez. */
    @Column(name = "max_active_bookings", nullable = false)
    private int maxActiveBookings = 3;

    @Column(nullable = false)
    private boolean active = true;

    /** Frase corta bajo el nombre, ej. "Reservá tu cancha". */
    @Column(length = 160)
    private String tagline;

    @Column(length = 200)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    /**
     * Link a la ficha real del lugar en Google Maps -con nombre, fotos y
     * reseñas-, no un pin pelado en unas coordenadas. Lo completa
     * {@code GoogleMapsLinkResolver} cuando puede identificar el lugar en el
     * link que el club pego; nulo si nunca se cargo asi, o si el link no
     * traia como identificarlo (solo el centro del mapa). Ver {@link #mapsUrl()}.
     */
    @Column(name = "google_maps_url", length = 500)
    private String googleMapsUrl;

    /**
     * Foto de portada. El club la pega como URL externa, o sube un archivo: en ese
     * caso esta URL apunta al endpoint publico que sirve los bytes, guardados
     * aparte en {@link TenantHeroImage} (ver esa clase para el porque).
     */
    @Column(name = "hero_image_url", length = 500)
    private String heroImageUrl;

    /** Titulo de la portada. Null cuando el club titula con su propio nombre. */
    @Column(name = "hero_headline", length = 80)
    private String heroHeadline;

    /** Texto del boton de la portada. Null usa el que trae la app. */
    @Column(name = "hero_cta_label", length = 40)
    private String heroCtaLabel;

    /**
     * Cuanto se oscurece la foto de portada, de 0 a 100.
     *
     * <p>No es cosmetico: sobre una foto clara el titulo queda ilegible, y esta
     * es la unica perilla que tiene el club para corregirlo sin cambiar la foto.
     */
    @Column(name = "hero_overlay", nullable = false)
    private int heroOverlay = 55;

    /** Diseño de la portada de la app del jugador. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hero_variant", nullable = false, length = 20)
    private HeroVariant heroVariant = HeroVariant.CLASSIC;

    /** Divisor del precio del turno para mostrar el dato "por persona". Siempre 4 en padel. */
    @Column(name = "players_per_court", nullable = false)
    private int playersPerCourt = 4;

    /** Paleta clara u oscura de la app del jugador. */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 10)
    private ThemeMode themeMode = ThemeMode.DARK;

    /** Acento de botones y detalles, en hex (#RRGGBB). Null usa el naranja de fábrica. */
    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    /** Acento mas claro, para iconos y estados hover. Null usa el de fábrica. */
    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    /**
     * Tarifa general del club, en pesos POR PERSONA, para cualquier dia/franja
     * sin una regla especifica. Nulo = se exige regla para publicar un horario.
     */
    @Column(name = "general_price_per_person", precision = 12, scale = 2)
    private BigDecimal generalPricePerPerson;

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    /**
     * Link a Google Maps para el boton "Abrir en Google Maps" de la portada.
     *
     * <p>El orden importa: {@code googleMapsUrl} es la ficha real del lugar
     * -con nombre, fotos y reseñas-, y coordenadas sueltas solo abren un pin
     * pelado en el medio del mapa, sin decir de que negocio se trata. Cae a
     * coordenadas, y de ahi a direccion y ciudad, solo para los clubes que
     * cargaron su ubicacion antes de que existiera {@code googleMapsUrl},
     * o cuyo link no traia como identificar el lugar. Da null solo cuando no
     * hay ninguno de los tres.
     */
    public String mapsUrl() {
        if (googleMapsUrl != null && !googleMapsUrl.isBlank()) {
            return googleMapsUrl;
        }
        if (latitude != null && longitude != null) {
            return "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;
        }
        if (address != null && !address.isBlank()) {
            String query = address.trim();
            if (city != null && !city.isBlank()) {
                query += ", " + city.trim();
            }
            return "https://www.google.com/maps/search/?api=1&query="
                    + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    // "?query=<texto>" o "&query=<texto>" del link de busqueda que arma mapsUrl().
    private static final java.util.regex.Pattern SEARCH_QUERY_PARAM =
            java.util.regex.Pattern.compile("[?&]query=([^&]+)");

    // "/maps/place/<nombre>/..." de un link de lugar puntual reusado tal cual en mapsUrl().
    private static final java.util.regex.Pattern PLACE_PATH_NAME =
            java.util.regex.Pattern.compile("/maps/place/([^/@?]+)");

    /**
     * Lo que hay que buscar para que el mapa embebido de la portada -y lo que
     * abre Google Maps si el jugador toca ese mapa- seleccionen el mismo
     * lugar que el boton "Abrir en Google Maps", en vez de un pin pelado.
     *
     * <p>El mapa embebido no puede usar {@link #mapsUrl()} tal cual: ese link
     * esta en el formato moderno de Google (una URL de busqueda o de lugar),
     * pensado para navegar a una pagina, no para incrustar un iframe. El
     * iframe de {@code HowToGetThereSection.tsx} usa en cambio el formato
     * viejo de embeber ({@code maps.google.com/maps?q=...&output=embed}), que
     * a cambio acepta cualquier texto de busqueda libre -un nombre, o
     * coordenadas-. Por eso este metodo saca el mismo nombre que ya eligio
     * {@code mapsUrl()} en vez de tener su propia logica: los dos tienen que
     * coincidir siempre en que lugar muestran.
     */
    public String mapsEmbedQuery() {
        if (googleMapsUrl != null) {
            java.util.regex.Matcher searchQuery = SEARCH_QUERY_PARAM.matcher(googleMapsUrl);
            if (searchQuery.find()) {
                return decode(searchQuery.group(1));
            }
            java.util.regex.Matcher placePath = PLACE_PATH_NAME.matcher(googleMapsUrl);
            if (placePath.find()) {
                return decode(placePath.group(1));
            }
        }
        if (latitude != null && longitude != null) {
            return latitude + "," + longitude;
        }
        return null;
    }

    private static String decode(String raw) {
        return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Verdadero cuando el horario de atencion cruza la medianoche. */
    public boolean closesAfterMidnight() {
        return !closeTime.isAfter(openTime);
    }

    public boolean acceptsOnlinePayments() {
        return mpAccessToken != null && !mpAccessToken.isBlank();
    }
}
