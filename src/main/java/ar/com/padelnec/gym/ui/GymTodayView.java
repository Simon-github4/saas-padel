package ar.com.padelnec.gym.ui;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymOverviewService.DayCheckin;
import ar.com.padelnec.gym.service.GymOverviewService.DayPayment;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Quien entro al gimnasio ese dia, y las cuotas que se cobraron en el mostrador. */
@Route(value = "gimnasio/hoy", layout = ar.com.padelnec.ui.MainLayout.class)
@PageTitle("Ingresos de hoy | Panel del club")
@PermitAll
public class GymTodayView extends VerticalLayout implements BeforeEnterObserver {

    private final GymModule gymModule;
    private final GymOverviewService overviewService;

    private final DatePicker day = new DatePicker();
    private final Span checkinCount = new Span();
    private final Grid<DayCheckin> checkins = new Grid<>();
    private final Span paymentTotals = new Span();
    private final Grid<DayPayment> payments = new Grid<>();
    private final DateTimeFormatter hour;

    public GymTodayView(GymModule gymModule, GymOverviewService overviewService) {
        this.gymModule = gymModule;
        this.overviewService = overviewService;
        ZoneId zone = overviewService.zone();
        this.hour = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);

        setSizeFull();
        addClassNames(LumoUtility.Gap.MEDIUM);

        day.setValue(overviewService.today());
        day.setAriaLabel("Día");
        day.addValueChangeListener(event -> refresh());

        checkinCount.getElement().getThemeList().add("badge contrast");
        HorizontalLayout toolbar = new HorizontalLayout(day, checkinCount);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setPadding(false);

        buildCheckinColumns();
        buildPaymentColumns();

        H3 paymentsTitle = new H3("Cobros del día");
        paymentsTitle.addClassNames(LumoUtility.Margin.Bottom.NONE, LumoUtility.Margin.Top.MEDIUM);
        HorizontalLayout paymentsHeader = new HorizontalLayout(paymentsTitle, paymentTotals);
        paymentsHeader.setAlignItems(Alignment.BASELINE);
        paymentsHeader.setPadding(false);

        add(toolbar, checkins, paymentsHeader, payments);
        setFlexGrow(1, checkins);
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        GymViewSupport.requireEnabled(event, gymModule);
    }

    private void buildCheckinColumns() {
        checkins.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        checkins.setSelectionMode(Grid.SelectionMode.NONE);
        checkins.addColumn(row -> hour.format(row.at())).setHeader("Hora").setAutoWidth(true).setFlexGrow(0);
        checkins.addColumn(DayCheckin::memberName).setHeader("Socio").setFlexGrow(3);
        checkins.addColumn(DayCheckin::dni).setHeader("DNI").setAutoWidth(true).setFlexGrow(0);
        checkins.addColumn(DayCheckin::sedeName).setHeader("Sede").setFlexGrow(2);
        checkins.addComponentColumn(this::marks).setHeader("").setAutoWidth(true).setFlexGrow(0);
        checkins.setEmptyStateText("Nadie entró este día.");
        checkins.setSizeFull();
    }

    private Component marks(DayCheckin row) {
        HorizontalLayout marks = new HorizontalLayout();
        marks.setPadding(false);
        marks.addClassNames(LumoUtility.Gap.XSMALL);
        if (row.override()) {
            marks.add(GymViewSupport.badge("Excepción", "error small"));
        }
        if (row.manual()) {
            marks.add(GymViewSupport.badge("Cargado a mano", "contrast small"));
        }
        return marks;
    }

    private void buildPaymentColumns() {
        payments.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        payments.setSelectionMode(Grid.SelectionMode.NONE);
        payments.addColumn(this::loadedAt).setHeader("Hora").setAutoWidth(true).setFlexGrow(0);
        payments.addColumn(DayPayment::memberName).setHeader("Socio").setFlexGrow(3);
        payments.addColumn(row -> GymViewSupport.money(row.price())).setHeader("Monto").setAutoWidth(true)
                .setFlexGrow(0);
        payments.addColumn(row -> row.method().label()).setHeader("Método").setAutoWidth(true).setFlexGrow(0);
        payments.addColumn(DayPayment::collectedAt).setHeader("Cobrado en").setFlexGrow(2);
        payments.setEmptyStateText("No se cobró ninguna cuota este día.");
        payments.setHeight("14em");
    }

    /** La hora de carga; si se cargo otro dia que el del cobro, tambien la fecha. */
    private String loadedAt(DayPayment row) {
        LocalDate loadedOn = row.at().atZone(hour.getZone()).toLocalDate();
        return loadedOn.equals(row.paidOn()) ? hour.format(row.at())
                : "cargado " + GymViewSupport.SHORT_DAY.format(loadedOn) + " " + hour.format(row.at());
    }

    private void refresh() {
        LocalDate selected = day.getValue() == null ? overviewService.today() : day.getValue();

        List<DayCheckin> entries = overviewService.checkinsOf(selected);
        checkins.setItems(entries);
        checkinCount.setText(entries.size() == 1 ? "1 ingreso" : entries.size() + " ingresos");

        List<DayPayment> paid = overviewService.paymentsOf(selected);
        payments.setItems(paid);
        Map<PayMethod, BigDecimal> byMethod = new EnumMap<>(PayMethod.class);
        paid.forEach(row -> byMethod.merge(row.method(), row.price(), BigDecimal::add));
        paymentTotals.setText(paid.isEmpty() ? "" : byMethod.entrySet().stream()
                .map(entry -> entry.getKey().label() + " " + GymViewSupport.money(entry.getValue()))
                .collect(Collectors.joining(" · ")));
        paymentTotals.addClassNames(LumoUtility.TextColor.SECONDARY);
    }
}
