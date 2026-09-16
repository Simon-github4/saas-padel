package ar.com.padelnec.ui;

import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.BuffetOrderService;
import ar.com.padelnec.service.BuffetOrderService.Checkout;
import ar.com.padelnec.service.BuffetOrderService.NewItem;
import ar.com.padelnec.service.BuffetOrderService.OrderWithItems;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.support.PersonNames;
import ar.com.padelnec.support.ProductQuickEntry;
import ar.com.padelnec.support.ProductQuickEntry.Command;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * El buffet como caja registradora: los productos a la vista, el pedido que se
 * está armando al costado y las cuentas abiertas abajo.
 *
 * <p>Vivía como una grilla más en la Caja, entre los números del día. Pero la
 * caja se mira al cierre y el buffet se usa todo el día, y en un torneo se
 * atiende a decenas de espectadores seguidos: esta pantalla está pensada para
 * cobrar sin soltar el teclado. El pedido se arma en pantalla y recién se guarda
 * al cobrarlo o al dejarlo en la cuenta, así que tocar un producto de más no
 * ensucia nada.
 *
 * <p>El circuito rápido: el nombre, Enter, "agua" Enter, "2 cafe" Enter, Enter
 * (pasa a "Paga con"), el billete, Enter. Cobrado, con el vuelto a la vista, y el
 * cursor de vuelta en el nombre para el siguiente.
 */
@Route(value = "buffet", layout = MainLayout.class)
@PageTitle("Buffet | Panel del club")
@PermitAll
public class BuffetView extends VerticalLayout {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d/M", Locale.forLanguageTag("es-AR"));

    private final TenantService tenantService;
    private final BuffetOrderService buffetOrderService;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final ProductRepository productRepository;
    private final UUID currentUserId;

    private Tenant club;
    private List<Product> catalog = List.of();
    private Map<UUID, Product> productsById = Map.of();
    private final Map<UUID, Tile> tiles = new LinkedHashMap<>();

    // --- catálogo
    private final TextField search = new TextField();
    private final Div tileGrid = new Div();
    private final Span noMatches = new Span();
    private List<Integer> matches = List.of();
    private int highlighted;

    // --- pedido en pantalla
    private final Span ticketTitle = new Span();
    private final Span ticketMeta = new Span();
    private final Button detailButton = new Button("Detalle");
    private final TextField customerName = new TextField("A nombre de");
    private final Div suggestions = new Div();
    private final Div lines = new Div();
    private final Div due = new Div();
    private final Span dueLabel = new Span();
    private final Span dueValue = new Span();
    private final BigDecimalField paysWith = new BigDecimalField("Paga con");
    private final Span change = new Span();
    private final Button cashButton = new Button("Efectivo");
    private final Button transferButton = new Button("Transferencia");
    private final Button laterButton = new Button();
    private final Button discardButton = new Button("Descartar");
    private final Div lastCharge = new Div();

    /** La cuenta que se está mirando, o null si es un pedido nuevo. */
    private OrderWithItems loaded;
    /** Lo que se suma ahora y todavía no se guardó: producto y cantidad. */
    private final Map<UUID, Integer> draft = new LinkedHashMap<>();

    // --- listas
    private final Span daySummary = new Span();
    private final Div accounts = new Div();
    private final Span accountsEmpty = new Span("No hay cuentas abiertas.");
    private final Grid<OrderWithItems> settledGrid = new Grid<>();
    private List<OrderWithItems> unsettled = List.of();

    private BooleanSupplier dialogOpen = () -> false;
    private Registration pollRegistration;

    public BuffetView(TenantService tenantService, BuffetOrderService buffetOrderService,
                      ProductService productService, PaymentService paymentService,
                      ProductRepository productRepository, AuthenticationContext authenticationContext) {
        this.tenantService = tenantService;
        this.buffetOrderService = buffetOrderService;
        this.productService = productService;
        this.paymentService = paymentService;
        this.productRepository = productRepository;
        this.currentUserId = authenticationContext.getAuthenticatedUser(ClubUserPrincipal.class)
                .map(ClubUserPrincipal::userId)
                .orElse(null);
        this.club = tenantService.requireCurrent();

        setWidthFull();
        addClassNames(LumoUtility.Gap.LARGE);

        add(header(), pos(), accountsSection(), settledSection());
        registerShortcuts();
        loadCatalog();
        resetTicket();
        refreshLists();
    }

