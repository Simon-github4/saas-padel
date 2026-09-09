package ar.com.padelnec.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Aplica el tema del panel.
 *
 * <p>Vaadin exige que la anotacion viva en una clase que implemente
 * AppShellConfigurator, y hay una sola por aplicacion. Va aparte de
 * PadelSaasApplication a proposito: el arranque de Spring no tiene por que
 * cargar con la configuracion visual.
 *
 * <p>La hoja de utilidades se pide explicitamente: en Vaadin 25 el resto de
 * los modulos de Lumo se cargan solos al extender el tema, pero las clases de
 * LumoUtility no. Sin esta linea, todos los Padding/Gap/FontSize que las
 * vistas ya escriben quedan como texto muerto en el HTML.
 */
@Theme("padel-saas")
@StyleSheet(Lumo.UTILITY_STYLESHEET)
public class AppShell implements AppShellConfigurator {
}
