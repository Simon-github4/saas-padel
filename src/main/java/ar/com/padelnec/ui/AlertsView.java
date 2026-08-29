package ar.com.padelnec.ui;

import ar.com.padelnec.domain.OperationalAlert;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.AlertType;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.AlertService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.support.PhoneNumbers;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Lo que el sistema no puede resolver solo.
 *
 * <p>Casi siempre es una devolucion de sena: MercadoPago cobro, el jugador
 * cancelo y la plata tiene que volver por fuera del sistema. Si eso no aparece en
 * ningun lado, el club se entera cuando el jugador reclama.
 */
@Route(value = "alertas", layout = MainLayout.class)
@PageTitle("Alertas | Panel del club")
@PermitAll
public class AlertsView extends VerticalLayout {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d/MM HH:mm", Locale.forLanguageTag("es-AR"));

    private final AlertService alertService;
    private final TenantService tenantService;
    private final PhoneNumbers phoneNumbers;
    private final transient AuthenticationContext authenticationContext;

    private final Grid<OperationalAlert> grid = new Grid<>();
    private final Paragraph empty = new Paragraph("No hay nada pendiente.");

    private Tenant club;

    public AlertsView(AlertService alertService, TenantService tenantService,
                      PhoneNumbers phoneNumbers, AuthenticationContext authenticationContext) {
        this.alertService = alertService;
        this.tenantService = tenantService;
        this.phoneNumbers = phoneNumbers;
        this.authenticationContext = authenticationContext;

        setSizeFull();
        empty.addClassNames(LumoUtility.TextColor.SECONDARY);
        add(grid, empty);
        setFlexGrow(1, grid);

        this.club = tenantService.requireCurrent();
        buildColumns();
        refresh();
    }

    private void buildColumns() {
        grid.addColumn(alert -> WHEN.format(alert.getCreatedAt().atZone(club.zoneId())))
                .setHeader("Cuando")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addComponentColumn(this::typeBadge)
                .setHeader("Tipo")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(OperationalAlert::getMessage).setHeader("Detalle").setFlexGrow(1);

        grid.addComponentColumn(this::contactLink).setHeader("Jugador").setAutoWidth(true);

        grid.addComponentColumn(this::resolveButton).setAutoWidth(true).setFlexGrow(0);
        grid.setSizeFull();
    }

    private Span typeBadge(OperationalAlert alert) {
        Span badge = new Span(readable(alert.getType()));
        badge.getElement().getThemeList().add(switch (alert.getType()) {
            case REFUND_REQUIRED, ORPHAN_PAYMENT -> "badge error";
            case RECURRING_CONFLICT -> "badge contrast";
            case NOTIFICATION_FAILED -> "badge";
        });
        return badge;
    }

    private String readable(AlertType type) {
        return switch (type) {
            case REFUND_REQUIRED -> "Devolver sena";
            case ORPHAN_PAYMENT -> "Pago sin turno";
            case NOTIFICATION_FAILED -> "WhatsApp no entregado";
            case RECURRING_CONFLICT -> "Turno fijo en conflicto";
        };
    }

    /** La devolucion se coordina por WhatsApp, asi que el link tiene que estar a mano. */
    private Component contactLink(OperationalAlert alert) {
        if (alert.getBooking() == null) {
            return new Span();
        }
        String phone = alert.getBooking().getCustomer().getPhoneNumber();
        Anchor link = new Anchor(phoneNumbers.whatsappLink(phone,
                "Hola, te escribimos de %s por tu turno.".formatted(club.getName())),
                phoneNumbers.forDisplay(phone));
        link.setTarget("_blank");
        return link;
    }

    private Button resolveButton(OperationalAlert alert) {
        Button resolve = new Button("Resuelta", event -> {
            alertService.resolve(alert.getId(), currentUserId());
            Notification.show("Alerta marcada como resuelta");
            refresh();
        });
        resolve.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return resolve;
    }

    private UUID currentUserId() {
        return authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class)
                .map(ClubUserPrincipal::userId)
                .orElse(null);
    }

    private void refresh() {
        club = tenantService.requireCurrent();
        var pending = alertService.pending();
        grid.setItems(pending);
        grid.setVisible(!pending.isEmpty());
        empty.setVisible(pending.isEmpty());
    }
}
