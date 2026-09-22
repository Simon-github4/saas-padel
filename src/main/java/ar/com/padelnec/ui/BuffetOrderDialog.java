package ar.com.padelnec.ui;

import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.service.BuffetOrderService;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Un pedido de buffet sin turno: sus productos y su cobro.
 *
 * <p>Mismo circuito que el detalle de un turno ({@link BookingDetailDialog}):
 * los productos se suman al total, el cobro se registra en efectivo o
 * transferencia, y si sacar un producto deja plata a favor se devuelve desde acá.
 * Queda abierto después de cada acción, porque un pedido se va armando de a poco:
 * el que pidió una gaseosa vuelve por un café.
 */
class BuffetOrderDialog extends Dialog {

    private static final DateTimeFormatter OPENED =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-AR"));

    private BuffetOrder order;
    private final Tenant club;
    private final BuffetOrderService buffetOrderService;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final ProductRepository productRepository;
    private final UUID currentUserId;
    private final Runnable onChange;

    /** Productos tildados para cobrar ahora, ej. lo que se lleva uno del grupo. */
    private final Set<UUID> selectedForCharge = new LinkedHashSet<>();

    BuffetOrderDialog(BuffetOrder order, Tenant club, BuffetOrderService buffetOrderService,
                      ProductService productService, PaymentService paymentService,
                      ProductRepository productRepository, UUID currentUserId, Runnable onChange) {
        this.order = order;
        this.club = club;
        this.buffetOrderService = buffetOrderService;
        this.productService = productService;
        this.paymentService = paymentService;
        this.productRepository = productRepository;
        this.currentUserId = currentUserId;
        this.onChange = onChange;

        setWidth("calc(32rem + 30px)");
        rebuild();
    }

    private void rebuild() {
        setHeaderTitle(order.getCustomerName() == null ? BuffetOrder.QUICK_SALE
                : "Pedido de " + order.getCustomerName());
        removeAll();
        getFooter().removeAll();
        List<ProductSale> sales = productService.salesOfOrder(order.getId());
        selectedForCharge.retainAll(sales.stream().map(ProductSale::getId).toList());
        add(details(), items(sales), payments(sales));
        getFooter().add(actions());
    }

    private FormLayout details() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Span name = new Span(order.displayName());
        name.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
        form.addFormItem(name, "A nombre de");

        Span opened = new Span(OPENED.format(order.getCreatedAt().atZone(club.zoneId())));
        opened.addClassNames("first-letter-caps");
        form.addFormItem(opened, "Abierto");

