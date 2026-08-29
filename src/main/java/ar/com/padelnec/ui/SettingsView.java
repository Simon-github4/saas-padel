package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.TenantService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Configuracion del club: horarios, tarifas, canchas y cobros.
 *
 * <p>Solo para el dueno. El personal de mostrador opera la agenda, pero no cambia
 * precios ni la tolerancia de cancelacion.
 */
@Route(value = "configuracion", layout = MainLayout.class)
@PageTitle("Configuracion | Panel del club")
@RolesAllowed({"OWNER", "SUPER_ADMIN"})
public class SettingsView extends VerticalLayout {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    /**
     * Locale de los selectores de hora, separado del de los textos a proposito.
     *
     * <p>Con es-AR el TimePicker formatea en 12 horas con "a. m." y despues no puede
     * volver a leer lo que escribio: las 18:00 quedaban guardadas como 06:00 y el
     * cierre 23:30 como 11:30. El dueno abria la configuracion, tocaba Guardar sin
     * cambiar nada y le movia el horario al club. es-ES formatea en 24 horas, que
     * ademas es como se escribe la hora en Argentina.
     */
    private static final Locale CLOCK = Locale.forLanguageTag("es-ES");

    private final TenantService tenantService;
    private final TenantRepository tenantRepository;
    private final CourtRepository courtRepository;
    private final PricingRuleRepository pricingRuleRepository;

    private final Grid<Court> courtGrid = new Grid<>();
    private final Grid<PricingRule> pricingGrid = new Grid<>();
    private final Paragraph pricingWarning = new Paragraph();

    private Tenant club;

    public SettingsView(TenantService tenantService, TenantRepository tenantRepository,
                        CourtRepository courtRepository, PricingRuleRepository pricingRuleRepository) {
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.courtRepository = courtRepository;
        this.pricingRuleRepository = pricingRuleRepository;

        setSizeFull();
        this.club = tenantService.requireCurrent();

        TabSheet tabs = new TabSheet();
        tabs.add(new Tab("Club"), clubForm());
        tabs.add(new Tab("Canchas"), courtsTab());
        tabs.add(new Tab("Tarifas"), pricingTab());
        tabs.add(new Tab("Cobros online"), paymentsForm());
        tabs.setSizeFull();
        add(tabs);
    }

    // ----------------------------------------------------------------- club

