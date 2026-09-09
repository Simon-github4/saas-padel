package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.service.CashRegisterService;
import ar.com.padelnec.service.CashRegisterService.DayCash;
import ar.com.padelnec.service.CashRegisterService.KioskLine;
import ar.com.padelnec.service.CashRegisterService.MethodTotal;
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
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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

    private final DatePicker datePicker = new DatePicker();
    private final H2 dateHeading = new H2();
    private final Span summary = new Span();
    private final Div kpis = new Div();
    private final Grid<Movement> movementsGrid = new Grid<>();
    private final Grid<KioskLine> kioskGrid = new Grid<>();

    private Tenant club;

    public CajaView(TenantService tenantService, CashRegisterService cashRegisterService) {
        this.tenantService = tenantService;
        this.cashRegisterService = cashRegisterService;

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
        buildKioskColumns();

        add(header(), kpis, movementsSection(), kioskSection());
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
        kioskGrid.setItems(caja.kiosk());
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
     * Lo que falta cobrar de los turnos del dia.
     *
     * <p>Va en rojo solo si hay algo: en cero es una buena noticia, no una
     * advertencia, y pintarlo igual entrena a ignorar el color.
     */
    private KpiCard pendingCard(DayCash caja) {
        KpiCard card = new KpiCard("Pendiente de cobro", Money.format(caja.pending()));
        if (caja.pending().signum() > 0) {
            card.alert().footnote(caja.pendingBookings() == 1
                    ? "1 turno de hoy con saldo"
                    : caja.pendingBookings() + " turnos de hoy con saldo");
        } else {
            card.footnote("Los turnos de hoy están al día");
        }
        return card;
    }

    // --------------------------------------------------------------- tablas

    private VerticalLayout movementsSection() {
        movementsGrid.setAllRowsVisible(true);
        return section("Movimientos del día", movementsGrid, null);
    }

    private VerticalLayout kioskSection() {
        kioskGrid.setAllRowsVisible(true);
        return section("Kiosco del día", kioskGrid,
                "Lo vendido, no lo cobrado: una consumición se suma al total del turno "
                        + "y puede cobrarse otro día.");
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
        movementsGrid.addColumn(Movement::customerName).setHeader("Jugador")
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

    private void buildKioskColumns() {
        kioskGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        kioskGrid.addColumn(KioskLine::productName).setHeader("Producto").setAutoWidth(true)
                .setFlexGrow(1);
        kioskGrid.addColumn(KioskLine::quantity).setHeader("Cantidad").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        kioskGrid.addColumn(line -> Money.format(line.total())).setHeader("Vendido")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(line -> "tabular");
        kioskGrid.setEmptyStateText("No se vendió nada del kiosco este día.");
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