    // --------------------------------------------------------------- armado

    private Component header() {
        daySummary.addClassName("day-summary-line");

        Button newOrder = new Button("Pedido nuevo", VaadinIcon.PLUS.create(), event -> newOrder());
        newOrder.setSuffixComponent(kbd("F2"));
        newOrder.getElement().setAttribute("aria-keyshortcuts", "F2");

        Div header = new Div(daySummary, newOrder);
        header.addClassName("buffet-header");
        return header;
    }

    private Div pos() {
        search.setPlaceholder("Buscar producto por nombre o número");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.setHelperText("Enter agrega el resaltado · ↑ ↓ elige otro · «3 agua» suma tres · "
                + "«-agua» saca uno · Enter con el buscador vacío pasa a «Paga con»");
        search.getElement().setAttribute("aria-label", "Buscar producto");
        search.addValueChangeListener(event -> filterTiles());
        search.addKeyDownListener(Key.ENTER, event -> onSearchEnter());
        search.addKeyDownListener(Key.ARROW_DOWN, event -> moveHighlight(1));
        search.addKeyDownListener(Key.ARROW_UP, event -> moveHighlight(-1));

        tileGrid.addClassName("buffet-tiles");
        noMatches.addClassNames(LumoUtility.TextColor.SECONDARY);
        noMatches.setVisible(false);

        Div catalogPanel = new Div(search, noMatches, tileGrid);
        catalogPanel.addClassName("buffet-catalog");

        Div pos = new Div(catalogPanel, ticket());
        pos.addClassName("buffet-pos");
        return pos;
    }

    private Div ticket() {
        ticketTitle.addClassName("buffet-ticket__title");
        ticketMeta.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        Div heading = new Div(ticketTitle, ticketMeta);
        heading.addClassName("buffet-ticket__heading");
        detailButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        detailButton.addClickListener(event -> openDetail());
        Div head = new Div(heading, detailButton);
        head.addClassName("buffet-ticket__head");

        customerName.setWidthFull();
        customerName.setMaxLength(120);
        customerName.setPlaceholder("¿Para quién es?");
        customerName.setManualValidation(true);
        customerName.setValueChangeMode(ValueChangeMode.EAGER);
        customerName.addValueChangeListener(event -> {
            customerName.setInvalid(false);
            suggestAccounts();
            renderActions();
        });
        customerName.addKeyDownListener(Key.ENTER, event -> search.focus());
        suggestions.addClassName("buffet-suggestions");

        lines.addClassName("buffet-lines");

        dueLabel.addClassName("buffet-due__label");
        dueValue.addClassNames("buffet-due__value", "tabular");
        due.add(dueLabel, dueValue);
        due.addClassName("buffet-due");

        paysWith.setPrefixComponent(new Span("$"));
        paysWith.setValueChangeMode(ValueChangeMode.EAGER);
        paysWith.setPlaceholder("Opcional");
        paysWith.addValueChangeListener(event -> renderChange());
        paysWith.addKeyDownListener(Key.ENTER, event -> save(PaymentMethod.CASH));
        change.addClassNames("buffet-change", "tabular");
        Div cash = new Div(paysWith, change);
        cash.addClassName("buffet-cash");

        cashButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        withKey(cashButton, "F8");
        cashButton.addClickListener(event -> save(PaymentMethod.CASH));
        transferButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST,
                ButtonVariant.LUMO_LARGE);
        withKey(transferButton, "F9");
        transferButton.addClickListener(event -> save(PaymentMethod.TRANSFER));
        withKey(laterButton, "F4");
        laterButton.addClickListener(event -> save(null));
        laterButton.addClassName("buffet-actions__wide");
        discardButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        discardButton.addClickListener(event -> resetTicket());
        discardButton.addClassName("buffet-actions__wide");
        Div actions = new Div(cashButton, transferButton, laterButton, discardButton);
        actions.addClassName("buffet-actions");

        lastCharge.addClassName("buffet-last");
        lastCharge.setVisible(false);

