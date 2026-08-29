package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.support.PhoneNumbers;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
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

    private List<Customer> all = List.of();

    public CustomersView(CustomerService customerService, PhoneNumbers phoneNumbers) {
        this.customerService = customerService;
        this.phoneNumbers = phoneNumbers;

        setSizeFull();
        add(searchField(), grid);
        setFlexGrow(1, grid);

        buildColumns();
        refresh();
    }

    private TextField searchField() {
        search.setPlaceholder("Buscar por nombre o telefono");
        search.setClearButtonVisible(true);
        search.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> applyFilter());
        search.setWidth("22em");
        return search;
    }

    private void buildColumns() {
        grid.addColumn(Customer::getFullName).setHeader("Jugador").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(this::whatsappLink).setHeader("Telefono").setAutoWidth(true);

        grid.addComponentColumn(this::trustedToggle)
                .setHeader("De confianza")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(Customer::getNoShowCount)
                .setHeader("Ausentes")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true);

        grid.addComponentColumn(this::blockedToggle)
                .setHeader("Bloqueado")
                .setAutoWidth(true)
                .setFlexGrow(0);

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
        applyFilter();

        if (all.isEmpty()) {
            Paragraph empty = new Paragraph(
                    "Todavia no hay jugadores. Se dan de alta solos con la primera reserva.");
            empty.addClassNames(LumoUtility.TextColor.SECONDARY);
            add(empty);
        }
    }

    private void applyFilter() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase();
        grid.setItems(all.stream()
                .filter(customer -> term.isEmpty()
                        || customer.getFullName().toLowerCase().contains(term)
                        || customer.getPhoneNumber().contains(term))
                .toList());
    }
}
