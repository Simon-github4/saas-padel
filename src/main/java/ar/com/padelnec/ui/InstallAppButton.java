package ar.com.padelnec.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.OrderedList;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.List;

/**
 * "Instalar app": deja el panel como app propia, "TurnosPadel Club", en la compu o en
 * el celular, separada de la app del jugador (otro icono y otro nombre, ver
 * {@code PanelPwaController}).
 *
 * <p>Cada navegador lo hace distinto:
 * <ul>
 *   <li>Chrome, Edge y Samsung Internet (compu y Android) tienen su propio
 *       instalador: el boton lo abre directo.</li>
 *   <li>iPhone y iPad no tienen instalador: se agrega a inicio desde Compartir, y el
 *       boton muestra los pasos. Ahi importa mas que en ningun lado, porque sin
 *       instalarla no llegan los avisos de reservas.</li>
 *   <li>Safari en la Mac la agrega al Dock desde el menu Archivo: tambien los pasos.</li>
 *   <li>Firefox en la compu no instala apps: el boton no aparece.</li>
 * </ul>
 * Con la app ya abierta como app instalada, tampoco aparece.
 *
 * <p>El toque se atiende en el navegador, como en {@link DevicePushItem}: el
 * instalador solo se puede abrir en el momento del toque, no despues de una vuelta
 * al servidor. Lo que el servidor resuelve es si el boton se ve y los pasos.
 */
class InstallAppButton extends Button {

    /**
     * Va en la cabecera de todas las paginas del panel ({@link AppShell}): el aviso de
     * que la app se puede instalar llega una sola vez, apenas carga la pagina, y sin
     * guardarlo aca se pierde antes de que exista el boton.
     */
    static final String EARLY_CAPTURE = """
            window.addEventListener('beforeinstallprompt', function (event) {
              event.preventDefault();
              window.__panelInstall = event;
              window.dispatchEvent(new Event('panel-install-change'));
            });
            window.addEventListener('appinstalled', function () {
              window.__panelInstall = null;
              window.__panelInstalled = true;
              window.dispatchEvent(new Event('panel-install-change'));
            });
            """;

    /** Que caso es este navegador, y el toque. */
    private static final String SETUP = """
            const host = this;
            const kind = () => {
              if (window.__panelInstalled || matchMedia('(display-mode: standalone)').matches
                  || navigator.standalone === true) {
                return 'installed';
              }
              if (window.__panelInstall) { return 'prompt'; }
              const ua = navigator.userAgent;
              if (/iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)) {
                return 'ios';
              }
              if (/Android/.test(ua)) { return 'android'; }
              if (/Macintosh/.test(ua) && /Safari/.test(ua) && !/Chrome|Chromium|Edg|Firefox/.test(ua)) {
                return 'mac-safari';
              }
              // Chrome o Edge en la compu que todavia no aviso (o al que ya le dijeron que
              // no): se instala igual desde la barra de direcciones.
              if (/Chrome|Edg/.test(ua) && !/Firefox/.test(ua)) { return 'desktop'; }
              return 'none';
            };
            if (!host.__installReady) {
              host.__installReady = true;
              host.addEventListener('click', async () => {
                const current = kind();
                if (current !== 'prompt') {
                  host.$server.showSteps(current);
                  return;
                }
                const prompt = window.__panelInstall;
                window.__panelInstall = null;
                prompt.prompt();
                const choice = await prompt.userChoice;
                host.$server.installed(choice.outcome === 'accepted');
              });
              window.addEventListener('panel-install-change', () => host.$server.update(kind()));
            }
            host.$server.update(kind());
            """;

    InstallAppButton(String text) {
        super(text, VaadinIcon.DOWNLOAD_ALT.create());
        addClassName("install-app");
        setTooltipText("Instalar el panel como app en este dispositivo");
        // Hasta que el navegador diga que caso es, no se sabe si ofrecerlo.
        show(false);
    }

    /**
     * Con el atributo y no con {@code setVisible}: Vaadin no manda al navegador un
     * componente que nace invisible, y entonces nunca correria el codigo que decide si
     * mostrarlo.
     */
    private void show(boolean visible) {
        getElement().setAttribute("hidden", !visible);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        getElement().executeJs(SETUP);
    }

    @ClientCallable
    void update(String kind) {
        show(!"installed".equals(kind) && !"none".equals(kind));
    }

    @ClientCallable
    void installed(boolean accepted) {
        if (accepted) {
            show(false);
            Notification.show("Listo: la app «TP Club» quedó instalada. La vas a encontrar con el ícono azul.",
                    5000, Notification.Position.BOTTOM_START);
        }
    }

    @ClientCallable
    void showSteps(String kind) {
        List<String> steps = switch (kind) {
            case "ios" -> List.of(
                    "Tocá Compartir: el cuadrado con la flecha hacia arriba (abajo en Safari, arriba en Chrome).",
                    "Bajá y elegí «Agregar a inicio».",
                    "Tocá «Agregar». Aparece «TP Club» en la pantalla de inicio, con el ícono azul.");
            case "android" -> List.of(
                    "Abrí el menú del navegador (los tres puntos).",
                    "Elegí «Instalar app» o «Agregar a la pantalla de inicio».",
                    "Confirmá. Aparece «TP Club» entre tus apps, con el ícono azul.");
            case "mac-safari" -> List.of(
                    "En la barra de menú de arriba, abrí «Archivo».",
                    "Elegí «Agregar al Dock».",
                    "Confirmá. «TurnosPadel Club» queda en el Dock como una app más.");
            default -> List.of(
                    "Hacé clic en el ícono de instalar, a la derecha de la barra de direcciones "
                            + "(una pantalla con una flecha).",
                    "Si no lo ves, abrí el menú del navegador (los tres puntos) y buscá «Instalar TurnosPadel Club».",
                    "Confirmá. Se abre en su propia ventana y queda en el menú de inicio o en el escritorio.");
        };
        OrderedList list = new OrderedList();
        steps.forEach(step -> list.add(new ListItem(step)));
        list.addClassNames(LumoUtility.Margin.Vertical.NONE, LumoUtility.Padding.Left.LARGE);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Instalar la app del club");
        dialog.add(list);
        if ("ios".equals(kind)) {
            // Es la razon de mas peso para instalarla en el iPhone.
            Paragraph push = new Paragraph("En iPhone, los avisos de reservas nuevas solo llegan con la app instalada.");
            push.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
            dialog.add(push);
        }
        Button ok = new Button("Entendido", event -> dialog.close());
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ok);
        dialog.setMaxWidth("28rem");
        dialog.open();
    }
}
