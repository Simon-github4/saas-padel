package ar.com.padelnec.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellSettings;
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

    /**
     * El panel se instala como app propia, "TurnosPadel Club" (ver
     * {@code PanelPwaController}), con icono y nombre distintos de los de la app del
     * jugador, tambien en la pestaña del navegador. En iPhone hace falta instalarlo
     * para recibir los avisos de reservas: Safari solo los permite desde la pantalla
     * de inicio.
     *
     * <p>El script guarda el aviso de "esta app se puede instalar" del navegador, que
     * llega una sola vez y muy temprano, antes de que el panel termine de arrancar:
     * sin esto el boton de {@link InstallAppButton} no tendria con que instalar.
     */
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addLink("manifest", "/panel.webmanifest");
        settings.addLink("apple-touch-icon", "/panel/icons/apple-touch-icon.png");
        settings.addFavIcon("icon", "/panel/icons/favicon-32.png", "32x32");
        settings.addMetaTag("apple-mobile-web-app-title", "TP Club");
        settings.addMetaTag("theme-color", "#c2410c");
        settings.addInlineWithContents(Inline.Position.PREPEND, InstallAppButton.EARLY_CAPTURE,
                Inline.Wrapping.JAVASCRIPT);
    }
}
