package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.service.BookingStatsService;
import ar.com.padelnec.service.BookingStatsService.CancellationStat;
import ar.com.padelnec.service.BookingStatsService.CustomerStat;
import ar.com.padelnec.service.BookingStatsService.HourlyStat;
import ar.com.padelnec.service.BookingStatsService.PaymentMethodStat;
import ar.com.padelnec.service.BookingStatsService.PeriodStats;
import ar.com.padelnec.service.BookingStatsService.Periodo;
import ar.com.padelnec.service.TenantService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.function.Supplier;

/**
 * Estadisticas del club: ingresos, ocupacion, horarios pico y top clientes.
 *
 * <p>El grafico de tendencia es {@link BarChart}, hecho a mano con divs: Vaadin
 * Charts es un addon comercial y una libreria de JavaScript para una sola serie
 * no se justifica. La tabla sigue abajo con todas las metricas del periodo, que
 * el grafico deliberadamente no repite.
 *
 * <p>Solo para el dueño: la facturacion es un dato sensible que el mostrador no
 * necesita para atender, mismo criterio que ya aisla {@link SettingsView}.
 */
@Route(value = "estadisticas", layout = MainLayout.class)
@PageTitle("Estadísticas | Panel del club")
@RolesAllowed({"OWNER", "SUPER_ADMIN"})
public class DashboardView extends VerticalLayout {

    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TenantService tenantService;
    private final BookingStatsService statsService;

    private final Select<Periodo> periodoSelect = new Select<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();

    private final Select<Metrica> metricaSelect = new Select<>();

    private final Div kpis = new Div();
    private final Span rangeHint = new Span();
    private final Span gridHint = new Span();
    private final BarChart chart = new BarChart();
    private final Grid<PeriodStats> periodsGrid = new Grid<>();
    private final Grid<HourlyStat> hoursGrid = new Grid<>();
    private final Grid<CustomerStat> customersGrid = new Grid<>();
    private final Grid<PaymentMethodStat> paymentMethodGrid = new Grid<>();

    private Tenant club;
    private List<PeriodStats> periods = List.of();

    public DashboardView(TenantService tenantService, BookingStatsService statsService) {
        this.tenantService = tenantService;
        this.statsService = statsService;

        setSizeFull();
        this.club = tenantService.requireCurrent();

        // Grid en vez de fila con wrap: con auto-fit las tarjetas se acomodan solas
        // segun el ancho disponible (tres por fila en desktop, menos en angosto) sin
        // necesitar un breakpoint a mano. Necesita ancho completo a proposito: el
        // VerticalLayout padre alinea sus hijos por contenido (flex-start), no por
        // ancho, asi que sin esto el grid nunca tiene contra que ancho repartir
        // columnas y colapsa a una sola.
        kpis.setWidth("100%");
        kpis.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(4, 1fr)")
                .set("gap", "var(--lumo-space-m)");

        buildPeriodsColumns();
        buildHoursColumns();
        buildCustomersColumns();
        buildPaymentMethodColumns();

        VerticalLayout toolbar = new VerticalLayout(dateRange(), quickRanges());
        toolbar.setPadding(false);
        toolbar.setSpacing(false);
        toolbar.addClassNames(LumoUtility.Gap.XSMALL);

        add(toolbar, kpis, periodsSection(), breakdowns());
        addClassNames(LumoUtility.Gap.XLARGE);

        refresh();
    }

    // -------------------------------------------------------------- toolbar

    private HorizontalLayout dateRange() {
        from.setLabel("Desde");
        from.setI18n(SpanishDates.datePicker());
        from.setValue(LocalDate.now(club.zoneId()).withDayOfMonth(1));
        from.addValueChangeListener(event -> refresh());

        to.setLabel("Hasta");
        to.setI18n(SpanishDates.datePicker());
        // Mañana, no hoy: asi el rango por defecto ya muestra los turnos reservados
        // para hoy mas tarde, en vez de necesitar que el dueño toque la fecha para
        // verlos.
        to.setValue(LocalDate.now(club.zoneId()).plusDays(1));
        to.addValueChangeListener(event -> refresh());

        HorizontalLayout row = new HorizontalLayout(from, to);
        row.setAlignItems(Alignment.END);
        row.addClassNames(LumoUtility.Gap.SMALL);
        return row;
    }

