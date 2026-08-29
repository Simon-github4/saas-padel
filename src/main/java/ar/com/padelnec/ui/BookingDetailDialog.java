package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.PaymentService;
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
import com.vaadin.flow.component.textfield.BigDecimalField;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Detalle de un turno y las acciones de mostrador.
 *
 * <p>Cobrar, marcar que se jugo, marcar que no vinieron y dar de baja: es todo lo
 * que hace falta el sabado a la tarde con gente esperando del otro lado.
 */
class BookingDetailDialog extends Dialog {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-AR"));

    private final Booking booking;
    private final Tenant club;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final PhoneNumbers phoneNumbers;
    private final UUID currentUserId;
    private final Runnable onChange;

    BookingDetailDialog(Booking booking, Tenant club, BookingService bookingService,
                        PaymentService paymentService, PhoneNumbers phoneNumbers,
                        UUID currentUserId, Runnable onChange) {
        this.booking = booking;
        this.club = club;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.phoneNumbers = phoneNumbers;
        this.currentUserId = currentUserId;
        this.onChange = onChange;

        setHeaderTitle(booking.getCustomer().getFullName());
        add(details(), payments());
        getFooter().add(actions());
    }

    private FormLayout details() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("30em", 2));

        form.addFormItem(new Span(WHEN.format(booking.getStartTime().atZone(club.zoneId()))), "Turno");
        form.addFormItem(new Span(booking.getCourt().getName()), "Cancha");
        form.addFormItem(statusBadge(), "Estado");
        form.addFormItem(whatsappLink(), "Telefono");
        form.addFormItem(new Span(money(booking.getTotalPrice())), "Total");
        form.addFormItem(new Span(money(booking.getPaidAmount())), "Pagado");
        form.addFormItem(new Span(money(booking.balanceDue())), "Saldo");

        if (booking.getAdminNotes() != null && !booking.getAdminNotes().isBlank()) {
            form.addFormItem(new Span(booking.getAdminNotes()), "Nota");
        }
        return form;
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
            case NO_SHOW -> "No se presento";
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

    private VerticalLayout payments() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        if (booking.balanceDue().compareTo(BigDecimal.ZERO) <= 0
                || !booking.getStatus().isCancellable()) {
            return layout;
        }

        BigDecimalField amount = new BigDecimalField("Cobrar en mostrador");
        amount.setValue(booking.balanceDue());
        Button charge = new Button("Registrar cobro", event -> run(() -> {
            paymentService.registerCashPayment(booking, amount.getValue(), currentUserId);
            Notification.show("Cobro registrado");
        }));
        charge.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout row = new HorizontalLayout(amount, charge);
        row.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        layout.add(row);
        return layout;
    }

    private HorizontalLayout actions() {
        HorizontalLayout actions = new HorizontalLayout(new Button("Cerrar", event -> close()));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            actions.add(new Button("Se jugo", event -> run(() -> {
                bookingService.markCompleted(booking.getId());
                Notification.show("Turno cerrado");
            })));

            Button noShow = new Button("No se presento", event -> run(() -> {
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
                        ? "Turno dado de baja. Queda una alerta para devolver la sena."
                        : "Turno dado de baja");
            }));
            cancel.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            actions.add(cancel);
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

    private String money(BigDecimal amount) {
        return "$" + amount.stripTrailingZeros().toPlainString();
    }
}
