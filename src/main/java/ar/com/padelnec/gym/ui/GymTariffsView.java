package ar.com.padelnec.gym.ui;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.EntryLimit;
import ar.com.padelnec.gym.domain.PlanLength;
import ar.com.padelnec.gym.service.GymAllowance;
import ar.com.padelnec.gym.service.GymSettingsService;
import ar.com.padelnec.gym.service.GymTariffService;
import ar.com.padelnec.gym.service.GymTariffService.TariffRow;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;

/**
 * Las cuotas segun los dias por semana: la mensual y la del pase de 15 dias, y como se cuenta
 * el tope de esos dias (por semana o por mes).
 *
 * <p>Es lo que el mostrador usa para auto-completar el monto al cobrar: fijar aca
 * la tarifa "de 3 dias" hace que la cuota del trimestre salga sola, y de todos
 * modos cada venta se puede ajustar a mano. Un plan puede tener solo una de las dos.
 * Solo el dueno.
 */
@Route(value = "gimnasio/cuotas", layout = ar.com.padelnec.ui.MainLayout.class)
@PageTitle("Cuotas del gimnasio | Panel del club")
@RolesAllowed({"OWNER", "SUPER_ADMIN"})
public class GymTariffsView extends VerticalLayout implements BeforeEnterObserver {

    private final GymModule gymModule;
    private final GymTariffService tariffService;
    private final GymSettingsService settingsService;

    private final Grid<TariffRow> grid = new Grid<>();
    private Grid.Column<TariffRow> entriesColumn;

    public GymTariffsView(GymModule gymModule, GymTariffService tariffService, GymSettingsService settingsService) {
        this.gymModule = gymModule;
        this.tariffService = tariffService;
        this.settingsService = settingsService;

        setSizeFull();
        addClassNames(LumoUtility.Gap.MEDIUM);

        Button newTariff = new Button("Fijar tarifa", VaadinIcon.PLUS.create(), event -> openEdit(null));
        newTariff.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Paragraph help = new Paragraph("La cuota mensual y la del pase de 15 días de cada plan. Al cobrar en «Socios», "
                + "el mostrador toma de acá el monto del plan del socio, y lo puede ajustar a mano si hace falta.");
        help.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE);

