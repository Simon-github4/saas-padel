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
import ar.com.padelnec.service.BlackoutService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.service.SlotGenerator;
import ar.com.padelnec.service.SlotGenerator.Slot;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.service.TournamentService;
import ar.com.padelnec.service.TournamentService.MarkResult;
import ar.com.padelnec.service.TournamentService.TournamentSlot;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Agenda del dia: canchas contra horarios, que es como el club mira su negocio.
 *
 * <p>Muestra a la vez lo vendido y lo que queda libre. Un hueco no es una celda
 * vacia sino una venta pendiente, asi que se puede tocar para cargar ahi mismo el
 * turno que entro por telefono.
 *
 * <p>En modo torneo, los huecos se tocan para elegirlos (uno por uno, o la fila o
 * la columna entera) y se guardan juntos como turnos de un torneo: ver
 * {@link TournamentService}.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Agenda | Panel del club")
@PermitAll
public class AgendaView extends VerticalLayout implements RefreshesOnNewBookings {

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
    private final CustomerService customerService;
    private final TournamentService tournamentService;
    private final BlackoutService blackoutService;
    private final transient AuthenticationContext authenticationContext;
    private final Clock clock;

    private final DatePicker datePicker = new DatePicker();
    private final H2 dateHeading = new H2();
    private final Span summary = new Span();
    private final Details fixedList = new Details();
    private final VerticalLayout fixedListItems = new VerticalLayout();
    private final Div board = new Div();

    private final Button markTournament = new Button();
    private final HorizontalLayout tournamentBar = new HorizontalLayout();
    private final Span tournamentCount = new Span();
    private final TextField tournamentName = new TextField();

    private Tenant club;
    private List<Court> courts = List.of();
    private List<Blackout> blackouts = List.of();
    private SlotGenerator.DayPlan plan;
    private Map<UUID, Instant> bookingRows = Map.of();

    /** En modo torneo, tocar un hueco lo elige en vez de abrir la carga de un turno. */
    private boolean marking;
    private final Set<TournamentSlot> marked = new LinkedHashSet<>();
    /**
     * Los huecos que se pueden elegir en el tablero dibujado, con su tarjeta: elegir
     * uno cambia solo esa tarjeta, sin redibujar todo (y sin que salte el scroll).
     */
    private final Map<TournamentSlot, MarkableCard> markable = new LinkedHashMap<>();

    private record MarkableCard(Div card, Button button) {
    }

    public AgendaView(TenantService tenantService, BookingService bookingService,
                      CourtRepository courtRepository, BlackoutRepository blackoutRepository,
                      SlotGenerator slotGenerator, PaymentService paymentService,
                      ProductService productService, ProductRepository productRepository,
                      PhoneNumbers phoneNumbers, NotificationService notificationService,
                      CustomerService customerService, TournamentService tournamentService,
                      BlackoutService blackoutService,
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
        this.customerService = customerService;
        this.tournamentService = tournamentService;
        this.blackoutService = blackoutService;
        this.authenticationContext = authenticationContext;
        this.clock = clock;

        // Ancho completo, pero alto natural: el tablero tiene una fila por
        // horario y quiere todo el alto que le corresponda. Con setSizeFull()
        // quedaba encerrado en el alto de la pantalla y se aplastaba cuando
        // "Turnos fijos" ocupaba varias lineas; asi, si no entra todo, scrollea
        // la pagina en vez de compactar el tablero.
        setWidthFull();
        board.addClassName("agenda-board");
        fixedListItems.addClassName("agenda-fixed-list");
        fixedListItems.setPadding(false);
        fixedListItems.setSpacing(false);
        fixedList.add(fixedListItems);
        // Abierta por default -para verlos de un vistazo sin abrir nada-, pero
        // se puede minimizar: con muchos turnos fijos, tapaba el tablero de abajo.
        fixedList.setOpened(true);
        fixedList.addClassName("agenda-fixed-details");

        add(header(), fixedList, board, legend(), tournamentBar());
        addClassNames(LumoUtility.Gap.MEDIUM);
        setMarking(false);

        this.club = tenantService.requireCurrent();
        // El value-change listener de dayPicker() ya quedo registrado por add(header()...)
        // de arriba, asi que este setValue dispara el primer refresh() solo.
        datePicker.setValue(LocalDate.now(club.zoneId()));
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
        datePicker.addValueChangeListener(event -> {
            // Un torneo se elige dia por dia: cambiar de dia descarta lo elegido.
            setMarking(false);
            refresh();
        });

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

        markTournament.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        markTournament.addClickListener(event -> {
            setMarking(!marking);
            refresh();
        });

        // Sin label, el campo ya no tiene un renglon extra arriba: las flechas
        // se alinean al centro con el resto del control de un solo bloque.
        HorizontalLayout dayPicker = new HorizontalLayout(previous, datePicker, next, today, markTournament);
        dayPicker.setAlignItems(Alignment.CENTER);
        dayPicker.setPadding(false);
        // En el celular "Marcar torneo" baja a otro renglon: en uno solo no
        // entra y corria toda la pagina de costado.
        dayPicker.addClassNames(LumoUtility.Gap.XSMALL, LumoUtility.FlexWrap.WRAP);
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
                legendItem("suspended", "Suspendido"),
                legendItem("tournament", "Torneo"));
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

