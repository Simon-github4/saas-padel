package ar.com.padelnec.gym.ui;

import ar.com.padelnec.gym.GymModule;
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
 * Las cuotas mensuales segun los dias por semana.
 *
 * <p>Es lo que el mostrador usa para auto-completar el monto al cobrar: fijar aca
 * la tarifa "de 3 dias" hace que la cuota del trimestre salga sola, y de todos
 * modos cada venta se puede ajustar a mano. Solo el dueno.
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
        Paragraph help = new Paragraph("La cuota mensual de cada plan. Al cobrar una cuota en «Socios», el mostrador "
                + "toma de aca el monto del plan del socio, y lo puede ajustar a mano si hace falta.");
        help.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE);

        // El boton a la izquierda y el texto a su derecha.
        HorizontalLayout toolbar = new HorizontalLayout(newTariff, help);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.START);
        toolbar.setPadding(false);

        grid.addColumn(this::daysLabel).setHeader("Plan").setFlexGrow(2);
        grid.addComponentColumn(row -> row.price() == null
                        ? GymViewSupport.badge("Sin fijar", "contrast small")
                        : new Span(GymViewSupport.money(row.price())))
                .setHeader("Cuota mensual").setAutoWidth(true).setFlexGrow(0);
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

    private HorizontalLayout actions(TariffRow row) {
        Button edit = new Button(row.price() == null ? "Fijar" : "Editar", event -> openEdit(row));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, row.price() == null
                ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
        if (row.price() == null) {
            HorizontalLayout cell = new HorizontalLayout(edit);
            cell.setPadding(false);
            return cell;
        }
        Button remove = new Button("Borrar", event -> {
            tariffService.remove(row.daysPerWeek());
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

        BigDecimalField price = new BigDecimalField("Cuota mensual");
        price.setPrefixComponent(new Span("$"));
        price.setRequired(true);
        price.setWidthFull();
        if (row != null) {
            price.setValue(row.price());
        }

        VerticalLayout body = new VerticalLayout(days, price);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Guardar", event -> {
            if (price.getValue() == null) {
                GymViewSupport.error("Poné el monto de la cuota.");
                return;
            }
            try {
                tariffService.setPrice(days.getValue(), price.getValue());
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
        price.focus();
    }

    private void refresh() {
        grid.setItems(tariffService.tariffs());
    }
}