    /**
     * Atajos para los rangos que se piden todo el tiempo. Sin esto, cambiar de
     * "el semestre completo" a "hoy" son cuatro clicks en los date pickers en vez
     * de uno.
     */
    private HorizontalLayout quickRanges() {
        // El fin de cada rango es el limite del periodo calendario, no "hoy": asi
        // "Este mes" o "Esta semana" tambien muestran lo que ya esta reservado
        // para lo que falta del periodo, no solo lo que ya paso.
        HorizontalLayout row = new HorizontalLayout(
                quickRangeButton("Hoy",
                        () -> LocalDate.now(club.zoneId()),
                        () -> LocalDate.now(club.zoneId())),
                quickRangeButton("Esta semana",
                        () -> LocalDate.now(club.zoneId()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        () -> LocalDate.now(club.zoneId()).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))),
                quickRangeButton("Este mes",
                        () -> LocalDate.now(club.zoneId()).withDayOfMonth(1),
                        () -> LocalDate.now(club.zoneId()).with(TemporalAdjusters.lastDayOfMonth())),
                quickRangeButton("Próximos 7 días",
                        () -> LocalDate.now(club.zoneId()),
                        () -> LocalDate.now(club.zoneId()).plusDays(7)),
                quickRangeButton("Este año",
                        () -> LocalDate.now(club.zoneId()).withDayOfYear(1),
                        () -> LocalDate.now(club.zoneId()).with(TemporalAdjusters.lastDayOfYear())));
        row.addClassNames(LumoUtility.Gap.XSMALL, LumoUtility.FlexWrap.WRAP);
        row.getStyle().set("margin-top", "var(--lumo-space-xs)");
        return row;
    }

    /** Chip de filtro: distinto de un boton de accion, para no competir con "Registrar cobro" y compania. */
    private Button quickRangeButton(String label, Supplier<LocalDate> desde, Supplier<LocalDate> hasta) {
        Button button = new Button(label, event -> {
            from.setValue(desde.get());
            to.setValue(hasta.get());
        });
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        button.addClassNames(LumoUtility.BorderRadius.FULL, LumoUtility.Border.ALL,
                LumoUtility.BorderColor.CONTRAST_20, LumoUtility.Background.CONTRAST_5,
                LumoUtility.Padding.Horizontal.SMALL, LumoUtility.FontWeight.MEDIUM);
        return button;
    }

    /** Lo que dibuja el grafico. Una sola por vez: dos escalas necesitarian dos ejes. */
    private enum Metrica {
        FACTURADO("Facturado"), JUGADOS("Turnos jugados"), OCUPACION("Ocupación");

        private final String label;

        Metrica(String label) {
            this.label = label;
        }
    }

    private static String periodoLabel(Periodo periodo) {
        return switch (periodo) {
            case DIA -> "Diario";
            case SEMANA -> "Semana";
            case MES -> "Mes";
            case ANIO -> "Año";
        };
    }

    // --------------------------------------------------------------- datos

    private void refresh() {
        club = tenantService.requireCurrent();
        LocalDate desde = from.getValue();
        LocalDate hasta = to.getValue();
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            return;
        }

        // Las tarjetas resumen todo el rango de una: no dependen de en que periodo
        // se agrupa la tabla de abajo, asi que se calculan aparte.
        refreshKpis(statsService.summary(club, desde, hasta),
                statsService.cancellationsByReason(club, desde, hasta));

        rangeHint.setText("Tomando datos desde " + DAY_MONTH_YEAR.format(desde)
                + " hasta " + DAY_MONTH_YEAR.format(hasta));

        periods = statsService.statsFor(club, periodoSelect.getValue(), desde, hasta);
        periodsGrid.setItems(periods);
        refreshChart();

        hoursGrid.setItems(statsService.topHours(club, desde, hasta, 8));
        customersGrid.setItems(statsService.topCustomers(club, desde, hasta, 8));
        paymentMethodGrid.setItems(statsService.paymentsByMethod(club, desde, hasta));
    }

    private void refreshKpis(PeriodStats summary, List<CancellationStat> cancellations) {
        kpis.removeAll();
        kpis.add(
                new KpiCard("Jugados", String.valueOf(summary.jugados())),
                new KpiCard("Reservados para jugar", String.valueOf(summary.reservados())),
                cancelacionesCard(summary.cancelados(), cancellations),
                new KpiCard("Ausentes (No se presento)", String.valueOf(summary.noShows())),
                // Las dos tarjetas de plata dicen con que criterio cuentan, porque
                // no tienen por que dar lo mismo y la diferencia no es un error:
                // una sena cobrada hoy por un turno del mes que viene esta en el
                // cobrado de hoy y en el facturado del mes que viene.
                new KpiCard("Facturado", money(summary.facturado()))
                        .footnote("Por fecha del turno"),
                new KpiCard("Cobrado", money(summary.cobrado()))
                        .footnote("Por fecha del cobro"),
                occupancyCard(summary));
    }

    /**
     * El total de cancelados con su desglose por motivo debajo: el numero solo
     * ("3 Cancelados") no dice si conviene mirar el checkout de MercadoPago o
     * llamar a un jugador puntual.
     */
    private KpiCard cancelacionesCard(int total, List<CancellationStat> porMotivo) {
        KpiCard card = new KpiCard("Cancelados", String.valueOf(total));
        if (porMotivo.isEmpty()) {
            return card;
        }

        VerticalLayout desglose = new VerticalLayout();
        desglose.setPadding(false);
        desglose.setSpacing(false);
        for (CancellationStat stat : porMotivo) {
            Span line = new Span(readable(stat.reason()) + ": " + stat.count());
            line.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
            desglose.add(line);
        }
        return card.detail(desglose);
    }

    /** "10/125" (turnos ocupados sobre turnos posibles) y el porcentaje debajo. */
    private KpiCard occupancyCard(PeriodStats summary) {
        return new KpiCard("Turnos", summary.slotsOcupados() + "/" + summary.slotsPosibles())
                .emphasis(summary.ocupacionPromedio() + "% de ocupación");
    }

    // ------------------------------------------------------------ tablas

    /** El combo de agrupamiento va pegado a esta tabla: es lo unico que controla. */
    private VerticalLayout periodsSection() {
        periodoSelect.setLabel("Agrupar por");
        periodoSelect.setItems(Periodo.values());
        periodoSelect.setItemLabelGenerator(DashboardView::periodoLabel);
        periodoSelect.setValue(Periodo.MES);
        periodoSelect.addValueChangeListener(event -> refresh());

        metricaSelect.setLabel("Ver en el gráfico");
        metricaSelect.setItems(Metrica.values());
        metricaSelect.setItemLabelGenerator(metrica -> metrica.label);
        metricaSelect.setValue(Metrica.FACTURADO);
        // Solo cambia lo que se dibuja: las filas ya estan consultadas, no hace
        // falta volver a la base para pintar otra metrica.
        metricaSelect.addValueChangeListener(event -> refreshChart());

        // El rango vive arriba de todo, en los date pickers, y para cuando el dueño
        // llego hasta el grafico ya no lo tiene a la vista: el texto se lo recuerda
        // sin que tenga que volver a subir.
        rangeHint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        rangeHint.getStyle().set("padding-bottom", "var(--lumo-space-s)");

        HorizontalLayout header = new HorizontalLayout(periodoSelect, metricaSelect, rangeHint);
        // Alineada por abajo: los dos combos y el texto de ayuda comparten la
        // linea de base, aunque los combos tengan su rotulo arriba.
        header.setAlignItems(Alignment.END);
        header.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.FlexWrap.WRAP);

        // El grafico dibuja una sola metrica; la tabla es donde estan las demas.
        // Sin este renglon la tabla aparece sin presentacion, sobre todo ahora que
        // la seccion no tiene titulo.
        gridHint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        periodsGrid.setAllRowsVisible(true);

        VerticalLayout section = new VerticalLayout(header, chart, gridHint, periodsGrid);
        section.setPadding(false);
        section.addClassNames(LumoUtility.Gap.SMALL);
        separate(section);
        return section;
    }

    /** Las columnas del grafico salen de las mismas filas que la tabla, sin otra consulta. */
    private void refreshChart() {
        chart.setBars(periods.stream().map(this::bar).toList());
        // El grafico se esconde solo cuando no hay con que dibujarlo, y ahi el
        // renglon no puede prometer el detalle de un grafico que no esta.
        gridHint.setText(chart.isVisible()
                ? "Detalle del gráfico, período por período, con todas las métricas"
                : "Detalle período por período, con todas las métricas");
    }

    private BarChart.Bar bar(PeriodStats stats) {
        Metrica metrica = metricaSelect.getValue();
        double value = switch (metrica) {
            case FACTURADO -> stats.facturado().doubleValue();
            case JUGADOS -> stats.jugados();
            case OCUPACION -> stats.ocupacionPromedio().doubleValue();
        };
        String formatted = switch (metrica) {
            case FACTURADO -> money(stats.facturado());
            case JUGADOS -> String.valueOf(stats.jugados());
            case OCUPACION -> stats.ocupacionPromedio() + "%";
        };
        return new BarChart.Bar(shortLabel(stats.label()), stats.label() + ": " + formatted,
                value, formatted);
    }

    /**
     * El rotulo del eje, que no es el de la tabla: "Semana del 3/9" abajo de una
     * columna angosta no entra. El nombre completo queda en el tooltip.
     */
    private String shortLabel(String label) {
        return switch (periodoSelect.getValue()) {
            // "Lunes 7/09" -> "7/09": el nombre del dia ya se ve en el tooltip.
            case DIA -> label.contains(" ") ? label.substring(label.indexOf(' ') + 1) : label;
            case SEMANA -> label.replace("Semana del ", "");
            case MES -> label.length() > 3 ? label.substring(0, 3) : label;
            case ANIO -> label;
        };
    }

    private VerticalLayout section(String title, Grid<?> grid) {
        H3 heading = new H3(title);
        heading.addClassNames(LumoUtility.Margin.NONE);
        grid.setAllRowsVisible(true);

        VerticalLayout section = new VerticalLayout(heading, grid);
        section.setPadding(false);
        section.addClassNames(LumoUtility.Gap.SMALL);
        separate(section);
        return section;
    }

    private HorizontalLayout breakdowns() {
        HorizontalLayout row = new HorizontalLayout(
                section("Horarios pico", hoursGrid), section("Top clientes", customersGrid),
                section("Método de cobro", paymentMethodGrid));
        row.setWidthFull();
        row.addClassNames(LumoUtility.Gap.LARGE, LumoUtility.FlexWrap.WRAP);
        // La linea la lleva cada desglose y no esta fila: apilados en pantalla
        // angosta, una sola linea arriba de todo dejaba los otros dos pegados.
        for (var child : row.getChildren().toList()) {
            row.setFlexGrow(1, child);
        }
        return row;
    }

    /**
     * Marca el arranque de un bloque con una linea y aire arriba.
     *
     * <p>Las tres partes del panel (resumen, periodos y desgloses) se leian como
     * una lista larga: sin un corte visible, el salto de la tabla de periodos a
     * las de horarios y clientes no se nota y parecen la misma cosa.
     */
    private void separate(HasStyle block) {
        block.addClassNames(LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.Padding.Top.LARGE);
    }

    private void buildPeriodsColumns() {
        periodsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        periodsGrid.addColumn(PeriodStats::label).setHeader("Período").setAutoWidth(true);
        periodsGrid.addColumn(stats -> stats.jugados()).setHeader("Jugados").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        periodsGrid.addColumn(stats -> stats.reservados()).setHeader("Reservados").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        periodsGrid.addColumn(stats -> money(stats.facturado())).setHeader("Facturado").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END).setPartNameGenerator(stats -> "tabular");
        periodsGrid.addColumn(stats -> money(stats.cobrado())).setHeader("Cobrado").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END).setPartNameGenerator(stats -> "tabular");
        periodsGrid.addColumn(stats -> stats.cancelados()).setHeader("Cancelados").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        periodsGrid.addColumn(stats -> stats.noShows()).setHeader("Ausentes").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        periodsGrid.addColumn(stats -> stats.ocupacionPromedio() + "%").setHeader("Ocupación")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        periodsGrid.setEmptyStateText("No hay turnos en este rango.");
    }

    private void buildHoursColumns() {
        hoursGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        hoursGrid.addColumn(stat -> stat.hour().toString()).setHeader("Horario").setAutoWidth(true)
                .setPartNameGenerator(stat -> "tabular");
        hoursGrid.addColumn(HourlyStat::turnos).setHeader("Turnos").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        hoursGrid.addColumn(stat -> money(stat.facturado())).setHeader("Facturado").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        hoursGrid.setEmptyStateText("No hay turnos en este rango.");
    }

    private void buildCustomersColumns() {
        customersGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        customersGrid.addColumn(CustomerStat::name).setHeader("Jugador").setAutoWidth(true).setFlexGrow(1);
        customersGrid.addColumn(CustomerStat::turnos).setHeader("Turnos").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        customersGrid.addColumn(stat -> money(stat.facturado())).setHeader("Facturado").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        customersGrid.setEmptyStateText("No hay turnos en este rango.");
    }

    private void buildPaymentMethodColumns() {
        paymentMethodGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        paymentMethodGrid.addColumn(stat -> readable(stat.method())).setHeader("Método").setAutoWidth(true)
                .setFlexGrow(1);
        paymentMethodGrid.addColumn(stat -> money(stat.total())).setHeader("Cobrado").setAutoWidth(true)
                .setTextAlign(ColumnTextAlign.END);
        paymentMethodGrid.setEmptyStateText("No hay cobros en este rango.");
    }

    private String readable(CancellationReason reason) {
        return switch (reason) {
            // Motivo nulo: la reserva no paso por Booking.markCancelled (una
            // migracion, una correccion manual en la base). No deberia pasar por
            // el flujo normal, pero BookingStatsService.cancellationsByReason lo
            // agrupa aparte en vez de filtrarlo, asi que hay que poder mostrarlo.
            case null -> "Sin motivo";
            case CUSTOMER -> "Cancelado por la web";
            case CLUB -> "Dado de baja por el club";
            case PAYMENT_TIMEOUT -> "No pagó a tiempo";
            case CONFIRMATION_TIMEOUT -> "No confirmó por WhatsApp";
        };
    }

    private String readable(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Efectivo";
            case TRANSFER -> "Transferencia";
            case MERCADOPAGO -> "MercadoPago";
        };
    }

    // ---------------------------------------------------------- formateo

    private String money(BigDecimal amount) {
        return Money.format(amount);
    }
}