    // ---------------------------------------------------------- modo torneo

    /**
     * La barra del modo torneo, pegada al pie de la pantalla como la de Guardar de
     * Configuracion: con muchos horarios el tablero es mas alto que la pantalla, y
     * el boton tiene que estar a mano mientras se eligen los de abajo.
     */
    private HorizontalLayout tournamentBar() {
        tournamentCount.addClassName("agenda-tournament-bar__count");
        tournamentName.setPlaceholder("Nombre del torneo");
        tournamentName.setAriaLabel("Nombre del torneo");
        tournamentName.setMaxLength(160);
        tournamentName.addValueChangeListener(event -> tournamentName.setInvalid(false));

        Button cancel = new Button("Cancelar", event -> {
            setMarking(false);
            refresh();
        });
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button save = new Button("Guardar torneo", event -> saveTournament());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        tournamentBar.add(tournamentCount, tournamentName, cancel, save);
        tournamentBar.setFlexGrow(1, tournamentName);
        tournamentBar.setAlignItems(Alignment.CENTER);
        tournamentBar.setWidthFull();
        tournamentBar.addClassName("agenda-tournament-bar");
        return tournamentBar;
    }

    /** Entra o sale del modo torneo, siempre sin nada elegido. No redibuja el tablero. */
    private void setMarking(boolean on) {
        marking = on;
        marked.clear();
        tournamentName.clear();
        tournamentName.setInvalid(false);
        markTournament.setText(on ? "Salir del modo torneo" : "Marcar torneo");
        markTournament.setIcon((on ? VaadinIcon.CLOSE_SMALL : VaadinIcon.TROPHY).create());
        board.getElement().getClassList().set("agenda-board--marking", on);
        tournamentBar.setVisible(on);
        updateTournamentCount();
    }

    private void toggle(TournamentSlot slot) {
        if (!marked.remove(slot)) {
            marked.add(slot);
        }
        paint(slot);
        updateTournamentCount();
    }

    /** Elige la fila o la columna entera; si ya estaba toda elegida, la suelta. */
    private void toggleAll(List<TournamentSlot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        if (marked.containsAll(slots)) {
            slots.forEach(marked::remove);
        } else {
            marked.addAll(slots);
        }
        slots.forEach(this::paint);
        updateTournamentCount();
    }

    private void paint(TournamentSlot slot) {
        MarkableCard markableCard = markable.get(slot);
        if (markableCard == null) {
            return;
        }
        boolean picked = marked.contains(slot);
        markableCard.card().getClassNames().set("agenda-card--picked", picked);
        markableCard.button().setText(picked ? "✓ Torneo" : "Libre");
    }

