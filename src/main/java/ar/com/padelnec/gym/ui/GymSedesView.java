package ar.com.padelnec.gym.ui;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.service.GymQr;
import ar.com.padelnec.gym.service.GymSedeService;
import ar.com.padelnec.gym.service.GymSedeService.SedeData;
import ar.com.padelnec.gym.service.GymSettingsService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.util.function.BiConsumer;
import org.springframework.web.util.HtmlUtils;

/**
 * Sedes del gimnasio y el QR de la puerta de cada una.
 *
 * <p>Solo el dueno: cambiar o regenerar un QR deja inservible el cartel impreso.
 */
@Route(value = "gimnasio/sedes", layout = ar.com.padelnec.ui.MainLayout.class)
@PageTitle("Sedes y QR | Panel del club")
@RolesAllowed({"OWNER", "SUPER_ADMIN"})
public class GymSedesView extends VerticalLayout implements BeforeEnterObserver {

    private final GymModule gymModule;
    private final GymSedeService sedeService;
    private final GymSettingsService settingsService;
    private final GymQr gymQr;
    private final String clubSlug;

    private final Grid<GymSede> grid = new Grid<>();

    public GymSedesView(GymModule gymModule, GymSedeService sedeService, GymSettingsService settingsService,
                        GymQr gymQr, TenantService tenantService) {
        this.gymModule = gymModule;
        this.sedeService = sedeService;
        this.settingsService = settingsService;
        this.gymQr = gymQr;
        this.clubSlug = tenantService.requireCurrent().getSlug();

        setSizeFull();
        addClassNames(LumoUtility.Gap.MEDIUM);

        Button newSede = new Button("Nueva sede", VaadinIcon.PLUS.create(), event -> openEdit(null));
        newSede.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Paragraph help = new Paragraph("Cada sede tiene su QR: se imprime y se pega en la entrada. El socio lo "
                + "escanea con la app y queda registrado el ingreso en esa sede.");
        help.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE);

