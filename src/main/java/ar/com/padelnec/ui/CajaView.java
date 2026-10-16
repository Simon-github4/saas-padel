package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.service.CashRegisterService;
import ar.com.padelnec.service.CashRegisterService.BuffetLine;
import ar.com.padelnec.service.CashRegisterService.DayCash;
import ar.com.padelnec.service.CashRegisterService.Movement;
import ar.com.padelnec.service.TenantService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * La caja de un dia: los movimientos de plata y a cuanto tienen que sumar,
 * repartidos entre los turnos y el buffet.
 *
 * <p>Es la pantalla del cierre, y por eso vive del lado del mostrador y no en
 * Estadisticas: el que cuenta los billetes a la noche es el que atiende, no el
 * dueño. Estadisticas sigue siendo solo del dueño porque ahi la pregunta es otra
 * (como viene el negocio en el semestre), no cuanto hay en el cajon hoy.
 *
 * <p>Todavia no registra nada: no hay apertura, ni fondo inicial, ni gastos, ni
 * arqueo contra lo contado. Muestra lo que el sistema ya sabe, que es lo que
 * hasta ahora habia que reconstruir turno por turno desde la agenda.
 */
@Route(value = "caja", layout = MainLayout.class)
@PageTitle("Caja | Panel del club")
@PermitAll
public class CajaView extends VerticalLayout {

    private static final DateTimeFormatter DATE_HEADING =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Que movimientos lista la tabla. Un cobro de turno con consumiciones es de
     * las dos partes, y aparece en los dos filtros.
     */
    private enum Part {
        TODO("Todo", movement -> true, "No hubo movimientos de caja este día."),
        TURNOS("Turnos", movement -> movement.turnosAmount().signum() != 0,
                "No hubo cobros de turnos este día."),
        BUFFET("Buffet", movement -> movement.buffetAmount().signum() != 0,
                "No hubo cobros del buffet este día.");

        private final String label;
        private final Predicate<Movement> includes;
        private final String emptyText;

        Part(String label, Predicate<Movement> includes, String emptyText) {
            this.label = label;
            this.includes = includes;
            this.emptyText = emptyText;
        }
    }

    private final TenantService tenantService;
    private final CashRegisterService cashRegisterService;

    private final DatePicker datePicker = new DatePicker();
    private final H2 dateHeading = new H2();
    private final Span summary = new Span();
    private final Div top = new Div();
    private final Grid<Movement> movementsGrid = new Grid<>();
    private final Grid<BuffetLine> buffetGrid = new Grid<>();
    private final Map<Part, Button> partButtons = new EnumMap<>(Part.class);

    private Tenant club;
    private DayCash caja;
    private Part part = Part.TODO;

    public CajaView(TenantService tenantService, CashRegisterService cashRegisterService) {
        this.tenantService = tenantService;
        this.cashRegisterService = cashRegisterService;

        setSizeFull();
        this.club = tenantService.requireCurrent();

        top.addClassName("caja-top");

        buildMovementsColumns();
        buildBuffetColumns();

        add(header(), top, movementsSection(), buffetSection());
        addClassNames(LumoUtility.Gap.LARGE);

        // El value-change listener de dayPicker() ya quedo registrado por el add()
        // de arriba, asi que este setValue dispara el primer refresh() solo.
        datePicker.setValue(LocalDate.now(club.zoneId()));
    }

    // --------------------------------------------------------------- header

    /** Mismo encabezado que la agenda: la fecha como titulo y el picker debajo. */
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

