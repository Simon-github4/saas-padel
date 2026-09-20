package ar.com.padelnec.gym.ui;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.security.ClubUserPrincipal;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Lo que comparten las pantallas del gimnasio del panel. */
final class GymViewSupport {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd/MM");

    private static final Locale AR = Locale.forLanguageTag("es-AR");

    private GymViewSupport() {
    }

    static void error(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    static void ok(String message) {
        Notification.show(message);
    }

    /** El usuario del panel que esta operando, para dejar constancia de quien cobro o cargo. */
    static Optional<UUID> currentUserId(AuthenticationContext authenticationContext) {
        return authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class).map(ClubUserPrincipal::userId);
    }

    /**
     * Un club sin el modulo prendido no tiene estas pantallas: ocultar el menu no
     * alcanza, alguien podria escribir la URL a mano.
     */
    static void requireEnabled(BeforeEnterEvent event, GymModule gymModule) {
        if (!gymModule.isEnabled()) {
            event.rerouteToError(NotFoundException.class, "El gimnasio no está activado para este club.");
        }
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(AR);
        format.setMaximumFractionDigits(2);
        return "$ " + format.format(amount);
    }

    static Span badge(String text, String theme) {
        Span badge = new Span(text);
        badge.getElement().getThemeList().add("badge " + theme);
        return badge;
    }

    /**
     * El socio quedo dado de alta y entra a la app con su DNI: no hay clave que dictar.
     *
     * @param onSell boton para cobrar la cuota a continuacion
     */
    static void showRegistered(String memberName, String dni, Runnable onSell) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(memberName + " ya es socio");

        Paragraph note = new Paragraph("Entra a la app con su DNI (" + dni + "), sin clave. Falta cobrarle la cuota "
                + "para que pueda registrar sus ingresos.");
        dialog.add(note);

        Button later = new Button("Después", event -> dialog.close());
        Button sell = new Button("Cobrar la cuota", event -> {
            dialog.close();
            onSell.run();
        });
        sell.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(later, sell);
        dialog.open();
    }

    /**
     * La clave temporal, mostrada UNA vez. No se cierra con un clic afuera: si el
     * mostrador la pierde hay que resetearla, no hay forma de volver a verla.
     *
     * @param onSell si no es null, agrega el boton para cobrar la cuota a continuacion
     */
    static void showTemporaryPassword(String memberName, String dni, String password, Runnable onSell) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Clave temporal de " + memberName);
        dialog.setCloseOnOutsideClick(false);
        dialog.setCloseOnEsc(false);

        Span dniLine = new Span("DNI " + dni);
        dniLine.addClassNames(LumoUtility.TextColor.SECONDARY);

        Span code = new Span(password);
        code.getStyle().set("font-family", "monospace").set("font-size", "2rem")
                .set("letter-spacing", "0.15em").set("font-weight", "600");

        Paragraph note = new Paragraph("Dictásela o anotala ahora: no se vuelve a mostrar. Es temporal, en su primer "
                + "ingreso la app le va a pedir que la cambie.");
        note.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout body = new VerticalLayout(dniLine, code, note);
        body.setPadding(false);
        dialog.add(body);

        Button copy = new Button("Copiar clave", event -> UI.getCurrent().getPage()
                .executeJs("navigator.clipboard && navigator.clipboard.writeText($0)", password));
        Button done = new Button("Listo", event -> dialog.close());
        done.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (onSell != null) {
            Button sell = new Button("Cobrar la cuota", event -> {
                dialog.close();
                onSell.run();
            });
            sell.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(copy, done, sell);
        } else {
            dialog.getFooter().add(copy, done);
        }
        dialog.open();
    }
}