        // El boton a la izquierda y el texto a su derecha.
        HorizontalLayout toolbar = new HorizontalLayout(newTariff, help);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.START);
        toolbar.setPadding(false);

        grid.addColumn(this::daysLabel).setHeader("Plan").setFlexGrow(2);
        entriesColumn = grid.addColumn(GymTariffsView::entriesLabel).setHeader("Ingresos").setAutoWidth(true)
                .setFlexGrow(0);
        grid.addComponentColumn(row -> priceCell(row.monthPrice()))
                .setHeader("Cuota mensual").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(row -> priceCell(row.fifteenDaysPrice()))
                .setHeader("Cuota de 15 días").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("Todavía no hay tarifas. Tocá «Fijar tarifa» para fijar la primera.");
        grid.setSizeFull();

        add(toolbar, limitSetting(), grid);
        setFlexGrow(1, grid);
        refresh();
    }

    /**
     * Como se cuenta el tope de los dias por semana, desde la proxima cuota de cada socio: la que
     * corre hoy sigue con la regla con la que se pago hasta que vence.
     */
    private Component limitSetting() {
        RadioButtonGroup<EntryLimit> entryLimit = new RadioButtonGroup<>("Tope de ingresos");
        entryLimit.setItems(EntryLimit.values());
        entryLimit.setItemLabelGenerator(option -> option.label() + (option == EntryLimit.WEEK
                ? " (de lunes a domingo)"
                : " (en el período de la cuota de cada socio)"));
        entryLimit.setValue(gymModule.entryLimit());
        entryLimit.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                settingsService.setEntryLimit(event.getValue());
                refresh();
                GymViewSupport.ok("Desde la próxima cuota de cada socio, el tope se cuenta "
                        + (event.getValue() == EntryLimit.MONTH ? "por mes." : "por semana.")
                        + " La cuota que tiene hoy sigue como estaba hasta que vence.");
            }
        });
        Span help = new Span("Por mes, cada socio reparte sus ingresos como quiera dentro de su período (del 15 al "
                + "14 si arrancó el 15): son los días por semana proporcionales a lo que dura ese período, "
                + "redondeados. Con 2 días por semana, 9 en un mes de 30 o 31 días y 8 en uno de 28. "
                + "El cambio le llega a cada socio con su próxima cuota.");
        help.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        // La cuenta de cada plan, a mano para decidir o para explicarsela a un socio.
        Button showTable = new Button("Ver ingresos por plan y mes", VaadinIcon.TABLE.create());
        showTable.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        Popover popover = new Popover(monthlyEntriesTable());
        popover.setTarget(showTable);
        popover.setOpenOnClick(true);
        popover.setOpenOnHover(true);
        popover.setPosition(PopoverPosition.BOTTOM_START);
        popover.addThemeVariants(PopoverVariant.LUMO_ARROW);

        // Al lado de "Por mes", que es la ultima opcion del selector.
        HorizontalLayout selector = new HorizontalLayout(entryLimit, showTable);
        selector.setPadding(false);
        selector.setWrap(true);
        selector.setAlignItems(Alignment.END);

        VerticalLayout box = new VerticalLayout(selector, help, popover);
        box.setPadding(false);
        box.setSpacing(false);
        return box;
    }

    /**
     * Cuantos ingresos da cada plan contado por mes, segun cuantos dias tenga el mes del socio, y
     * en el pase de 15 dias. Sale de la misma cuenta que el tope, no de una tabla aparte.
     */
    private static Component monthlyEntriesTable() {
        Span title = new Span("Ingresos en el mes de cada socio");
        title.addClassNames(LumoUtility.FontWeight.SEMIBOLD);

        Grid<Integer> table = new Grid<>();
        table.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        table.setSelectionMode(Grid.SelectionMode.NONE);
        table.addColumn(days -> days == 1 ? "1 día por semana" : days + " días por semana")
                .setHeader("Plan").setAutoWidth(true).setFlexGrow(0);
        table.addColumn(days -> GymAllowance.proportional(days, 28)).setHeader("Febrero").setAutoWidth(true);
        table.addColumn(days -> GymAllowance.proportional(days, 30)).setHeader("30 días").setAutoWidth(true);
        table.addColumn(days -> GymAllowance.proportional(days, 31)).setHeader("31 días").setAutoWidth(true);
        table.addColumn(days -> GymAllowance.proportional(days, PlanLength.PASS_DAYS)).setHeader("Pase 15 días")
                .setAutoWidth(true);
        table.setItems(1, 2, 3, 4, 5, 6, 7);
        table.setAllRowsVisible(true);
        table.setWidth("34em");

        Span note = new Span("Cuenta el mes en que arranca el período: 30 días son abril, junio, septiembre y "
                + "noviembre; 31, el resto. En febrero bisiesto, 2 días por semana siguen siendo 8.");
        note.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        note.setMaxWidth("34em");

        VerticalLayout content = new VerticalLayout(title, table, note);
        content.setPadding(false);
        content.addClassNames(LumoUtility.Gap.SMALL);
        return content;
    }

    /**
     * Con el tope por mes: cuantos ingresos da el plan en un mes (segun dure de 28 a 31 dias) y en
     * el pase de 15 dias, de los que ofrece.
     */
    private static String entriesLabel(TariffRow row) {
        int days = row.daysPerWeek();
        String month = null;
        if (row.monthPrice() != null) {
            int least = GymAllowance.proportional(days, 28);
            int most = GymAllowance.proportional(days, 31);
            month = (least == most ? String.valueOf(least) : least + " a " + most) + " por mes";
        }
        String pass = row.fifteenDaysPrice() == null ? null
                : GymAllowance.proportional(days, PlanLength.PASS_DAYS) + " en el pase";
        return month == null ? pass : pass == null ? month : month + " · " + pass;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        GymViewSupport.requireEnabled(event, gymModule);
    }

    private String daysLabel(TariffRow row) {
        return row.daysPerWeek() == 1 ? "1 día por semana" : row.daysPerWeek() + " días por semana";
    }

    private static Span priceCell(BigDecimal price) {
        return price == null ? GymViewSupport.badge("Sin fijar", "contrast small")
                : new Span(GymViewSupport.money(price));
    }

    private HorizontalLayout actions(TariffRow row) {
        Button edit = new Button("Editar", event -> openEdit(row));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button remove = new Button("Borrar", event -> {
            tariffService.remove(PlanLength.MONTH, row.daysPerWeek());
            tariffService.remove(PlanLength.FIFTEEN_DAYS, row.daysPerWeek());
            refresh();
        });
        remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        HorizontalLayout cell = new HorizontalLayout(edit, remove);
        cell.setPadding(false);
        return cell;
    }

    private void openEdit(TariffRow row) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(row == null ? "Fijar tarifa" : "Editar tarifa");

        Select<Integer> days = new Select<>();
        days.setLabel("Días por semana");
        days.setItems(1, 2, 3, 4, 5, 6, 7);
        days.setItemLabelGenerator(d -> d == 1 ? "1 día" : d + " días");
        if (row == null) {
            days.setValue(3);
        } else {
            days.setValue(row.daysPerWeek());
            days.setEnabled(false);
        }
        days.setWidthFull();

        BigDecimalField monthPrice = new BigDecimalField("Cuota mensual");
        monthPrice.setPrefixComponent(new Span("$"));
        monthPrice.setWidthFull();
        BigDecimalField fifteenDaysPrice = new BigDecimalField("Cuota de 15 días");
        fifteenDaysPrice.setPrefixComponent(new Span("$"));
        fifteenDaysPrice.setHelperText("Para el que viene de paso: vale 15 días y vence sin deuda. Vacío: no se ofrece.");
        fifteenDaysPrice.setWidthFull();
        if (row != null) {
            monthPrice.setValue(row.monthPrice());
            fifteenDaysPrice.setValue(row.fifteenDaysPrice());
        }

        VerticalLayout body = new VerticalLayout(days, monthPrice, fifteenDaysPrice);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Guardar", event -> {
            if (monthPrice.getValue() == null && fifteenDaysPrice.getValue() == null) {
                GymViewSupport.error("Poné el monto de al menos una de las dos cuotas.");
                return;
            }
            try {
                savePrice(PlanLength.MONTH, days.getValue(), monthPrice.getValue());
                savePrice(PlanLength.FIFTEEN_DAYS, days.getValue(), fifteenDaysPrice.getValue());
                dialog.close();
                refresh();
                GymViewSupport.ok("Tarifa guardada");
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
        monthPrice.focus();
    }

    /** Vacío borra ese precio: el plan deja de ofrecer ese tipo de cuota. */
    private void savePrice(PlanLength length, int daysPerWeek, BigDecimal price) {
        if (price == null) {
            tariffService.remove(length, daysPerWeek);
        } else {
            tariffService.setPrice(length, daysPerWeek, price);
        }
    }

    private void refresh() {
        entriesColumn.setVisible(gymModule.entryLimit() == EntryLimit.MONTH);
        grid.setItems(tariffService.tariffs());
        // La columna que aparece al pasar a "Por mes" se mide recien ahora; si no, queda cortada.
        grid.recalculateColumnWidths();
    }
}