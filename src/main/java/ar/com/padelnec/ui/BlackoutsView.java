package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.service.SlotGenerator;
import ar.com.padelnec.service.TenantService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Suspender un dia entero, una franja o una cancha puntual: feriado, torneo
 * cerrado o refaccion.
 *
 * <p>No hace nada nuevo por debajo: {@link Blackout} ya lo bloquea en la
 * grilla publica y en el alta de turnos (ver {@code AvailabilityService}).
 * Esta pantalla solo carga esa fila. Los turnos que ya existian permanecen:
 * cerrar disponibilidad nunca modifica reservas silenciosamente.
 */
@Route(value = "suspensiones", layout = MainLayout.class)
@PageTitle("Suspensiones | Panel del club")
@PermitAll
public class BlackoutsView extends VerticalLayout {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    /** 24 horas: con es-AR el TimePicker no puede releer lo que el mismo formatea. */
    private static final Locale CLOCK = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_AR);
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm", ES_AR);

    private final BookingRepository bookingRepository;
    private final BlackoutRepository blackoutRepository;
    private final CourtRepository courtRepository;
    private final SlotGenerator slotGenerator;
    private final TenantService tenantService;

    private final Grid<Blackout> grid = new Grid<>();
    private final Span count = new Span();
    private Tenant club;

    public BlackoutsView(BookingRepository bookingRepository, BlackoutRepository blackoutRepository,
                         CourtRepository courtRepository,
                         SlotGenerator slotGenerator, TenantService tenantService) {
        this.bookingRepository = bookingRepository;
        this.blackoutRepository = blackoutRepository;
        this.courtRepository = courtRepository;
        this.slotGenerator = slotGenerator;
        this.tenantService = tenantService;

        setSizeFull();
        this.club = tenantService.requireCurrent();

        Button add = new Button("Suspender un día", event -> openForm());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Paragraph help = new Paragraph(
                "La cancha suspendida desaparece de la grilla pública. Los turnos que ya estaban "
                        + "reservados siguen vigentes y el panel te avisa para que los revises.");
        help.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.NONE, LumoUtility.MaxWidth.SCREEN_SMALL);

        HorizontalLayout toolbar = new HorizontalLayout(add, count);
        toolbar.setWidthFull();
        toolbar.setPadding(false);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setEmptyStateText("No hay suspensiones cargadas.");

        add(toolbar, help, grid);
        setFlexGrow(1, grid);
        addClassNames(LumoUtility.Gap.MEDIUM);

        buildColumns();
        refresh();
    }

    private void buildColumns() {
        grid.addColumn(this::rangoLegible).setHeader("Fecha").setAutoWidth(true);
        grid.addColumn(blackout -> blackout.getCourt() == null ? "Todas las canchas"
                        : blackout.getCourt().getName())
                .setHeader("Cancha").setAutoWidth(true);
        grid.addColumn(Blackout::getReason).setHeader("Motivo").setAutoWidth(true).setFlexGrow(1);

        grid.addComponentColumn(this::actions).setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.setSizeFull();
    }

    private HorizontalLayout actions(Blackout blackout) {
        Button delete = new Button("Eliminar", event -> {
            blackoutRepository.deleteById(blackout.getId());
            Notification.show("Suspensión eliminada. La cancha vuelve a estar disponible.");
            refresh();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_SMALL);

        HorizontalLayout row = new HorizontalLayout(delete);
        row.setPadding(false);
        row.setSpacing(false);
        return row;
    }

    /** "Miércoles 17 de septiembre" si cubre el día operativo entero, si no con horario. */
    private String rangoLegible(Blackout blackout) {
        ZonedDateTime start = blackout.getStartTime().atZone(club.zoneId());
        ZonedDateTime end = blackout.getEndTime().atZone(club.zoneId());
        LocalDate date = start.toLocalDate();
        String dateLabel = capitalize(DATE_LABEL.format(start));

        boolean wholeDay = blackout.getStartTime().equals(slotGenerator.dayStart(club, date))
                && blackout.getEndTime().equals(slotGenerator.dayEnd(club, date));
        if (wholeDay) {
            return dateLabel;
        }
        return "%s, %s a %s".formatted(dateLabel, TIME_LABEL.format(start), TIME_LABEL.format(end));
    }

    private String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void openForm() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Suspender un día");

        DatePicker date = new DatePicker("Fecha");
        date.setI18n(SpanishDates.datePicker());
        date.setValue(LocalDate.now(club.zoneId()));

        Checkbox allDay = new Checkbox("Todo el día", true);

        TimePicker startTime = new TimePicker("Desde");
        startTime.setLocale(CLOCK);
        startTime.setStep(Duration.ofMinutes(30));
        startTime.setValue(club.getOpenTime());
        startTime.setVisible(false);

        TimePicker endTime = new TimePicker("Hasta");
        endTime.setLocale(CLOCK);
        endTime.setStep(Duration.ofMinutes(30));
        endTime.setValue(club.getCloseTime());
        endTime.setVisible(false);

        allDay.addValueChangeListener(event -> {
            boolean whole = event.getValue();
            startTime.setVisible(!whole);
            endTime.setVisible(!whole);
        });

        Select<Court> court = new Select<>();
        court.setLabel("Cancha");
        court.setItems(courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc());
        court.setEmptySelectionAllowed(true);
        court.setEmptySelectionCaption("Todas las canchas");
        // Null-safe a proposito: al habilitar la opcion vacia, Select invoca el
        // generador de etiquetas con null para rotular esa opcion.
        court.setItemLabelGenerator(item -> item == null ? "Todas las canchas" : item.getName());

        TextField reason = new TextField("Motivo");
        reason.setPlaceholder("Feriado, torneo, refacción...");

        Button confirm = new Button("Suspender", event -> {
            if (reason.getValue() == null || reason.getValue().isBlank()) {
                Notification.show("El motivo es obligatorio").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            LocalDate fecha = date.getValue();
            Instant start;
            Instant end;
            if (allDay.getValue()) {
                start = slotGenerator.dayStart(club, fecha);
                end = slotGenerator.dayEnd(club, fecha);
            } else {
                if (startTime.getValue() == null || endTime.getValue() == null) {
                    Notification.show("Falta el horario").addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                start = fecha.atTime(startTime.getValue()).atZone(club.zoneId()).toInstant();
                end = fecha.atTime(endTime.getValue()).atZone(club.zoneId()).toInstant();
            }
            if (!end.isAfter(start)) {
                Notification.show("El horario de \"hasta\" tiene que ser posterior al de \"desde\"")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Runnable save = () -> {
                Blackout blackout = new Blackout();
                blackout.setCourt(court.getValue());
                blackout.setStartTime(start);
                blackout.setEndTime(end);
                blackout.setReason(reason.getValue());
                blackoutRepository.save(blackout);

                Notification.show("Día suspendido. Los turnos existentes siguen vigentes.");
                dialog.close();
                refresh();
            };

            int affected = overlappingBookings(start, end, court.getValue()).size();
            if (affected == 0) {
                save.run();
            } else {
                confirmExistingBookings(affected, save);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);

        FormLayout form = new FormLayout(date, allDay, startTime, endTime, court, reason);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("26em", 2));
        form.setColspan(reason, 2);

        dialog.setWidth("30rem");
        dialog.add(form);
        dialog.getFooter().add(cancel(dialog), confirm);
        dialog.open();
    }

    /** Turnos vigentes que el cierre no va a tocar y el club tiene que revisar. */
    private List<Booking> overlappingBookings(Instant start, Instant end, Court court) {
        List<Booking> overlapping = bookingRepository.findOverlapping(start, end,
                java.util.EnumSet.of(BookingStatus.DRAFT, BookingStatus.AWAITING_CONFIRMATION,
                        BookingStatus.CONFIRMED, BookingStatus.COMPLETED));
        return overlapping.stream()
                .filter(booking -> court == null || booking.getCourt().getId().equals(court.getId()))
                .toList();
    }

    private void confirmExistingBookings(int affected, Runnable save) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader(affected == 1 ? "Hay un turno ya reservado"
                : "Hay %d turnos ya reservados".formatted(affected));
        confirm.setText("La suspensión no los cancela. Revisalos para decidir si siguen, se reubican "
                + "o se dan de baja.");
        confirm.setCancelable(true);
        confirm.setCancelText("Volver");
        confirm.setConfirmText("Suspender igualmente");
        confirm.addConfirmListener(event -> save.run());
        confirm.open();
    }

    /** Cancelar es terciario: al lado del primario tiene que pesar menos. */
    private static Button cancel(Dialog dialog) {
        Button button = new Button("Cancelar", event -> dialog.close());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return button;
    }

    private void refresh() {
        club = tenantService.requireCurrent();
        List<Blackout> blackouts =
                blackoutRepository.findUpcoming(LocalDate.now(club.zoneId()).atStartOfDay(club.zoneId())
                        .toInstant());
        grid.setItems(blackouts);
        count.setText(blackouts.size() == 1 ? "1 suspensión" : blackouts.size() + " suspensiones");
        count.getElement().getThemeList().add("badge contrast");
    }
}
