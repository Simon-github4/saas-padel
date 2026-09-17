package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
import java.util.Set;

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
    private Set<String> phonesWithAccount = Set.of();

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
        grid.addComponentColumn(this::nameCell)
                .setHeader("Jugador")
                .setFlexGrow(3)
                .setComparator(Customer::getFullName);

        grid.addComponentColumn(this::whatsappLink)
                .setHeader("Teléfono")
                .setFlexGrow(2);

        grid.addComponentColumn(this::accountBadge)
                .setHeader("Cuenta")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);

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

    /**
     * El nombre con un lapiz para corregirlo.
     *
     * <p>El nombre ya no lo cambia cualquiera que reserve con ese telefono (ver
     * {@link CustomerService#findOrCreate}), asi que el club necesita poder
     * arreglarlo a mano: un nombre que quedo pisado antes de esa regla, o uno
     * escrito con errores.
     */
    private Component nameCell(Customer customer) {
        Span name = new Span(customer.getFullName());
        Button edit = new Button(VaadinIcon.PENCIL.create(), event -> openRename(customer));
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        edit.setAriaLabel("Cambiar el nombre de " + customer.getFullName());
        edit.getElement().setAttribute("title", "Cambiar nombre");

        HorizontalLayout cell = new HorizontalLayout(name, edit);
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.setAlignItems(Alignment.CENTER);
        cell.addClassNames(LumoUtility.Gap.XSMALL);
        return cell;
    }

    private void openRename(Customer customer) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nombre del jugador");

        TextField name = new TextField("Nombre");
        name.setValue(customer.getFullName());
        name.setWidthFull();
        name.setMaxLength(120);

        Span hint = new Span("Los turnos ya reservados conservan el nombre con el que se reservaron.");
        hint.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout body = new VerticalLayout(name, hint);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Guardar", event -> {
            try {
                customerService.rename(customer, name.getValue());
                dialog.close();
                refresh();
                Notification.show("Nombre actualizado");
            } catch (BusinessRuleException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancelar", event -> dialog.close());
        dialog.getFooter().add(cancel, save);

        dialog.open();
        name.focus();
    }

    /**
     * Si ese telefono tiene cuenta de jugador. Solo el si o el no, sin el mail:
     * el mail es de la persona, y cualquiera puede haber reservado en este club
     * con ese telefono.
     */
    private Component accountBadge(Customer customer) {
        if (!phonesWithAccount.contains(customer.getPhoneNumber())) {
            return new Span();
        }
        Span badge = new Span("Con cuenta");
        badge.getElement().getThemeList().add("badge success small");
        badge.getElement().setAttribute("title",
                "Su nombre solo lo cambia el jugador con su sesión o el club desde acá");
        return badge;
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
        checkbox.setAriaLabel("Reserva sin seña");
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
        phonesWithAccount = customerService.phonesWithAccount(
                all.stream().map(Customer::getPhoneNumber).toList());
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
