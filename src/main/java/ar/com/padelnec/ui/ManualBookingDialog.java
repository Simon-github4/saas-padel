package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.service.BookingService;
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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Carga a mano un turno que entro por telefono o por el mostrador.
 *
 * <p>Es la pantalla que hace usable el sistema el primer dia: mientras el 90% de
 * las reservas sigan llegando por WhatsApp, sin esto la agenda del panel muestra
 * una realidad que no es y el club no puede confiar en la grilla.
 */
class ManualBookingDialog extends Dialog {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-AR"));

    private final Tenant club;
    private final NotificationService notificationService;
    private final PhoneNumbers phoneNumbers;

    private final TextField name = new TextField("Nombre del jugador");
    private final TextField phone = new TextField("Teléfono");
    private final BigDecimalField price = new BigDecimalField("Precio");
    private final TextArea notes = new TextArea("Nota interna");

    ManualBookingDialog(Tenant club, Court court, Instant startsAt,
                        BookingService bookingService, NotificationService notificationService,
                        PhoneNumbers phoneNumbers, Consumer<Booking> onSaved) {
        this.club = club;
        this.notificationService = notificationService;
        this.phoneNumbers = phoneNumbers;
        setHeaderTitle("Cargar turno");

        phone.setHelperText("Se usa para identificar al jugador y, si hace falta, avisarle");
        price.setHelperText("Vacío toma la tarifa de la franja");
        notes.setHelperText("Solo la ve el club");
        name.setRequiredIndicatorVisible(true);
        phone.setRequiredIndicatorVisible(true);

        setWidth("28rem");

        // Que turno se esta cargando es el dato que hay que tener a la vista
        // mientras se completa el formulario, no una linea de texto corrido
        // antes de los campos: si el mostrador se equivoca de celda, esto es lo
        // unico que se lo avisa antes de guardar.
        add(slotSummary(club, court, startsAt));

        FormLayout form = new FormLayout(name, phone, price, notes);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("22em", 2));
        form.setColspan(notes, 2);
        add(form);

        Button save = new Button("Cargar turno", event -> {
            try {
                Booking booking = bookingService.createManual(club, court.getId(), startsAt,
                        name.getValue(), phone.getValue(), price.getValue(), notes.getValue());
                onSaved.accept(booking);
                showConfirmationStep(booking);
            } catch (BusinessRuleException ex) {
                // El mensaje ya viene escrito para leerse en pantalla.
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancelar", event -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        getFooter().add(cancel, save);
    }

    /**
     * Reemplaza el formulario por el ofrecimiento de avisar por WhatsApp.
     *
     * <p>WhatsApp esta en stand by: no hay envio automatico, asi que esto arma un
     * link de wa.me con el mismo texto que mandaria el sistema si estuviera
     * activo, para que el mostrador lo mande el con su propio WhatsApp en un
     * toque, sin tener que escribirlo de cero.
     */
    private void showConfirmationStep(Booking booking) {
        removeAll();
        getFooter().removeAll();

        Span done = new Span("Turno cargado para " + booking.getCustomer().getFullName() + ".");
        done.addClassNames(LumoUtility.FontWeight.SEMIBOLD);

        Anchor sendConfirmation = new Anchor(
                phoneNumbers.whatsappLink(booking.getCustomer().getPhoneNumber(),
                        notificationService.confirmedUnpaidMessage(club, booking)),
                "Enviar confirmación por WhatsApp");
        sendConfirmation.setTarget("_blank");
        sendConfirmation.getElement().getThemeList().add("button");
        sendConfirmation.addClassNames(LumoUtility.Display.BLOCK);

        add(new VerticalLayout(done, sendConfirmation));

        Button close = new Button("Listo", event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(close);
    }

    /** La cancha y el horario que se estan por vender, destacados. */
    private static VerticalLayout slotSummary(Tenant club, Court court, Instant startsAt) {
        Span courtName = new Span(court.getName());
        courtName.addClassNames(LumoUtility.FontWeight.SEMIBOLD);

        Span when = new Span(WHEN.format(startsAt.atZone(club.zoneId())));
        when.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY,
                "first-letter-caps");

        VerticalLayout box = new VerticalLayout(courtName, when);
        box.setPadding(false);
        box.setSpacing(false);
        box.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Padding.MEDIUM, LumoUtility.Margin.Bottom.MEDIUM);
        return box;
    }
}
