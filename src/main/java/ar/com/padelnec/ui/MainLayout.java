package ar.com.padelnec.ui;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.AlertService;
import ar.com.padelnec.service.TenantService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.IconFactory;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoIcon;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import java.util.Optional;

/**
 * Marco comun del panel: navegacion, usuario y el aviso de alertas pendientes.
 *
 * <p>Lleva su propia anotacion de acceso porque Vaadin evalua tambien el layout
 * padre: una vista accesible dentro de un layout sin anotar queda denegada.
 * Cualquier usuario autenticado del club puede ver el marco; que puede hacer
 * adentro lo decide cada vista.
 */
@PermitAll
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final transient AuthenticationContext authenticationContext;
    private final AlertService alertService;
    private final String clubName;

    /**
     * La pagina publica del club, la que se comparte fuera del panel. Mismo
     * patron de URL que ya arma {@code NotificationService} para los links que
     * viajan por WhatsApp -- si el dia de mañana cambia, hay que tocar los dos
     * lugares.
     */
    private final String publicUrl;

    /** Se rellena en cada navegacion con el nombre de la pantalla activa. */
    private final H1 viewTitle = new H1();

    public MainLayout(AuthenticationContext authenticationContext, AlertService alertService,
                      TenantService tenantService, AppProperties properties) {
        this.authenticationContext = authenticationContext;
        this.alertService = alertService;
        Tenant club = tenantService.requireCurrent();
        this.clubName = club.getName();
        this.publicUrl = properties.getBaseUrl() + "/club/" + club.getSlug();

        setPrimarySection(Section.DRAWER);
        addToNavbar(true, new DrawerToggle(), header());
        addToDrawer(navigation());
    }

    /**
     * Barra superior: a la izquierda donde estas, a la derecha el link de la
     * pagina y con quien entraste.
     *
     * <p>El club no se repite aca porque ya encabeza el menu lateral; la barra
     * se queda con lo que si cambia al navegar.
     */
    private HorizontalLayout header() {
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE,
                LumoUtility.FontWeight.SEMIBOLD, LumoUtility.Whitespace.NOWRAP);

        // Agrupados aparte del titulo: el titulo cambia de pantalla en pantalla y
        // se queda pegado a la izquierda, este bloque es siempre lo mismo y se
        // queda pegado a la derecha, sin que "space-between" los separe a ellos
        // dos entre si.
        HorizontalLayout actions = new HorizontalLayout(shareLinkButton(), userMenu());
        actions.setPadding(false);
        actions.setAlignItems(HorizontalLayout.Alignment.CENTER);
        actions.addClassNames(LumoUtility.Gap.SMALL);

        HorizontalLayout header = new HorizontalLayout(viewTitle, actions);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        header.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM, LumoUtility.Gap.MEDIUM);
        return header;
    }

    /**
     * El link de la pagina del club, para compartir por fuera del panel.
     *
     * <p>Vive en la barra superior y no en el menu lateral: la barra no se
     * esconde nunca, ni siquiera en el celular con el drawer cerrado, y es el
     * dato que mas se pide de golpe ("pasame el link de la cancha") sin que
     * haga falta ir a buscarlo a Configuracion.
     *
     * <p>Solo el boton, sin el link de texto al lado: mostrar los dos juntos
     * quedaba redundante ("aca esta el link... copiar link"). El link real
     * sigue disponible en el tooltip, para quien lo quiera leer o confirmar
     * que es el de su club.
     */
    private Component shareLinkButton() {
        Button copy = new Button("Copiar link", VaadinIcon.COPY_O.create(), event -> copyPublicLink());
        copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        copy.setAriaLabel("Copiar link de la página del club");
        copy.getElement().setAttribute("title", "Copiar link de la página de reservas: " + publicUrl);
        // Mismo color que el fondo tinte: sin esto el boton queda gris por
        // defecto, un detalle mas entre el resto de la barra.
        copy.getStyle().set("color", "var(--lumo-primary-text-color)");
        copy.addClassNames(LumoUtility.Gap.XSMALL, LumoUtility.Padding.Horizontal.SMALL,
                LumoUtility.Border.ALL, LumoUtility.BorderRadius.FULL);
        copy.addClassName("share-link");
        copy.setHeight("2.25rem");
        return copy;
    }

    /**
     * Copia al portapapeles y avisa si no se pudo: el navegador puede negarse
     * (un sitio sin HTTPS que no sea localhost, o el usuario sin haber tocado la
     * pagina todavia), y sin este chequeo el club creeria que ya lo tiene
     * copiado cuando en realidad no paso nada.
     */
    private void copyPublicLink() {
        UI.getCurrent().getPage()
                .executeJs("return navigator.clipboard.writeText($0).then(() => true).catch(() => false)",
                        publicUrl)
                .then(Boolean.class, copied -> {
                    if (Boolean.TRUE.equals(copied)) {
                        Notification.show("Link copiado", 2500, Notification.Position.BOTTOM_START);
                    } else {
                        Notification failure = Notification.show(
                                "No se pudo copiar. El link es: " + publicUrl, 6000,
                                Notification.Position.BOTTOM_START);
                        failure.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                });
    }

    /**
     * Marca del panel, arriba de todo en el menu lateral.
     *
     * <p>El club vive aca y no en la barra superior: es lo que no cambia nunca
     * mientras se navega, y la barra queda libre para decir en que pantalla
     * estas parado.
     */
    private HorizontalLayout brand() {
        Span mark = new Span("▦");
        mark.setClassName("brand-mark");
        mark.getElement().setAttribute("aria-hidden", "true");

        H1 club = new H1(clubName);
        club.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.Margin.NONE,
                LumoUtility.FontWeight.SEMIBOLD);

        Span product = new Span("Panel del club");
        product.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout words = new VerticalLayout(club, product);
        words.setPadding(false);
        words.setSpacing(false);
        words.setWidth(null);

        HorizontalLayout brand = new HorizontalLayout(mark, words);
        brand.setAlignItems(HorizontalLayout.Alignment.CENTER);
        brand.setPadding(false);
        brand.setWidthFull();
        brand.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.Padding.Horizontal.SMALL,
                LumoUtility.Padding.Vertical.MEDIUM, LumoUtility.Border.BOTTOM,
                LumoUtility.BorderColor.CONTRAST_10);
        return brand;
    }

    /**
     * La barra superior dice en que pantalla estas.
     *
     * <p>El titulo sale del @PageTitle de la vista, que ya viene como
     * "Agenda | Panel del club": del " | " para atras es el sufijo del titulo
     * del navegador y aca sobra.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        viewTitle.setText(currentViewTitle());
    }

    private String currentViewTitle() {
        Component content = getContent();
        if (content == null) {
            return "";
        }
        PageTitle annotation = content.getClass().getAnnotation(PageTitle.class);
        if (annotation == null) {
            return "";
        }
        return annotation.value().split("\\|")[0].trim();
    }

    /**
     * Identidad y sesion en un solo control.
     *
     * <p>Antes eran dos piezas sueltas al borde de la barra: el nombre en un
     * Span y un boton "Salir" al lado. Agrupadas en un menu, la barra queda con
     * dos bloques y el cierre de sesion deja de estar a un clic de distancia de
     * cualquier otra cosa que el mostrador toque con apuro.
     */
    private MenuBar userMenu() {
        MenuBar bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        String name = currentUser().map(ClubUserPrincipal::fullName).orElse("");

        Avatar avatar = new Avatar(name);
        avatar.setThemeName("xsmall");
        Span label = new Span(name);
        label.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.MEDIUM,
                LumoUtility.Whitespace.NOWRAP);

        HorizontalLayout trigger = new HorizontalLayout(avatar, label);
        trigger.setAlignItems(HorizontalLayout.Alignment.CENTER);
        trigger.setPadding(false);
        trigger.addClassNames(LumoUtility.Gap.SMALL);

        MenuItem item = bar.addItem(trigger);
        SubMenu menu = item.getSubMenu();

        // El rol explica por que la Configuracion esta o no en el menu lateral.
        Span role = new Span(currentUser().map(MainLayout::roleLabel).orElse(""));
        role.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        menu.addItem(role).setEnabled(false);

        menu.addItem("Salir", event -> authenticationContext.logout());
        return bar;
    }

    private static String roleLabel(ClubUserPrincipal user) {
        return switch (user.role()) {
            case OWNER -> "Dueño del club";
            case STAFF -> "Mostrador";
            case SUPER_ADMIN -> "Soporte de la plataforma";
        };
    }

    private VerticalLayout navigation() {
        // El trabajo diario primero y la configuracion aparte, abajo: no es una
        // lista de cosas equivalentes, son las pantallas de mostrador mas un
        // cajon de ajustes que el dueno abre de vez en cuando.
        SideNav diario = new SideNav();
        diario.setLabel("Mostrador");
        // El drawer alinea sus hijos por contenido (flex-start), no por ancho: sin
        // esto cada SideNav se achica al item mas angosto y deja aire a la derecha
        // que se lee como que el item termina ahi.
        diario.setWidthFull();
        diario.addItem(new SideNavItem("Agenda", AgendaView.class, iconChip(LumoIcon.CALENDAR)));
        // Pegada a la agenda porque son el mismo dia visto de dos maneras, y del
        // lado del mostrador y no de Estadisticas: el que cuenta los billetes a
        // la noche es el que atiende.
        diario.addItem(new SideNavItem("Caja", CajaView.class, iconChip(LumoIcon.ORDERED_LIST)));
        diario.addItem(new SideNavItem("Turnos fijos", RecurringView.class, iconChip(LumoIcon.RELOAD)));
        diario.addItem(new SideNavItem("Jugadores", CustomersView.class, iconChip(LumoIcon.USER)));
        diario.addItem(alertsItem());
        // Suspender un dia o una cancha es una decision operativa del dia a dia,
        // no financiera: el mostrador tambien la necesita.
        diario.addItem(new SideNavItem("Suspensiones", BlackoutsView.class, iconChip(LumoIcon.CLOCK)));

        VerticalLayout drawer = new VerticalLayout(brand(), diario);
        drawer.setPadding(false);
        drawer.setSpacing(false);
        drawer.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.Gap.MEDIUM);

        // Configuracion no es del dia a dia, es del club: vive aca y no en
        // "Mostrador" aunque el mostrador tambien entre -- adentro, la propia
        // vista le esconde la pestana de Cobros online, que es la unica plata
        // de verdad. Estadisticas si sigue siendo solo del dueno.
        SideNav club = new SideNav();
        club.setLabel("Club");
        club.setWidthFull();
        club.addItem(new SideNavItem("Configuración", SettingsView.class, iconChip(LumoIcon.COG)));
        if (currentUser().map(ClubUserPrincipal::canManageSettings).orElse(false)) {
            club.addItem(new SideNavItem("Estadísticas", DashboardView.class, iconChip(LumoIcon.BAR_CHART)));
        }
        drawer.add(club);

        drawer.add(pendingAlertsBadge());
        return drawer;
    }

    /**
     * Item "Alertas" con un punto rojo cuando hay algo sin resolver.
     *
     * <p>El aviso de mas abajo (pendingAlertsBadge) hay que verlo desplazando la
     * vista; este punto esta pegado al item que ya se toca para entrar, asi que se
     * nota sin buscarlo.
     */
    private SideNavItem alertsItem() {
        SideNavItem item = new SideNavItem("Alertas", AlertsView.class, iconChip(LumoIcon.BELL));
        if (alertService.pendingCount() > 0) {
            item.setSuffixComponent(pendingDot());
        }
        return item;
    }

    // El azul de fabrica de Lumo, no el --lumo-primary-color de la app (el
    // ladrillo de marca): el pedido fue ese celeste puntual para los iconos del
    // menu, no el acento del club.
    private static final String ICON_BG = "#dbeafe";
    private static final String ICON_FG = "#1676f3";

    /**
     * Icono del set nuevo de Lumo ({@link LumoIcon}, SVG, mas fino que los
     * glifos clasicos de {@code VaadinIcon}) con su propia chapa de color en
     * vez del trazo mudo por defecto.
     */
    private Component iconChip(IconFactory iconFactory) {
        Icon icon = iconFactory.create();
        icon.setSize("1.375rem");
        icon.getStyle().set("color", ICON_FG);

        Span chip = new Span(icon);
        chip.getElement().setAttribute("aria-hidden", "true");
        chip.addClassNames(LumoUtility.Display.FLEX, LumoUtility.BorderRadius.MEDIUM);
        chip.getStyle()
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("width", "2.25rem")
                .set("height", "2.25rem")
                .set("flex", "none")
                .set("background", ICON_BG);
        return chip;
    }

    private Span pendingDot() {
        Span dot = new Span();
        dot.getElement().setAttribute("aria-hidden", "true");
        dot.addClassNames(LumoUtility.Background.ERROR, LumoUtility.BorderRadius.FULL,
                LumoUtility.Display.INLINE_BLOCK);
        dot.getStyle().set("width", "0.5rem").set("height", "0.5rem");
        return dot;
    }

    /**
     * Aviso permanente de lo que quedo sin resolver.
     *
     * <p>Tipicamente son devoluciones de senas: si eso no se ve, el club se entera
     * cuando el jugador reclama.
     */
    private Span pendingAlertsBadge() {
        long pending = alertService.pendingCount();
        if (pending == 0) {
            return new Span();
        }
        Span badge = new Span(pending == 1
                ? "1 alerta sin resolver"
                : pending + " alertas sin resolver");
        badge.getElement().getThemeList().add("badge error");
        // Se despega del bloque de navegacion: es un aviso, no un item mas.
        badge.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Horizontal.SMALL);
        return badge;
    }

    private Optional<ClubUserPrincipal> currentUser() {
        return authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class);
    }
}
