package ar.com.padelnec.ui;

import ar.com.padelnec.domain.ClubAmenity;
import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.TenantHeroImage;
import ar.com.padelnec.domain.enums.HeroVariant;
import ar.com.padelnec.domain.enums.ThemeMode;
import ar.com.padelnec.repository.ClubAmenityRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.repository.TenantHeroImageRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.ClubUserService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.support.GoogleMapsLinkResolver;
import ar.com.padelnec.support.ImageSignature;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuracion del club: horarios, tarifas, canchas y cobros.
 *
 * <p>El personal de mostrador entra a todo menos a "Cobros online": esa pestana
 * tiene las credenciales de MercadoPago del club, y esa si es plata que solo
 * el dueno maneja.
 */
@Route(value = "configuracion", layout = MainLayout.class)
@PageTitle("Configuración | Panel del club")
@PermitAll
public class SettingsView extends VerticalLayout {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    /**
     * Locale de los selectores de hora, separado del de los textos a proposito.
     *
     * <p>Con es-AR el TimePicker formatea en 12 horas con "a. m." y despues no puede
     * volver a leer lo que escribio: las 18:00 quedaban guardadas como 06:00 y el
     * cierre 23:30 como 11:30. El dueno abria la configuracion, tocaba Guardar sin
     * cambiar nada y le movia el horario al club. es-ES formatea en 24 horas, que
     * ademas es como se escribe la hora en Argentina.
     */
    private static final Locale CLOCK = Locale.forLanguageTag("es-ES");

    /** Texto del boton de portada cuando el club no escribe uno propio. */
    private static final String DEFAULT_HERO_CTA = "Ver horarios";

    /** Oscurecido por defecto de la foto de portada, en porcentaje. */
    private static final int DEFAULT_HERO_OVERLAY = 55;

    /** Naranja de fábrica: lo que ya traía la app antes de que el color fuera configurable. */
    private static final String DEFAULT_PRIMARY_COLOR = "#ea580c";
    private static final String DEFAULT_SECONDARY_COLOR = "#fb923c";
    private static final java.util.regex.Pattern HEX_COLOR = java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");

    /** Paleta rapida al lado de cada campo de color, para no tener que escribir el hex a mano. */
    private static final List<String> COLOR_PRESETS = List.of(
            "#ea580c", "#fb923c", "#dc2626", "#db2777",
            "#7c3aed", "#2563eb", "#0891b2", "#16a34a");

    /** Formatos aceptados para la foto de portada subida. */
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final TenantService tenantService;
    private final TenantRepository tenantRepository;
    private final TenantHeroImageRepository tenantHeroImageRepository;
    private final CourtRepository courtRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ClubAmenityRepository amenityRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ClubUserService clubUserService;
    private final GoogleMapsLinkResolver mapsLinkResolver;
    private final transient AuthenticationContext authenticationContext;

    private final Grid<ClubAmenity> amenityGrid = new Grid<>();
    private final Grid<Court> courtGrid = new Grid<>();
    private final Grid<PricingRule> pricingGrid = new Grid<>();
    private final Grid<Product> productGrid = new Grid<>();
    private final Paragraph pricingWarning = new Paragraph();
    private final VerticalLayout usersContent = new VerticalLayout();

    private Tenant club;

    public SettingsView(TenantService tenantService, TenantRepository tenantRepository,
                        TenantHeroImageRepository tenantHeroImageRepository,
                        CourtRepository courtRepository, PricingRuleRepository pricingRuleRepository,
                        ClubAmenityRepository amenityRepository, ProductRepository productRepository,
                        ProductService productService, ClubUserService clubUserService,
                        GoogleMapsLinkResolver mapsLinkResolver,
                        AuthenticationContext authenticationContext) {
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.tenantHeroImageRepository = tenantHeroImageRepository;
        this.courtRepository = courtRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.amenityRepository = amenityRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.clubUserService = clubUserService;
        this.mapsLinkResolver = mapsLinkResolver;
        this.authenticationContext = authenticationContext;

        setSizeFull();
        this.club = tenantService.requireCurrent();

        TabSheet tabs = new TabSheet();
        tabs.add(new Tab("Perfil"), profileForm());
        tabs.add(new Tab("Club"), clubForm());
        tabs.add(new Tab("Canchas"), courtsTab());
        tabs.add(new Tab("Tarifas"), pricingTab());
        tabs.add(new Tab("Productos"), productsTab());
        // Las credenciales de MercadoPago son plata de verdad: esta pestana
        // es la unica de Configuracion que el mostrador no ve.
        if (canManageSettings()) {
            tabs.add(new Tab("Cobros online"), paymentsForm());
        }
        tabs.add(new Tab("Usuarios"), usersTab());
        tabs.setSizeFull();
        add(tabs);
    }

