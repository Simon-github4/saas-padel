package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Detalle de un turno y las acciones de mostrador.
 *
 * <p>Cobrar, marcar que no vinieron y dar de baja: es todo lo que hace falta el
 * sabado a la tarde con gente esperando del otro lado. Marcar que se jugo no
 * hace falta pedirlo aparte: cobrar el total ya lo cierra solo (ver
 * {@link PaymentService#registerManualPayment}), y si nadie cobra nada, el
 * turno se cierra igual una hora despues de terminar.
 */
class BookingDetailDialog extends Dialog {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-AR"));

    private Booking booking;
    private final Tenant club;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final PhoneNumbers phoneNumbers;
    private final NotificationService notificationService;
    private final UUID currentUserId;
    private final Runnable onChange;

    BookingDetailDialog(Booking booking, Tenant club, BookingService bookingService,
                        PaymentService paymentService, ProductService productService,
                        ProductRepository productRepository, PhoneNumbers phoneNumbers,
                        NotificationService notificationService,
                        UUID currentUserId, Runnable onChange) {
        this.booking = booking;
        this.club = club;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.productService = productService;
        this.productRepository = productRepository;
        this.phoneNumbers = phoneNumbers;
        this.notificationService = notificationService;
        this.currentUserId = currentUserId;
        this.onChange = onChange;

        setHeaderTitle(booking.getCustomer().getFullName());
        setWidth("calc(32rem + 30px)");
        rebuild();
    }

    /** Reconstruye cuerpo y pie a partir del estado actual de {@link #booking}. */
    private void rebuild() {
        removeAll();
        getFooter().removeAll();
        add(details(), products(), payments());
        getFooter().add(actions());
    }

    private FormLayout details() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        form.addFormItem(new Span(WHEN.format(booking.getStartTime().atZone(club.zoneId()))), "Turno");
        form.addFormItem(new Span(booking.getCourt().getName()), "Cancha");
        form.addFormItem(statusBadge(), "Estado");
        form.addFormItem(whatsappLink(), "Teléfono");
        form.addFormItem(amount(booking.getTotalPrice(), false), "Total");
        form.addFormItem(amount(booking.getPaidAmount(), false), "Pagado");
        // El saldo es el numero sobre el que actua el mostrador: si hay algo
        // pendiente tiene que saltar, no leerse igual que los otros dos.
        form.addFormItem(saldoValue(), "Saldo");

        if (booking.getAdminNotes() != null && !booking.getAdminNotes().isBlank()) {
            form.addFormItem(new Span(booking.getAdminNotes()), "Nota");
        }
        return form;
    }

    /**
     * El saldo normal se resalta si hay algo pendiente; si en cambio quedó un
     * excedente a favor del cliente (ej. se sacó un producto ya cobrado), se
     * marca aparte para que no se lea como una deuda del jugador.
     */
    private Span saldoValue() {
        BigDecimal credit = booking.creditBalance();
        if (credit.compareTo(BigDecimal.ZERO) > 0) {
            Span span = new Span(money(credit) + " a favor del cliente");
            span.addClassNames("tabular", LumoUtility.FontWeight.SEMIBOLD, LumoUtility.TextColor.SUCCESS);
            return span;
        }
        BigDecimal due = booking.balanceDue();
        return amount(due, due.compareTo(BigDecimal.ZERO) > 0);
    }

    /** Importe con cifras de ancho fijo, para que la columna de plata se compare. */
    private Span amount(BigDecimal value, boolean highlight) {
        Span span = new Span(money(value));
        span.addClassNames("tabular");
        if (highlight) {
            span.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.TextColor.ERROR);
        }
        return span;
    }

    private Span statusBadge() {
        Span badge = new Span(readable(booking.getStatus()));
        badge.getElement().getThemeList().add(switch (booking.getStatus()) {
            case DRAFT, AWAITING_CONFIRMATION -> "badge contrast";
            case COMPLETED -> "badge success";
            case CANCELLED, NO_SHOW -> "badge error";
            default -> "badge";
        });
        return badge;
    }

    private String readable(BookingStatus status) {
        return switch (status) {
            case DRAFT -> "Esperando pago";
            case AWAITING_CONFIRMATION -> "Sin confirmar";
            case CONFIRMED -> "Confirmado";
            case COMPLETED -> "Jugado";
            case CANCELLED -> "Cancelado";
            case NO_SHOW -> "No se presentó";
        };
    }

    private String readable(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Efectivo";
            case TRANSFER -> "Transferencia";
            case MERCADOPAGO -> "MercadoPago";
        };
    }

    /** Un toque para escribirle al jugador, que es como el club resuelve todo. */
    private Anchor whatsappLink() {
        String phone = booking.getCustomer().getPhoneNumber();
        Anchor link = new Anchor(phoneNumbers.whatsappLink(phone,
                "Hola %s, te escribimos de %s por tu turno."
                        .formatted(booking.getCustomer().getFullName(), club.getName())),
                phoneNumbers.forDisplay(phone));
        link.setTarget("_blank");
        return link;
    }

    /** Productos de kiosco vendidos durante el turno: se suman al total, no al pago. */
    private VerticalLayout products() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        if (!booking.getStatus().acceptsPayment()) {
            return layout;
        }

        List<ProductSale> sales = productService.salesOf(booking.getId());
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
            layout.add(row);
        }

        List<Product> catalog = productRepository.findAllByActiveTrueOrderByNameAsc();
        Select<Product> product = new Select<>();
        product.setLabel("Producto");
        product.setItems(catalog);
        product.setItemLabelGenerator(p -> "%s (%s)".formatted(p.getName(), money(p.getUnitPrice())));

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
                productService.registerSale(booking, product.getValue(), quantity.getValue(), currentUserId);
                Notification.show("Producto agregado");
            });
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.setDisableOnClick(true);

        HorizontalLayout addRow = new HorizontalLayout(product, quantity, add);
        addRow.setAlignItems(HorizontalLayout.Alignment.END);
        addRow.setPadding(false);
        addRow.addClassNames(LumoUtility.Gap.SMALL);

        layout.addClassNames(LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.Padding.Top.MEDIUM, LumoUtility.Margin.Top.MEDIUM);
        layout.add(addRow);
        return layout;
    }

    private VerticalLayout payments() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        if (!booking.getStatus().acceptsPayment()) {
            return layout;
        }

        HorizontalLayout row;
        if (booking.balanceDue().compareTo(BigDecimal.ZERO) > 0) {
            row = chargeRow();
        } else if (booking.creditBalance().compareTo(BigDecimal.ZERO) > 0) {
            row = refundRow();
        } else {
            return layout;
        }

        layout.addClassNames(LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.Padding.Top.MEDIUM, LumoUtility.Margin.Top.MEDIUM);
        layout.add(row);
        return layout;
    }

    private HorizontalLayout chargeRow() {
        BigDecimalField amount = new BigDecimalField("Cobrar en mostrador");
        amount.setValue(booking.balanceDue());

        Select<PaymentMethod> method = new Select<>();
        method.setLabel("Cómo");
        method.setItems(PaymentMethod.CASH, PaymentMethod.TRANSFER);
        method.setItemLabelGenerator(this::readable);
        method.setValue(PaymentMethod.CASH);
        method.setWidth("9rem");

        // Sin cerrar: si despues de este cobro queda saldo, el mostrador puede
        // seguir ajustando en la misma visita.
        Button charge = new Button("Registrar cobro", event -> runKeepOpen(() -> {
            paymentService.registerManualPayment(booking, amount.getValue(), method.getValue(), currentUserId);
            Notification.show("Cobro registrado");
        }));
        charge.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        // Un doble click cargaba el cobro dos veces: el segundo evento sale antes
        // de que rebuild() reemplace el boton, y aca eso es plata cobrada de mas.
        charge.setDisableOnClick(true);

        // BASELINE alineaba el boton con el renglon de la etiqueta del campo,
        // que esta un piso mas arriba; END lo alinea con el campo.
        HorizontalLayout row = new HorizontalLayout(amount, method, charge);
        row.setAlignItems(HorizontalLayout.Alignment.END);
        row.setPadding(false);
        row.addClassNames(LumoUtility.Gap.SMALL);
        return row;
    }

    /**
     * Cuando sacar un producto (u otro ajuste) deja plata a favor del cliente, el
     * mostrador la devuelve en efectivo o transferencia y queda registrada acá:
     * mismo circuito que el cobro, pero restando en vez de sumando.
     */
    private HorizontalLayout refundRow() {
        BigDecimalField amount = new BigDecimalField("Devolver");
        amount.setValue(booking.creditBalance());

        Select<PaymentMethod> method = new Select<>();
        method.setLabel("Cómo");
        method.setItems(PaymentMethod.CASH, PaymentMethod.TRANSFER);
        method.setItemLabelGenerator(this::readable);
        method.setValue(PaymentMethod.CASH);
        method.setWidth("9rem");

        Button refund = new Button("Registrar devolución", event -> runKeepOpen(() -> {
            paymentService.registerRefund(booking, amount.getValue(), method.getValue(), currentUserId);
            Notification.show("Devolución registrada");
        }));
        refund.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refund.setDisableOnClick(true);

        HorizontalLayout row = new HorizontalLayout(amount, method, refund);
        row.setAlignItems(HorizontalLayout.Alignment.END);
        row.setPadding(false);
        row.addClassNames(LumoUtility.Gap.SMALL);
        return row;
    }

    /**
     * Pie del dialogo, ordenado por peso.
     *
     * <p>Antes "Cerrar" abria la fila sin variante y "Se jugo" —la accion que el
     * mostrador toca todos los dias— quedaba en el medio, con el mismo aspecto
     * que las otras. Ahora: cerrar en terciario a la izquierda, las acciones de
     * excepcion en el medio, y la habitual primaria y a la derecha.
     */
    private HorizontalLayout actions() {
        Button close = new Button("Cerrar", event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // Margen automatico y no expand: estirar el boton centra su etiqueta
        // dentro del area estirada, y "Cerrar" quedaba flotando en el medio.
        close.addClassNames(LumoUtility.Margin.Right.AUTO);

        HorizontalLayout actions = new HorizontalLayout(close);
        actions.setWidthFull();
        actions.setAlignItems(HorizontalLayout.Alignment.CENTER);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            // WhatsApp esta en stand by: nada se manda solo. Este link arma el
            // mismo texto del recordatorio automatico para que el mostrador lo
            // pueda mandar a mano, con su propio WhatsApp. Una vez que el turno
            // arranco, un recordatorio ya no tiene sentido.
            if (booking.getStartTime().isAfter(Instant.now())) {
                Anchor remind = new Anchor(
                        phoneNumbers.whatsappLink(booking.getCustomer().getPhoneNumber(),
                                notificationService.reminderMessage(club, booking)),
                        "Recordatorio por WhatsApp");
                remind.setTarget("_blank");
                remind.getElement().getThemeList().add("button");
                actions.add(remind);
            }

            Button noShow = new Button("No se presentó", event -> run(() -> {
                bookingService.markNoShow(booking.getId());
                // Libera la franja: si todavia queda tiempo, el club puede revenderla.
                Notification.show("Marcado como ausente");
            }));
            noShow.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            actions.add(noShow);
        }

        if (booking.getStatus().isCancellable()) {
            Button cancel = new Button("Dar de baja", event -> run(() -> {
                bookingService.cancelByClub(club, booking.getId(), "Baja desde el panel");
                Notification.show(booking.hasMoneyIn()
                        ? "Turno dado de baja. Queda una alerta para devolver la seña."
                        : "Turno dado de baja");
            }));
            cancel.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            actions.add(cancel);
        }

        if (booking.getStatus() == BookingStatus.DRAFT
                || booking.getStatus() == BookingStatus.AWAITING_CONFIRMATION) {
            // Lo arreglaron por telefono o en el mostrador: no hace falta esperar
            // a que el jugador toque el link para que la cancha quede firme.
            Button confirm = new Button("Confirmar Turno", event -> run(() -> {
                bookingService.confirmByClub(club, booking.getId());
                Notification.show("Turno confirmado");
            }));
            confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(confirm);
        }

        return actions;
    }

    /** Ejecuta la accion, avisa si una regla la rechaza y refresca la agenda. */
    private void run(Runnable action) {
        try {
            action.run();
            onChange.run();
            close();
        } catch (BusinessRuleException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Como {@link #run}, pero deja el dialogo abierto: para cobrar y cerrar el
     * turno sin tener que volver a abrirlo. Como cada accion de servicio trabaja
     * sobre su propia copia de la reserva, hay que releerla para que el estado y
     * los montos que se muestran queden al dia.
     */
    private void runKeepOpen(Runnable action) {
        try {
            action.run();
            booking = bookingService.findByIdWithDetails(booking.getId()).orElse(booking);
            onChange.run();
        } catch (BusinessRuleException ex) {
            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        } finally {
            // Tambien cuando una regla rechaza la accion: los botones que mueven
            // plata se deshabilitan solos al tocarlos y solo vuelven cuando se
            // reconstruye el cuerpo. Sin esto, un cobro rechazado dejaba el
            // dialogo tildado y habia que cerrarlo y volver a abrirlo.
            rebuild();
        }
    }

    private String money(BigDecimal amount) {
        return Money.format(amount);
    }
}