    private void updateTournamentCount() {
        long courtCount = marked.stream().map(TournamentSlot::courtId).distinct().count();
        tournamentCount.setText("%d %s en %d %s".formatted(
                marked.size(), marked.size() == 1 ? "turno" : "turnos",
                courtCount, courtCount == 1 ? "cancha" : "canchas"));
    }

    private void saveTournament() {
        MarkResult result;
        try {
            result = tournamentService.mark(club, tournamentName.getValue(), marked);
        } catch (BusinessRuleException ex) {
            // El mensaje ya viene escrito para leerse en pantalla.
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        String name = tournamentName.getValue().strip();
        setMarking(false);
        refresh();

        if (result.marked() == 0) {
            Notification.show("No se guardó ningún turno: ya no estaban libres.")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        String saved = "%s: %d %s.".formatted(name, result.marked(), result.marked() == 1 ? "turno" : "turnos");
        if (result.leftOut().isEmpty()) {
            Notification.show(saved).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            return;
        }
        // Entre que se eligieron y se guardaron, alguien pudo reservar uno: el club
        // tiene que saber cual, porque en el tablero ya no lo va a ver como torneo.
        Notification notice = Notification.show("%s %s se %s mientras marcabas y %s afuera.".formatted(
                saved, describe(result.leftOut()),
                result.leftOut().size() == 1 ? "ocupó" : "ocuparon",
                result.leftOut().size() == 1 ? "quedó" : "quedaron"), 8000, Notification.Position.BOTTOM_START);
        notice.addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    /** "Cancha 2 18:30 y Cancha 3 20:00", para decir que turnos quedaron afuera. */
    private String describe(List<TournamentSlot> slots) {
        Map<UUID, String> names = courts.stream().collect(Collectors.toMap(Court::getId, Court::getName));
        List<String> parts = slots.stream()
                .map(slot -> "%s %s".formatted(names.getOrDefault(slot.courtId(), "Una cancha"),
                        HH_MM.format(slot.startsAt().atZone(club.zoneId()))))
                .toList();
        return parts.size() == 1 ? parts.getFirst()
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " y " + parts.getLast();
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
        plan = slotGenerator.plan(club, date);
        blackouts = blackoutRepository.findOverlapping(plan.start(), plan.end());

        // Si el horario se achico despues de reservar, el turno sigue vigente y
        // necesita una fila propia aunque ya no forme parte de la grilla actual.
        // La reserva manda visualmente sobre el cierre, igual que con suspensiones.
        Map<Instant, Slot> visibleSlots = new LinkedHashMap<>();
        List<Slot> scheduleSlots = plan.slots().isEmpty()
                ? slotGenerator.clubPlan(club, date).slots() : plan.slots();
        scheduleSlots.forEach(slot -> visibleSlots.put(slot.startsAt(), slot));
        bookings.forEach(booking -> visibleSlots.putIfAbsent(booking.getStartTime(),
                new Slot(booking.getStartTime().atZone(club.zoneId()).toLocalTime(),
                        booking.getEndTime().atZone(club.zoneId()).toLocalTime(),
                        booking.getStartTime(), booking.getEndTime())));

        List<AgendaRow> rows = new ArrayList<>();
        for (Slot slot : visibleSlots.values().stream()
                .sorted(Comparator.comparing(Slot::startsAt)).toList()) {
            Map<UUID, Booking> byCourt = new LinkedHashMap<>();
            for (Booking booking : bookings) {
                if (booking.overlaps(slot.startsAt(), slot.endsAt())) {
                    byCourt.put(booking.getCourt().getId(), booking);
                }
            }
            rows.add(new AgendaRow(slot.startTime(), slot.endTime(), slot, byCourt));
        }
        bookingRows = rowWhereEachBookingShows(bookings, rows);

        renderBoard(rows);
        summary.setText(summaryOf(rows));
        renderFixedList(bookings);
        dropWhatIsNoLongerFree();
    }

    /**
     * Si entra una reserva mientras se elige un torneo, el tablero se redibuja con
     * lo elegido, menos lo que ya no esta libre: se avisa para que no se pierda de
     * vista.
     */
    private void dropWhatIsNoLongerFree() {
        if (!marking) {
            return;
        }
        int before = marked.size();
        marked.retainAll(markable.keySet());
        int dropped = before - marked.size();
        if (dropped > 0) {
            Notification.show(dropped == 1
                            ? "Uno de los turnos que elegiste se ocupó y quedó afuera."
                            : "%d de los turnos que elegiste se ocuparon y quedaron afuera.".formatted(dropped),
                    6000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
        updateTournamentCount();
    }

    /**
     * Redibuja el dia que se esta mirando, sea o no el de la reserva nueva: es
     * una consulta por reserva que entra, y asi no hay que adivinar que dia toca.
     */
    @Override
    public void newBookingsArrived() {
        refresh();
    }

    /**
     * La fila en la que se dibuja cada turno: la que arranca a su misma hora.
     *
     * <p>Con canchas de turnos corridos (13:30 contra el 14:00 de las demas) un
     * turno se superpone con dos o tres filas, y se repetia en todas. Se dibuja una
     * sola vez; si ninguna fila arranca justo a su hora (un turno cargado antes de
     * cambiar el horario), en la primera que toca.
     */
    private static Map<UUID, Instant> rowWhereEachBookingShows(List<Booking> bookings, List<AgendaRow> rows) {
        Map<UUID, Instant> shownAt = new HashMap<>();
        for (Booking booking : bookings) {
            rows.stream()
                    .filter(row -> row.slot().startsAt().equals(booking.getStartTime()))
                    .findFirst()
                    .or(() -> rows.stream()
                            .filter(row -> row.at(booking.getCourt())
                                    .filter(booking::equals).isPresent())
                            .findFirst())
                    .ifPresent(row -> shownAt.put(booking.getId(), row.slot().startsAt()));
        }
        return shownAt;
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

        fixedListItems.removeAll();
        fixedList.setVisible(!fixed.isEmpty());
        if (fixed.isEmpty()) {
            return;
        }

        fixedList.setSummaryText("Turnos fijos de hoy (%d)".formatted(fixed.size()));
        fixed.forEach(booking -> fixedListItems.add(fixedRow(booking)));
    }

    private Component fixedRow(Booking booking) {
        String label = "%s · %s · %s".formatted(
                booking.getCourt().getName(),
                HH_MM.format(booking.getStartTime().atZone(club.zoneId())),
                booking.displayName());
        Button row = new Button(label, VaadinIcon.ROTATE_LEFT.create(), event -> openDetail(booking));
        row.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        row.addClassName("agenda-fixed-row");
        return row;
    }

    private String summaryOf(List<AgendaRow> rows) {
        if (courts.isEmpty()) {
            return "Todavia no cargaste ninguna cancha.";
        }
        long total = courts.stream().mapToLong(plan::slotsOpenIn).sum();
        // Distintos: con canchas de turnos corridos, un turno cruza dos filas.
        long taken = rows.stream().flatMap(row -> row.byCourt().values().stream()).distinct().count();
        // Los turnos de un torneo no estan a la venta: no cuentan como turnos por
        // vender, y se nombran aparte para que el total no parezca un dia flojo.
        long tournament = rows.stream()
                .mapToLong(row -> courts.stream()
                        .filter(court -> plan.opens(court, row.slot()) && row.at(court).isEmpty()
                                && suspensionFor(row, court).filter(Blackout::isTournament).isPresent())
                        .count())
                .sum();
        String sold = "%d de %d turnos vendidos".formatted(taken, total - tournament);
        return tournament == 0 ? sold : "%s · %d de torneo".formatted(sold, tournament);
    }

    /** Reconstruye el tablero entero: una celda de esquina, un encabezado por
     * cancha, y por cada horario su etiqueta mas una tarjeta por cancha. El
     * orden de insercion alcanza para que CSS Grid las ubique bien, con las
     * dimensiones que fija grid-template-columns/-rows mas abajo. */
    private void renderBoard(List<AgendaRow> rows) {
        board.removeAll();
        markable.clear();
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
        Div header = new Div(marking
                ? markAllButton(court.getName(), "Elegir toda la columna",
                        slot -> slot.courtId().equals(court.getId()))
                : new Span(court.getName()));
        header.addClassNames("agenda-board__court-header", "agenda-board__header-cell");
        return header;
    }

    private Component timeLabel(AgendaRow row) {
        Div label = new Div(marking
                ? markAllButton(row.start().toString(), "Elegir toda la fila",
                        slot -> slot.startsAt().equals(row.slot().startsAt()))
                : new Span(row.start().toString()));
        label.addClassNames("agenda-board__time-label", "agenda-board__header-cell", "tabular");
        return label;
    }

    /**
     * En modo torneo, el nombre de la cancha y el horario eligen su columna o su fila.
     * Lo que se elige se mira al tocar, no al dibujar: las tarjetas de esa fila
     * todavia no existen cuando se dibuja su horario.
     */
    private Button markAllButton(String text, String tooltip,
                                 Predicate<TournamentSlot> belongs) {
        Button button = new Button(text, event -> toggleAll(markable.keySet().stream()
                .filter(belongs)
                .toList()));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("agenda-board__mark-all");
        button.setTooltipText(tooltip);
        return button;
    }

    // --------------------------------------------------------------- celdas

    private Component slotCard(AgendaRow row, Court court) {
        // Las demas filas que el turno ocupa quedan en blanco: ver rowWhereEachBookingShows.
        return row.at(court)
                .<Component>map(booking -> row.slot().startsAt().equals(bookingRows.get(booking.getId()))
                        ? bookedCard(booking) : new Div())
                .orElseGet(() -> plan.opens(court, row.slot()) ? freeCard(row, court)
                        : plan.openDuring(court, row.slot()) ? new Div() : closedCard(row, court));
    }

    /**
     * La cancha no abre a esa hora ese dia, por su horario propio (Configuracion >
     * Canchas). Si abre pero sus turnos arrancan a otra hora (13:30 contra el 14:00
     * de las demas), la fila no le corresponde y la celda queda en blanco.
     */
    private Component closedCard(AgendaRow row, Court court) {
        Button button = new Button("Cerrada",
                event -> openManualBooking(row, court, "La cancha está cerrada en este horario."));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("agenda-card__button");
        button.setWidthFull();
        button.setEnabled(!marking && row.slot().startsAt().isAfter(clock.instant()));
        button.setTooltipText("Podés cargar un turno manual como excepción");
        Div wrapper = card(button, "agenda-card--suspended");
        if (!button.isEnabled()) {
            wrapper.addClassName("agenda-card--disabled");
        }
        return wrapper;
    }

    private Component bookedCard(Booking booking) {
        Tone tone = toneFor(booking);
        // Si se reservo con un nombre que no es el del dueño del telefono, la
        // tarjeta lo marca: puede ser alguien que se equivoco de numero, y el
        // mostrador lo tiene que ver antes del partido, no cuando llega otro.
        boolean otherName = booking.isBookedUnderAnotherName();
        Button button = new Button((otherName ? "⚠ " : "") + booking.displayName(),
                event -> openDetail(booking));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("agenda-card__button");
        button.setTooltipText(otherName
                ? "Reservó como %s · el teléfono es de %s".formatted(
                        booking.displayName(), booking.getCustomer().getFullName())
                : booking.displayName());
        button.setWidthFull();
        // El color de estado va en la tarjeta entera (franja, tinte de fondo y el
        // propio texto del nombre) y no en una insignia aparte: una insignia le
        // saca ancho al nombre, que es el dato que hace falta leer de un vistazo.
        // Que color es cada estado se explica una sola vez en la referencia de
        // abajo del tablero, no repetido en cada tarjeta.
        Div wrapper = card(button, "agenda-card--booked", "agenda-card--" + tone.modifier());
        if (marking) {
            // En modo torneo solo se tocan los huecos: lo demas queda atenuado.
            button.setEnabled(false);
            wrapper.addClassName("agenda-card--disabled");
        }
        return wrapper;
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
        boolean tournament = suspension.filter(Blackout::isTournament).isPresent();
        // Un turno que ya empezo no se puede cargar hacia atras. Una suspension
        // futura si admite una carga administrativa, con confirmacion explicita.
        boolean future = row.slot().startsAt().isAfter(clock.instant());
        if (marking && future && !blocked) {
            return markableCard(row, court);
        }
        boolean enabled = future && !marking;

        String exception = suspension.map(blackout -> {
            String reason = blackout.getReason();
            if (blackout.isTournament()) {
                return "La cancha está reservada para el torneo «%s».".formatted(reason);
            }
            return reason == null || reason.isBlank()
                    ? "La cancha está suspendida en este horario."
                    : "La cancha está suspendida por «%s».".formatted(reason);
        }).orElse(null);
        // El torneo se nombra en la tarjeta: es lo que el mostrador busca con la
        // vista ("¿donde juega el torneo?"), no un aviso de que algo anda mal.
        String label = tournament ? suspension.get().getReason() : blocked ? "Suspendido" : "Libre";
        Button button = new Button(label, event -> {
            if (blocked) {
                openSuspendedSlot(row, court, exception);
            } else {
                openManualBooking(row, court, null);
            }
        });
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("agenda-card__button");
        button.setWidthFull();
        button.setEnabled(enabled);
        suspension.map(blackout -> blackout.isTournament() ? "Torneo: " + blackout.getReason() : blackout.getReason())
                .filter(reason -> reason != null && !reason.isBlank())
                .ifPresent(button::setTooltipText);

        Div wrapper = card(button, tournament ? "agenda-card--tournament"
                : blocked ? "agenda-card--suspended" : "agenda-card--free");
        if (!enabled) {
            wrapper.addClassName("agenda-card--disabled");
        }
        return wrapper;
    }

    /** Un hueco en modo torneo: tocarlo lo elige o lo suelta. */
    private Component markableCard(AgendaRow row, Court court) {
        TournamentSlot slot = new TournamentSlot(court.getId(), row.slot().startsAt());
        Button button = new Button("Libre", event -> toggle(slot));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.addClassName("agenda-card__button");
        button.setWidthFull();
        Div wrapper = card(button, "agenda-card--free", "agenda-card--markable");
        markable.put(slot, new MarkableCard(wrapper, button));
        paint(slot);
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

    /**
     * Un turno suspendido o de torneo: se puede liberar (vuelve a estar a la venta,
     * recortando la suspension) o cargar un turno igual, como excepcion. Antes el
     * toque iba directo a la carga del turno, y la unica forma de abrir un horario
     * era borrar la suspension entera y volver a cargarla sin ese turno.
     */
    private void openSuspendedSlot(AgendaRow row, Court court, String exception) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("%s · %s".formatted(court.getName(), row.start()));
        dialog.setText(exception + " Podés liberar este horario para que se pueda reservar, "
                + "o cargar un turno igual.");
        dialog.setCancelable(true);
        dialog.setCancelText("Volver");
        dialog.setRejectable(true);
        dialog.setRejectText("Cargar turno igual");
        dialog.addRejectListener(event -> openManualBooking(row, court, exception));
        dialog.setConfirmText("Liberar este horario");
        dialog.addConfirmListener(event -> release(row, court));
        dialog.open();
    }

    private void release(AgendaRow row, Court court) {
        BlackoutService.Released released;
        try {
            released = blackoutService.release(club, court.getId(), row.slot().startsAt());
        } catch (BusinessRuleException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            refresh();
            return;
        }
        refresh();
        Notification.show("%s de %s a %s quedó libre para reservar.".formatted(court.getName(),
                        HH_MM.format(released.startsAt().atZone(club.zoneId())),
                        HH_MM.format(released.endsAt().atZone(club.zoneId()))))
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void openManualBooking(AgendaRow row, Court court, String availabilityException) {
        new ManualBookingDialog(club, court, row.slot().startsAt(), bookingService, customerService,
                notificationService, phoneNumbers, availabilityException, saved -> refresh()).open();
    }
}
