package ar.com.padelnec.gym.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.IconFactory;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import java.util.function.Function;

/**
 * El grupo "Gimnasio" del menu lateral del panel.
 *
 * <p>Vive aca y no en {@code MainLayout} para que el panel de padel solo tenga
 * que agregar una linea: el resto del modulo puede cambiar sin tocarlo.
 */
public final class GymNavigation {

    private GymNavigation() {
    }

    /**
     * @param canManage  el dueno ve ademas la configuracion de sedes y QR
     * @param iconChip   como el panel dibuja el icono de cada item, para que se vea igual que el resto
     */
    public static SideNav menu(boolean canManage, Function<IconFactory, Component> iconChip) {
        SideNav nav = new SideNav();
        nav.setLabel("Gimnasio");
        nav.setWidthFull();
        nav.addItem(new SideNavItem("Socios", GymMembersView.class, iconChip.apply(VaadinIcon.USERS)));
        nav.addItem(new SideNavItem("Ingresos de hoy", GymTodayView.class, iconChip.apply(VaadinIcon.CHECK_CIRCLE_O)));
        if (canManage) {
            nav.addItem(new SideNavItem("Sedes y QR", GymSedesView.class, iconChip.apply(VaadinIcon.QRCODE)));
            nav.addItem(new SideNavItem("Cuotas", GymTariffsView.class, iconChip.apply(VaadinIcon.MONEY)));
        }
        return nav;
    }
}
