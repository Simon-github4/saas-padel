package ar.com.padelnec.ui;

import ar.com.padelnec.notification.push.PanelPushService;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.VaadinRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * "Avisarme en este celular", en el menu del usuario: los avisos de reservas nuevas
 * en este dispositivo, aunque el panel este cerrado (Web Push).
 *
 * <p>Es de cada persona en cada dispositivo. El estado lo pregunta el navegador al
 * abrir el panel: si ya esta suscripto y el servidor tiene esa suscripcion para
 * este usuario, la opcion sale tildada. Tocarla de nuevo la apaga.
 *
 * <p>El click se atiende en el navegador y no en el servidor: el permiso de
 * notificaciones solo se puede pedir en el momento del toque (Safari y Firefox lo
 * niegan si llega despues de una vuelta al servidor). El resultado vuelve por
 * {@link MainLayout#pushReply}.
 */
@Slf4j
final class DevicePushItem {

    /**
     * Instala {@code window.panelPush} una sola vez por pagina.
     *
     * <p>El service worker ({@code /panel-sw.js}, ver {@code PanelPwaController}) se
     * registra con alcance {@code /admin/} y solo muestra avisos: no intercepta nada.
     * Si las claves del servidor cambiaron, la suscripcion vieja no sirve y se
     * cambia por una nueva.
     */
    private static final String SETUP = """
            if (!window.panelPush) {
              const decode = (base64) => {
                const padded = (base64 + '='.repeat((4 - base64.length % 4) % 4)).replace(/-/g, '+').replace(/_/g, '/');
                return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
              };
              const sameKey = (subscription, key) => {
                const current = subscription.options && subscription.options.applicationServerKey;
                if (!current) { return true; }
                const a = new Uint8Array(current);
                const b = decode(key);
                return a.length === b.length && a.every((value, i) => value === b[i]);
              };
              const ios = /iPad|iPhone|iPod/.test(navigator.userAgent)
                  || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
              const installed = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
              const mobile = navigator.userAgentData ? navigator.userAgentData.mobile
                  : /Mobi|Android|iPhone|iPad/.test(navigator.userAgent);
              const registration = async () => {
                const reg = await navigator.serviceWorker.register('/panel-sw.js', {scope: '/admin/'});
                const worker = reg.installing || reg.waiting;
                if (!reg.active && worker) {
                  await new Promise((resolve) => worker.addEventListener('statechange', () => {
                    if (worker.state === 'activated') { resolve(); }
                  }));
                }
                return reg;
              };
              const current = async (key) => {
                const subscription = await (await registration()).pushManager.getSubscription();
                return subscription && sameKey(subscription, key) ? subscription : null;
              };
              window.panelPush = {
                key: null,
                ready: false,
                on: false,
                async state(key) {
                  this.key = key;
                  if (ios && !installed) { return {state: 'ios-install', mobile}; }
                  if (!window.isSecureContext || !('serviceWorker' in navigator) || !('PushManager' in window)
                      || !('Notification' in window)) {
                    return {state: 'unsupported', mobile};
                  }
                  if (Notification.permission === 'denied') { return {state: 'denied', mobile}; }
                  try {
                    const subscription = await current(key);
                    this.ready = true;
                    return {state: 'ready', mobile, endpoint: subscription ? subscription.endpoint : null};
                  } catch (e) {
                    // Sin service worker no hay avisos: mejor decirlo que ofrecer algo que no anda.
                    return {state: 'unsupported', mobile};
                  }
                },
                async toggle() {
                  try {
                    if (this.on) {
                      const subscription = await current(this.key);
                      if (subscription) { await subscription.unsubscribe(); }
                      return {state: 'off', mobile, endpoint: subscription ? subscription.endpoint : null};
                    }
                    // Primero, todavia dentro del toque: despues de un await ya no cuenta como toque.
                    const permission = await Notification.requestPermission();
                    if (permission !== 'granted') {
                      return {state: permission === 'denied' ? 'denied' : 'dismissed', mobile};
                    }
                    const reg = await registration();
                    let subscription = await reg.pushManager.getSubscription();
                    if (subscription && !sameKey(subscription, this.key)) {
                      await subscription.unsubscribe();
                      subscription = null;
                    }
                    if (!subscription) {
                      subscription = await reg.pushManager.subscribe(
                          {userVisibleOnly: true, applicationServerKey: decode(this.key)});
                    }
                    return {state: 'on', mobile, endpoint: subscription.endpoint,
                        subscription: JSON.stringify(subscription.toJSON())};
                  } catch (e) {
                    return {state: 'error', mobile, message: String(e)};
                  }
                },
                async forget() {
                  const subscription = await current(this.key);
                  if (subscription) { await subscription.unsubscribe(); }
                }
              };
            }
            """;

    /** Lo que contesta el navegador, siempre por el mismo camino. */
    private static final String REPLY =
            "(r) => host.$server.pushReply(r.state, r.endpoint || null, !!r.mobile, r.subscription || null,"
                    + " r.message || null)";

    private final PanelPushService panelPushService;
    private final ClubUserPrincipal user;
    private final MenuItem item;

    /** Endpoint de la suscripcion que tiene este navegador, si tiene una. */
    private String endpoint;
    private boolean on;

    DevicePushItem(SubMenu menu, PanelPushService panelPushService, ClubUserPrincipal user) {
        this.panelPushService = panelPushService;
        this.user = user;
        this.item = menu.addItem("Avisarme en este celular");
        item.setCheckable(true);
        // Hasta que el navegador conteste no se sabe que decir: no se muestra.
        item.setVisible(false);
    }

    /** Solo para usuarios de un club, y si el servidor tiene claves para mandar avisos. */
    static boolean availableFor(ClubUserPrincipal user, PanelPushService panelPushService) {
        return panelPushService.isEnabled() && user.clubId() != null;
    }

    /** Al abrir el panel: instala el codigo, pregunta el estado y engancha el toque. */
    void attach(UI ui, Element host) {
        String key = panelPushService.applicationServerKey().orElseThrow();
        ui.getPage().executeJs(SETUP);
        ui.getPage().executeJs("const host = $1; window.panelPush.state($0).then(" + REPLY + ")", key, host);
        item.getElement().executeJs("""
                const host = $0;
                if (this.__panelPush) { return; }
                this.__panelPush = true;
                this.addEventListener('click', () => {
                  if (!window.panelPush || !window.panelPush.ready) { return; }
                  window.panelPush.toggle().then(%s);
                });
                """.formatted(REPLY), host);
    }

    void reply(String state, String browserEndpoint, boolean mobile, String subscription, String message) {
        item.setText(mobile ? "Avisarme en este celular" : "Avisarme en esta computadora");
        item.setEnabled(true);
        item.setVisible(true);
        switch (state) {
            case "ready" -> {
                endpoint = browserEndpoint;
                on = panelPushService.isSubscribed(user.userId(), endpoint);
            }
            case "on" -> turnOn(browserEndpoint, subscription, mobile);
            case "off" -> {
                panelPushService.unsubscribe(user.userId(), browserEndpoint != null ? browserEndpoint : endpoint);
                endpoint = null;
                on = false;
                show("Listo, ya no te avisamos en este " + (mobile ? "celular" : "dispositivo"), false);
            }
            case "denied" -> {
                on = false;
                disable("Avisos bloqueados en este navegador");
                show("El navegador tiene bloqueados los avisos del panel. Para activarlos, permitilos en los "
                        + "ajustes del sitio.", true);
            }
            case "dismissed" -> on = false;
            case "unsupported" -> disable("Este navegador no puede recibir avisos");
            case "ios-install" -> disable("Para avisos en el iPhone: Compartir › Agregar a inicio");
            default -> {
                log.warn("No se pudieron activar los avisos del panel para {}: {}", user.userId(),
                        message == null || message.length() <= 200 ? message : message.substring(0, 200));
                show("No se pudieron activar los avisos. Probá de nuevo en un rato.", true);
            }
        }
        item.setChecked(on);
        item.getUI().ifPresent(ui -> ui.getPage().executeJs("window.panelPush && (window.panelPush.on = $0)", on));
    }

    private void turnOn(String browserEndpoint, String subscription, boolean mobile) {
        VaadinRequest request = VaadinRequest.getCurrent();
        try {
            panelPushService.subscribe(user, subscription, request == null ? null : request.getHeader("User-Agent"));
            endpoint = browserEndpoint;
            on = true;
            show("Listo: cuando entre una reserva por la página te avisamos en este "
                    + (mobile ? "celular" : "dispositivo") + ", aunque el panel esté cerrado.", false);
        } catch (BusinessRuleException ex) {
            on = false;
            show(ex.getMessage(), true);
        }
    }

    /**
     * "Salir": este dispositivo deja de avisarle a esta persona. El servidor lo borra
     * en el momento; el navegador, si llega antes de irse de la pagina.
     */
    void forgetOnLogout() {
        if (endpoint == null) {
            return;
        }
        panelPushService.unsubscribe(user.userId(), endpoint);
        item.getUI().ifPresent(ui -> ui.getPage().executeJs("window.panelPush && window.panelPush.forget()"));
    }

    private void disable(String text) {
        item.setText(text);
        item.setEnabled(false);
        on = false;
    }

    private static void show(String text, boolean error) {
        Notification notification = Notification.show(text, error ? 8000 : 5000, Notification.Position.BOTTOM_START);
        if (error) {
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
