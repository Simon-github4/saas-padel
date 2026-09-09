package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.service.SlotGenerator;
import ar.com.padelnec.service.SlotGenerator.Slot;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.support.PhoneNumbers;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private static final DateTimeFormatter DATE_HEADING =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TenantService tenantService;
    private final BookingService bookingService;
    private final CourtRepository courtRepository;
    private final BlackoutRepository blackoutRepository;
    private final SlotGenerator slotGenerator;
    private final PaymentService paymentService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final PhoneNumbers phoneNumbers;
    private final NotificationService notificationService;
    private final transient AuthenticationContext authenticationContext;
    private final Clock clock;

    private final DatePicker datePicker = new DatePicker();
    private final H2 dateHeading = new H2();
    private final Span summary = new Span();
    private final VerticalLayout fixedList = new VerticalLayout();
    private final Div board = new Div();

    private Tenant club;
    private List<Court> courts = List.of();
    private List<Blackout> blackouts = List.of();

    public AgendaView(TenantService tenantService, BookingService bookingService,
                      CourtRepository courtRepository, BlackoutRepository blackoutRepository,
                      SlotGenerator slotGenerator, PaymentService paymentService,
                      ProductService productService, ProductRepository productRepository,
                      PhoneNumbers phoneNumbers, NotificationService notificationService,
                      AuthenticationContext authenticationContext, Clock clock) {
        this.tenantService = tenantService;
        this.bookingService = bookingService;
        this.courtRepository = courtRepository;
        this.blackoutRepository = blackoutRepository;
        this.slotGenerator = slotGenerator;
        this.paymentService = paymentService;
        this.productService = productService;
        this.productRepository = productRepository;
        this.phoneNumbers = phoneNumbers;
        this.notificationService = notificationService;
        this.authenticationContext = authenticationContext;
        this.clock = clock;

        setSizeFull();
        board.addClassName("agenda-board");
        fixedList.addClassName("agenda-fixed-list");
        fixedList.setPadding(false);
        fixedList.setSpacing(false);

        add(header(), fixedList, board, legend());
        setFlexGrow(1, board);
        addClassNames(LumoUtility.Gap.MEDIUM);

        this.club = tenantService.requireCurrent();
        datePicker.setValue(LocalDate.now(club.zoneId()));
        refresh();
    }

    // --------------------------------------------------------------- header

    /**
     * La fecha como titulo, no como una etiqueta mas de formulario, con el
     * resumen del dia debajo y el DatePicker/flechas debajo de eso: son los que
     * cambian el dia, pero no hace falta que compitan con el titulo por la
     * misma linea.
     */
    private VerticalLayout header() {
        dateHeading.addClassName("day-heading");
        summary.addClassName("day-summary-line");

        VerticalLayout titleBlock = new VerticalLayout(dateHeading, summary);
        titleBlock.setPadding(false);
        titleBlock.setSpacing(false);
        titleBlock.addClassNames(LumoUtility.Gap.XSMALL);

        VerticalLayout header = new VerticalLayout(titleBlock, dayPicker());
        header.setPadding(false);
        header.setSpacing(false);
        header.addClassNames(LumoUtility.Gap.XSMALL);
        return header;
    }

    private HorizontalLayout dayPicker() {
        datePicker.setI18n(SpanishDates.datePicker());
        datePicker.addValueChangeListener(event -> refresh());

        Button previous = new Button(VaadinIcon.ANGLE_LEFT.create(),
                event -> datePicker.setValue(datePicker.getValue().minusDays(1)));
        previous.setAriaLabel("Día anterior");
        Button next = new Button(VaadinIcon.ANGLE_RIGHT.create(),
                event -> datePicker.setValue(datePicker.getValue().plusDays(1)));
        next.setAriaLabel("Día siguiente");
        for (Button arrow : List.of(previous, next)) {
            arrow.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        }

        Button today = new Button("Hoy",
                event -> datePicker.setValue(LocalDate.now(club.zoneId())));
        today.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // Sin label, el campo ya no tiene un renglon extra arriba: las flechas
        // se alinean al centro con el resto del control de un solo bloque.
        HorizontalLayout dayPicker = new HorizontalLayout(previous, datePicker, next, today);
        dayPicker.setAlignItems(Alignment.CENTER);
        dayPicker.setPadding(false);
        dayPicker.addClassNames(LumoUtility.Gap.XSMALL);
        return dayPicker;
    }

    /**
     * Que significa cada color, explicado una sola vez abajo del tablero: la
     * tarjeta ya no lleva una insignia con el texto del estado (le sacaba ancho
     * al nombre), asi que esta es la unica referencia de esos colores.
     */
    private Component legend() {
        // Los rotulos son del estado de la plata, no del turno: es lo unico que
        // el color distingue, y "Pago / Jugado" invitaba a leer como cobrado un
        // turno que el sistema habia cerrado solo sin que nadie lo cobrara.
        Div legend = new Div(
                legendItem("pending", "Sin confirmar"),
                legendItem("neutral", "Falta cobrar"),
                legendItem("success", "Cobrado"),
                legendItem("suspended", "Suspendido"));
        legend.addClassName("agenda-legend");
        return legend;
    }

    private Component legendItem(String modifier, String label) {
        Span dot = new Span();
        dot.addClassNames("agenda-legend__dot", "agenda-legend__dot--" + modifier);
        Div item = new Div(dot, new Span(label));
        item.addClassName("agenda-legend__item");
        return item;
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
        dateHeading.setText(DATE_HEADING.format(date));

        List<Booking> bookings = bookingService.agendaFor(club, date).stream()
                .filter(booking -> booking.getStatus().occupiesSlot())
                .toList();
        blackouts = blackoutRepository.findOverlapping(
                slotGenerator.dayStart(club, date), slotGenerator.dayEnd(club, date));

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

        renderBoard(rows);
        summary.setText(summaryOf(rows));
        renderFixedList(bookings);
    }

    /**
     * Los turnos que vinieron de un turno fijo, listados aparte arriba del
     * tablero: encontrarlos celda por celda en la grilla de abajo no es rapido,
     * y son justo los que el mostrador necesita confirmar de un vistazo.
     */
    private void renderFixedList(List<Booking> bookings) {
        List<Booking> fixed = bookings.stream()
                .filter(booking -> booking.getRecurringBooking() != null)
                .sorted(Comparator.comparing(Booking::getStartTime))
                .toList();

        fixedList.removeAll();
        fixedList.setVisible(!fixed.isEmpty());
        if (fixed.isEmpty()) {
            return;
        }

        H3 heading = new H3("Turnos fijos de hoy");
        heading.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Margin.NONE,
                LumoUtility.FontWeight.SEMIBOLD);
        fixedList.add(heading);
        fixed.forEach(booking -> fixedList.add(fixedRow(booking)));
    }

    private Component fixedRow(Booking booking) {
        String label = "%s · %s · %s".formatted(
                booking.getCourt().getName(),
                HH_MM.format(booking.getStartTime().atZone(club.zoneId())),
                booking.getCustomer().getFullName());
        Button row = new Button(label, VaadinIcon.ROTATE_LEFT.create(), event -> openDetail(booking));
        row.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        row.addClassName("agenda-fixed-row");
        return row;
    }

    private String summaryOf(List<AgendaRow> rows) {
        if (courts.isEmpty()) {
            return "Todavia no cargaste ninguna cancha.";
        }
        long total = (long) rows.size() * courts.size();
        long taken = rows.stream().mapToLong(row -> row.byCourt().size()).sum();
        return "%d de %d turnos vendidos".formatted(taken, total);
    }

    /** Reconstruye el tablero entero: una celda de esquina, un encabezado por
     * cancha, y por cada horario su etiqueta mas una tarjeta por cancha. El
     * orden de insercion alcanza para que CSS Grid las ubique bien, con las
     * dimensiones que fija grid-template-columns/-rows mas abajo. */
    private void renderBoard(List<AgendaRow> rows) {
        board.removeAll();
        if (courts.isEmpty()) {
            board.add(new Span("Todavia no cargaste ninguna cancha."));
            return;
        }
        // minmax y no un ancho fijo: con pocas canchas, las columnas se estiran
        // para llenar el ancho disponible en vez de dejar un hueco en blanco a
        // la derecha del tablero. Con muchas, el minimo es el que fuerza el
        // scroll horizontal.
        board.getStyle()
                .set("grid-template-columns",
                        "var(--agenda-label-width) repeat(%d, minmax(var(--agenda-col-width), 1fr))"
                                .formatted(courts.size()))
                .set("grid-template-rows",
                        "var(--agenda-header-height) repeat(%d, var(--agenda-row-height))".formatted(rows.size()));

        board.add(corner());
        courts.forEach(court -> board.add(courtHeader(court)));
        for (AgendaRow row : rows) {
            board.add(timeLabel(row));
            courts.forEach(court -> board.add(slotCard(row, court)));
        }
    }

    private Component corner() {
        Div corner = new Div();
        corner.addClassNames("agenda-board__corner", "agenda-board__header-cell");
        return corner;
    }

    private Component courtHeader(Court court) {
        Div header = new Div(new Span(court.getName()));
        header.addClassNames("agenda-board__court-header", "agenda-board__header-cell");
        return header;
    }

    private Component timeLabel(AgendaRow row) {
        Div label = new Div(new Span(row.start().toString()));
        label.addClassNames("agenda-board__time-label", "agenda-board__header-cell", "tabular");
        return label;
    }

    // --------------------------------------------------------------- celdas

    private Component slotCard(AgendaRow row, Court court) {
        return row.at(court)
                .<Component>map(this::bookedCard)
                .orElseGet(() -> freeCard(row, court));
    }

    private Component bookedCard(Booking booking) {
        Tone tone = toneFor(booking);
        Button button = new Button(booking.getCustomer().getFullName(), event -> openDetail(booking));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("agenda-card__button");
        button.getElement().setAttribute("title", booking.getCustomer().getFullName());
        button.setWidthFull();
        // El color de estado va en la tarjeta entera (franja, tinte de fondo y el
        // propio texto del nombre) y no en una insignia aparte: una insignia le
        // saca ancho al nombre, que es el dato que hace falta leer de un vistazo.
        // Que color es cada estado se explica una sola vez en la referencia de
        // abajo del tablero, no repetido en cada tarjeta.
        return card(button, "agenda-card--booked", "agenda-card--" + tone.modifier());
    }

    /**
     * Envuelve el boton en un Div que es el que lleva el color: el theme
     * "tertiary-inline" fuerza background transparente dentro de su propio
     * shadow DOM con !important, y eso no se puede pisar desde afuera. El
     * fondo/borde de la tarjeta vive en este Div; el boton adentro solo pone
     * el texto, la insignia y el click.
     */
    private Div card(Button button, String... modifiers) {
        Div wrapper = new Div(button);
        wrapper.addClassName("agenda-card");
        wrapper.addClassNames(modifiers);
        return wrapper;
    }

    /** Mismo caso a caso que antes distinguia el sufijo de texto, ahora como color. */
    private record Tone(String modifier) {
    }

    /**
     * El color dice el estado de la plata, no el del turno.
     *
     * <p>COMPLETED tenia su propio caso y salia verde sin mirar el saldo. Como
     * {@code BookingExpiryWorker.closePlayedBookings} cierra solo los turnos una
     * hora despues de terminar, un turno sin cobrar se veia neutral -"falta
     * algo"- mientras el jugador estaba en la cancha, y se ponia verde justo
     * cuando ya se habia ido y cobrarlo era mas dificil. Encima la caja lo seguia
     * contando en "Pendiente de cobro": dos pantallas del mismo club diciendo
     * cosas distintas del mismo turno.
     *
     * <p>Lo que se pierde es distinguir un turno jugado de uno confirmado, que
     * cobrados ya se veian iguales igual. El estado exacto esta en el detalle.
     */
    private Tone toneFor(Booking booking) {
        return switch (booking.getStatus()) {
            case AWAITING_CONFIRMATION, DRAFT -> new Tone("pending");
            default -> new Tone(booking.isPaidInFull() ? "success" : "neutral");
        };
    }

    private Component freeCard(AgendaRow row, Court court) {
        Optional<Blackout> suspension = suspensionFor(row, court);
        boolean blocked = suspension.isPresent();
        // Un turno que ya empezo no se puede cargar hacia atras, y una franja
        // suspendida tampoco: cargarla igual solo pasa la pelota a que el
        // service la rechace despues de llenar el formulario entero.
        boolean enabled = !blocked && row.slot().startsAt().isAfter(clock.instant());

        Button button = new Button(blocked ? "Suspendido" : "Libre", event -> openManualBooking(row, court));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("agenda-card__button");
        button.setWidthFull();
        button.setEnabled(enabled);
        suspension.map(Blackout::getReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .ifPresent(reason -> button.getElement().setAttribute("title", reason));

        Div wrapper = card(button, blocked ? "agenda-card--suspended" : "agenda-card--free");
        if (!enabled) {
            wrapper.addClassName("agenda-card--disabled");
        }
        return wrapper;
    }

    private Optional<Blackout> suspensionFor(AgendaRow row, Court court) {
        return blackouts.stream()
                .filter(blackout -> blackout.appliesTo(court)
                        && blackout.overlaps(row.slot().startsAt(), row.slot().endsAt()))
                .findFirst();
    }

    // -------------------------------------------------------------- dialogos

    private void openDetail(Booking booking) {
        UUID userId = authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class)
                .map(ClubUserPrincipal::userId)
                .orElse(null);
        new BookingDetailDialog(booking, club, bookingService, paymentService, productService,
                productRepository, phoneNumbers, notificationService, userId, this::refresh).open();
    }

    private void openManualBooking(AgendaRow row, Court court) {
        new ManualBookingDialog(club, court, row.slot().startsAt(), bookingService,
                notificationService, phoneNumbers, saved -> refresh()).open();
    }
}
