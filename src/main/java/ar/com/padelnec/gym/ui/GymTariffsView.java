package ar.com.padelnec.gym.ui;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.PlanLength;
import ar.com.padelnec.gym.service.GymTariffService;
import ar.com.padelnec.gym.service.GymTariffService.TariffRow;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
 * Las cuotas segun los dias por semana: la mensual y la del pase de 15 dias.
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

    private final Grid<TariffRow> grid = new Grid<>();

    public GymTariffsView(GymModule gymModule, GymTariffService tariffService) {
        this.gymModule = gymModule;
        this.tariffService = tariffService;

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
        grid.addComponentColumn(row -> priceCell(row.monthPrice()))
                .setHeader("Cuota mensual").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(row -> priceCell(row.fifteenDaysPrice()))
                .setHeader("Cuota de 15 días").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("Todavía no hay tarifas. Tocá «Fijar tarifa» para fijar la primera.");
        grid.setSizeFull();

        add(toolbar, grid);
        setFlexGrow(1, grid);
        refresh();
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
        grid.setItems(tariffService.tariffs());
    }
}