        form.addFormItem(amount(order.getTotalPrice(), false), "Total");
        form.addFormItem(amount(order.getPaidAmount(), false), "Pagado");
        form.addFormItem(saldoValue(), "Saldo");
        return form;
    }

    /** Mismo criterio que en un turno: lo pendiente resalta, lo que queda a favor se aclara. */
    private Span saldoValue() {
        BigDecimal credit = order.creditBalance();
        if (credit.signum() > 0) {
            Span span = new Span(Money.format(credit) + " a favor del cliente");
            span.addClassNames("tabular", LumoUtility.FontWeight.SEMIBOLD, LumoUtility.TextColor.SUCCESS);
            return span;
        }
        BigDecimal due = order.balanceDue();
        return amount(due, due.signum() > 0);
    }

    private Span amount(BigDecimal value, boolean highlight) {
        Span span = new Span(Money.format(value));
        span.addClassNames("tabular");
        if (highlight) {
            span.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.TextColor.ERROR);
        }
        return span;
    }

    /** Los productos cargados y la fila para sumar otro. */
    private VerticalLayout items(List<ProductSale> sales) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.addClassNames(LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.Padding.Top.MEDIUM, LumoUtility.Margin.Top.MEDIUM);

        if (sales.isEmpty()) {
            Span empty = new Span("Todavía no tiene productos.");
            empty.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
            layout.add(empty);
        }
        boolean canPickForCharge = order.balanceDue().signum() > 0;
        for (ProductSale sale : sales) {
            Span label = new Span("%dx %s".formatted(sale.getQuantity(), sale.getProductName()));
            Span subtotal = amount(sale.subtotal(), false);
            Button remove = new Button("Quitar", event -> runKeepOpen(() -> {
                productService.removeSale(sale.getId());
                Notification.show("Producto quitado");
            }));
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            HorizontalLayout row = new HorizontalLayout(label, subtotal, remove);
            row.setWidthFull();
            row.setAlignItems(HorizontalLayout.Alignment.CENTER);
            row.expand(label);
            row.addClassNames(LumoUtility.Gap.SMALL);

            // Tildar productos arma el monto a cobrar solo: para cuando uno del
            // grupo se va y paga lo suyo, y el resto de la cuenta sigue abierta.
            if (canPickForCharge) {
                Checkbox pick = new Checkbox();
                pick.setValue(selectedForCharge.contains(sale.getId()));
                pick.addValueChangeListener(event -> {
                    if (Boolean.TRUE.equals(event.getValue())) {
                        selectedForCharge.add(sale.getId());
                    } else {
                        selectedForCharge.remove(sale.getId());
                    }
                    rebuild();
                });
                row.addComponentAsFirst(pick);
            }
            layout.add(row);
        }

        Select<Product> product = new Select<>();
        product.setLabel("Producto");
        product.setItems(productRepository.findAllByActiveTrueOrderByNameAsc());
        product.setItemLabelGenerator(p -> "%s (%s)".formatted(p.getName(), Money.format(p.getUnitPrice())));

        IntegerField quantity = new IntegerField("Cantidad");
        quantity.setValue(1);
        quantity.setMin(1);
        quantity.setStepButtonsVisible(true);
        quantity.setWidth("8rem");

        Button add = new Button("Agregar", event -> {
            if (product.getValue() == null) {
                Notification.show("Elegí un producto").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            runKeepOpen(() -> {
                productService.registerSale(order, product.getValue(),
                        quantity.getValue() == null ? 0 : quantity.getValue(), currentUserId);
                Notification.show("Producto agregado");
            });
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.setDisableOnClick(true);

        HorizontalLayout addRow = new HorizontalLayout(product, quantity, add);
        addRow.setAlignItems(HorizontalLayout.Alignment.END);
        addRow.setPadding(false);
        addRow.addClassNames(LumoUtility.Gap.SMALL);
        layout.add(addRow);
        return layout;
    }

    private VerticalLayout payments(List<ProductSale> sales) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        HorizontalLayout row;
        if (order.balanceDue().signum() > 0) {
            BigDecimal selectedSubtotal = sales.stream()
                    .filter(sale -> selectedForCharge.contains(sale.getId()))
                    .map(ProductSale::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal suggested = selectedSubtotal.signum() > 0
                    ? selectedSubtotal.min(order.balanceDue())
                    : order.balanceDue();
            row = moneyRow("Cobrar en mostrador", suggested, "Registrar cobro", (value, method) -> {
                paymentService.registerManualPayment(order, value, method, currentUserId);
                selectedForCharge.clear();
                Notification.show("Cobro registrado");
            });
        } else if (order.creditBalance().signum() > 0) {
            row = moneyRow("Devolver", order.creditBalance(), "Registrar devolución", (value, method) -> {
                paymentService.registerRefund(order, value, method, currentUserId);
                Notification.show("Devolución registrada");
            });
        } else {
            return layout;
        }

        layout.addClassNames(LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.Padding.Top.MEDIUM, LumoUtility.Margin.Top.MEDIUM);
        layout.add(row);
        return layout;
    }

    private interface MoneyAction {
        void apply(BigDecimal amount, PaymentMethod method);
    }

    /** Monto, método y botón: la misma fila para cobrar y para devolver. */
    private HorizontalLayout moneyRow(String label, BigDecimal suggested, String buttonText, MoneyAction action) {
        BigDecimalField amount = new BigDecimalField(label);
        amount.setValue(suggested);

        Select<PaymentMethod> method = new Select<>();
        method.setLabel("Cómo");
        method.setItems(PaymentMethod.CASH, PaymentMethod.TRANSFER);
        method.setItemLabelGenerator(value -> value == PaymentMethod.CASH ? "Efectivo" : "Transferencia");
        method.setValue(PaymentMethod.CASH);
        method.setWidth("9rem");

        Button button = new Button(buttonText,
                event -> runKeepOpen(() -> action.apply(amount.getValue(), method.getValue())));
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        // Un doble click cargaba el cobro dos veces, igual que en el turno.
        button.setDisableOnClick(true);

        HorizontalLayout row = new HorizontalLayout(amount, method, button);
        row.setAlignItems(HorizontalLayout.Alignment.END);
        row.setPadding(false);
        row.addClassNames(LumoUtility.Gap.SMALL);
        return row;
    }

    private HorizontalLayout actions() {
        Button close = new Button("Cerrar", event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        close.addClassNames(LumoUtility.Margin.Right.AUTO);

        HorizontalLayout actions = new HorizontalLayout(close);
        actions.setWidthFull();
        actions.setAlignItems(HorizontalLayout.Alignment.CENTER);

        // Para un pedido abierto por error. Con cobros no se puede (lo rechaza el
        // servicio): esa plata ya paso por la caja.
        if (!order.hasMoneyIn()) {
            Button delete = new Button("Eliminar pedido", event -> {
                try {
                    buffetOrderService.delete(order.getId());
                    Notification.show("Pedido eliminado");
                    onChange.run();
                    close();
                } catch (BusinessRuleException ex) {
                    Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            actions.add(delete);
        }
        return actions;
    }

    /**
     * Ejecuta la acción y deja el diálogo abierto con el pedido releído: cada
     * servicio trabaja sobre su propia copia, y sin releerlo los montos quedan
     * viejos y el siguiente cobro choca contra el bloqueo optimista.
     */
    private void runKeepOpen(Runnable action) {
        try {
            action.run();
            order = buffetOrderService.require(order.getId());
            onChange.run();
        } catch (BusinessRuleException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        } finally {
            rebuild();
        }
    }
}
