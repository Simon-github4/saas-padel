package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
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

    private final TextField name = new TextField("Nombre del jugador");
    private final TextField phone = new TextField("Telefono");
    private final BigDecimalField price = new BigDecimalField("Precio");
    private final TextArea notes = new TextArea("Nota interna");

    ManualBookingDialog(Tenant club, Court court, Instant startsAt,
                        BookingService bookingService, Consumer<Booking> onSaved) {
        setHeaderTitle("Cargar turno");

        phone.setHelperText("Se usa para identificar al jugador y avisarle por WhatsApp");
        price.setHelperText("Vacio toma la tarifa de la franja");
        notes.setHelperText("Solo la ve el club");
        name.setRequiredIndicatorVisible(true);
        phone.setRequiredIndicatorVisible(true);

        FormLayout form = new FormLayout(name, phone, price, notes);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(new com.vaadin.flow.component.html.Paragraph(
                "%s, %s".formatted(court.getName(), WHEN.format(startsAt.atZone(club.zoneId())))));
        add(form);

        Button save = new Button("Cargar turno", event -> {
            try {
                Booking booking = bookingService.createManual(club, court.getId(), startsAt,
                        name.getValue(), phone.getValue(), price.getValue(), notes.getValue());
                onSaved.accept(booking);
                close();
            } catch (BusinessRuleException ex) {
                // El mensaje ya viene escrito para leerse en pantalla.
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        getFooter().add(new Button("Cancelar", event -> close()), save);
    }
}
