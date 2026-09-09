package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.support.PhoneNumbers;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.util.List;

/**
 * Jugadores del club.
 *
 * <p>Lo importante aca es la marca de confianza: es lo que le permite al club
 * seguir tratando distinto al grupo de toda la vida que a alguien que reserva por
 * primera vez, sin volver a hacerlo todo por WhatsApp.
 */
@Route(value = "jugadores", layout = MainLayout.class)
@PageTitle("Jugadores | Panel del club")
@PermitAll
public class CustomersView extends VerticalLayout {

    private final CustomerService customerService;
    private final PhoneNumbers phoneNumbers;

    private final Grid<Customer> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Span count = new Span();

    private List<Customer> all = List.of();

    public CustomersView(CustomerService customerService, PhoneNumbers phoneNumbers) {
        this.customerService = customerService;
        this.phoneNumbers = phoneNumbers;

        setSizeFull();
        // Zebra si, bordes de columna no: esto es una lista de jugadores, no la
        // matriz de la agenda. Ahi los bordes ayudan a cruzar cancha con hora;
        // aca solo agregarian rayas.
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.NONE);

        add(toolbar(), grid);
        setFlexGrow(1, grid);
        addClassNames(LumoUtility.Gap.MEDIUM);

        buildColumns();
        refresh();
    }

    private HorizontalLayout toolbar() {
        search.setPlaceholder("Buscar por nombre o teléfono");
        search.setAriaLabel("Buscar jugadores");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> applyFilter());
        search.setWidth("22em");

        // El mismo badge de conteo que la agenda, en el mismo lugar: dos vistas
        // con grilla que se leen igual.
        count.getElement().getThemeList().add("badge contrast");

        HorizontalLayout toolbar = new HorizontalLayout(search, count);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.setPadding(false);
        toolbar.addClassNames(LumoUtility.Gap.MEDIUM);
        return toolbar;
    }

    private void buildColumns() {
        // Sin autoWidth a proposito: autoWidth fija el ancho al contenido y
        // flexGrow solo reparte lo que sobra. Con telefonos largos las dos
        // columnas de texto sumaban mas que la grilla y aparecia scroll
        // horizontal, con "Bloqueado" fuera de vista. Asi se reparten lo que
        // hay y truncan si hace falta.
        grid.addColumn(Customer::getFullName)
                .setHeader("Jugador")
                .setFlexGrow(3)
                .setSortable(true);

        grid.addComponentColumn(this::whatsappLink)
                .setHeader("Teléfono")
                .setFlexGrow(2);

        grid.addComponentColumn(this::trustedToggle)
                .setHeader("De confianza")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);

        // Numero: alineado a la derecha y con cifras de ancho fijo, que es como
        // se compara una columna de cantidades de un renglon al otro.
        grid.addColumn(Customer::getNoShowCount)
                .setHeader("Ausentes")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(customer -> "tabular");

        grid.addComponentColumn(this::blockedToggle)
                .setHeader("Bloqueado")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);

        grid.setSizeFull();
    }

    private Anchor whatsappLink(Customer customer) {
        Anchor link = new Anchor(
                phoneNumbers.whatsappLink(customer.getPhoneNumber(), null),
                phoneNumbers.forDisplay(customer.getPhoneNumber()));
        link.setTarget("_blank");
        return link;
    }

    private Checkbox trustedToggle(Customer customer) {
        Checkbox checkbox = new Checkbox(customer.isTrusted());
        checkbox.setAriaLabel("Reserva sin sena");
        checkbox.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                customerService.setTrusted(customer, event.getValue());
            }
        });
        return checkbox;
    }

    private Checkbox blockedToggle(Customer customer) {
        Checkbox checkbox = new Checkbox(customer.isBlocked());
        checkbox.setAriaLabel("No puede reservar online");
        checkbox.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                customerService.setBlocked(customer, event.getValue());
            }
        });
        return checkbox;
    }

    private void refresh() {
        all = customerService.all();
        // El vacio lo muestra la propia grilla. Antes se agregaba un Paragraph
        // al final de la vista, debajo de una grilla que ocupa todo el alto: el
        // mensaje quedaba fuera de pantalla justo cuando era lo unico que habia
        // para leer.
        grid.setEmptyStateText(
                "Todavía no hay jugadores. Se dan de alta solos con la primera reserva.");
        applyFilter();
    }

    private void applyFilter() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase();
        List<Customer> shown = all.stream()
                .filter(customer -> term.isEmpty()
                        || customer.getFullName().toLowerCase().contains(term)
                        || customer.getPhoneNumber().contains(term))
                .toList();
        grid.setItems(shown);
        count.setText(countLabel(shown.size(), term));
    }

    /** Cuando hay filtro, el conteo dice sobre cuantos, para no perder la escala. */
    private String countLabel(int shown, String term) {
        if (!term.isEmpty() && shown != all.size()) {
            return "%d de %d jugadores".formatted(shown, all.size());
        }
        return shown == 1 ? "1 jugador" : "%d jugadores".formatted(shown);
    }
}