    private VerticalLayout clubForm() {
        TextField name = new TextField("Nombre del club");
        name.setValue(club.getName());

        TextField whatsapp = new TextField("WhatsApp del club");
        whatsapp.setValue(club.getWhatsappNumber());
        whatsapp.setHelperText("A este numero se deriva al jugador cuando algo lo tiene que "
                + "resolver una persona");

        TimePicker open = timePicker("Abre", club.getOpenTime());
        TimePicker close = timePicker("Cierra", club.getCloseTime());
        close.setHelperText("Si es anterior a la apertura, se entiende que cierran de madrugada");

        IntegerField duration = new IntegerField("Duracion del turno (minutos)");
        duration.setValue(club.getDefaultSlotDuration());
        duration.setStep(30);
        duration.setMin(30);
        duration.setMax(240);
        duration.setHelperText("Los turnos se encadenan desde la apertura");

        IntegerField cancellation = new IntegerField("Cancelacion hasta (horas antes)");
        cancellation.setValue(club.getCancellationLimitHours());
        cancellation.setHelperText("Mas cerca del turno, la baja la tiene que hacer el club");

        IntegerField horizon = new IntegerField("Se reserva con (dias de anticipacion)");
        horizon.setValue(club.getBookingHorizonDays());

        IntegerField maxActive = new IntegerField("Turnos futuros por jugador");
        maxActive.setValue(club.getMaxActiveBookings());
        maxActive.setHelperText("Evita que un mismo telefono bloquee la agenda entera");

        Button save = new Button("Guardar", event -> {
            club.setName(name.getValue());
            club.setWhatsappNumber(whatsapp.getValue());
            club.setOpenTime(open.getValue());
            club.setCloseTime(close.getValue());
            club.setDefaultSlotDuration(duration.getValue());
            club.setCancellationLimitHours(cancellation.getValue());
            club.setBookingHorizonDays(horizon.getValue());
            club.setMaxActiveBookings(maxActive.getValue());
            club = tenantRepository.save(club);
            Notification.show("Configuracion guardada");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(name, whatsapp, open, close, duration,
                cancellation, horizon, maxActive);
        return new VerticalLayout(form, save);
    }

    // -------------------------------------------------------------- canchas

    private VerticalLayout courtsTab() {
        courtGrid.addColumn(Court::getName).setHeader("Cancha").setAutoWidth(true);
        courtGrid.addColumn(Court::getDisplayOrder).setHeader("Orden").setAutoWidth(true).setFlexGrow(0);
        courtGrid.addComponentColumn(court -> {
            Checkbox active = new Checkbox(court.isActive());
            active.addValueChangeListener(event -> {
                if (event.isFromClient()) {
                    court.setActive(event.getValue());
                    courtRepository.save(court);
                    // Desactivar saca la cancha de la grilla pero conserva su historial.
                    Notification.show(event.getValue() ? "Cancha activada" : "Cancha desactivada");
                }
            });
            return active;
        }).setHeader("Activa").setAutoWidth(true).setFlexGrow(0);

        TextField newName = new TextField();
        newName.setPlaceholder("Nombre de la cancha");
        IntegerField newOrder = new IntegerField();
        newOrder.setPlaceholder("Orden");
        newOrder.setWidth("7em");

        Button add = new Button("Agregar cancha", event -> {
            if (newName.getValue() == null || newName.getValue().isBlank()) {
                Notification.show("Poné un nombre para la cancha");
                return;
            }
            Court court = new Court();
            court.setName(newName.getValue().trim());
            court.setDisplayOrder(newOrder.getValue() == null ? 0 : newOrder.getValue());
            courtRepository.save(court);
            newName.clear();
            newOrder.clear();
            refreshCourts();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        refreshCourts();
        HorizontalLayout toolbar = new HorizontalLayout(newName, newOrder, add);
        toolbar.setAlignItems(Alignment.BASELINE);
        return new VerticalLayout(toolbar, courtGrid);
    }

    private void refreshCourts() {
        courtGrid.setItems(courtRepository.findAllByOrderByDisplayOrderAscNameAsc());
    }

    // -------------------------------------------------------------- tarifas

    private VerticalLayout pricingTab() {
        pricingGrid.addColumn(rule -> dayName(rule.day())).setHeader("Dia").setAutoWidth(true);
        pricingGrid.addColumn(rule -> "%s - %s".formatted(rule.getStartTime(), rule.getEndTime()))
                .setHeader("Franja").setAutoWidth(true);
        pricingGrid.addColumn(rule -> rule.getCourt() == null
                        ? "Todas las canchas" : rule.getCourt().getName())
                .setHeader("Aplica a").setAutoWidth(true);
        pricingGrid.addColumn(rule -> "$" + rule.getPrice().stripTrailingZeros().toPlainString())
                .setHeader("Precio del turno").setAutoWidth(true);
        pricingGrid.addComponentColumn(rule -> {
            Button delete = new Button("Borrar", event -> {
                pricingRuleRepository.delete(rule);
                refreshPricing();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return delete;
        }).setAutoWidth(true).setFlexGrow(0);

        pricingWarning.addClassNames(LumoUtility.TextColor.ERROR);
        refreshPricing();

        return new VerticalLayout(
                new Paragraph("Gana la regla mas especifica: primero la que nombra la cancha, "
                        + "y entre iguales, la de franja mas angosta."),
                pricingWarning, newPricingRuleForm(), pricingGrid);
    }

    private HorizontalLayout newPricingRuleForm() {
        Select<DayOfWeek> day = new Select<>();
        day.setLabel("Dia");
        day.setItems(DayOfWeek.values());
        day.setItemLabelGenerator(this::dayName);
        day.setValue(DayOfWeek.MONDAY);

        Select<Court> court = new Select<>();
        court.setLabel("Cancha");
        court.setItems(courtRepository.findAllByOrderByDisplayOrderAscNameAsc());
        court.setEmptySelectionAllowed(true);
        court.setEmptySelectionCaption("Todas");
        // Null-safe a proposito: al habilitar la opcion vacia, Select invoca el
        // generador de etiquetas con null para rotular esa opcion.
        court.setItemLabelGenerator(item -> item == null ? "Todas" : item.getName());

        TimePicker from = timePicker("Desde", LocalTime.of(8, 0));
        TimePicker to = timePicker("Hasta", LocalTime.of(18, 0));

        BigDecimalField price = new BigDecimalField("Precio");

        Button add = new Button("Agregar tarifa", event -> {
            if (price.getValue() == null || price.getValue().compareTo(BigDecimal.ZERO) < 0) {
                Notification.show("Poné un precio valido");
                return;
            }
            if (!to.getValue().isAfter(from.getValue())) {
                Notification.show("La franja tiene que terminar despues de empezar");
                return;
            }
            PricingRule rule = new PricingRule();
            rule.setDay(day.getValue());
            rule.setCourt(court.getValue());
            rule.setStartTime(from.getValue());
            rule.setEndTime(to.getValue());
            rule.setPrice(price.getValue());
            pricingRuleRepository.save(rule);
            price.clear();
            refreshPricing();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout form = new HorizontalLayout(day, court, from, to, price, add);
        form.setAlignItems(Alignment.BASELINE);
        return form;
    }

    private void refreshPricing() {
        var rules = pricingRuleRepository.findAllByOrderByDayOfWeekAscStartTimeAsc();
        pricingGrid.setItems(rules);

        // Un horario sin tarifa no se publica, asi que el club tiene que enterarse
        // antes de que un jugador no encuentre turnos.
        long daysCovered = rules.stream().map(PricingRule::getDayOfWeek).distinct().count();
        pricingWarning.setText(daysCovered < 7
                ? "Hay dias sin tarifa cargada. Esos horarios no se publican en la app."
                : "");
    }

    private String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, ES_AR);
    }

    /** Reloj de 24 horas: en Argentina nadie dice "8:00 PM". */
    private TimePicker timePicker(String label, LocalTime value) {
        TimePicker picker = new TimePicker(label);
        picker.setLocale(CLOCK);
        picker.setStep(java.time.Duration.ofMinutes(30));
        picker.setValue(value);
        return picker;
    }

    // --------------------------------------------------------- cobros online

    private VerticalLayout paymentsForm() {
        Checkbox allowUnpaid = new Checkbox("Aceptar reservas sin sena (de palabra)");
        allowUnpaid.setValue(club.isAllowUnpaidBooking());

        BigDecimalField deposit = new BigDecimalField("Sena (% del turno)");
        deposit.setValue(club.getDepositPercentage());

        PasswordField token = new PasswordField("Access token de MercadoPago");
        token.setPlaceholder(club.acceptsOnlinePayments()
                ? "Ya cargado. Escribi uno nuevo solo si querés reemplazarlo."
                : "Sin cargar: el pago online esta deshabilitado");
        token.setHelperText("Se guarda cifrado");

        PasswordField secret = new PasswordField("Clave secreta de webhooks");
        secret.setPlaceholder(club.getMpWebhookSecret() != null && !club.getMpWebhookSecret().isBlank()
                ? "Ya cargada. Escribi una nueva solo si querés reemplazarla."
                : "Sin cargar: las notificaciones de pago se rechazan");
        secret.setHelperText("Sin esto no se puede verificar que un aviso de pago sea real");

        Paragraph webhookUrl = new Paragraph(
                "URL a configurar en MercadoPago: /api/webhooks/mercadopago/" + club.getSlug());
        webhookUrl.addClassNames(LumoUtility.TextColor.SECONDARY);

        Button save = new Button("Guardar", event -> {
            club.setAllowUnpaidBooking(allowUnpaid.getValue());
            club.setDepositPercentage(deposit.getValue());
            // Vacio significa "no lo toques": mostrar el token guardado seria
            // exponerlo en pantalla sin ninguna necesidad.
            if (token.getValue() != null && !token.getValue().isBlank()) {
                club.setMpAccessToken(token.getValue().trim());
            }
            if (secret.getValue() != null && !secret.getValue().isBlank()) {
                club.setMpWebhookSecret(secret.getValue().trim());
            }
            club = tenantRepository.save(club);
            token.clear();
            secret.clear();
            Notification.show("Cobros actualizados");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(allowUnpaid, deposit, token, secret);
        return new VerticalLayout(form, webhookUrl, save);
    }
}
