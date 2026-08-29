package ar.com.padelnec.ui;

import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.AlertService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
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
public class MainLayout extends AppLayout {

    private final transient AuthenticationContext authenticationContext;
    private final AlertService alertService;

    public MainLayout(AuthenticationContext authenticationContext, AlertService alertService) {
        this.authenticationContext = authenticationContext;
        this.alertService = alertService;

        setPrimarySection(Section.DRAWER);
        addToNavbar(true, new DrawerToggle(), header());
        addToDrawer(navigation());
    }

    private HorizontalLayout header() {
        H1 title = new H1("Panel del club");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        HorizontalLayout header = new HorizontalLayout(title, spacer(), user(), logout());
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM);
        return header;
    }

    private Span spacer() {
        Span spacer = new Span();
        spacer.getStyle().set("flex-grow", "1");
        return spacer;
    }

    private Span user() {
        return new Span(currentUser().map(ClubUserPrincipal::fullName).orElse(""));
    }

    private Button logout() {
        Button logout = new Button("Salir", event -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return logout;
    }

    private VerticalLayout navigation() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Agenda", AgendaView.class, VaadinIcon.CALENDAR.create()));
        nav.addItem(new SideNavItem("Turnos fijos", RecurringView.class, VaadinIcon.REFRESH.create()));
        nav.addItem(new SideNavItem("Jugadores", CustomersView.class, VaadinIcon.USERS.create()));
        nav.addItem(new SideNavItem("Alertas", AlertsView.class, VaadinIcon.BELL.create()));

        // Configuracion solo para el dueno: el mostrador no toca precios ni horarios.
        if (currentUser().map(ClubUserPrincipal::canManageSettings).orElse(false)) {
            nav.addItem(new SideNavItem("Configuracion", SettingsView.class, VaadinIcon.COG.create()));
        }

        VerticalLayout drawer = new VerticalLayout(nav, pendingAlertsBadge());
        drawer.setPadding(false);
        drawer.setSpacing(false);
        return drawer;
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
        badge.addClassNames(LumoUtility.Margin.MEDIUM);
        return badge;
    }

    private Optional<ClubUserPrincipal> currentUser() {
        return authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class);
    }
}
