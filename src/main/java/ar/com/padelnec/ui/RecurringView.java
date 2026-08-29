package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.RecurringBooking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.service.RecurringBookingService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Turnos fijos: el grupo que juega todas las semanas a la misma hora.
 *
 * <p>En los clubes de Necochea son la mayor parte de la ocupacion. El turno fijo
 * es una regla, no una reserva: el sistema materializa las semanas por adelantado
 * para que aparezcan en la agenda y bloqueen la grilla publica.
 */
@Route(value = "turnos-fijos", layout = MainLayout.class)
@PageTitle("Turnos fijos | Panel del club")
@PermitAll
public class RecurringView extends VerticalLayout {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    /** 24 horas: con es-AR el TimePicker no puede releer lo que el mismo formatea. */
    private static final Locale CLOCK = Locale.forLanguageTag("es-ES");

    private final RecurringBookingService recurringBookingService;
    private final CourtRepository courtRepository;
    private final CustomerService customerService;
    private final TenantService tenantService;

    private final Grid<RecurringBooking> grid = new Grid<>();
    private Tenant club;

    public RecurringView(RecurringBookingService recurringBookingService,
                         CourtRepository courtRepository, CustomerService customerService,
                         TenantService tenantService) {
        this.recurringBookingService = recurringBookingService;
        this.courtRepository = courtRepository;
        this.customerService = customerService;
        this.tenantService = tenantService;

        setSizeFull();
        this.club = tenantService.requireCurrent();

        Button add = new Button("Nuevo turno fijo", event -> openForm());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Paragraph help = new Paragraph(
                "Los turnos se generan por adelantado. Si el grupo avisa que una semana no juega, "
                        + "usá \"Liberar fecha\" y esa cancha vuelve a la venta.");
        help.addClassNames(LumoUtility.TextColor.SECONDARY);

        add(new HorizontalLayout(add), help, grid);
        setFlexGrow(1, grid);

        buildColumns();
        refresh();
    }

    private void buildColumns() {
        grid.addColumn(fixed -> fixed.getCustomer().getFullName())
                .setHeader("Grupo").setAutoWidth(true);
        grid.addColumn(fixed -> dayName(fixed.day())).setHeader("Dia").setAutoWidth(true);
        grid.addColumn(fixed -> "%s (%d min)".formatted(fixed.getStartTime(), fixed.getDurationMinutes()))
                .setHeader("Horario").setAutoWidth(true);
        grid.addColumn(fixed -> fixed.getCourt().getName()).setHeader("Cancha").setAutoWidth(true);
        grid.addColumn(fixed -> fixed.getPriceOverride() == null
                        ? "Tarifa de la franja"
                        : "$" + fixed.getPriceOverride().stripTrailingZeros().toPlainString())
                .setHeader("Precio").setAutoWidth(true);
        grid.addColumn(fixed -> fixed.getValidUntil() == null
                        ? "Sin fecha de corte" : fixed.getValidUntil().toString())
                .setHeader("Hasta").setAutoWidth(true);

        grid.addComponentColumn(this::actions).setAutoWidth(true).setFlexGrow(0);
        grid.setSizeFull();
    }

    private HorizontalLayout actions(RecurringBooking fixed) {
        Button skip = new Button("Liberar fecha", event -> openSkip(fixed));
        skip.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deactivate = new Button("Dar de baja", event -> {
            int cancelled = recurringBookingService.deactivate(fixed.getId());
            Notification.show("Turno fijo dado de baja. Se cancelaron %d turnos futuros."
                    .formatted(cancelled));
            refresh();
        });
        deactivate.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        deactivate.setVisible(fixed.isActive());

        return new HorizontalLayout(skip, deactivate);
    }

    /** El grupo avisa que una semana no juega y la cancha vuelve a la venta. */
    private void openSkip(RecurringBooking fixed) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Liberar una fecha");

        DatePicker date = new DatePicker("Fecha que no juegan");
        date.setI18n(SpanishDates.datePicker());
        date.setValue(LocalDate.now(club.zoneId()));
        TextField reason = new TextField("Motivo");

        Button confirm = new Button("Liberar", event -> {
            recurringBookingService.skipDate(club, fixed.getId(), date.getValue(), reason.getValue());
            Notification.show("Fecha liberada. La cancha ya vuelve a estar disponible.");
            dialog.close();
            refresh();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new FormLayout(date, reason));
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), confirm);
        dialog.open();
    }

    private void openForm() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nuevo turno fijo");

        TextField name = new TextField("Nombre del grupo");
        TextField phone = new TextField("Telefono de contacto");

        Select<Court> court = new Select<>();
        court.setLabel("Cancha");
        court.setItems(courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc());
        court.setItemLabelGenerator(Court::getName);

        Select<DayOfWeek> day = new Select<>();
        day.setLabel("Dia");
        day.setItems(DayOfWeek.values());
        day.setItemLabelGenerator(this::dayName);
        day.setValue(DayOfWeek.TUESDAY);

        TimePicker start = new TimePicker("Hora");
        start.setLocale(CLOCK);
        start.setStep(java.time.Duration.ofMinutes(30));
        start.setValue(LocalTime.of(20, 0));

        IntegerField duration = new IntegerField("Duracion (minutos)");
        duration.setValue(club.getDefaultSlotDuration());
        duration.setStep(30);

        DatePicker from = new DatePicker("Desde");
        from.setI18n(SpanishDates.datePicker());
        from.setValue(LocalDate.now(club.zoneId()));

        DatePicker until = new DatePicker("Hasta (opcional)");
        until.setI18n(SpanishDates.datePicker());
        until.setHelperText("Vacio: el turno fijo sigue hasta que lo den de baja");

        BigDecimalField price = new BigDecimalField("Precio pactado (opcional)");
        price.setHelperText("Vacio cobra la tarifa vigente de la franja");

        Button save = new Button("Crear y generar turnos", event -> {
            try {
                RecurringBooking fixed = new RecurringBooking();
                fixed.setCustomer(customerService.findOrCreate(phone.getValue(), name.getValue()));
                fixed.setCourt(court.getValue());
                fixed.setDay(day.getValue());
                fixed.setStartTime(start.getValue());
                fixed.setDurationMinutes(duration.getValue());
                fixed.setValidFrom(from.getValue());
                fixed.setValidUntil(until.getValue());
                fixed.setPriceOverride(price.getValue());
                RecurringBooking saved = recurringBookingService.save(fixed);

                // Se materializa en el momento para que el club vea el resultado y se
                // entere ahora si alguna semana choca con un turno ya vendido.
                int created = recurringBookingService.materializeUpcoming(club);
                Notification.show("Turno fijo creado. Se generaron %d turnos.".formatted(created));
                dialog.close();
                refresh();
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(name, phone, court, day, start, duration, from, until, price);
        dialog.add(form);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
    }

    private void refresh() {
        club = tenantService.requireCurrent();
        grid.setItems(recurringBookingService.all());
    }

    private String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, ES_AR);
    }
}
