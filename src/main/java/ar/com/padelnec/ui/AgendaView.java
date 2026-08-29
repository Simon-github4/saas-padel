package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.SlotGenerator;
import ar.com.padelnec.service.SlotGenerator.Slot;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.support.PhoneNumbers;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Agenda del dia: canchas contra horarios, que es como el club mira su negocio.
 *
 * <p>Muestra a la vez lo vendido y lo que queda libre. Un hueco no es una celda
 * vacia sino una venta pendiente, asi que se puede tocar para cargar ahi mismo el
 * turno que entro por telefono.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Agenda | Panel del club")
@PermitAll
public class AgendaView extends VerticalLayout {

    private final TenantService tenantService;
    private final BookingService bookingService;
    private final CourtRepository courtRepository;
    private final SlotGenerator slotGenerator;
    private final PaymentService paymentService;
    private final PhoneNumbers phoneNumbers;
    private final transient AuthenticationContext authenticationContext;
    private final Clock clock;

    private final DatePicker datePicker = new DatePicker();
    private final Grid<AgendaRow> grid = new Grid<>();
    private final Span summary = new Span();

    private Tenant club;
    private List<Court> courts = List.of();

    public AgendaView(TenantService tenantService, BookingService bookingService,
                      CourtRepository courtRepository, SlotGenerator slotGenerator,
                      PaymentService paymentService, PhoneNumbers phoneNumbers,
                      AuthenticationContext authenticationContext, Clock clock) {
        this.tenantService = tenantService;
        this.bookingService = bookingService;
        this.courtRepository = courtRepository;
        this.slotGenerator = slotGenerator;
        this.paymentService = paymentService;
        this.phoneNumbers = phoneNumbers;
        this.authenticationContext = authenticationContext;
        this.clock = clock;

        setSizeFull();
        add(toolbar(), summary, grid);
        setFlexGrow(1, grid);

        this.club = tenantService.requireCurrent();
        datePicker.setValue(LocalDate.now(club.zoneId()));
        refresh();
    }

    // -------------------------------------------------------------- toolbar

    private HorizontalLayout toolbar() {
        datePicker.setLabel("Dia");
        datePicker.setI18n(SpanishDates.datePicker());
        datePicker.addValueChangeListener(event -> refresh());

        Button previous = new Button(VaadinIcon.ANGLE_LEFT.create(),
                event -> datePicker.setValue(datePicker.getValue().minusDays(1)));
        Button next = new Button(VaadinIcon.ANGLE_RIGHT.create(),
                event -> datePicker.setValue(datePicker.getValue().plusDays(1)));
        Button today = new Button("Hoy",
                event -> datePicker.setValue(LocalDate.now(club.zoneId())));
        today.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout toolbar = new HorizontalLayout(previous, datePicker, next, today);
        toolbar.setAlignItems(Alignment.BASELINE);
        return toolbar;
    }

    // ---------------------------------------------------------------- datos

    /** Una fila de la grilla: un horario y lo que pasa en cada cancha a esa hora. */
    private record AgendaRow(LocalTime start, LocalTime end, Slot slot, Map<UUID, Booking> byCourt) {

        Optional<Booking> at(Court court) {
            return Optional.ofNullable(byCourt.get(court.getId()));
        }
    }

    private void refresh() {
        club = tenantService.requireCurrent();
        courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        LocalDate date = datePicker.getValue();

        List<Booking> bookings = bookingService.agendaFor(club, date).stream()
                .filter(booking -> booking.getStatus().occupiesSlot())
                .toList();

        List<AgendaRow> rows = new ArrayList<>();
        for (Slot slot : slotGenerator.generate(club, date)) {
            Map<UUID, Booking> byCourt = new LinkedHashMap<>();
            for (Booking booking : bookings) {
                if (booking.overlaps(slot.startsAt(), slot.endsAt())) {
                    byCourt.put(booking.getCourt().getId(), booking);
                }
            }
            rows.add(new AgendaRow(slot.startTime(), slot.endTime(), slot, byCourt));
        }

        rebuildColumns();
        grid.setItems(rows);
        summary.setText(summaryOf(rows));
    }

    private String summaryOf(List<AgendaRow> rows) {
        if (courts.isEmpty()) {
            return "Todavia no cargaste ninguna cancha.";
        }
        long total = (long) rows.size() * courts.size();
        long taken = rows.stream().mapToLong(row -> row.byCourt().size()).sum();
        return "%d de %d turnos vendidos".formatted(taken, total);
    }

    private void rebuildColumns() {
        grid.removeAllColumns();
        grid.addColumn(row -> "%s - %s".formatted(row.start(), row.end()))
                .setHeader("Horario")
                .setAutoWidth(true)
                .setFlexGrow(0);

        for (Court court : courts) {
            grid.addComponentColumn(row -> cell(row, court))
                    .setHeader(court.getName())
                    .setAutoWidth(true);
        }
    }

    // --------------------------------------------------------------- celdas

    private Component cell(AgendaRow row, Court court) {
        return row.at(court)
                .<Component>map(booking -> bookedCell(booking))
                .orElseGet(() -> freeCell(row, court));
    }

    private Component bookedCell(Booking booking) {
        Button cell = new Button(label(booking), event -> openDetail(booking));
        cell.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        cell.getElement().getThemeList().add(themeFor(booking.getStatus()));
        cell.setWidthFull();
        return cell;
    }

    private String label(Booking booking) {
        String name = booking.getCustomer().getFullName();
        return switch (booking.getStatus()) {
            case AWAITING_CONFIRMATION -> name + " (sin confirmar)";
            case DRAFT -> name + " (esperando pago)";
            case COMPLETED -> name + " (jugado)";
            default -> booking.isPaidInFull() ? name + " (pago)" : name;
        };
    }

    private String themeFor(BookingStatus status) {
        return switch (status) {
            case DRAFT, AWAITING_CONFIRMATION -> "badge contrast";
            case COMPLETED -> "badge success";
            default -> "badge";
        };
    }

    private Component freeCell(AgendaRow row, Court court) {
        Button free = new Button("Libre", event -> openManualBooking(row, court));
        free.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        free.addClassNames(LumoUtility.TextColor.SECONDARY);
        free.setWidthFull();
        // Un turno que ya empezo no se puede cargar hacia atras.
        free.setEnabled(row.slot().startsAt().isAfter(clock.instant()));
        return free;
    }

    // -------------------------------------------------------------- dialogos

    private void openDetail(Booking booking) {
        UUID userId = authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class)
                .map(ClubUserPrincipal::userId)
                .orElse(null);
        new BookingDetailDialog(booking, club, bookingService, paymentService, phoneNumbers,
                userId, this::refresh).open();
    }

    private void openManualBooking(AgendaRow row, Court court) {
        new ManualBookingDialog(club, court, row.slot().startsAt(), bookingService, saved -> {
            Notification.show("Turno cargado para " + saved.getCustomer().getFullName());
            refresh();
        }).open();
    }
}