        Div ticket = new Div(head, customerName, suggestions, lines, due, cash, actions, lastCharge);
        ticket.addClassName("buffet-ticket");
        return ticket;
    }

    private Component accountsSection() {
        accounts.addClassName("buffet-accounts");
        accountsEmpty.addClassNames(LumoUtility.TextColor.SECONDARY);
        return section("Cuentas abiertas",
                "Las que deben o tienen plata a favor, de cualquier día. Tocá una para sumarle productos o cobrarla.",
                accounts, accountsEmpty);
    }

    private Component settledSection() {
        settledGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        settledGrid.setAllRowsVisible(true);
        settledGrid.addColumn(item -> HH_MM.format(item.order().getCreatedAt().atZone(club.zoneId())))
                .setHeader("Hora").setAutoWidth(true).setPartNameGenerator(item -> "tabular");
        settledGrid.addColumn(item -> item.order().getCustomerName()).setHeader("A nombre de")
                .setAutoWidth(true);
        settledGrid.addColumn(item -> itemsSummary(item.items())).setHeader("Detalle").setFlexGrow(1);
        settledGrid.addColumn(item -> Money.format(item.order().getTotalPrice())).setHeader("Total")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(item -> "tabular");
        settledGrid.setEmptyStateText("Todavía no se cobró ningún pedido hoy.");
        settledGrid.addItemClickListener(event -> openAccount(event.getItem()));
        return section("Cobrados hoy", "Tocá uno para sumarle algo, sacar un producto o devolver plata.",
                settledGrid);
    }

    private VerticalLayout section(String title, String hint, Component... content) {
        H3 heading = new H3(title);
        heading.addClassNames(LumoUtility.Margin.NONE);
        Span hintSpan = new Span(hint);
        hintSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout block = new VerticalLayout(heading, hintSpan);
        block.add(content);
        block.setPadding(false);
        block.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.Border.TOP,
                LumoUtility.BorderColor.CONTRAST_10, LumoUtility.Padding.Top.LARGE);
        return block;
    }

    /**
     * Las teclas de función no chocan con lo que se tipea en el nombre o el
     * buscador, y el navegador no las usa para nada (F5, F3 y F7 sí, por eso no
     * están). Escuchan en toda la pantalla, esté donde esté el cursor.
     */
    private void registerShortcuts() {
        Shortcuts.addShortcutListener(this, this::newOrder, Key.F2);
        Shortcuts.addShortcutListener(this, () -> save(PaymentMethod.CASH), Key.F8);
        Shortcuts.addShortcutListener(this, () -> save(PaymentMethod.TRANSFER), Key.F9);
        Shortcuts.addShortcutListener(this, () -> save(null), Key.F4);
    }

    private void withKey(Button button, String key) {
        button.setSuffixComponent(kbd(key));
        button.getElement().setAttribute("aria-keyshortcuts", key);
    }

    private Span kbd(String key) {
        Span span = new Span(key);
        span.addClassName("kbd");
        span.getElement().setAttribute("aria-hidden", "true");
        return span;
    }

    // ------------------------------------------------------------ catálogo

    private void loadCatalog() {
        catalog = productRepository.findAllByActiveTrueOrderByNameAsc();
        productsById = catalog.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        tiles.clear();
        tileGrid.removeAll();
        for (int i = 0; i < catalog.size(); i++) {
            Tile tile = new Tile(catalog.get(i), i + 1);
            tiles.put(tile.product.getId(), tile);
            tileGrid.add(tile.button);
        }
        if (catalog.isEmpty()) {
            Span empty = new Span("Todavía no hay productos a la venta. Se cargan en ");
            empty.add(new RouterLink("Configuración → Productos", SettingsView.class));
            empty.add(".");
            empty.addClassNames(LumoUtility.TextColor.SECONDARY);
            tileGrid.add(empty);
            search.setEnabled(false);
        }
    }

    /** Un producto en pantalla: se toca para sumarlo, y muestra cuántos van. */
    private final class Tile {

        private final Product product;
        private final NativeButton button = new NativeButton();
        private final Span count = new Span();

        private Tile(Product product, int position) {
            this.product = product;
            Span key = new Span(String.valueOf(position));
            key.addClassName("buffet-tile__key");
            Span name = new Span(product.getName());
            name.addClassName("buffet-tile__name");
            Span price = new Span(Money.format(product.getUnitPrice()));
            price.addClassNames("buffet-tile__price", "tabular");
            count.addClassName("buffet-tile__count");
            count.setVisible(false);

            button.add(key, name, price, count);
            button.addClassName("buffet-tile");
            button.getElement().setAttribute("type", "button");
            button.getElement().setAttribute("aria-label",
                    "Sumar %s, %s".formatted(product.getName(), Money.format(product.getUnitPrice())));
            button.addClickListener(event -> add(product, 1));
        }

        private void showCount(int quantity) {
            count.setText(String.valueOf(quantity));
            count.setVisible(quantity > 0);
            button.setClassName("buffet-tile--in-order", quantity > 0);
        }
    }

    /**
     * Deja a la vista solo los productos que coinciden, el mejor primero y
     * resaltado: es el que agrega el Enter.
     */
    private void filterTiles() {
        Command command = ProductQuickEntry.parse(search.getValue());
        matches = command.isEmpty() ? List.of() : ProductQuickEntry.matches(names(), command.query());
        highlighted = 0;

        List<Tile> all = List.copyOf(tiles.values());
        for (int i = 0; i < all.size(); i++) {
            NativeButton button = all.get(i).button;
            int place = matches.indexOf(i);
            button.setVisible(command.isEmpty() || place >= 0);
            if (command.isEmpty() || place < 0) {
                button.getStyle().remove("order");
            } else {
                button.getStyle().set("order", String.valueOf(place));
            }
        }
        boolean nothing = !command.isEmpty() && matches.isEmpty();
        noMatches.setText("Ningún producto coincide con «%s».".formatted(command.query()));
        noMatches.setVisible(nothing);
        markHighlighted();
    }

    private void moveHighlight(int step) {
        if (matches.size() > 1) {
            highlighted = Math.floorMod(highlighted + step, matches.size());
            markHighlighted();
        }
    }

    private void markHighlighted() {
        List<Tile> all = List.copyOf(tiles.values());
        for (int i = 0; i < all.size(); i++) {
            boolean next = !matches.isEmpty() && matches.get(highlighted) == i;
            all.get(i).button.setClassName("buffet-tile--next", next);
        }
    }

    private void onSearchEnter() {
        Command command = ProductQuickEntry.parse(search.getValue());
        if (command.isEmpty()) {
            if (amountDue().signum() > 0) {
                paysWith.focus();
            }
            return;
        }
        if (command.quantity() <= 0) {
            warn("La cantidad tiene que ser mayor a cero");
            return;
        }
        matches = ProductQuickEntry.matches(names(), command.query());
        if (matches.isEmpty()) {
            warn("Ningún producto coincide con «%s»".formatted(command.query()));
            return;
        }
        Product product = catalog.get(matches.get(Math.min(highlighted, matches.size() - 1)));
        if (command.remove()) {
            if (!draft.containsKey(product.getId())) {
                warn(product.getName() + " no está entre lo que estás sumando");
                return;
            }
            changeQuantity(product, -command.quantity());
        } else {
            add(product, command.quantity());
        }
        search.clear();
    }

    private List<String> names() {
        return catalog.stream().map(Product::getName).toList();
    }

    // -------------------------------------------------------------- pedido

    private void add(Product product, int quantity) {
        draft.merge(product.getId(), quantity, Integer::sum);
        lastCharge.setVisible(false);
        renderTicket();
    }

    private void changeQuantity(Product product, int step) {
        int quantity = draft.getOrDefault(product.getId(), 0) + step;
        if (quantity <= 0) {
            draft.remove(product.getId());
        } else {
            draft.put(product.getId(), quantity);
        }
        renderTicket();
    }

    /** F2: un pedido en blanco. Si había productos sin guardar, pregunta antes. */
    private void newOrder() {
        if (dialogOpen.getAsBoolean()) {
            return;
        }
        if (draft.isEmpty()) {
            resetTicket();
        } else {
            confirmDiscard("Se pierde lo que cargaste y empezás un pedido nuevo.", this::resetTicket);
        }
    }

    private void resetTicket() {
        loaded = null;
        draft.clear();
        customerName.clear();
        customerName.setInvalid(false);
        paysWith.clear();
        search.clear();
        renderTicket();
        renderAccounts();
        customerName.focus();
    }

    private void openAccount(OrderWithItems item) {
        if (loaded != null && loaded.order().getId().equals(item.order().getId())) {
            return;
        }
        if (draft.isEmpty()) {
            loadAccount(item, false);
        } else {
            confirmDiscard("Se pierde lo que cargaste y se abre la cuenta de %s."
                    .formatted(item.order().getCustomerName()), () -> loadAccount(item, false));
        }
    }

    /**
     * Pone una cuenta en pantalla para sumarle productos o cobrarla.
     *
     * @param keepDraft si lo que se venía cargando pasa a esa cuenta: sí cuando se
     *                  eligió desde la sugerencia del nombre, que es la misma persona
     */
    private void loadAccount(OrderWithItems item, boolean keepDraft) {
        try {
            loaded = fresh(item.order().getId());
        } catch (ResourceNotFoundException ex) {
            warn("Ese pedido ya no existe");
            refreshLists();
            return;
        }
        if (!keepDraft) {
            draft.clear();
        }
        customerName.clear();
        paysWith.clear();
        lastCharge.setVisible(false);
        renderTicket();
        renderAccounts();
        search.focus();
    }

    private OrderWithItems fresh(UUID orderId) {
        return new OrderWithItems(buffetOrderService.require(orderId), productService.salesOfOrder(orderId));
    }

    private void reloadLoaded() {
        if (loaded != null) {
            try {
                loaded = fresh(loaded.order().getId());
            } catch (ResourceNotFoundException ex) {
                // Se borró desde el detalle.
                loaded = null;
                draft.clear();
            }
        }
        renderTicket();
    }

    /** Un producto ya guardado en la cuenta se saca en el momento, como en el detalle. */
    private void removeSaved(ProductSale sale) {
        try {
            productService.removeSale(sale.getId());
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            warn(ex.getMessage());
        }
        reloadLoaded();
        refreshLists();
    }

    /**
     * Guarda lo que hay en pantalla y, con un método, cobra todo el saldo.
     *
     * @param method efectivo o transferencia; null deja el saldo en la cuenta
     */
    private void save(PaymentMethod method) {
        if (dialogOpen.getAsBoolean()) {
            return;
        }
        try {
            boolean isNew = loaded == null;
            String name = customerName.getValue().strip();
            if (isNew && draft.isEmpty() && name.isEmpty()) {
                // Pantalla en blanco: un F8 de más después de cobrar no es un error.
                customerName.focus();
                return;
            }
            if (isNew && name.isEmpty()) {
                customerName.setErrorMessage("Poné a nombre de quién es");
                customerName.setInvalid(true);
                customerName.focus();
                return;
            }
            BigDecimal toCharge = amountDue();
            if (method != null && toCharge.signum() <= 0) {
                warn(isNew ? "Sumá algún producto para cobrar" : "Esta cuenta no tiene nada para cobrar");
                return;
            }
            if (method == null && !isNew && draft.isEmpty()) {
                warn("No sumaste nada a la cuenta");
                return;
            }
            BigDecimal given = method == PaymentMethod.CASH ? paysWith.getValue() : null;
            if (given != null && given.compareTo(toCharge) < 0) {
                warn("Con %s no alcanza: son %s".formatted(Money.format(given), Money.format(toCharge)));
                paysWith.focus();
                return;
            }

            List<NewItem> items = draft.entrySet().stream()
                    .map(entry -> new NewItem(entry.getKey(), entry.getValue()))
                    .toList();
            Checkout result = buffetOrderService.checkout(isNew ? null : loaded.order().getId(),
                    name, items, method, currentUserId);
            showLastCharge(result, method, given);
            resetTicket();
            refreshLists();
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            warn(ex.getMessage());
        } catch (ObjectOptimisticLockingFailureException ex) {
            warn("Otro puesto cambió esta cuenta recién. Revisala y volvé a cobrar.");
            reloadLoaded();
            refreshLists();
        } finally {
            renderActions();
        }
    }

    /** Queda a la vista hasta que se empieza el siguiente: la notificación se va sola. */
    private void showLastCharge(Checkout result, PaymentMethod method, BigDecimal given) {
        BuffetOrder order = result.order();
        lastCharge.removeAll();
        Span text = new Span();
        text.addClassName("buffet-last__text");
        if (method == null) {
            text.setText("Quedó en la cuenta de %s: debe %s".formatted(
                    order.getCustomerName(), Money.format(order.balanceDue())));
        } else {
            text.setText("Cobrado a %s: %s %s".formatted(order.getCustomerName(),
                    Money.format(result.charged()),
                    method == PaymentMethod.CASH ? "en efectivo" : "por transferencia"));
        }
        lastCharge.add(VaadinIcon.CHECK_CIRCLE.create(), text);
        if (given != null) {
            Span changeDue = new Span("Vuelto " + Money.format(given.subtract(result.charged())));
            changeDue.addClassNames("buffet-last__change", "tabular");
            lastCharge.add(changeDue);
        }
        lastCharge.setVisible(true);
    }

    /** Lo que hay que cobrar con lo agregado; negativo si la cuenta tiene plata a favor. */
    private BigDecimal amountDue() {
        BigDecimal base = loaded == null
                ? BigDecimal.ZERO
                : loaded.order().getTotalPrice().subtract(loaded.order().getPaidAmount());
        return base.add(draftTotal());
    }

    private BigDecimal draftTotal() {
        return draft.entrySet().stream()
                .map(entry -> productsById.get(entry.getKey()).getUnitPrice()
                        .multiply(BigDecimal.valueOf(entry.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void openDetail() {
        if (loaded == null) {
            return;
        }
        BuffetOrderDialog dialog;
        try {
            dialog = new BuffetOrderDialog(buffetOrderService.require(loaded.order().getId()), club,
                    buffetOrderService, productService, paymentService, productRepository, currentUserId,
                    this::afterDetailChange);
        } catch (ResourceNotFoundException ex) {
            reloadLoaded();
            return;
        }
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                afterDetailChange();
            }
        });
        dialogOpen = dialog::isOpened;
        dialog.open();
    }

    private void afterDetailChange() {
        reloadLoaded();
        refreshLists();
    }

    private void confirmDiscard(String text, Runnable then) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Hay productos sin guardar");
        dialog.setText(text);
        dialog.setCancelable(true);
        dialog.setCancelText("Seguir con este");
        dialog.setConfirmText("Descartar");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> then.run());
        dialogOpen = dialog::isOpened;
        dialog.open();
    }

    // -------------------------------------------------------------- pintar

    private void renderTicket() {
        boolean isNew = loaded == null;
        if (isNew) {
            ticketTitle.setText("Pedido nuevo");
            ticketMeta.setText("Se guarda al cobrarlo o al dejarlo en la cuenta");
        } else {
            BuffetOrder order = loaded.order();
            ticketTitle.setText((isSettled(order) ? "Pedido de " : "Cuenta de ") + order.getCustomerName());
            ticketMeta.setText(openedText(order)
                    + (order.getPaidAmount().signum() > 0 ? " · ya pagó " + Money.format(order.getPaidAmount()) : ""));
        }
        detailButton.setVisible(!isNew);
        customerName.setVisible(isNew);
        suggestAccounts();

        lines.removeAll();
        if (!isNew && !loaded.items().isEmpty()) {
            if (!draft.isEmpty()) {
                lines.add(caption("En la cuenta"));
            }
            loaded.items().forEach(sale -> lines.add(savedLine(sale)));
        }
        if (!draft.isEmpty()) {
            if (!isNew && !loaded.items().isEmpty()) {
                lines.add(caption("Se suma ahora"));
            }
            draft.forEach((productId, quantity) -> lines.add(draftLine(productsById.get(productId), quantity)));
        }
        if (lines.getComponentCount() == 0) {
            Span empty = new Span("Tocá un producto o escribilo en el buscador.");
            empty.addClassName("buffet-lines__empty");
            lines.add(empty);
        }

        BigDecimal amount = amountDue();
        due.setClassName("buffet-due--credit", amount.signum() < 0);
        dueLabel.setText(amount.signum() < 0 ? "A favor del cliente (devolvelo desde Detalle)" : "A cobrar");
        dueValue.setText(Money.format(amount.abs()));

        tiles.values().forEach(tile -> tile.showCount(draft.getOrDefault(tile.product.getId(), 0)));
        renderChange();
        renderActions();
    }

    private Span caption(String text) {
        Span span = new Span(text);
        span.addClassName("buffet-lines__caption");
        return span;
    }

    private Div savedLine(ProductSale sale) {
        Span quantity = new Span(sale.getQuantity() + "×");
        quantity.addClassNames("buffet-line__qty", "tabular");
        Span name = new Span(sale.getProductName());
        name.addClassName("buffet-line__name");
        Span amount = new Span(Money.format(sale.subtotal()));
        amount.addClassNames("buffet-line__amount", "tabular");

        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> removeSaved(sale));
        remove.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        remove.setAriaLabel("Sacar " + sale.getProductName() + " de la cuenta");
        remove.setTooltipText("Sacar de la cuenta");

        Div row = new Div(quantity, name, amount, remove);
        row.addClassNames("buffet-line", "buffet-line--saved");
        return row;
    }

    private Div draftLine(Product product, int quantity) {
        Button less = stepButton(VaadinIcon.MINUS, "Uno menos de " + product.getName(),
                () -> changeQuantity(product, -1));
        Span count = new Span(String.valueOf(quantity));
        count.addClassNames("buffet-stepper__count", "tabular");
        Button more = stepButton(VaadinIcon.PLUS, "Uno más de " + product.getName(),
                () -> changeQuantity(product, 1));
        Div stepper = new Div(less, count, more);
        stepper.addClassName("buffet-stepper");

        Span name = new Span(product.getName());
        name.addClassName("buffet-line__name");
        Span amount = new Span(Money.format(product.getUnitPrice().multiply(BigDecimal.valueOf(quantity))));
        amount.addClassNames("buffet-line__amount", "tabular");

        Div row = new Div(stepper, name, amount);
        row.addClassNames("buffet-line", "buffet-line--new");
        return row;
    }

    private Button stepButton(VaadinIcon icon, String label, Runnable action) {
        Button button = new Button(icon.create(), event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST,
                ButtonVariant.LUMO_TERTIARY);
        button.setAriaLabel(label);
        return button;
    }

    private void renderChange() {
        BigDecimal given = paysWith.getValue();
        BigDecimal amount = amountDue();
        if (given == null || amount.signum() <= 0) {
            change.setText("");
            change.setVisible(false);
            return;
        }
        BigDecimal rest = given.subtract(amount);
        change.setVisible(true);
        change.setClassName("buffet-change--short", rest.signum() < 0);
        change.setText(rest.signum() < 0
                ? "Faltan " + Money.format(rest.negate())
                : "Vuelto " + Money.format(rest));
    }

    private void renderActions() {
        boolean isNew = loaded == null;
        boolean named = !customerName.getValue().isBlank();
        boolean chargeable = amountDue().signum() > 0;
        cashButton.setEnabled(chargeable);
        transferButton.setEnabled(chargeable);
        paysWith.setEnabled(chargeable);

        laterButton.setText(isNew ? "Cobrar después" : "Guardar en la cuenta");
        laterButton.setEnabled(!draft.isEmpty() || (isNew && named));
        discardButton.setVisible(!draft.isEmpty() || (isNew && named));
    }

    private void refreshLists() {
        club = tenantService.requireCurrent();
        LocalDate today = LocalDate.now(club.zoneId());
        unsettled = buffetOrderService.unsettledOrders(club, today);
        List<OrderWithItems> todays = buffetOrderService.ordersOn(club, today);
        settledGrid.setItems(todays.stream().filter(item -> isSettled(item.order())).toList());
        renderAccounts();
        renderSummary(todays);
        suggestAccounts();
    }

    private void renderSummary(List<OrderWithItems> todays) {
        BigDecimal owed = unsettled.stream()
                .map(item -> item.order().balanceDue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String pending = owed.signum() > 0
                ? " · %d %s %s".formatted(unsettled.size(), unsettled.size() == 1 ? "cuenta debe" : "cuentas deben",
                        Money.format(owed))
                : "";
        if (todays.isEmpty()) {
            daySummary.setText("Todavía no hubo pedidos hoy" + pending);
            return;
        }
        BigDecimal sold = todays.stream()
                .map(item -> item.order().getTotalPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        daySummary.setText("Hoy: %d %s · %s vendido%s".formatted(todays.size(),
                todays.size() == 1 ? "pedido" : "pedidos", Money.format(sold), pending));
    }

    private void renderAccounts() {
        accounts.removeAll();
        unsettled.forEach(item -> accounts.add(accountCard(item)));
        accounts.setVisible(!unsettled.isEmpty());
        accountsEmpty.setVisible(unsettled.isEmpty());
    }

    private NativeButton accountCard(OrderWithItems item) {
        BuffetOrder order = item.order();
        Span name = new Span(order.getCustomerName());
        name.addClassName("buffet-account__name");

        Span amount = new Span();
        amount.addClassNames("buffet-account__amount", "tabular");
        if (order.balanceDue().signum() > 0) {
            amount.setText("Debe " + Money.format(order.balanceDue()));
            amount.addClassName("buffet-account__amount--due");
        } else if (order.creditBalance().signum() > 0) {
            amount.setText("A favor " + Money.format(order.creditBalance()));
            amount.addClassName("buffet-account__amount--credit");
        } else {
            amount.setText("Sin productos");
        }

        Span meta = new Span(openedText(order) + (item.items().isEmpty() ? "" : " · " + itemsSummary(item.items())));
        meta.addClassName("buffet-account__meta");

        NativeButton card = new NativeButton();
        card.add(name, amount, meta);
        card.addClassName("buffet-account");
        card.setClassName("buffet-account--current",
                loaded != null && loaded.order().getId().equals(order.getId()));
        card.getElement().setAttribute("type", "button");
        card.addClickListener(event -> openAccount(item));
        return card;
    }

    /**
     * Mientras se escribe el nombre de un pedido nuevo, las cuentas abiertas de
     * alguien que se llama así: para sumarle a la suya en vez de abrirle otra.
     */
    private void suggestAccounts() {
        suggestions.removeAll();
        String typed = PersonNames.searchable(customerName.getValue());
        List<OrderWithItems> found = loaded != null || typed.length() < 2
                ? List.of()
                : unsettled.stream()
                        .filter(item -> PersonNames.searchable(item.order().getCustomerName()).contains(typed)
                                || PersonNames.samePerson(item.order().getCustomerName(), customerName.getValue()))
                        .limit(3)
                        .toList();
        for (OrderWithItems item : found) {
            BuffetOrder order = item.order();
            String owes = order.balanceDue().signum() > 0 ? " (debe " + Money.format(order.balanceDue()) + ")" : "";
            Button use = new Button("Sumar a la cuenta de " + order.getCustomerName() + owes,
                    VaadinIcon.ARROW_RIGHT.create(), event -> loadAccount(item, true));
            use.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            suggestions.add(use);
        }
        suggestions.setVisible(!found.isEmpty());
    }

    private String openedText(BuffetOrder order) {
        ZonedDateTime opened = order.getCreatedAt().atZone(club.zoneId());
        boolean today = opened.toLocalDate().equals(LocalDate.now(club.zoneId()));
        return (today ? "Hoy" : DAY.format(opened)) + " " + HH_MM.format(opened);
    }

    private static boolean isSettled(BuffetOrder order) {
        return order.getTotalPrice().signum() > 0 && order.getTotalPrice().compareTo(order.getPaidAmount()) == 0;
    }

    /** "2x Agua · 1x Café". */
    private static String itemsSummary(List<ProductSale> items) {
        if (items.isEmpty()) {
            return "Sin productos";
        }
        return items.stream()
                .map(sale -> "%dx %s".formatted(sale.getQuantity(), sale.getProductName()))
                .collect(Collectors.joining(" · "));
    }

    private void warn(String message) {
        Notification.show(message, 4000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    // ------------------------------------------------------ puestos a la par

    /**
     * En un torneo puede haber dos personas cobrando: con el sondeo que ya hace
     * el panel, las cuentas de un puesto aparecen en el otro sin recargar. El
     * pedido que se está armando no se toca.
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        pollRegistration = attachEvent.getUI().addPollListener(event -> {
            if (!dialogOpen.getAsBoolean()) {
                refreshLists();
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        super.onDetach(detachEvent);
    }
}