    private boolean canManageSettings() {
        return authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class)
                .map(ClubUserPrincipal::canManageSettings)
                .orElse(false);
    }

    // --------------------------------------------------------------- perfil

    private VerticalLayout profileForm() {
        TextField tagline = new TextField("Frase bajo el nombre");
        tagline.setValue(club.getTagline() == null ? "" : club.getTagline());
        tagline.setHelperText("Ej. \"Reservá tu cancha\"");

        TextField heroImage = new TextField("Foto de portada (URL)");
        heroImage.setValue(club.getHeroImageUrl() == null ? "" : club.getHeroImageUrl());
        heroImage.setPlaceholder("https://…");
        heroImage.setHelperText("Pegá una URL de imagen, o subí un archivo con el botón de al lado");

        // La subida se guarda al toque y no espera al "Guardar" del formulario, igual
        // que los servicios de la portada mas abajo: es una accion propia, no un campo
        // mas del perfil. Asi el club ve confirmado que la foto quedo, en vez de
        // enterarse recien si se olvida de tocar Guardar.
        MemoryBuffer heroImageBuffer = new MemoryBuffer();
        Upload heroImageUpload = new Upload(heroImageBuffer);
        heroImageUpload.setAcceptedFileTypes(ALLOWED_IMAGE_TYPES.toArray(new String[0]));
        heroImageUpload.setMaxFiles(1);
        heroImageUpload.setMaxFileSize(5 * 1024 * 1024);
        heroImageUpload.setUploadButton(new Button("Subir imagen"));
        heroImageUpload.addSucceededListener(event -> {
            // El tipo que declara el navegador es el unico dato que tenemos, pero no
            // es de fiar: sin esta lista, alguien podria subir un archivo con un
            // content-type que el navegador de otro visitante interprete como algo
            // ejecutable (ej. SVG con script) en vez de una imagen.
            if (!ALLOWED_IMAGE_TYPES.contains(event.getMIMEType())) {
                Notification.show("Formato no soportado: usá JPG, PNG, WEBP o GIF")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                byte[] data = heroImageBuffer.getInputStream().readAllBytes();
                // El Content-Type es lo que declara el navegador, no lo que hay adentro
                // del archivo de verdad: sin este chequeo, un archivo con cualquier
                // contenido -etiquetado como imagen permitida- quedaba guardado y se
                // servia despues, publico, con ese mismo content-type.
                if (!ImageSignature.matches(data, event.getMIMEType())) {
                    Notification.show("El archivo no es una imagen válida de ese formato")
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                TenantHeroImage image = tenantHeroImageRepository.findById(club.getId())
                        .orElseGet(TenantHeroImage::new);
                image.setTenantId(club.getId());
                image.setData(data);
                image.setContentType(event.getMIMEType());
                tenantHeroImageRepository.save(image);

                club.setHeroImageUrl("/api/public/" + club.getSlug() + "/hero-image");
                club = tenantRepository.save(club);
                heroImage.setValue(club.getHeroImageUrl());
                Notification.show("Imagen subida");
            } catch (IOException e) {
                Notification.show("No se pudo subir la imagen").addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        heroImageUpload.addFileRejectedListener(event ->
                Notification.show(event.getErrorMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR));

        TextField heroHeadline = new TextField("Título de la portada");
        heroHeadline.setValue(club.getHeroHeadline() == null ? "" : club.getHeroHeadline());
        heroHeadline.setPlaceholder(club.getName());
        heroHeadline.setMaxLength(80);
        heroHeadline.setHelperText("Si lo dejás vacío se usa el nombre del club");

        TextField heroCta = new TextField("Texto del botón");
        heroCta.setValue(club.getHeroCtaLabel() == null ? "" : club.getHeroCtaLabel());
        heroCta.setPlaceholder(DEFAULT_HERO_CTA);
        heroCta.setMaxLength(40);
        heroCta.setHelperText("El botón que baja a la grilla de horarios");

        // Sobre una foto clara el titulo queda ilegible: esta es la unica
        // perilla que tiene el club para corregirlo sin cambiar la foto.
        IntegerField heroOverlay = new IntegerField("Oscurecido de la foto");
        heroOverlay.setValue(club.getHeroOverlay());
        heroOverlay.setMin(0);
        heroOverlay.setMax(100);
        heroOverlay.setStepButtonsVisible(true);
        heroOverlay.setSuffixComponent(new Span("%"));
        heroOverlay.setHelperText("0 deja la foto tal cual; 100 la tapa del todo. 55 anda bien");

        Select<HeroVariant> heroVariant = new Select<>();
        heroVariant.setLabel("Diseño de portada");
        heroVariant.setItems(HeroVariant.values());
        heroVariant.setItemLabelGenerator(SettingsView::heroVariantLabel);
        heroVariant.setValue(club.getHeroVariant());
        heroVariant.setEmptySelectionAllowed(false);

        Select<ThemeMode> theme = new Select<>();
        theme.setLabel("Paleta");
        theme.setItems(ThemeMode.values());
        theme.setItemLabelGenerator(mode -> mode == ThemeMode.LIGHT ? "Clara" : "Oscura");
        theme.setValue(club.getThemeMode());
        theme.setEmptySelectionAllowed(false);

        TextField primaryColor = new TextField("Color primario");
        primaryColor.setValue(club.getPrimaryColor() == null ? "" : club.getPrimaryColor());
        primaryColor.setPlaceholder(DEFAULT_PRIMARY_COLOR);

        TextField secondaryColor = new TextField("Color secundario");
        secondaryColor.setValue(club.getSecondaryColor() == null ? "" : club.getSecondaryColor());
        secondaryColor.setPlaceholder(DEFAULT_SECONDARY_COLOR);

        VerticalLayout primaryColorField = colorField(primaryColor, DEFAULT_PRIMARY_COLOR);
        VerticalLayout secondaryColorField = colorField(secondaryColor, DEFAULT_SECONDARY_COLOR);

        TextField address = new TextField("Dirección");
        address.setValue(club.getAddress() == null ? "" : club.getAddress());

        TextField city = new TextField("Ciudad");
        city.setValue(club.getCity() == null ? "" : club.getCity());

        // No son campos de la pantalla: el dueño ya no escribe estos numeros
        // ni este link, los completa mapsLinkField() a partir del link que
        // pega. Se guardan igual que antes -Tenant.latitude/longitude no
        // cambio-, solo que ahora nada los muestra ni los deja tocar a mano.
        BigDecimalField latitude = new BigDecimalField();
        latitude.setValue(club.getLatitude());
        BigDecimalField longitude = new BigDecimalField();
        longitude.setValue(club.getLongitude());
        TextField googleMapsUrl = new TextField();
        googleMapsUrl.setValue(club.getGoogleMapsUrl() == null ? "" : club.getGoogleMapsUrl());

        Button save = new Button("Guardar Cambios", event -> {
            String primary = blankToNull(primaryColor.getValue());
            String secondary = blankToNull(secondaryColor.getValue());
            if (!isValidHexOrNull(primary) || !isValidHexOrNull(secondary)) {
                Notification.show("El color tiene que tener el formato #RRGGBB")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            club.setTagline(blankToNull(tagline.getValue()));
            club.setHeroImageUrl(blankToNull(heroImage.getValue()));
            club.setHeroHeadline(blankToNull(heroHeadline.getValue()));
            club.setHeroCtaLabel(blankToNull(heroCta.getValue()));
            // El campo admite quedar vacio; ahi vuelve al valor por defecto.
            club.setHeroOverlay(clampOverlay(heroOverlay.getValue()));
            club.setHeroVariant(heroVariant.getValue());
            club.setThemeMode(theme.getValue());
            club.setPrimaryColor(primary);
            club.setSecondaryColor(secondary);
            club.setAddress(blankToNull(address.getValue()));
            club.setCity(blankToNull(city.getValue()));
            club.setLatitude(latitude.getValue());
            club.setLongitude(longitude.getValue());
            club.setGoogleMapsUrl(blankToNull(googleMapsUrl.getValue()));
            club = tenantRepository.save(club);
            Notification.show("Perfil guardado");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Cuatro grupos, en el orden en que el dueno los piensa: como se llama
        // el club, como se ve su portada, con que paleta, y donde queda.
        return tabContent(
                section("Identidad", tagline),
                section("Portada", heroImage, heroImageUpload, heroVariant, heroHeadline, heroOverlay, heroCta),
                section("Apariencia", 3, theme, primaryColorField, secondaryColorField),
                section("Ubicación", address, city, mapsLinkField(latitude, longitude, googleMapsUrl)),
                actions(save),
                servicesSection());
    }

    /**
     * Pegar un link de Google Maps en vez de escribir la latitud y la
     * longitud a mano.
     *
     * <p>Antes latitud y longitud eran dos campos de la pantalla: el dueño
     * tenia que saber las coordenadas de su propia cancha en decimales, un
     * dato que nadie memoriza y que Google Maps no muestra en ningun lado a
     * simple vista. Lo que si tiene a mano es el boton "Compartir" de la app,
     * que le da exactamente esto -un link-, y ese link ya trae las
     * coordenadas adentro (ver {@link GoogleMapsLinkResolver}). Con eso
     * alcanza: no hace falta mostrar los numeros ni el link resultante ni
     * dejarlos tocar a mano, asi que {@code latitude}/{@code longitude}/
     * {@code googleMapsUrl} pasan a ser solo el lugar donde este metodo deja
     * el resultado -{@link #profileForm} los lee recien al guardar-, no
     * controles de la pantalla.
     */
    private Component mapsLinkField(BigDecimalField latitude, BigDecimalField longitude, TextField googleMapsUrl) {
        TextField mapsLink = new TextField("Link de Google Maps");
        mapsLink.setPlaceholder("Buscá el club en Google Maps, tocá Compartir y pegá el link");
        mapsLink.setClearButtonVisible(true);
        mapsLink.setWidthFull();

        // Unica señal de que ya hay algo cargado: sin los campos de numeros a
        // la vista, el dueño no tiene otra forma de saber si esto ya funciono
        // alguna vez o si la portada todavia no tiene mapa.
        Span status = new Span();
        status.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        refreshLocationStatus(status, latitude, longitude);

        Button use = new Button("Usar este link", event -> {
            String pasted = mapsLink.getValue();
            if (pasted == null || pasted.isBlank()) {
                Notification.show("Pegá primero un link de Google Maps")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            Optional<GoogleMapsLinkResolver.Coordinates> found = mapsLinkResolver.resolve(pasted);
            if (found.isEmpty()) {
                Notification.show("No pude leer la ubicación de ese link. Fijate que sea el que te da "
                                + "\"Compartir\" en Google Maps.", 6000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            latitude.setValue(found.get().latitude());
            longitude.setValue(found.get().longitude());
            // Se pisa aunque el link nuevo no traiga uno: si no vino con
            // nombre ni pin puntual, el link a la ficha del pegado anterior
            // ya no corresponde a estas coordenadas nuevas.
            googleMapsUrl.setValue(found.get().mapsUrl() == null ? "" : found.get().mapsUrl());
            refreshLocationStatus(status, latitude, longitude);
            mapsLink.clear();
            Notification.show("Ubicación encontrada");
        });
        use.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout row = new HorizontalLayout(mapsLink, use);
        row.setAlignItems(Alignment.END);
        row.setWidthFull();
        row.expand(mapsLink);
        row.addClassNames(LumoUtility.Gap.SMALL);

        VerticalLayout field = new VerticalLayout(row, status);
        field.setPadding(false);
        field.setSpacing(false);
        field.addClassNames(LumoUtility.Gap.XSMALL);
        return field;
    }

    private void refreshLocationStatus(Span status, BigDecimalField latitude, BigDecimalField longitude) {
        status.setText(latitude.getValue() != null && longitude.getValue() != null
                ? "📍 Ya hay una ubicación guardada. Pegá otro link para reemplazarla."
                : "Todavía no cargaste la ubicación del club: la portada no va a mostrar el mapa.");
    }

    private VerticalLayout servicesSection() {
        H3 heading = new H3("Servicios de la portada");
        heading.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.Margin.NONE,
                LumoUtility.FontWeight.SEMIBOLD);

        Paragraph hint = new Paragraph("Se muestran como tarjetas debajo de la reserva.");
        hint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY,
                LumoUtility.Margin.NONE);

        VerticalLayout box = new VerticalLayout(heading, hint, amenitiesForm());
        box.setPadding(false);
        box.setSpacing(false);
        box.setWidthFull();
        box.addClassNames(LumoUtility.Gap.SMALL);
        return box;
    }

    /**
     * Los unicos iconos que existen de verdad para un servicio de portada.
     *
     * <p>Antes esto era un {@code TextField} libre con un placeholder de
     * ejemplo ("court, parking, racket…"): el dueño del club no tiene por que
     * saber esos nombres en ingles, y si escribia cualquier otra cosa -o los
     * escribia mal- la portada mostraba un tilde generico sin avisar nada. El
     * token en si sigue viajando igual en {@code ClubAmenity.icon}; lo unico
     * que cambia es que ahora se elige de una lista en vez de tipearse.
     *
     * <p>Mismo conjunto que {@code GLYPHS} en
     * {@code player-app/src/pages/sections/ServicesSection.tsx}: si se agrega
     * un icono aca hay que agregarlo alla tambien, o la portada lo va a mostrar
     * con el tilde de repuesto.
     */
    private enum AmenityIcon {
        COURT("court", "▦", "Canchas"),
        PARKING("parking", "🅿", "Estacionamiento"),
        RACKET("racket", "✚", "Alquiler de paletas"),
        SHOWER("shower", "~", "Vestuarios"),
        TIMER("timer", "◷", "Horario extendido"),
        CAFE("cafe", "☕", "Buffet"),
        STAR("star", "★", "Otro");

        private final String token;
        private final String glyph;
        private final String label;

        AmenityIcon(String token, String glyph, String label) {
            this.token = token;
            this.glyph = glyph;
            this.label = label;
        }

        private String display() {
            return glyph + "  " + label;
        }

        /**
         * El de una amenity ya guardada. Si no matchea ninguno -de antes de
         * este cambio, cargado con un token distinto- cae en STAR: mismo
         * fallback que ya usa la portada para un icono que no reconoce.
         */
        private static AmenityIcon of(String token) {
            for (AmenityIcon icon : values()) {
                if (icon.token.equals(token)) {
                    return icon;
                }
            }
            return STAR;
        }
    }

    private VerticalLayout amenitiesForm() {
        dressGrid(amenityGrid);
        amenityGrid.addColumn(amenity -> AmenityIcon.of(amenity.getIcon()).display()).setHeader("Icono")
                .setAutoWidth(true).setFlexGrow(0);
        amenityGrid.addColumn(ClubAmenity::getTitle).setHeader("Servicio").setFlexGrow(2);
        amenityGrid.addColumn(ClubAmenity::getDescription).setHeader("Descripción").setFlexGrow(3);
        amenityGrid.addComponentColumn(amenity -> {
            Button delete = new Button("Quitar", event -> {
                amenityRepository.delete(amenity);
                refreshAmenities();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return delete;
        }).setAutoWidth(true).setFlexGrow(0);

        Select<AmenityIcon> icon = new Select<>();
        icon.setLabel("Icono");
        icon.setItems(AmenityIcon.values());
        icon.setItemLabelGenerator(AmenityIcon::display);
        icon.setValue(AmenityIcon.STAR);
        icon.setWidth("11rem");
        TextField title = new TextField();
        title.setPlaceholder("Ej. 3 canchas techadas");
        TextField description = new TextField();
        description.setPlaceholder("Descripción opcional");

        Button add = new Button("Agregar servicio", event -> {
            if (title.getValue() == null || title.getValue().isBlank()) {
                Notification.show("Poné un título para el servicio");
                return;
            }
            ClubAmenity amenity = new ClubAmenity();
            amenity.setIcon(icon.getValue().token);
            amenity.setTitle(title.getValue().trim());
            amenity.setDescription(blankToNull(description.getValue()));
            amenity.setDisplayOrder(nextDisplayOrder());
            amenityRepository.save(amenity);
            icon.setValue(AmenityIcon.STAR);
            title.clear();
            description.clear();
            refreshAmenities();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        refreshAmenities();
        HorizontalLayout toolbar = new HorizontalLayout(icon, title, description, add);
        toolbar.setAlignItems(Alignment.END);
        toolbar.setPadding(false);
        toolbar.setWidthFull();
        toolbar.expand(description);
        toolbar.addClassNames(LumoUtility.Gap.SMALL);

        VerticalLayout box = new VerticalLayout(toolbar, amenityGrid);
        box.setPadding(false);
        box.setSpacing(false);
        box.addClassNames(LumoUtility.Gap.SMALL);
        return box;
    }

    private void refreshAmenities() {
        amenityGrid.setItems(amenityRepository.findAllByOrderByDisplayOrderAscTitleAsc());
    }

    private int nextDisplayOrder() {
        return amenityRepository.findAllByOrderByDisplayOrderAscTitleAsc().stream()
                .mapToInt(ClubAmenity::getDisplayOrder)
                .max()
                .orElse(0) + 1;
    }

    // ------------------------------------------------------------- armado

    /** Mismo trato para todas las grillas del panel: zebra y sin seleccion. */
    private static void dressGrid(Grid<?> grid) {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setAllRowsVisible(true);
    }

    /**
     * Bloque de formulario con titulo.
     *
     * <p>Los campos que se contestan juntos van juntos y con un encabezado que
     * los nombra. Antes cada pestana era una lista plana de campos: nueve
     * seguidos en Perfil, sin nada que dijera donde termina la identidad del
     * club y donde empieza su portada.
     */
    private static VerticalLayout section(String title, com.vaadin.flow.component.Component... fields) {
        return section(title, 2, fields);
    }

    /** Variante con mas columnas, para secciones de campos cortos como Apariencia. */
    private static VerticalLayout section(String title, int maxColumns,
            com.vaadin.flow.component.Component... fields) {
        H3 heading = new H3(title);
        heading.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.Margin.NONE,
                LumoUtility.FontWeight.SEMIBOLD);

        FormLayout form = new FormLayout(fields);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("30em", maxColumns));

        VerticalLayout box = new VerticalLayout(heading, form);
        box.setPadding(false);
        box.setSpacing(false);
        box.setWidthFull();
        box.addClassNames(LumoUtility.Gap.SMALL);
        return box;
    }

    /**
     * Contenido de una pestana.
     *
     * <p>Con ancho maximo, pero generoso: el FormLayout de adentro no pasa de
     * dos columnas, asi que mas alla de este punto ensanchar no acerca mas la
     * etiqueta a su campo, solo deja mas aire a los costados en un monitor
     * grande. Y sin anidar VerticalLayouts con padding propio, que era lo que
     * descuadraba los margenes de una pestana a otra.
     */
    private static VerticalLayout tabContent(com.vaadin.flow.component.Component... blocks) {
        VerticalLayout layout = new VerticalLayout(blocks);
        layout.setSpacing(false);
        layout.setWidthFull();
        layout.addClassNames(LumoUtility.Gap.XLARGE, LumoUtility.MaxWidth.SCREEN_XLARGE);
        return layout;
    }

    /** Barra de acciones al pie de un formulario, siempre en el mismo lugar. */
    private static HorizontalLayout actions(com.vaadin.flow.component.Component... buttons) {
        HorizontalLayout row = new HorizontalLayout(buttons);
        row.setPadding(false);
        row.setAlignItems(Alignment.CENTER);
        row.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.Margin.Top.SMALL);
        return row;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Deja el oscurecido dentro del 0-100 que acepta la base.
     *
     * <p>El IntegerField admite quedar vacio y que le escriban a mano un numero
     * fuera de rango; sin esto, guardar rompia con un error de constraint que al
     * dueno no le dice nada.
     */
    private int clampOverlay(Integer value) {
        if (value == null) {
            return DEFAULT_HERO_OVERLAY;
        }
        return Math.max(0, Math.min(100, value));
    }

    /** Vacio pasa (ahi se usa el color de fábrica); si no, tiene que ser #RRGGBB. */
    private boolean isValidHexOrNull(String value) {
        return value == null || HEX_COLOR.matcher(value).matches();
    }

    private static String heroVariantLabel(HeroVariant variant) {
        return switch (variant) {
            case CLASSIC -> "Actual";
            case SCOREBOARD -> "Marcador de cancha";
            case COURT_SPLIT -> "Vista de cancha partida";
        };
    }

    /**
     * El campo de color de siempre, mas dos formas de no tener que escribir el
     * hex a mano: el selector nativo del navegador (que en Chrome/Edge trae
     * cuentagotas para levantar un color de cualquier parte de la pantalla) y
     * una fila de swatches con la paleta de la marca.
     */
    private VerticalLayout colorField(TextField hexField, String fallbackHex) {
        Input picker = new Input();
        picker.setType("color");
        picker.getElement().getStyle()
                .set("width", "1.75rem")
                .set("height", "1.75rem")
                .set("padding", "0")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("cursor", "pointer");
        picker.setValue(isValidHexOrNull(hexField.getValue()) && !hexField.getValue().isBlank()
                ? hexField.getValue() : fallbackHex);

        // El campo de texto sigue siendo la fuente de verdad que se guarda: el
        // selector solo lo completa. Si escriben o pegan un hex valido, el
        // selector lo sigue; si no es valido (a medio escribir), lo ignora.
        picker.addValueChangeListener(event -> hexField.setValue(event.getValue()));
        hexField.addValueChangeListener(event -> {
            if (isValidHexOrNull(event.getValue()) && event.getValue() != null
                    && !event.getValue().isBlank()) {
                picker.setValue(event.getValue());
            }
        });

        // Adentro del propio campo, como prefijo: Vaadin ya lo alinea con el
        // renglon del input, y el campo sigue estirando a todo el ancho de su
        // columna como cualquier otro de este formulario.
        hexField.setPrefixComponent(picker);
        hexField.setWidthFull();

        HorizontalLayout palette = new HorizontalLayout();
        palette.setPadding(false);
        palette.addClassNames(LumoUtility.Gap.XSMALL, LumoUtility.Margin.Top.XSMALL);
        for (String hex : COLOR_PRESETS) {
            palette.add(paletteSwatch(hex, hexField));
        }

        VerticalLayout box = new VerticalLayout(hexField, palette);
        box.setPadding(false);
        box.setSpacing(false);
        box.setWidthFull();
        box.addClassNames(LumoUtility.Gap.XSMALL);
        return box;
    }

    /** Circulo clickeable de la paleta rapida: un click carga ese hex en el campo. */
    private Div paletteSwatch(String hex, TextField hexField) {
        Div swatch = new Div();
        swatch.getElement().setAttribute("title", hex);
        swatch.getStyle()
                .set("width", "1.25rem")
                .set("height", "1.25rem")
                .set("border-radius", "50%")
                .set("background", hex)
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("cursor", "pointer");
        swatch.getElement().addEventListener("click", event -> hexField.setValue(hex));
        return swatch;
    }

    // ----------------------------------------------------------------- club

    private VerticalLayout clubForm() {
        TextField name = new TextField("Nombre del club");
        name.setValue(club.getName());

        TextField whatsapp = new TextField("WhatsApp del club");
        whatsapp.setValue(club.getWhatsappNumber());
        whatsapp.setHelperText("A este número se deriva al jugador cuando algo lo tiene que "
                + "resolver una persona");

        TimePicker open = timePicker("Abre", club.getOpenTime());
        TimePicker close = timePicker("Cierra", club.getCloseTime());
        close.setHelperText("Si es anterior a la apertura, se entiende que cierran de madrugada");

        IntegerField duration = new IntegerField("Duración del turno (minutos)");
        duration.setValue(club.getDefaultSlotDuration());
        duration.setStep(30);
        duration.setMin(30);
        duration.setMax(240);
        duration.setHelperText("Los turnos se encadenan desde la apertura");

        IntegerField cancellation = new IntegerField("Cancelación hasta (horas antes)");
        cancellation.setValue(club.getCancellationLimitHours());
        cancellation.setHelperText("Más cerca del turno, la baja la tiene que hacer el club");

        IntegerField horizon = new IntegerField("Se reserva con (días de anticipación)");
        horizon.setValue(club.getBookingHorizonDays());

        IntegerField maxActive = new IntegerField("Turnos futuros por jugador");
        maxActive.setValue(club.getMaxActiveBookings());
        maxActive.setHelperText("Evita que un mismo teléfono bloquee la agenda entera");

        Button save = new Button("Guardar Cambios", event -> {
            club.setName(name.getValue());
            club.setWhatsappNumber(whatsapp.getValue());
            club.setOpenTime(open.getValue());
            club.setCloseTime(close.getValue());
            club.setDefaultSlotDuration(duration.getValue());
            club.setCancellationLimitHours(cancellation.getValue());
            club.setBookingHorizonDays(horizon.getValue());
            club.setMaxActiveBookings(maxActive.getValue());
            club = tenantRepository.save(club);
            Notification.show("Configuración guardada");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Ocho campos seguidos no dicen nada; separados, cada grupo se contesta
        // de una: quien sos, cuando abris, y con que reglas se reserva.
        return tabContent(
                section("Identidad y contacto", name, whatsapp),
                section("Horarios", open, close, duration),
                section("Reglas de reserva", cancellation, horizon, maxActive),
                actions(save));
    }

    // -------------------------------------------------------------- canchas

    private VerticalLayout courtsTab() {
        dressGrid(courtGrid);
        courtGrid.addColumn(Court::getName).setHeader("Cancha").setFlexGrow(1);
        courtGrid.addColumn(Court::getDisplayOrder).setHeader("Orden")
                .setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(court -> "tabular");
        courtGrid.addComponentColumn(court -> {
            Checkbox active = new Checkbox(court.isActive());
            active.addValueChangeListener(event -> {
                if (event.isFromClient()) {
                    court.setActive(event.getValue());
                    courtRepository.save(court);
                    // Desactivar saca la cancha de la grilla pero conserva su historial.
                    Notification.show(event.getValue() ? "Cancha activada" : "Cancha desactivada");
                }
            });
            return active;
        }).setHeader("Activa").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);

        TextField newName = new TextField();
        newName.setPlaceholder("Nombre de la cancha");
        IntegerField newOrder = new IntegerField();
        newOrder.setPlaceholder("Orden");
        newOrder.setWidth("7em");

        Button add = new Button("Agregar cancha", event -> {
            if (newName.getValue() == null || newName.getValue().isBlank()) {
                Notification.show("Poné un nombre para la cancha");
                return;
            }
            Court court = new Court();
            court.setName(newName.getValue().trim());
            court.setDisplayOrder(newOrder.getValue() == null ? 0 : newOrder.getValue());
            courtRepository.save(court);
            newName.clear();
            newOrder.clear();
            refreshCourts();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        refreshCourts();
        HorizontalLayout toolbar = new HorizontalLayout(newName, newOrder, add);
        toolbar.setAlignItems(Alignment.END);
        toolbar.setPadding(false);
        toolbar.addClassNames(LumoUtility.Gap.SMALL);
        return tabContent(toolbar, courtGrid);
    }

    private void refreshCourts() {
        courtGrid.setItems(courtRepository.findAllByOrderByDisplayOrderAscNameAsc());
    }

    // ------------------------------------------------------------ productos

    private VerticalLayout productsTab() {
        dressGrid(productGrid);
        productGrid.addColumn(Product::getName).setHeader("Producto").setFlexGrow(1);
        productGrid.addColumn(product -> "$" + product.getUnitPrice().stripTrailingZeros().toPlainString())
                .setHeader("Precio")
                .setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(product -> "tabular");
        productGrid.addComponentColumn(product -> {
            Checkbox active = new Checkbox(product.isActive());
            active.addValueChangeListener(event -> {
                if (event.isFromClient()) {
                    productService.setActive(product.getId(), event.getValue());
                    // Desactivar lo saca del combo de venta pero conserva las ventas ya cargadas.
                    Notification.show(event.getValue() ? "Producto activado" : "Producto desactivado");
                }
            });
            return active;
        }).setHeader("Activo").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);

        TextField newName = new TextField();
        newName.setPlaceholder("Nombre del producto");
        BigDecimalField newPrice = new BigDecimalField();
        newPrice.setPlaceholder("Precio");
        newPrice.setWidth("9em");

        Button add = new Button("Agregar producto", event -> {
            try {
                productService.createProduct(newName.getValue(), newPrice.getValue());
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            newName.clear();
            newPrice.clear();
            refreshProducts();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        refreshProducts();
        HorizontalLayout toolbar = new HorizontalLayout(newName, newPrice, add);
        toolbar.setAlignItems(Alignment.END);
        toolbar.setPadding(false);
        toolbar.addClassNames(LumoUtility.Gap.SMALL);
        return tabContent(toolbar, productGrid);
    }

    private void refreshProducts() {
        productGrid.setItems(productRepository.findAllByOrderByNameAsc());
    }

    // -------------------------------------------------------------- tarifas

    private VerticalLayout pricingTab() {
        dressGrid(pricingGrid);
        pricingGrid.addColumn(rule -> "%s - %s".formatted(rule.getStartTime(), rule.getEndTime()))
                .setHeader("Franja").setAutoWidth(true).setSortable(true)
                .setPartNameGenerator(rule -> "tabular");
        pricingGrid.addColumn(this::daysText).setHeader("Días").setAutoWidth(true);
        pricingGrid.addColumn(rule -> rule.getCourt() == null
                        ? "Todas las canchas" : rule.getCourt().getName())
                .setHeader("Aplica a").setAutoWidth(true);
        pricingGrid.addColumn(rule -> "$" + perPersonPrice(rule.getPrice()))
                .setHeader("Precio por persona").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(rule -> "tabular");
        pricingGrid.addComponentColumn(rule -> {
            Paragraph label = new Paragraph(rule.isPromo() ? "PROMO" : "");
            if (rule.isPromo()) {
                label.addClassNames(LumoUtility.TextColor.PRIMARY);
            }
            return label;
        }).setHeader("Promo").setAutoWidth(true).setFlexGrow(0);
        pricingGrid.addComponentColumn(rule -> {
            Button delete = new Button("Borrar", event -> {
                if (rule.isPromo()) {
                    // Borrar una promo es una accion delicada: se pide confirmacion.
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Borrar promo");
                    dialog.setText("Se va a eliminar esta franja promocional.");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Borrar");
                    dialog.setConfirmButtonTheme(ButtonVariant.LUMO_ERROR.getVariantName());
                    dialog.addConfirmListener(e -> {
                        pricingRuleRepository.delete(rule);
                        refreshPricing();
                    });
                    dialog.open();
                } else {
                    pricingRuleRepository.delete(rule);
                    refreshPricing();
                }
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return delete;
        }).setAutoWidth(true).setFlexGrow(0);

        pricingWarning.addClassNames(LumoUtility.TextColor.ERROR);
        refreshPricing();

        // El titulo y el formulario quedan pegados: no hay espacio entre "Tarifas
        // por franja" y la fila de dias.
        VerticalLayout franja = new VerticalLayout(
                new Paragraph("Tarifas por franja"),
                newPricingRuleForm());
        franja.setPadding(false);
        franja.setSpacing(false);

        return new VerticalLayout(
                new Paragraph("Gana la regla mas especifica: primero la que nombra la cancha, "
                        + "y entre iguales, la de franja mas angosta. Si ninguna regla cubre un "
                        + "dia/franja, se cobra la tarifa general por persona."),
                generalPriceForm(),
                franja, pricingWarning, pricingGrid);
    }

    /**
     * Tarifa general por persona: el fallback de todo horario que ninguna regla
     * cubra. Sin este campo, esos turnos no se publican en la app.
     */
    private HorizontalLayout generalPriceForm() {
        BigDecimalField general = new BigDecimalField("Tarifa general (por persona)");
        general.setValue(club.getGeneralPricePerPerson());
        general.setHelperText("%d por cancha".formatted(club.getPlayersPerCourt()));
        Button save = new Button("Guardar Cambios", event -> {
            club.setGeneralPricePerPerson(general.getValue());
            club = tenantRepository.save(club);
            Notification.show("Tarifa general guardada");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout row = new HorizontalLayout(general, save);
        row.setAlignItems(Alignment.BASELINE);
        return row;
    }

    private VerticalLayout newPricingRuleForm() {
        // Etiqueta de grupo y una fila de "nombre + checkbox" (lun [x] mar [ ]…)
        // en un solo renglon. "Dias" es un rotulo con estilo de label, no un checkbox.
        Span daysLabel = new Span("Dias:");
        daysLabel.addClassNames(LumoUtility.TextColor.SECONDARY,
                LumoUtility.FontWeight.SEMIBOLD);
        daysLabel.getStyle().set("margin-right", "1.25rem");
        HorizontalLayout daysRow = new HorizontalLayout(daysLabel);
        daysRow.setAlignItems(Alignment.CENTER);
        daysRow.getStyle().set("flex-wrap", "wrap");
        daysRow.getStyle().set("column-gap", "0.5rem");
        Map<DayOfWeek, Checkbox> dayChecks = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            // El texto va a la izquierda del checkbox: "lun [x]", no "[x] lun".
            Span name = new Span(shortDayName(day));
            name.addClassNames(LumoUtility.TextColor.SECONDARY);
            Checkbox check = new Checkbox();
            check.setAriaLabel(shortDayName(day));
            check.setValue(day == DayOfWeek.MONDAY);
            HorizontalLayout item = new HorizontalLayout(name, check);
            item.setAlignItems(Alignment.CENTER);
            item.setSpacing(false);
            item.getStyle().set("gap", "0.3rem");
            dayChecks.put(day, check);
            daysRow.add(item);
        }

        Select<Court> court = new Select<>();
        court.setLabel("Cancha");
        court.setItems(courtRepository.findAllByOrderByDisplayOrderAscNameAsc());
        court.setEmptySelectionAllowed(true);
        court.setEmptySelectionCaption("Todas");
        // Null-safe a proposito: al habilitar la opcion vacia, Select invoca el
        // generador de etiquetas con null para rotular esa opcion.
        court.setItemLabelGenerator(item -> item == null ? "Todas" : item.getName());

        TimePicker from = timePicker("Desde", LocalTime.of(8, 0));
        TimePicker to = timePicker("Hasta", LocalTime.of(18, 0));

        BigDecimalField price = new BigDecimalField("Precio (por persona)");

        Checkbox promo = new Checkbox("Es promo", false);

        Button add = new Button("Agregar tarifa", event -> {
            Set<DayOfWeek> selected = dayChecks.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            if (selected.isEmpty()) {
                Notification.show("Elegi al menos un dia");
                return;
            }
            if (price.getValue() == null || price.getValue().compareTo(BigDecimal.ZERO) < 0) {
                Notification.show("Poné un precio valido");
                return;
            }
            if (!to.getValue().isAfter(from.getValue())) {
                Notification.show("La franja tiene que terminar despues de empezar");
                return;
            }
            PricingRule rule = new PricingRule();
            rule.setDays(selected);
            rule.setCourt(court.getValue());
            rule.setStartTime(from.getValue());
            rule.setEndTime(to.getValue());
            // El panel carga el precio POR PERSONA; por dentro se guarda el del turno.
            rule.setPrice(price.getValue().multiply(BigDecimal.valueOf(club.getPlayersPerCourt())));
            rule.setPromo(promo.getValue());
            pricingRuleRepository.save(rule);
            dayChecks.values().forEach(check -> check.setValue(false));
            price.clear();
            promo.clear();
            refreshPricing();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Los dias en su propia linea, los campos en la de abajo y el boton en la
        // ultima, separado con un poco de aire de los datos.
        HorizontalLayout fields = new HorizontalLayout(court, from, to, price, promo);
        fields.setAlignItems(Alignment.BASELINE);
        fields.getStyle().set("flex-wrap", "wrap");

        HorizontalLayout actions = new HorizontalLayout(add);
        actions.getStyle().set("margin-top", "0.75rem");

        VerticalLayout form = new VerticalLayout(daysRow, fields, actions);
        form.setPadding(false);
        form.setSpacing(false);
        return form;
    }

    private void refreshPricing() {
        var rules = pricingRuleRepository.findAllByOrderByStartTimeAsc();
        pricingGrid.setItems(rules);

        // Un horario sin regla ni tarifa general no se publica, asi que el club
        // tiene que enterarse antes de que un jugador no encuentre turnos.
        boolean covered = club.getGeneralPricePerPerson() != null
                || !rules.isEmpty();
        pricingWarning.setText(covered
                ? ""
                : "No hay tarifas cargadas. Cargá una tarifa general o al menos una franja "
                        + "para que la grilla se publique.");
    }

    private String daysText(PricingRule rule) {
        List<String> names = rule.getDays().stream()
                .sorted()
                .map(this::shortDayName)
                .toList();
        return String.join(", ", names);
    }

    /** El panel guarda el precio del turno completo; el dato se muestra por persona. */
    private String perPersonPrice(BigDecimal courtPrice) {
        return courtPrice.divide(BigDecimal.valueOf(club.getPlayersPerCourt()))
                .stripTrailingZeros().toPlainString();
    }

    private String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, ES_AR);
    }

    private String shortDayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, ES_AR);
    }

    /** Reloj de 24 horas: en Argentina nadie dice "8:00 PM". */
    private TimePicker timePicker(String label, LocalTime value) {
        TimePicker picker = new TimePicker(label);
        picker.setLocale(CLOCK);
        picker.setStep(java.time.Duration.ofMinutes(30));
        picker.setValue(value);
        return picker;
    }

    // --------------------------------------------------------- cobros online

    private VerticalLayout paymentsForm() {
        Checkbox allowUnpaid = new Checkbox("Aceptar reservas sin seña (de palabra)");
        allowUnpaid.setValue(club.isAllowUnpaidBooking());

        Checkbox requiresConfirmation = new Checkbox("Pedir confirmación por WhatsApp");
        requiresConfirmation.setValue(club.isRequiresBookingConfirmation());
        requiresConfirmation.setHelperText("Si lo destildás, las reservas de palabra quedan "
                + "confirmadas al toque, sin que el jugador tenga que tocar el link de WhatsApp. "
                + "Sacás fricción, pero corrés más riesgo de ausentes en turnos que nadie llegó a "
                + "confirmar. Ojo: tildarlo solo sirve si WhatsApp está activo a nivel plataforma "
                + "(no en stand by) — si no, la reserva queda esperando un link que nunca llega.");

        BigDecimalField deposit = new BigDecimalField("Seña (% del turno)");
        deposit.setValue(club.getDepositPercentage());

        PasswordField token = new PasswordField("Access token de MercadoPago");
        token.setPlaceholder(club.acceptsOnlinePayments()
                ? "Ya cargado. Escribí uno nuevo solo si querés reemplazarlo."
                : "Sin cargar: el pago online está deshabilitado");
        token.setHelperText("Se guarda cifrado");

        PasswordField secret = new PasswordField("Clave secreta de webhooks");
        secret.setPlaceholder(club.getMpWebhookSecret() != null && !club.getMpWebhookSecret().isBlank()
                ? "Ya cargada. Escribí una nueva solo si querés reemplazarla."
                : "Sin cargar: las notificaciones de pago se rechazan");
        secret.setHelperText("Sin esto no se puede verificar que un aviso de pago sea real");

        Paragraph webhookUrl = new Paragraph(
                "URL a configurar en MercadoPago: /api/webhooks/mercadopago/" + club.getSlug());
        webhookUrl.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.NONE);

        Button save = new Button("Guardar Cambios", event -> {
            club.setAllowUnpaidBooking(allowUnpaid.getValue());
            club.setRequiresBookingConfirmation(requiresConfirmation.getValue());
            club.setDepositPercentage(deposit.getValue());
            // Vacio significa "no lo toques": mostrar el token guardado seria
            // exponerlo en pantalla sin ninguna necesidad.
            if (token.getValue() != null && !token.getValue().isBlank()) {
                club.setMpAccessToken(token.getValue().trim());
            }
            if (secret.getValue() != null && !secret.getValue().isBlank()) {
                club.setMpWebhookSecret(secret.getValue().trim());
            }
            club = tenantRepository.save(club);
            token.clear();
            secret.clear();
            Notification.show("Cobros actualizados");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Las credenciales van aparte de la politica de cobro: son dos cosas que
        // se tocan en momentos distintos, y meter dos PasswordField al lado de
        // un checkbox hacia que la pestana pareciera un solo bloque de riesgo.
        return tabContent(
                section("Cómo se cobra", allowUnpaid, requiresConfirmation, deposit),
                section("Credenciales de MercadoPago", token, secret),
                webhookUrl,
                actions(save));
    }

    // ---------------------------------------------------------------- usuarios

    /**
     * Un solo usuario de mostrador por club, sin pantalla de alta multiple: si
     * ya existe, esta pestana solo deja habilitarlo/deshabilitarlo y cambiarle
     * la contraseña; si no existe, deja crearlo.
     */
    private VerticalLayout usersTab() {
        usersContent.setPadding(false);
        usersContent.setSpacing(false);
        usersContent.addClassNames(LumoUtility.Gap.XLARGE);
        refreshUsers();
        return tabContent(usersContent);
    }

    private void refreshUsers() {
        usersContent.removeAll();
        Optional<ClubUser> staff = clubUserService.findStaff(club.getId());
        if (staff.isPresent()) {
            addExistingStaffSection(staff.get());
        } else {
            addNewStaffSection();
        }
    }

    private void addExistingStaffSection(ClubUser staff) {
        TextField name = new TextField("Nombre");
        name.setValue(staff.getFullName());
        name.setReadOnly(true);

        TextField email = new TextField("Mail");
        email.setValue(staff.getEmail());
        email.setReadOnly(true);

        Checkbox enabled = new Checkbox("Acceso habilitado");
        enabled.setValue(staff.isEnabled());
        enabled.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                clubUserService.setEnabled(club, staff.getId(), event.getValue());
                Notification.show(event.getValue() ? "Acceso habilitado" : "Acceso deshabilitado");
            }
        });

        PasswordField newPassword = new PasswordField("Nueva contraseña");
        newPassword.setHelperText(
                "Al menos 8 caracteres. Se la compartís vos al empleado, no se manda ningún mail.");

        Button changePassword = new Button("Actualizar contraseña", event -> {
            try {
                clubUserService.setPassword(club, staff.getId(), newPassword.getValue());
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            newPassword.clear();
            Notification.show("Contraseña actualizada");
        });
        changePassword.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        usersContent.add(
                section("Usuario de mostrador", name, email, enabled),
                section("Cambiar su contraseña", newPassword, actions(changePassword)));
    }

    private void addNewStaffSection() {
        TextField name = new TextField("Nombre");
        TextField email = new TextField("Mail");
        PasswordField password = new PasswordField("Contraseña inicial");
        password.setHelperText(
                "Al menos 8 caracteres. Se la compartís vos al empleado, no se manda ningún mail.");

        Button create = new Button("Crear usuario de mostrador", event -> {
            try {
                clubUserService.createStaff(club, name.getValue(), email.getValue(), password.getValue());
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            Notification.show("Usuario creado");
            refreshUsers();
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        usersContent.add(
                section("Usuario de mostrador", name, email, password),
                actions(create));
    }
}
