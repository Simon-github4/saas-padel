package ar.com.padelnec.ui;

import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.BuffetOrderService;
import ar.com.padelnec.service.BuffetOrderService.OrderWithItems;
import ar.com.padelnec.service.CashRegisterService;
import ar.com.padelnec.service.CashRegisterService.BuffetLine;
import ar.com.padelnec.service.CashRegisterService.DayCash;
import ar.com.padelnec.service.CashRegisterService.MethodTotal;
import ar.com.padelnec.service.CashRegisterService.Movement;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * La caja de un dia: los movimientos de plata y a cuanto tienen que sumar.
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

    private final TenantService tenantService;
    private final CashRegisterService cashRegisterService;
    private final BuffetOrderService buffetOrderService;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final ProductRepository productRepository;
    private final transient AuthenticationContext authenticationContext;

    private final DatePicker datePicker = new DatePicker();
    private final H2 dateHeading = new H2();
    private final Span summary = new Span();
    private final Div kpis = new Div();
    private final Grid<Movement> movementsGrid = new Grid<>();
    private final Grid<OrderWithItems> ordersGrid = new Grid<>();
    private final Grid<BuffetLine> buffetGrid = new Grid<>();

    private Tenant club;

    public CajaView(TenantService tenantService, CashRegisterService cashRegisterService,
                    BuffetOrderService buffetOrderService, ProductService productService,
                    PaymentService paymentService, ProductRepository productRepository,
                    AuthenticationContext authenticationContext) {
        this.tenantService = tenantService;
        this.cashRegisterService = cashRegisterService;
        this.buffetOrderService = buffetOrderService;
        this.productService = productService;
        this.paymentService = paymentService;
        this.productRepository = productRepository;
        this.authenticationContext = authenticationContext;

        setSizeFull();
        this.club = tenantService.requireCurrent();

        // auto-fit y no un numero fijo de columnas: son cinco tarjetas, y en un
        // ancho donde no entran las cinco es preferible que se acomoden solas a
        // que la ultima quede sola en su fila.
        kpis.setWidth("100%");
        kpis.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(11rem, 1fr))")
                .set("gap", "var(--lumo-space-m)");

        buildMovementsColumns();
        buildOrdersColumns();
        buildBuffetColumns();

        // Los pedidos van antes que los movimientos: es lo unico de esta pantalla
        // que se usa durante el dia, no al cierre.
        add(header(), kpis, ordersSection(), movementsSection(), buffetSection());
        addClassNames(LumoUtility.Gap.LARGE);

        datePicker.setValue(LocalDate.now(club.zoneId()));
        refresh();
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
        DayCash caja = cashRegisterService.of(club, date);

        dateHeading.setText(DATE_HEADING.format(date));
        summary.setText(caja.movements().isEmpty()
                ? "Sin movimientos de caja"
                : "%d %s · %s".formatted(caja.movements().size(),
                        caja.movements().size() == 1 ? "movimiento" : "movimientos",
                        Money.format(caja.total())));

        refreshKpis(caja);
        movementsGrid.setItems(caja.movements());
        ordersGrid.setItems(buffetOrderService.ordersOn(club, date));
        buffetGrid.setItems(caja.buffet());
    }

    private void refreshKpis(DayCash caja) {
        kpis.removeAll();
        // El efectivo primero y con su aclaracion: de las cinco tarjetas es la
        // unica que se contrasta contra algo fisico.
        kpis.add(new KpiCard("Efectivo", Money.format(caja.cash()))
                .footnote("Lo que tiene que haber en el cajón"));
        for (MethodTotal entry : caja.byMethod()) {
            if (entry.method() == PaymentMethod.CASH) {
                continue;
            }
            KpiCard card = new KpiCard(readable(entry.method()), Money.format(entry.total()));
            if (entry.method() == PaymentMethod.MERCADOPAGO) {
                card.footnote("No pasa por el cajón");
            }
            kpis.add(card);
        }
        kpis.add(new KpiCard("Total cobrado", Money.format(caja.total()))
                .footnote("Por fecha del cobro"));
        kpis.add(pendingCard(caja));
    }

    /**
     * Lo que falta cobrar de los turnos y los pedidos de buffet del dia.
     *
     * <p>Va en rojo solo si hay algo: en cero es una buena noticia, no una
     * advertencia, y pintarlo igual entrena a ignorar el color.
     */
    private KpiCard pendingCard(DayCash caja) {
        KpiCard card = new KpiCard("Pendiente de cobro", Money.format(caja.pending()));
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
        return section("Movimientos del día", movementsGrid, null);
    }

    /**
     * Pedidos de buffet sin turno: lo que consume alguien que no está jugando.
     *
     * <p>Se abren acá con el nombre y los productos se les suman después, tocando
     * el pedido en la lista: se cobran igual que un turno.
     */
    private VerticalLayout ordersSection() {
        ordersGrid.setAllRowsVisible(true);
        VerticalLayout block = section("Pedidos del buffet", ordersGrid,
                "Para quien consume sin tener turno, por ejemplo alguien que vino a mirar. "
                        + "Tocá un pedido para sumarle productos o cobrarlo.");

        Button newOrder = new Button("Nuevo pedido", VaadinIcon.PLUS.create(), event -> openNewOrder());
        newOrder.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        // El boton va junto al titulo de la seccion, no suelto arriba de la grilla.
        Component heading = block.getComponentAt(0);
        HorizontalLayout titleRow = new HorizontalLayout(heading, newOrder);
        titleRow.setWidthFull();
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        titleRow.setPadding(false);
        block.addComponentAsFirst(titleRow);
        return block;
    }

    private VerticalLayout buffetSection() {
        buffetGrid.setAllRowsVisible(true);
        return section("Buffet del día", buffetGrid,
                "Lo vendido en turnos y pedidos, no lo cobrado: una consumición se suma al total "
                        + "del turno o del pedido y puede cobrarse otro día.");
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
        movementsGrid.addColumn(this::conceptOf).setHeader("Concepto").setAutoWidth(true);
        movementsGrid.addColumn(movement -> readable(movement.method())).setHeader("Método")
                .setAutoWidth(true);
        movementsGrid.addComponentColumn(this::amountCell).setHeader("Importe")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        movementsGrid.addColumn(this::registeredByOf).setHeader("Cargó").setAutoWidth(true);
        movementsGrid.setEmptyStateText("No hubo movimientos de caja este día.");
    }

    private void buildOrdersColumns() {
        ordersGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        ordersGrid.addColumn(item -> HH_MM.format(item.order().getCreatedAt().atZone(club.zoneId())))
                .setHeader("Hora").setAutoWidth(true).setPartNameGenerator(item -> "tabular");
        ordersGrid.addColumn(item -> item.order().getCustomerName()).setHeader("A nombre de")
                .setAutoWidth(true);
        ordersGrid.addColumn(this::itemsSummary).setHeader("Detalle").setFlexGrow(1);
        ordersGrid.addColumn(item -> Money.format(item.order().getTotalPrice())).setHeader("Total")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(item -> "tabular");
        ordersGrid.addComponentColumn(this::orderBalance).setHeader("Saldo")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        ordersGrid.setEmptyStateText("No hubo pedidos de buffet sin turno este día.");
        ordersGrid.addItemClickListener(event -> openOrder(event.getItem().order()));
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

    /** "2x Agua · 1x Café", o el aviso de que todavía no se le cargó nada. */
    private String itemsSummary(OrderWithItems item) {
        if (item.items().isEmpty()) {
            return "Sin productos todavía";
        }
        return item.items().stream()
                .map(sale -> "%dx %s".formatted(sale.getQuantity(), sale.getProductName()))
                .collect(Collectors.joining(" · "));
    }

    /** Lo que debe en rojo; pagado o a favor, tranquilo. */
    private Component orderBalance(OrderWithItems item) {
        BuffetOrder order = item.order();
        if (order.balanceDue().signum() > 0) {
            Span due = new Span(Money.format(order.balanceDue()));
            due.addClassNames("tabular", LumoUtility.TextColor.ERROR, LumoUtility.FontWeight.SEMIBOLD);
            return due;
        }
        if (order.creditBalance().signum() > 0) {
            Span credit = new Span(Money.format(order.creditBalance()) + " a favor");
            credit.addClassNames("tabular", LumoUtility.TextColor.SUCCESS);
            return credit;
        }
        Span paid = new Span(order.getTotalPrice().signum() > 0 ? "Pagado" : "—");
        paid.addClassNames(LumoUtility.TextColor.SECONDARY);
        return paid;
    }

    // ------------------------------------------------------------- pedidos

    /** Pide el nombre, abre el pedido y va directo a cargarle productos. */
    private void openNewOrder() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nuevo pedido de buffet");

        TextField name = new TextField("A nombre de");
        name.setWidthFull();
        name.setMaxLength(120);
        name.setPlaceholder("Ej. Juan (mira el partido de la cancha 2)");
        dialog.add(name);

        Button open = new Button("Abrir pedido", event -> {
            try {
                BuffetOrder order = buffetOrderService.open(name.getValue(), currentUserId());
                dialog.close();
                // El pedido es de hoy: si se estaba mirando otro dia, se vuelve a
                // hoy para que aparezca en la lista.
                datePicker.setValue(LocalDate.now(club.zoneId()));
                refresh();
                openOrder(order);
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        open.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        open.addClickShortcut(Key.ENTER);
        Button cancel = new Button("Cancelar", event -> dialog.close());
        dialog.getFooter().add(cancel, open);

        dialog.open();
        name.focus();
    }

    private void openOrder(BuffetOrder order) {
        new BuffetOrderDialog(buffetOrderService.require(order.getId()), club, buffetOrderService,
                productService, paymentService, productRepository, currentUserId(), this::refresh).open();
    }

    private UUID currentUserId() {
        return authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class)
                .map(ClubUserPrincipal::userId)
                .orElse(null);
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

    private String turnoOf(Movement movement) {
        if (movement.buffet()) {
            return "Buffet";
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

    private String readable(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Efectivo";
            case TRANSFER -> "Transferencia";
            case MERCADOPAGO -> "MercadoPago";
        };
    }
}