        Button today = new Button("Hoy", event -> datePicker.setValue(LocalDate.now(club.zoneId())));
        today.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout row = new HorizontalLayout(previous, datePicker, next, today);
        row.setAlignItems(Alignment.CENTER);
        row.setPadding(false);
        row.addClassNames(LumoUtility.Gap.XSMALL);
        return row;
    }

    // ---------------------------------------------------------------- datos

    private void refresh() {
        club = tenantService.requireCurrent();
        LocalDate date = datePicker.getValue();
        if (date == null) {
            return;
        }
        caja = cashRegisterService.of(club, date);

        dateHeading.setText(DATE_HEADING.format(date));
        summary.setText(caja.movements().isEmpty()
                ? "Sin movimientos de caja"
                : "%d %s · %s".formatted(caja.movements().size(),
                        caja.movements().size() == 1 ? "movimiento" : "movimientos",
                        Money.format(caja.total())));

        top.removeAll();
        top.add(new RepartoCard(caja), pendingCard(caja));
        showMovements();
        buffetGrid.setItems(caja.buffet());
    }

    /**
     * Lo que falta cobrar de los turnos y los pedidos de buffet del dia.
     *
     * <p>Va en rojo solo si hay algo: en cero es una buena noticia, no una
     * advertencia, y pintarlo igual entrena a ignorar el color.
     */
    private KpiCard pendingCard(DayCash caja) {
        KpiCard card = new KpiCard("Pendiente de cobro", Money.format(caja.pending()));
        card.addClassName("caja-top__pending");
        if (caja.pending().signum() > 0) {
            List<String> parts = new ArrayList<>();
            if (caja.pendingBookings() > 0) {
                parts.add(caja.pendingBookings() == 1 ? "1 turno" : caja.pendingBookings() + " turnos");
            }
            if (caja.pendingOrders() > 0) {
                parts.add(caja.pendingOrders() == 1 ? "1 pedido" : caja.pendingOrders() + " pedidos");
            }
            card.alert().footnote(String.join(" y ", parts) + " con saldo");
        } else {
            card.footnote("Los turnos y pedidos están al día");
        }
        return card;
    }

    // --------------------------------------------------------------- tablas

    private VerticalLayout movementsSection() {
        movementsGrid.setAllRowsVisible(true);
        VerticalLayout block = section("Movimientos del día", movementsGrid, null);
        // El filtro va en el renglon del titulo y no arriba de todo: filtra esta
        // tabla, no el reparto, que siempre muestra las dos partes.
        Component heading = block.getComponentAt(0);
        Div headingRow = new Div(heading, partFilter());
        headingRow.addClassName("caja-section-heading");
        block.addComponentAsFirst(headingRow);
        return block;
    }

    /**
     * Todo · Turnos · Buffet, como un control segmentado.
     *
     * <p>Botones con aria-pressed y no pestañas: no cambian de panel, filtran la
     * misma tabla.
     */
    private Div partFilter() {
        Div group = new Div();
        group.addClassName("caja-filter");
        group.getElement().setAttribute("role", "group");
        group.getElement().setAttribute("aria-label", "Mostrar movimientos de");
        for (Part option : Part.values()) {
            Button button = new Button(option.label, event -> {
                part = option;
                showMovements();
            });
            // Sin LUMO_TERTIARY: esa variante fija el fondo transparente por encima
            // del tema, y el elegido tiene que poder levantarse con su fondo.
            button.addThemeVariants(ButtonVariant.LUMO_SMALL);
            button.addClassName("caja-filter__option");
            partButtons.put(option, button);
            group.add(button);
        }
        return group;
    }

    private void showMovements() {
        partButtons.forEach((option, button) ->
                button.getElement().setAttribute("aria-pressed", String.valueOf(option == part)));
        movementsGrid.setEmptyStateText(part.emptyText);
        movementsGrid.setItems(caja == null ? List.of()
                : caja.movements().stream().filter(part.includes).toList());
    }

    private VerticalLayout buffetSection() {
        buffetGrid.setAllRowsVisible(true);
        VerticalLayout block = section("Buffet del día", buffetGrid,
                "Lo vendido, no lo cobrado: una consumición se suma al total del turno o del pedido "
                        + "y puede cobrarse otro día. Lo cobrado del buffet está en el reparto de arriba.");
        // Los pedidos sin turno se cargan y se cobran en su propia pantalla; aca
        // solo se suman.
        RouterLink toBuffet = new RouterLink("Cargar y cobrar pedidos en Buffet", BuffetView.class);
        toBuffet.addClassNames(LumoUtility.FontSize.SMALL);
        block.addComponentAtIndex(2, toBuffet);
        return block;
    }

    private VerticalLayout section(String title, Grid<?> grid, String hint) {
        H3 heading = new H3(title);
        heading.addClassNames(LumoUtility.Margin.NONE);

        VerticalLayout block = new VerticalLayout(heading);
        if (hint != null) {
            Span hintSpan = new Span(hint);
            hintSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
            block.add(hintSpan);
        }
        block.add(grid);
        block.setPadding(false);
        block.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.Border.TOP,
                LumoUtility.BorderColor.CONTRAST_10, LumoUtility.Padding.Top.LARGE);
        return block;
    }

    private void buildMovementsColumns() {
        movementsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        movementsGrid.addColumn(movement -> HH_MM.format(movement.at().atZone(club.zoneId())))
                .setHeader("Hora").setAutoWidth(true).setPartNameGenerator(movement -> "tabular");
        // "A nombre de" y no "Jugador": un cobro de buffet no es de un jugador.
        movementsGrid.addColumn(Movement::customerName).setHeader("A nombre de")
                .setAutoWidth(true).setFlexGrow(1);
        movementsGrid.addColumn(this::turnoOf).setHeader("Turno").setAutoWidth(true);
        movementsGrid.addComponentColumn(this::partCell).setHeader("Parte").setAutoWidth(true);
        movementsGrid.addColumn(this::conceptOf).setHeader("Concepto").setAutoWidth(true);
        movementsGrid.addColumn(movement -> readable(movement.method())).setHeader("Método")
                .setAutoWidth(true);
        movementsGrid.addComponentColumn(this::amountCell).setHeader("Importe")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        // Sin autoWidth: con la columna "Parte" la tabla ya no entraba en una
        // laptop con el menu abierto, y el nombre de quien cargo es lo que mejor
        // tolera cortarse. Crece si hay lugar, y entero queda en el tooltip.
        movementsGrid.addColumn(this::registeredByOf).setHeader("Cargó").setWidth("9rem")
                .setFlexGrow(1).setTooltipGenerator(this::registeredByOf);
        movementsGrid.setEmptyStateText(Part.TODO.emptyText);
    }

    private void buildBuffetColumns() {
        buffetGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        buffetGrid.addColumn(BuffetLine::productName).setHeader("Producto").setAutoWidth(true)
                .setFlexGrow(1);
        buffetGrid.addColumn(BuffetLine::quantity).setHeader("Cantidad").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        buffetGrid.addColumn(line -> Money.format(line.total())).setHeader("Vendido")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(line -> "tabular");
        buffetGrid.setEmptyStateText("No se vendió nada del buffet este día.");
    }

    /**
     * De que parte es el movimiento. Un cobro de turno que tambien pago
     * consumiciones lleva las dos etiquetas con lo que le toca a cada una; si es
     * de una sola parte, el importe ya esta en su columna y no se repite.
     */
    private Component partCell(Movement movement) {
        Div cell = new Div();
        cell.addClassName("caja-parts");
        boolean turnos = movement.turnosAmount().signum() != 0;
        boolean buffet = movement.buffetAmount().signum() != 0;
        if (turnos && buffet) {
            cell.add(partTag("Turno " + Money.format(movement.turnosAmount()), "turnos"),
                    partTag("Buffet " + Money.format(movement.buffetAmount()), "buffet"));
        } else if (buffet || movement.buffetOrder()) {
            cell.add(partTag("Buffet", "buffet"));
        } else {
            cell.add(partTag("Turno", "turnos"));
        }
        return cell;
    }

    private Span partTag(String text, String part) {
        Span tag = new Span(text);
        tag.addClassNames("caja-part", "caja-part--" + part, "tabular");
        return tag;
    }

    /** La devolucion se lee distinto del cobro: es plata que salio, no que entro. */
    private Component amountCell(Movement movement) {
        Span span = new Span(Money.format(movement.amount()));
        span.addClassNames("tabular");
        if (movement.isRefund()) {
            span.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontWeight.SEMIBOLD);
        }
        return span;
    }

    /** Un pedido de buffet no tiene turno; que es del buffet ya lo dice la columna de al lado. */
    private String turnoOf(Movement movement) {
        if (movement.buffetOrder()) {
            return "—";
        }
        return "%s · %s".formatted(movement.courtName(),
                HH_MM.format(movement.bookingStartTime().atZone(club.zoneId())));
    }

    /**
     * El concepto no es un campo guardado: sale del signo y del metodo, que es
     * todo lo que distingue hoy un movimiento de otro. Cuando existan gastos y
     * retiros va a tener que serlo.
     */
    private String conceptOf(Movement movement) {
        if (movement.isRefund()) {
            return "Devolución";
        }
        return movement.method() == PaymentMethod.MERCADOPAGO ? "Pago online" : "Cobro en mostrador";
    }

    /** Un pago de MercadoPago no lo carga nadie: lo asienta el webhook. */
    private String registeredByOf(Movement movement) {
        if (movement.registeredByName() != null) {
            return movement.registeredByName();
        }
        return movement.method() == PaymentMethod.MERCADOPAGO ? "MercadoPago" : "—";
    }

    static String readable(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Efectivo";
            case TRANSFER -> "Transferencia";
            case MERCADOPAGO -> "MercadoPago";
        };
    }
}
