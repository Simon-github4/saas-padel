package ar.com.padelnec.ui;

import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.BuffetOrderService;
import ar.com.padelnec.service.BuffetOrderService.Charge;
import ar.com.padelnec.service.BuffetOrderService.Checkout;
import ar.com.padelnec.service.BuffetOrderService.NewItem;
import ar.com.padelnec.service.BuffetOrderService.OrderWithItems;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.support.BuffetCoverage;
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
import com.vaadin.flow.component.checkbox.Checkbox;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
 * <p>El circuito rápido: el nombre (o nada, en una venta rápida), Enter, "agua"
 * Enter, "cafe" Enter, Enter (pasa a "Efectivo"), el billete, Enter. Cobrado,
 * con el vuelto a la vista, y el cursor de vuelta en el nombre para el siguiente.
 * La flecha abajo baja del buscador a los productos, que se recorren con las
 * flechas de costado como una botonera; dos unidades son dos Enter.
 *
 * <p>Una cuenta grande se cobra de a partes, sin salir de ella: eligiendo
 * productos de a unidades (3 de las 5 milanesas) o por monto (el que da
 * $20.000), y cada parte en efectivo, por transferencia o mitad y mitad. Lo
 * pagado por monto cubre los productos en el orden en que se cargaron, y el
 * que queda a medio pagar dice cuánto le falta ({@link BuffetCoverage}).
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
    private final Span dueMeta = new Span();
    private final BigDecimalField cashField = new BigDecimalField("Efectivo");
    private final BigDecimalField transferField = new BigDecimalField("Transferencia");
    private final Button cashRest = new Button("el resto");
    private final Button transferRest = new Button("el resto");
    private final Span tenderSummary = new Span();
    private final Button cashButton = new Button("Efectivo");
    private final Button transferButton = new Button("Transferencia");
    private final Button chargeButton = new Button("Cobrar");
    /** El campo que se completó solo con lo que falta, o null: el escrito a mano no se pisa. */
    private BigDecimalField autoFilled;
    private final Button laterButton = new Button();
    private final Button discardButton = new Button("Descartar");
    private final Div lastCharge = new Div();

    /** La cuenta que se está mirando, o null si es un pedido nuevo. */
    private OrderWithItems loaded;
    /** Lo que se suma ahora y todavía no se guardó: producto y cantidad. */
    private final Map<UUID, Integer> draft = new LinkedHashMap<>();

    /**
     * Lo elegido para cobrar ahora nomás, en unidades: las 3 milanesas que paga
     * uno del grupo mientras el resto de la cuenta sigue abierta. Uno va por id
     * de venta ya guardada, el otro por id de producto de lo recién sumado.
     */
    private final Map<UUID, Integer> pickedSales = new LinkedHashMap<>();
    private final Map<UUID, Integer> pickedDrafts = new LinkedHashMap<>();

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

        // El botón a la izquierda, sobre el buscador, donde arranca el trabajo; el
        // resumen del día a la derecha.
        Div header = new Div(newOrder, daySummary);
        header.addClassName("buffet-header");
        return header;
    }

    private Div pos() {
        search.setPlaceholder("Buscar producto por nombre o número");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.setHelperText("↓ baja a los productos: ← → para elegir, "
                + "Enter suma, ↑ vuelve · «-agua» saca uno · Enter vacío pasa a «Efectivo»");
        search.getElement().setAttribute("aria-label", "Buscar producto");
        search.addValueChangeListener(event -> filterTiles());
        search.addKeyDownListener(Key.ENTER, event -> onSearchEnter());

        tileGrid.addClassName("buffet-tiles");
        noMatches.addClassNames(LumoUtility.TextColor.SECONDARY);
        noMatches.setVisible(false);

        Div catalogPanel = new Div(search, noMatches, tileGrid);
        catalogPanel.addClassName("buffet-catalog");

        Div pos = new Div(catalogPanel, ticket());
        pos.addClassName("buffet-pos");
        enableTileNavigation();
        return pos;
    }

    /**
     * Flechas sobre los productos, del lado del navegador: ir y volver al
     * servidor en cada tecla se notaría.
     *
     * <p>Siempre igual, haya o no texto en el buscador: la flecha abajo baja del
     * buscador al primer producto a la vista (el resaltado, si se filtró), las
     * flechas de costado recorren los productos en el orden en que se ven, y la
     * flecha arriba o Escape vuelven al buscador. Enter o espacio suman el
     * producto con foco, que se queda ahí para sumar otro. Tipear una letra
     * vuelve al buscador con la letra ya escrita.
     */
    private void enableTileNavigation() {
        tileGrid.getElement().executeJs("""
                const grid = this;
                const search = $0;
                if (grid.__buffetNavigation) {
                  return;
                }
                grid.__buffetNavigation = true;
                // Los productos a la vista en el orden en que se ven: el buscador
                // los reordena y la grilla cambia de columnas con el ancho.
                const tiles = () => [...grid.querySelectorAll('.buffet-tile')]
                    .filter((tile) => !tile.hidden && tile.offsetParent !== null)
                    .map((tile) => ({ tile, box: tile.getBoundingClientRect() }))
                    .sort((a, b) => (Math.round(a.box.top) - Math.round(b.box.top)) || (a.box.left - b.box.left))
                    .map((entry) => entry.tile);
                search.addEventListener('keydown', (event) => {
                  if (event.key !== 'ArrowDown') {
                    return;
                  }
                  event.preventDefault();
                  const first = tiles()[0];
                  if (first) {
                    first.focus();
                  }
                });
                grid.addEventListener('keydown', (event) => {
                  const current = event.target.closest('.buffet-tile');
                  if (!current || event.ctrlKey || event.metaKey || event.altKey) {
                    return;
                  }
                  const all = tiles();
                  const index = all.indexOf(current);
                  if (index < 0) {
                    return;
                  }
                  let target;
                  switch (event.key) {
                    case 'ArrowRight': target = all[index + 1]; break;
                    case 'ArrowLeft': target = all[index - 1]; break;
                    case 'Home': target = all[0]; break;
                    case 'End': target = all[all.length - 1]; break;
                    case 'ArrowUp':
                    case 'Escape': target = search; break;
                    // Abajo ya está en los productos: no hace nada, ni baja la página.
                    case 'ArrowDown': break;
                    case 'Enter':
                    case ' ':
                      // Explícito y no el click nativo del botón, que según el
                      // navegador sale al soltar la tecla o no sale.
                      event.preventDefault();
                      current.click();
                      return;
                    default:
                      // Una letra o un número: vuelve al buscador y la letra cae ahí.
                      if (event.key.length === 1) {
                        search.focus();
                      }
                      return;
                  }
                  event.preventDefault();
                  if (target) {
                    target.focus();
                  }
                });
                """, search.getElement());
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
        // Opcional: una venta que se cobra en el momento no necesita nombre. Solo
        // lo pide "Cobrar después", porque una cuenta sin nombre no se encuentra.
        customerName.setPlaceholder("Venta rápida (opcional)");
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
        dueMeta.addClassNames("buffet-due__meta", "tabular");

        // Los dos medios a la vista, a mano: así se ve que se puede pagar una
        // parte de cada uno. Vacíos, no hacen falta: F8 y F9 cobran todo por uno.
        tenderField(cashField, PaymentMethod.CASH);
        tenderField(transferField, PaymentMethod.TRANSFER);
        restButton(cashRest, cashField, transferField);
        restButton(transferRest, transferField, cashField);
        tenderSummary.addClassNames("buffet-tender__summary", "tabular");
        Div cashBox = new Div(cashField, cashRest);
        cashBox.addClassName("buffet-tender__field");
        Div transferBox = new Div(transferField, transferRest);
        transferBox.addClassName("buffet-tender__field");
        Div tender = new Div(cashBox, transferBox, tenderSummary);
        tender.addClassName("buffet-tender");

        cashButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        withKey(cashButton, "F8");
        cashButton.addClickListener(event -> pay(PaymentMethod.CASH));
        // A todo el ancho: el texto dice cuánto cobra y por qué medio.
        cashButton.addClassName("buffet-actions__wide");
        transferButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST,
                ButtonVariant.LUMO_LARGE);
        withKey(transferButton, "F9");
        transferButton.addClickListener(event -> pay(PaymentMethod.TRANSFER));
        transferButton.addClassName("buffet-actions__wide");
        chargeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        withKey(chargeButton, "F8");
        chargeButton.addClickListener(event -> pay(PaymentMethod.CASH));
        chargeButton.addClassName("buffet-actions__wide");
        withKey(laterButton, "F4");
        laterButton.addClickListener(event -> save(null, null));
        laterButton.addClassName("buffet-actions__wide");
        discardButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        discardButton.addClickListener(event -> resetTicket());
        discardButton.addClassName("buffet-actions__wide");
        Div actions = new Div(cashButton, transferButton, chargeButton, laterButton, discardButton);
        actions.addClassName("buffet-actions");

        lastCharge.addClassName("buffet-last");
        lastCharge.setVisible(false);

        Div ticket = new Div(head, customerName, suggestions, lines, due, dueMeta, tender, actions, lastCharge);
        ticket.addClassName("buffet-ticket");
        return ticket;
    }

    /**
     * Enter en un campo cobra: con algo escrito, lo que dicen los campos; vacío,
     * todo por ese medio, como el Enter en «Paga con» de antes.
     */
    private void tenderField(BigDecimalField field, PaymentMethod method) {
        field.setPrefixComponent(new Span("$"));
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.setPlaceholder("0");
        field.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                onTyped(field);
            }
            renderTender();
            renderActions();
        });
        field.addKeyDownListener(Key.ENTER, event -> pay(method));
    }

    /**
     * Con productos elegidos se cobra justo esa parte: lo que se escribe en un
     * campo completa el otro con lo que falta. Lo que se escribió a mano no se
     * pisa, así se puede poner el billete y ver el vuelto.
     */
    private void onTyped(BigDecimalField field) {
        if (autoFilled == field) {
            autoFilled = null;
        }
        BigDecimalField other = otherField(field);
        if (picking() && (other.getValue() == null || autoFilled == other)) {
            completeWithRest(other, field);
        }
    }

    /**
     * Rehace el campo completado solo cuando cambia lo elegido. Sin nada elegido
     * se cobra por monto y los campos van a mano: se vacía.
     */
    private void refreshAutoFilled() {
        if (!picking()) {
            if (autoFilled != null) {
                BigDecimalField field = autoFilled;
                autoFilled = null;
                field.clear();
            }
            return;
        }
        BigDecimalField typed = null;
        if (autoFilled != null) {
            typed = otherField(autoFilled);
        } else if (cashField.getValue() != null && transferField.getValue() == null) {
            typed = cashField;
        } else if (transferField.getValue() != null && cashField.getValue() == null) {
            typed = transferField;
        }
        if (typed != null) {
            completeWithRest(otherField(typed), typed);
        }
    }

    private void completeWithRest(BigDecimalField field, BigDecimalField typed) {
        BigDecimal rest = typed.getValue() == null ? BigDecimal.ZERO : target().subtract(typed.getValue());
        if (rest.signum() > 0) {
            field.setValue(withoutZeroCents(rest));
            autoFilled = field;
        } else {
            // Lo escrito ya cubre todo (o se borró): el otro no lleva nada.
            field.clear();
            autoFilled = null;
        }
    }

    private BigDecimalField otherField(BigDecimalField field) {
        return field == cashField ? transferField : cashField;
    }

    /** «el resto»: lo que falta para lo que se cobra, descontado lo que ya dice el otro campo. */
    private void restButton(Button button, BigDecimalField field, BigDecimalField other) {
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.setTooltipText("Completa con lo que falta");
        button.addClickListener(event -> {
            BigDecimal rest = target().subtract(valueOf(other));
            if (rest.signum() > 0) {
                field.setValue(withoutZeroCents(rest));
            }
            field.focus();
        });
    }

    /** 1500.00 se escribe 1500, como lo tipearía el que cobra; los centavos de verdad quedan. */
    private static BigDecimal withoutZeroCents(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
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
        settledGrid.addColumn(item -> item.order().displayName()).setHeader("A nombre de")
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
        Shortcuts.addShortcutListener(this, () -> pay(PaymentMethod.CASH), Key.F8);
        Shortcuts.addShortcutListener(this, () -> pay(PaymentMethod.TRANSFER), Key.F9);
        Shortcuts.addShortcutListener(this, () -> save(null, null), Key.F4);
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
            button.addClickListener(event -> {
                add(product, 1);
                // Como el Enter del buscador: lo buscado ya se usó.
                if (!search.isEmpty()) {
                    search.clear();
                }
            });
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

    private void markHighlighted() {
        List<Tile> all = List.copyOf(tiles.values());
        for (int i = 0; i < all.size(); i++) {
            boolean next = !matches.isEmpty() && matches.getFirst() == i;
            all.get(i).button.setClassName("buffet-tile--next", next);
        }
    }

    private void onSearchEnter() {
        Command command = ProductQuickEntry.parse(search.getValue());
        if (command.isEmpty()) {
            if (amountDue().signum() > 0) {
                cashField.focus();
            }
            return;
        }
        matches = ProductQuickEntry.matches(names(), command.query());
        if (matches.isEmpty()) {
            warn("Ningún producto coincide con «%s»".formatted(command.query()));
            return;
        }
        Product product = catalog.get(matches.getFirst());
        if (command.remove()) {
            if (!draft.containsKey(product.getId())) {
                warn(product.getName() + " no está entre lo que estás sumando");
                return;
            }
            changeQuantity(product, -1);
        } else {
            add(product, 1);
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
            pickedDrafts.remove(product.getId());
        } else {
            draft.put(product.getId(), quantity);
            // Si se elegían más de las que quedan, se eligen las que quedan.
            pickedDrafts.computeIfPresent(product.getId(), (id, units) -> Math.min(units, quantity));
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
        clearPicks();
        customerName.clear();
        customerName.setInvalid(false);
        clearTender();
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
                    .formatted(item.order().displayName()), () -> loadAccount(item, false));
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
        clearPicks();
        customerName.clear();
        clearTender();
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
                // Lo elegido sigue elegido mientras siga pendiente, y nunca más
                // unidades de las que tiene la línea.
                Map<UUID, ProductSale> byId = loaded.items().stream()
                        .collect(Collectors.toMap(ProductSale::getId, Function.identity()));
                pickedSales.entrySet().removeIf(entry -> byId.get(entry.getKey()) == null
                        || byId.get(entry.getKey()).isPaid());
                pickedSales.replaceAll((id, units) -> Math.min(units, byId.get(id).getQuantity()));
            } catch (ResourceNotFoundException ex) {
                // Se borró desde el detalle.
                loaded = null;
                draft.clear();
                clearPicks();
            }
        }
        renderTicket();
    }

    /**
     * Un producto ya guardado en la cuenta se saca en el momento, como en el
     * detalle. Lo ya cobrado de un producto puede estar en varias líneas: salen juntas.
     */
    private void removeSaved(List<ProductSale> sales) {
        try {
            productService.removeSales(sales.stream().map(ProductSale::getId).toList());
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            warn(ex.getMessage());
        }
        reloadLoaded();
        refreshLists();
    }

    /**
     * F8, F9 o Enter en un campo. Con los campos vacíos cobra todo por ese medio:
     * lo elegido o, sin nada elegido, lo que debe. Con algo escrito cobra lo que
     * dicen los campos, sea cual sea la tecla.
     */
    private void pay(PaymentMethod method) {
        if (!tenderFilled()) {
            // Con lo que se ve en pantalla: si la cuenta cambió en el medio, no se cobra otro monto.
            save(Charge.all(method, target()), null);
            return;
        }
        Tender tender = tender();
        if (tender.problem() != null) {
            warn(tender.problem());
            (tender.transfer().compareTo(target()) > 0 ? transferField : cashField).focus();
            return;
        }
        save(Charge.split(tender.cash(), tender.transfer()), tender.change());
    }

    /**
     * Guarda lo que hay en pantalla y, si viene un cobro, lo registra.
     *
     * <p>Si después del cobro la cuenta sigue debiendo, queda en pantalla para
     * cobrar la parte que sigue; si quedó saldada, la pantalla pasa al siguiente.
     *
     * @param charge cómo se paga; null deja el saldo en la cuenta
     * @param change el vuelto, para tenerlo a la vista; null si no hay
     */
    private void save(Charge charge, BigDecimal change) {
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
            if (isNew && name.isEmpty() && charge == null) {
                askForName("Para dejarlo en la cuenta, poné a nombre de quién es");
                return;
            }
            BigDecimal toCharge = amountDue();
            if (charge != null && toCharge.signum() <= 0) {
                warn(isNew ? "Sumá algún producto para cobrar" : "Esta cuenta no tiene nada para cobrar");
                return;
            }
            if (charge != null && picking() && partTotal().signum() <= 0) {
                warn("Lo que elegiste no tiene nada para cobrar");
                return;
            }
            if (charge == null && !isNew && draft.isEmpty()) {
                warn("No sumaste nada a la cuenta");
                return;
            }
            if (isNew && name.isEmpty() && charge != null && leavesBalance(charge, toCharge)) {
                askForName("Para dejar lo que falta en la cuenta, poné a nombre de quién es");
                return;
            }

            List<NewItem> items = draft.entrySet().stream()
                    .map(entry -> new NewItem(entry.getKey(), entry.getValue()))
                    .toList();
            boolean charging = charge != null;
            Checkout result = buffetOrderService.checkout(isNew ? null : loaded.order().getId(),
                    name, items, charge,
                    charging ? Map.copyOf(pickedSales) : Map.of(),
                    charging ? Map.copyOf(pickedDrafts) : Map.of(),
                    currentUserId);
            showLastCharge(result, change);
            if (charging && result.order().balanceDue().signum() > 0) {
                stayOn(result.order());
            } else {
                resetTicket();
            }
            refreshLists();
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            warn(ex.getMessage());
            // Si el rechazo fue porque otro puesto tocó la cuenta, que se vea cómo quedó.
            reloadLoaded();
        } catch (ObjectOptimisticLockingFailureException ex) {
            warn("Otro puesto cambió esta cuenta recién. Revisala y volvé a cobrar.");
            reloadLoaded();
            refreshLists();
        } finally {
            renderActions();
        }
    }

    /** Si el cobro deja algo pendiente: lo elegido no es todo, o el monto no llega a lo que debe. */
    private boolean leavesBalance(Charge charge, BigDecimal toCharge) {
        if (picking()) {
            return partTotal().compareTo(toCharge) < 0;
        }
        return charge.allWith() == null && charge.cash().add(charge.transfer()).compareTo(toCharge) < 0;
    }

    private void askForName(String message) {
        customerName.setErrorMessage(message);
        customerName.setInvalid(true);
        customerName.focus();
    }

    /**
     * Después de cobrar una parte, la cuenta queda en pantalla con lo que falta:
     * el que sigue elige lo suyo, paga otro monto o con F8 o F9 paga todo lo que queda.
     */
    private void stayOn(BuffetOrder order) {
        loaded = fresh(order.getId());
        draft.clear();
        clearPicks();
        customerName.clear();
        customerName.setInvalid(false);
        clearTender();
        search.clear();
        renderTicket();
        search.focus();
    }

    /** Queda a la vista hasta que se empieza el siguiente: la notificación se va sola. */
    private void showLastCharge(Checkout result, BigDecimal change) {
        BuffetOrder order = result.order();
        lastCharge.removeAll();
        Span text = new Span();
        text.addClassName("buffet-last__text");
        if (result.charged().signum() == 0) {
            text.setText("Quedó en la cuenta de %s: debe %s".formatted(
                    order.getCustomerName(), Money.format(order.balanceDue())));
        } else {
            String who = order.getCustomerName() == null
                    ? "Venta rápida cobrada: "
                    : "Cobrado a %s: ".formatted(order.getCustomerName());
            String rest = order.balanceDue().signum() > 0
                    ? " · quedan " + Money.format(order.balanceDue())
                    : "";
            text.setText(who + howPaid(result) + rest);
        }
        lastCharge.add(VaadinIcon.CHECK_CIRCLE.create(), text);
        if (change != null && change.signum() > 0) {
            Span changeDue = new Span("Vuelto " + Money.format(change));
            changeDue.addClassNames("buffet-last__change", "tabular");
            lastCharge.add(changeDue);
        }
        lastCharge.setVisible(true);
    }

    /** "$5.000 en efectivo", o "$3.000 por transferencia + $5.000 en efectivo". */
    private static String howPaid(Checkout result) {
        List<String> parts = new ArrayList<>();
        if (result.transfer().signum() > 0) {
            parts.add(Money.format(result.transfer()) + " por transferencia");
        }
        if (result.cash().signum() > 0) {
            parts.add(Money.format(result.cash()) + " en efectivo");
        }
        return String.join(" + ", parts);
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

    /** Cuánto le cubre ya lo pagado por monto a cada producto, guardado o recién sumado. */
    private record Covered(Map<UUID, BigDecimal> saved, Map<UUID, BigDecimal> drafts) {
    }

    /**
     * La misma cuenta que hace el servicio al cobrar ({@link BuffetCoverage}): lo
     * guardado en el orden en que se cargó, y detrás lo que se suma ahora.
     */
    private Covered covered() {
        List<ProductSale> saved = loaded == null ? List.of() : loaded.items();
        List<Map.Entry<UUID, Integer>> drafts = List.copyOf(draft.entrySet());
        List<BuffetCoverage.Line> lines = new ArrayList<>();
        saved.forEach(sale -> lines.add(coverageLine(sale)));
        drafts.forEach(entry -> lines.add(draftCoverageLine(entry.getKey(), entry.getValue())));
        List<BigDecimal> amounts = BuffetCoverage.covered(lines,
                loaded == null ? BigDecimal.ZERO : loaded.order().getPaidAmount());

        Map<UUID, BigDecimal> savedCovered = new LinkedHashMap<>();
        for (int i = 0; i < saved.size(); i++) {
            savedCovered.put(saved.get(i).getId(), amounts.get(i));
        }
        Map<UUID, BigDecimal> draftCovered = new LinkedHashMap<>();
        for (int i = 0; i < drafts.size(); i++) {
            draftCovered.put(drafts.get(i).getKey(), amounts.get(saved.size() + i));
        }
        return new Covered(savedCovered, draftCovered);
    }

    private static BuffetCoverage.Line coverageLine(ProductSale sale) {
        return new BuffetCoverage.Line(sale.getUnitPrice(), sale.getQuantity(), sale.isPaid());
    }

    private BuffetCoverage.Line draftCoverageLine(UUID productId, int quantity) {
        return new BuffetCoverage.Line(productsById.get(productId).getUnitPrice(), quantity, false);
    }

    private boolean picking() {
        return !pickedSales.isEmpty() || !pickedDrafts.isEmpty();
    }

    /** Lo que suma lo elegido, ya descontado lo que le cubre lo pagado por monto. */
    private BigDecimal partTotal() {
        Covered covered = covered();
        BigDecimal total = BigDecimal.ZERO;
        if (loaded != null) {
            for (ProductSale sale : loaded.items()) {
                Integer units = pickedSales.get(sale.getId());
                if (units != null) {
                    total = total.add(BuffetCoverage.cost(coverageLine(sale), units,
                            covered.saved().get(sale.getId())));
                }
            }
        }
        for (Map.Entry<UUID, Integer> entry : draft.entrySet()) {
            Integer units = pickedDrafts.get(entry.getKey());
            if (units != null) {
                total = total.add(BuffetCoverage.cost(draftCoverageLine(entry.getKey(), entry.getValue()), units,
                        covered.drafts().get(entry.getKey())));
            }
        }
        return total;
    }

    /** Lo que se cobra en esta pasada si se paga justo: lo elegido o, sin nada elegido, todo lo que debe. */
    private BigDecimal target() {
        return picking() ? partTotal() : amountDue().max(BigDecimal.ZERO);
    }

    /**
     * Elige o deja de elegir unidades de un producto para esta parte.
     *
     * @param units cuántas; null lo saca de lo elegido
     */
    private void pick(boolean saved, UUID id, Integer units) {
        Map<UUID, Integer> target = saved ? pickedSales : pickedDrafts;
        if (units == null) {
            target.remove(id);
        } else {
            target.put(id, units);
        }
        renderTicket();
    }

    private void clearPicks() {
        pickedSales.clear();
        pickedDrafts.clear();
    }

    // ------------------------------------------------------ efectivo y transferencia

    /**
     * Lo que dicen los dos campos frente a lo que se cobra. La transferencia va
     * justa; el efectivo es lo que te dan, y lo que pase de lo que falta es vuelto.
     *
     * @param cash     el efectivo que entra a la caja, ya sin el vuelto
     * @param left     lo que queda en la cuenta, cobrando por monto menos de lo que debe
     * @param problem  por qué así no se puede cobrar, o null
     */
    private record Tender(BigDecimal cash, BigDecimal transfer, BigDecimal change, BigDecimal left,
                          String problem) {
    }

    private Tender tender() {
        BigDecimal target = target();
        BigDecimal given = valueOf(cashField);
        BigDecimal transfer = valueOf(transferField);
        BigDecimal total = given.add(transfer);
        BigDecimal change = total.subtract(target).max(BigDecimal.ZERO);
        BigDecimal left = target.subtract(total).max(BigDecimal.ZERO);
        String problem = null;
        if (given.signum() < 0 || transfer.signum() < 0) {
            problem = "Los montos no pueden ser negativos";
        } else if (given.stripTrailingZeros().scale() > 2 || transfer.stripTrailingZeros().scale() > 2) {
            // El botón diría «Cobrar $100,01» por un $100,005: mejor avisar acá.
            problem = "Los montos van con hasta dos decimales";
        } else if (transfer.compareTo(target) > 0) {
            problem = "La transferencia pasa lo que se cobra: son " + Money.format(target);
        } else if (total.signum() == 0) {
            problem = "Poné cuánto se cobra";
        } else if (picking() && left.signum() > 0) {
            problem = "Faltan %s para lo elegido".formatted(Money.format(left));
        }
        return new Tender(given.subtract(change), transfer, change, left, problem);
    }

    /** "Cobrar $5.000 en efectivo"; mitad y mitad, el total, y el desglose queda debajo de los campos. */
    private static String chargeText(Tender tender) {
        String amount = Money.format(tender.cash().add(tender.transfer()));
        if (tender.transfer().signum() == 0) {
            return "Cobrar " + amount + " en efectivo";
        }
        if (tender.cash().signum() == 0) {
            return "Cobrar " + amount + " por transferencia";
        }
        return "Cobrar " + amount;
    }

    private boolean tenderFilled() {
        return cashField.getValue() != null || transferField.getValue() != null;
    }

    private static BigDecimal valueOf(BigDecimalField field) {
        return field.getValue() == null ? BigDecimal.ZERO : field.getValue();
    }

    private void clearTender() {
        autoFilled = null;
        cashField.clear();
        transferField.clear();
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
            ticketTitle.setText(order.getCustomerName() == null
                    ? BuffetOrder.QUICK_SALE
                    : (isSettled(order) ? "Pedido de " : "Cuenta de ") + order.getCustomerName());
            ticketMeta.setText(openedText(order)
                    + (order.getPaidAmount().signum() > 0 ? " · ya pagó " + Money.format(order.getPaidAmount()) : ""));
        }
        detailButton.setVisible(!isNew);
        customerName.setVisible(isNew);
        suggestAccounts();

        lines.removeAll();
        Covered covered = covered();
        boolean chargeable = amountDue().signum() > 0;
        // Arriba lo que falta cobrar, en el orden en que se cargó; abajo lo ya
        // cobrado, junto por producto: dos aguas pagadas en dos partes son "2× Agua".
        List<ProductSale> pending = new ArrayList<>();
        Map<String, List<ProductSale>> paidGroups = new LinkedHashMap<>();
        if (!isNew) {
            for (ProductSale sale : loaded.items()) {
                if (sale.isPaid() || isCovered(sale.subtotal(), covered.saved().get(sale.getId()))) {
                    paidGroups.computeIfAbsent(sale.getProductName() + "|" + sale.getUnitPrice().stripTrailingZeros(),
                            key -> new ArrayList<>()).add(sale);
                } else {
                    pending.add(sale);
                }
            }
        }
        if (!pending.isEmpty()) {
            if (!draft.isEmpty()) {
                lines.add(caption("En la cuenta"));
            }
            pending.forEach(sale -> lines.add(savedLine(sale, covered.saved().get(sale.getId()), chargeable)));
        }
        if (!draft.isEmpty()) {
            if (!pending.isEmpty() || !paidGroups.isEmpty()) {
                lines.add(caption("Se suma ahora"));
            }
            draft.forEach((productId, quantity) -> lines.add(
                    draftLine(productsById.get(productId), quantity, covered.drafts().get(productId), chargeable)));
        }
        if (!paidGroups.isEmpty()) {
            lines.add(caption("Ya cobrado"));
            paidGroups.values().forEach(group -> lines.add(paidLine(group)));
        }
        if (lines.getComponentCount() == 0) {
            Span empty = new Span("Tocá un producto o escribilo en el buscador.");
            empty.addClassName("buffet-lines__empty");
            lines.add(empty);
        }

        // Con algo elegido, lo grande es lo que se cobra ahora; lo que debe la
        // cuenta entera queda abajo, chico.
        BigDecimal amount = amountDue();
        boolean part = picking() && chargeable;
        due.setClassName("buffet-due--credit", amount.signum() < 0);
        if (part) {
            dueLabel.setText("Esta parte");
            dueValue.setText(Money.format(partTotal()));
            dueMeta.setText("La cuenta debe " + Money.format(amount));
        } else {
            dueLabel.setText(amount.signum() < 0 ? "A favor del cliente (devolvelo desde Detalle)" : "A cobrar");
            dueValue.setText(Money.format(amount.abs()));
        }
        dueMeta.setVisible(part);

        tiles.values().forEach(tile -> tile.showCount(draft.getOrDefault(tile.product.getId(), 0)));
        refreshAutoFilled();
        renderTender();
        renderActions();
    }

    private Span caption(String text) {
        Span span = new Span(text);
        span.addClassName("buffet-lines__caption");
        return span;
    }

    /**
     * Un producto guardado en la cuenta que falta cobrar, entero o a medio pagar
     * («quedan $4.000 de $12.000»): se puede elegir para esta parte.
     */
    private Div savedLine(ProductSale sale, BigDecimal covered, boolean chargeable) {
        Span quantity = new Span(sale.getQuantity() + "×");
        quantity.addClassNames("buffet-line__qty", "tabular");
        Span amount = new Span(Money.format(sale.subtotal()));
        amount.addClassNames("buffet-line__amount", "tabular");

        Integer picked = pickedSales.get(sale.getId());
        Div row = new Div();
        row.addClassNames("buffet-line", "buffet-line--saved");
        row.add(lead(sale.getProductName(), false, chargeable, picked != null,
                        on -> pick(true, sale.getId(), on ? sale.getQuantity() : null)),
                quantity, nameCell(sale.getProductName(), sale.subtotal(), covered, false), amount,
                removeButton(sale.getProductName(), covered.signum() > 0
                        // A medio pagar: lo que ya tiene pago se mueve, se confirma igual que lo cobrado.
                        ? () -> confirmRemovePaid(sale.getQuantity() + "× " + sale.getProductName(),
                                sale.subtotal(), covered, () -> removeSaved(List.of(sale)))
                        : () -> removeSaved(List.of(sale))));
        if (picked != null && sale.getQuantity() > 1) {
            row.add(unitsPicker(sale.getProductName(), picked, sale.getQuantity(),
                    units -> pick(true, sale.getId(), units)));
        }
        return row;
    }

    /**
     * Lo ya cobrado de un producto, en una línea aunque se haya pagado en varias
     * partes. Sacarlo deja plata a favor del cliente: se confirma antes.
     */
    private Div paidLine(List<ProductSale> group) {
        String productName = group.getFirst().getProductName();
        int units = group.stream().mapToInt(ProductSale::getQuantity).sum();
        BigDecimal subtotal = group.stream().map(ProductSale::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        Span quantity = new Span(units + "×");
        quantity.addClassNames("buffet-line__qty", "tabular");
        Span amount = new Span(Money.format(subtotal));
        amount.addClassNames("buffet-line__amount", "tabular");

        Div row = new Div();
        row.addClassNames("buffet-line", "buffet-line--saved", "buffet-line--paid");
        row.add(lead(productName, true, false, false, on -> { }), quantity,
                nameCell(productName, subtotal, subtotal, true), amount,
                removeButton(productName, () -> confirmRemovePaid(units + "× " + productName, subtotal, subtotal,
                        () -> removeSaved(group))));
        return row;
    }

    private Button removeButton(String productName, Runnable onRemove) {
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> onRemove.run());
        remove.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        remove.setAriaLabel("Sacar " + productName + " de la cuenta");
        remove.setTooltipText("Sacar de la cuenta");
        return remove;
    }

    private void confirmRemovePaid(String what, BigDecimal subtotal, BigDecimal paidOfIt, Runnable then) {
        ConfirmDialog dialog = BuffetOrderDialog.confirmPaidRemoval(loaded.order(), what, subtotal, paidOfIt, then);
        dialogOpen = dialog::isOpened;
        dialog.open();
    }

    private Div draftLine(Product product, int quantity, BigDecimal covered, boolean chargeable) {
        Button less = stepButton(VaadinIcon.MINUS, "Uno menos de " + product.getName(),
                () -> changeQuantity(product, -1));
        Span count = new Span(String.valueOf(quantity));
        count.addClassNames("buffet-stepper__count", "tabular");
        Button more = stepButton(VaadinIcon.PLUS, "Uno más de " + product.getName(),
                () -> changeQuantity(product, 1));
        Div stepper = new Div(less, count, more);
        stepper.addClassName("buffet-stepper");

        BigDecimal subtotal = product.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        Span amount = new Span(Money.format(subtotal));
        amount.addClassNames("buffet-line__amount", "tabular");

        // Lo nuevo casi nunca viene cubierto: solo si la cuenta tenía plata a favor.
        boolean done = isCovered(subtotal, covered);
        Integer picked = pickedDrafts.get(product.getId());
        Div row = new Div();
        row.addClassNames("buffet-line", "buffet-line--new");
        row.setClassName("buffet-line--paid", done);
        row.add(lead(product.getName(), done, chargeable, picked != null,
                        on -> pick(false, product.getId(), on ? quantity : null)),
                stepper, nameCell(product.getName(), subtotal, covered, done), amount);
        if (!done && picked != null && quantity > 1) {
            row.add(unitsPicker(product.getName(), picked, quantity, units -> pick(false, product.getId(), units)));
        }
        return row;
    }

    private static boolean isCovered(BigDecimal subtotal, BigDecimal covered) {
        return subtotal.signum() > 0 && covered.compareTo(subtotal) >= 0;
    }

    /**
     * Lo primero de cada línea: la marca de cobrado, o el check para elegirla
     * para esta parte y dejar el resto para después. Sin nada para cobrar, vacío.
     */
    private Component lead(String productName, boolean done, boolean chargeable, boolean picked,
                           Consumer<Boolean> onPick) {
        if (done) {
            Span mark = new Span(VaadinIcon.CHECK.create());
            mark.addClassName("buffet-line__paid-mark");
            mark.getElement().setAttribute("title", "Ya cobrado");
            mark.getElement().setAttribute("aria-label", "Ya cobrado");
            return mark;
        }
        if (!chargeable) {
            return new Span();
        }
        Checkbox checkbox = new Checkbox();
        checkbox.addClassName("buffet-line__pick");
        checkbox.setValue(picked);
        checkbox.setAriaLabel("Cobrar " + productName + " en esta parte");
        checkbox.setTooltipText("Cobrar esto ahora, dejar el resto en la cuenta");
        checkbox.addValueChangeListener(event -> onPick.accept(Boolean.TRUE.equals(event.getValue())));
        return checkbox;
    }

    /** El nombre y, si un pago por monto lo dejó a medio pagar, cuánto le falta. */
    private Div nameCell(String productName, BigDecimal subtotal, BigDecimal covered, boolean done) {
        Span name = new Span(productName);
        name.addClassName("buffet-line__name");
        Div cell = new Div(name);
        cell.addClassName("buffet-line__label");
        if (!done && covered.signum() > 0) {
            Span left = new Span("quedan %s de %s".formatted(Money.format(subtotal.subtract(covered)),
                    Money.format(subtotal)));
            left.addClassNames("buffet-line__left", "tabular");
            cell.add(left);
        }
        return cell;
    }

    /** Cuántas de las unidades de un producto entran en esta parte: 3 de las 5 milanesas. */
    private Div unitsPicker(String productName, int picked, int available, Consumer<Integer> onChange) {
        Button less = stepButton(VaadinIcon.MINUS, "Una menos de " + productName + " en esta parte",
                () -> onChange.accept(picked - 1));
        less.setEnabled(picked > 1);
        Span count = new Span(String.valueOf(picked));
        count.addClassNames("buffet-stepper__count", "tabular");
        Button more = stepButton(VaadinIcon.PLUS, "Una más de " + productName + " en esta parte",
                () -> onChange.accept(picked + 1));
        more.setEnabled(picked < available);
        Div stepper = new Div(less, count, more);
        stepper.addClassName("buffet-stepper");

        Span label = new Span("Paga");
        Span of = new Span("de " + available);
        of.addClassName("tabular");
        Div row = new Div(label, stepper, of);
        row.addClassName("buffet-line__units");
        return row;
    }

    private Button stepButton(VaadinIcon icon, String label, Runnable action) {
        Button button = new Button(icon.create(), event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST,
                ButtonVariant.LUMO_TERTIARY);
        button.setAriaLabel(label);
        return button;
    }

    /** La línea debajo de los campos: cómo queda la cuenta con lo que se escribió. */
    private void renderTender() {
        tenderSummary.removeClassNames("buffet-tender__summary--change", "buffet-tender__summary--left",
                "buffet-tender__summary--problem");
        if (!tenderFilled() || amountDue().signum() <= 0) {
            tenderSummary.setText("");
            tenderSummary.setVisible(false);
            return;
        }
        Tender tender = tender();
        if (tender.problem() != null) {
            tenderSummary.setText(tender.problem());
            tenderSummary.addClassName("buffet-tender__summary--problem");
        } else if (tender.change().signum() > 0) {
            tenderSummary.setText("Vuelto " + Money.format(tender.change()));
            tenderSummary.addClassName("buffet-tender__summary--change");
        } else if (tender.left().signum() > 0) {
            tenderSummary.setText("Quedan %s en la cuenta".formatted(Money.format(tender.left())));
            tenderSummary.addClassName("buffet-tender__summary--left");
        } else if (tender.cash().signum() > 0 && tender.transfer().signum() > 0) {
            tenderSummary.setText("%s en efectivo + %s por transferencia".formatted(
                    Money.format(tender.cash()), Money.format(tender.transfer())));
        } else {
            tenderSummary.setText(picking() ? "Justo lo elegido" : "Paga todo lo que debe");
        }
        tenderSummary.setVisible(true);
    }

    /**
     * Con los campos vacíos, un botón por medio que dice cuánto cobra: todo lo
     * que debe, o lo elegido, entero por ese medio. Con algo escrito, uno solo
     * que cobra lo que dicen los campos: así no queda la duda de cuál apretar.
     */
    private void renderActions() {
        boolean isNew = loaded == null;
        boolean named = !customerName.getValue().isBlank();
        boolean chargeable = amountDue().signum() > 0;
        boolean filled = tenderFilled();
        cashButton.setVisible(!filled);
        transferButton.setVisible(!filled);
        cashButton.setEnabled(chargeable);
        transferButton.setEnabled(chargeable);
        String all = chargeable ? Money.format(target()) + " " : "";
        cashButton.setText("Cobrar " + all + "en efectivo");
        transferButton.setText("Cobrar " + all + "por transferencia");
        cashField.setEnabled(chargeable);
        transferField.setEnabled(chargeable);
        cashRest.setEnabled(chargeable);
        transferRest.setEnabled(chargeable);

        Tender tender = filled && chargeable ? tender() : null;
        boolean ready = tender != null && tender.problem() == null;
        chargeButton.setVisible(filled);
        chargeButton.setEnabled(ready);
        chargeButton.setText(ready ? chargeText(tender) : "Cobrar");

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
        Span name = new Span(order.displayName());
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

    /**
     * "2x Agua · 1x Café". Lo que se partió al cobrar de a partes vuelve a ir
     * junto: 5 milanesas son "5x Milanesa", se hayan pagado como se hayan pagado.
     */
    private static String itemsSummary(List<ProductSale> items) {
        if (items.isEmpty()) {
            return "Sin productos";
        }
        Map<String, Integer> byName = new LinkedHashMap<>();
        items.forEach(sale -> byName.merge(sale.getProductName(), sale.getQuantity(), Integer::sum));
        return byName.entrySet().stream()
                .map(entry -> "%dx %s".formatted(entry.getValue(), entry.getKey()))
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