        // El boton a la izquierda y el texto a su derecha.
        HorizontalLayout toolbar = new HorizontalLayout(newSede, help);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.START);
        toolbar.setPadding(false);

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.addColumn(GymSede::getName).setHeader("Sede").setFlexGrow(2);
        grid.addColumn(sede -> sede.getAddress() == null ? "" : sede.getAddress()).setHeader("Dirección")
                .setFlexGrow(3);
        grid.addComponentColumn(sede -> sede.hasLocation()
                        ? GymViewSupport.badge("Verifica a " + sede.getRadiusMeters() + " m", "success small")
                        : GymViewSupport.badge("Sin verificar", "contrast small"))
                .setHeader("Ubicación").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(sede -> sede.getPartnerSharePct().signum() == 0 ? "—"
                        : sede.getPartnerSharePct().stripTrailingZeros().toPlainString() + " %")
                .setHeader("Del socio").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(sede -> sede.isActive() ? GymViewSupport.badge("Activa", "success small")
                : GymViewSupport.badge("Inactiva", "contrast small")).setHeader("Estado").setAutoWidth(true)
                .setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("Todavía no hay sedes. Creá la primera para poder cobrar cuotas y registrar ingresos.");
        grid.setSizeFull();

        add(toolbar, accessSetting(), grid);
        setFlexGrow(1, grid);
        refresh();
    }

    /**
     * Como entran los socios. Lo normal es solo con el DNI; con esto prendido tambien piden la clave
     * temporal del mostrador. Cambiarlo no toca las sesiones abiertas.
     */
    private Component accessSetting() {
        Checkbox passwordRequired = new Checkbox("Los socios entran con DNI y clave", gymModule.isPasswordRequired());
        passwordRequired.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                settingsService.setPasswordRequired(event.getValue());
                GymViewSupport.ok(event.getValue()
                        ? "Ahora los socios entran con DNI y clave. Los que no tengan clave: «Resetear clave» en Socios."
                        : "Ahora los socios entran solo con el DNI.");
            }
        });
        Span help = new Span("Sin esto, alcanza con el DNI: el mostrador ve quién entra. Prendelo si preferís "
                + "que además tengan una clave personal.");
        help.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        VerticalLayout box = new VerticalLayout(passwordRequired, help);
        box.setPadding(false);
        box.setSpacing(false);
        return box;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        GymViewSupport.requireEnabled(event, gymModule);
    }

    private Component actions(GymSede sede) {
        Button qr = new Button("Ver QR", VaadinIcon.QRCODE.create(), event -> openQr(sede));
        qr.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        Button edit = new Button("Editar", event -> openEdit(sede));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout cell = new HorizontalLayout(qr, edit);
        cell.setPadding(false);
        return cell;
    }

    private void openEdit(GymSede sede) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(sede == null ? "Nueva sede" : "Editar sede");

        TextField name = new TextField("Nombre");
        name.setRequired(true);
        name.setMaxLength(120);
        name.setWidthFull();
        TextField address = new TextField("Dirección (opcional)");
        address.setMaxLength(200);
        address.setWidthFull();
        BigDecimalField partner = new BigDecimalField("Porcentaje del socio (0 si la sede es toda del club)");
        partner.setSuffixComponent(new Span("%"));
        partner.setValue(BigDecimal.ZERO);
        partner.setWidthFull();
        Checkbox active = new Checkbox("Sede activa", true);

        // La ubicacion no se escribe a mano: se toma del dispositivo con el boton. Las coordenadas solo
        // viven aca, y lo unico que se muestra es si la sede esta verificada.
        Double[] location = {null, null};
        IntegerField radius = new IntegerField("Radio permitido");
        radius.setSuffixComponent(new Span("m"));
        radius.setValue(GymSede.DEFAULT_RADIUS_METERS);
        radius.setStepButtonsVisible(true);
        radius.setStep(50);
        radius.setMin(20);
        radius.setMax(5000);
        radius.setHelperText("Tan grande porque el GPS falla adentro de un edificio.");
        radius.setWidthFull();

        if (sede != null) {
            name.setValue(sede.getName());
            address.setValue(sede.getAddress() == null ? "" : sede.getAddress());
            partner.setValue(sede.getPartnerSharePct());
            active.setValue(sede.isActive());
            location[0] = sede.getLatitude();
            location[1] = sede.getLongitude();
            radius.setValue(sede.getRadiusMeters());
        }

        Span locationHelp = new Span("Para registrar un ingreso, el socio tiene que estar cerca de la sede: así no "
                + "se puede hacer desde lejos con una foto del QR. Parate en el gimnasio y tocá el botón.");
        locationHelp.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span locationStatus = new Span();
        Button useMyLocation = new Button(VaadinIcon.MAP_MARKER.create());
        useMyLocation.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button clearLocation = new Button("Quitar", event -> {
            location[0] = null;
            location[1] = null;
            showLocationState(location, locationStatus, useMyLocation, event.getSource());
        });
        clearLocation.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        useMyLocation.addClickListener(event -> captureDeviceLocation((latitude, longitude) -> {
            location[0] = latitude;
            location[1] = longitude;
            showLocationState(location, locationStatus, useMyLocation, clearLocation);
        }));
        showLocationState(location, locationStatus, useMyLocation, clearLocation);

        HorizontalLayout locationRow = new HorizontalLayout(useMyLocation, locationStatus, clearLocation);
        locationRow.setAlignItems(Alignment.CENTER);
        locationRow.setPadding(false);

        VerticalLayout body = new VerticalLayout(name, address, partner);
        body.add(locationHelp, locationRow, radius);
        if (sede != null) {
            body.add(active);
        }
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Guardar", event -> {
            try {
                SedeData data = new SedeData(name.getValue(), address.getValue(), partner.getValue(),
                        location[0], location[1], radius.getValue());
                if (sede == null) {
                    sedeService.create(data);
                } else {
                    sedeService.update(sede.getId(), data, active.getValue());
                }
                dialog.close();
                refresh();
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
        name.focus();
    }

    /**
     * Dice si la sede tiene la ubicacion verificada y ajusta los botones: si ya la tiene, el boton pasa a
     * "actualizar" y aparece "Quitar".
     */
    private static void showLocationState(Double[] location, Span status, Button useMyLocation, Button clear) {
        boolean verified = location[0] != null && location[1] != null;
        status.setText(verified ? "Ubicación verificada" : "Sin ubicación: no se verifica");
        status.getElement().getThemeList().clear();
        status.getElement().getThemeList().add(verified ? "badge success" : "badge contrast");
        useMyLocation.setText(verified ? "Actualizar con este dispositivo" : "Usar la ubicación de este dispositivo");
        clear.setVisible(verified);
    }

    /**
     * Pide la ubicacion al navegador de quien esta en el panel. Sirve estando parado en el gimnasio con el
     * celular o la notebook.
     */
    private void captureDeviceLocation(BiConsumer<Double, Double> onFound) {
        UI.getCurrent().getPage().executeJs(
                "return new Promise(resolve => { if (!navigator.geolocation) { resolve('ERR'); return; } "
                        + "navigator.geolocation.getCurrentPosition("
                        + "p => resolve(p.coords.latitude + ',' + p.coords.longitude), "
                        + "() => resolve('ERR'), {enableHighAccuracy: true, timeout: 15000}); });")
                .then(String.class, result -> {
                    String[] parts = result == null ? new String[0] : result.split(",");
                    if (parts.length != 2) {
                        GymViewSupport.error("No pudimos obtener la ubicación. Revisá el permiso de ubicación del "
                                + "navegador para este sitio.");
                        return;
                    }
                    onFound.accept(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                });
    }

    private void openQr(GymSede sede) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("QR de " + sede.getName());
        dialog.setWidth("28rem");

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setAlignItems(Alignment.CENTER);
        dialog.add(body);
        showQr(body, sede);

        Button print = new Button("Imprimir", VaadinIcon.PRINT.create(), event -> print(body, sede));
        print.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button regenerate = new Button("Generar un código nuevo", event -> confirmRegenerate(dialog, sede));
        regenerate.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(regenerate, new Button("Cerrar", event -> dialog.close()), print);
        dialog.open();
    }

    private void showQr(VerticalLayout body, GymSede sede) {
        body.removeAll();
        String url = gymQr.checkInUrl(clubSlug, sede.getQrToken());
        Image image = new Image(gymQr.svgDataUri(url), "QR de la sede " + sede.getName());
        image.setWidth("20rem");
        image.setHeight("20rem");

        Span link = new Span(url);
        link.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        link.getStyle().set("word-break", "break-all").set("text-align", "center");

        Paragraph hint = new Paragraph("El socio lo escanea con la app del gimnasio. También funciona con la cámara "
                + "del celular. Cualquiera que fotografíe el cartel tiene el código: si se filtra, generá uno nuevo.");
        hint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        body.add(image, link, hint);
    }

    private void confirmRegenerate(Dialog qrDialog, GymSede sede) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Generar un código nuevo");
        confirm.add(new Paragraph("El cartel que está impreso deja de servir al instante. Vas a tener que "
                + "imprimir el nuevo y reemplazarlo."));
        Button go = new Button("Generar código nuevo", event -> {
            GymSede updated = sedeService.regenerateQr(sede.getId());
            confirm.close();
            qrDialog.close();
            refresh();
            openQr(updated);
        });
        go.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirm.getFooter().add(new Button("Cancelar", event -> confirm.close()), go);
        confirm.open();
    }

    /** Abre solo el QR en una ventana nueva y manda a imprimir esa, no todo el panel. */
    private void print(VerticalLayout body, GymSede sede) {
        String url = gymQr.checkInUrl(clubSlug, sede.getQrToken());
        String html = "<!doctype html><html><head><meta charset='utf-8'><title>QR " + HtmlUtils.htmlEscape(sede.getName())
                + "</title><style>body{font-family:sans-serif;text-align:center;margin:48px}"
                + "img{width:420px;height:420px}h1{margin-bottom:8px}p{color:#444;font-size:18px}</style></head><body>"
                + "<h1>" + HtmlUtils.htmlEscape(sede.getName()) + "</h1>"
                + "<img src='" + gymQr.svgDataUri(url) + "' alt='QR'/>"
                + "<p>Escaneá este código con la app del gimnasio para registrar tu ingreso.</p></body></html>";
        UI.getCurrent().getPage().executeJs(
                "const w = window.open('', '_blank'); if (w) { w.document.write($0); w.document.close(); "
                        + "setTimeout(() => { w.focus(); w.print(); }, 300); }", html);
    }

    private void refresh() {
        grid.setItems(sedeService.all());
    }
}
