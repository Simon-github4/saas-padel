package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.support.PersonNames;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    private final ComboBox<PlayerOption> player = new ComboBox<>("Nombre del jugador");
    private final TextField phone = new TextField("Teléfono");
    private final BigDecimalField price = new BigDecimalField("Precio");
    private final TextArea notes = new TextArea("Nota interna");

    /** Teléfonos de los jugadores del club que tienen cuenta, para marcarlos en las sugerencias. */
    private final Set<String> phonesWithAccount;

    /**
     * Una opción del combo: un jugador del club, o un nombre nuevo que el
     * mostrador escribió y todavía no existe ({@code customer} nulo).
     */
    private record PlayerOption(String name, Customer customer) {
    }

    ManualBookingDialog(Tenant club, Court court, Instant startsAt,
                        BookingService bookingService, CustomerService customerService,
                        NotificationService notificationService,
                        PhoneNumbers phoneNumbers, Consumer<Booking> onSaved) {
        this.club = club;
        this.notificationService = notificationService;
        this.phoneNumbers = phoneNumbers;
        setHeaderTitle("Cargar turno");

        List<Customer> known = customerService.all();
        this.phonesWithAccount = customerService.phonesWithAccount(
                known.stream().map(Customer::getPhoneNumber).toList());
        configurePlayerPicker(known);

        phone.setHelperText("Se usa para identificar al jugador y, si hace falta, avisarle");
        price.setHelperText("Vacío toma la tarifa de la franja");
        notes.setHelperText("Solo la ve el club");
        phone.setRequiredIndicatorVisible(true);

        setWidth("28rem");

        // Que turno se esta cargando es el dato que hay que tener a la vista
        // mientras se completa el formulario, no una linea de texto corrido
        // antes de los campos: si el mostrador se equivoca de celda, esto es lo
        // unico que se lo avisa antes de guardar.
        add(slotSummary(club, court, startsAt));

        FormLayout form = new FormLayout(player, phone, price, notes);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("22em", 2));
        form.setColspan(notes, 2);
        // A todo el ancho: la lista de sugerencias toma el ancho del campo, y en
        // media columna cada jugador se partia en tres renglones (nombre,
        // telefono y la marca de cuenta apretados uno arriba del otro).
        form.setColspan(player, 2);
        add(form);

        Button save = new Button("Cargar turno", event -> {
            try {
                PlayerOption chosen = player.getValue();
                Booking booking = bookingService.createManual(club, court.getId(), startsAt,
                        chosen == null ? null : chosen.name(), phone.getValue(),
                        price.getValue(), notes.getValue());
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
     * El nombre sugiere a los jugadores del club mientras se escribe.
     *
     * <p>Antes eran dos campos de texto sueltos, y cargar a un habitual era volver
     * a tipear su nombre y su telefono de memoria: un digito mal y el turno
     * quedaba a nombre de otro jugador, o se creaba uno repetido. Elegirlo de la
     * lista completa el telefono tal como esta guardado.
     *
     * <p>Solo jugadores de este club, no todas las cuentas de la plataforma: esas
     * cuentas son de personas que quizas nunca pisaron este club, y sugerirlas le
     * mostraria a cualquier mostrador el nombre y el telefono de todos.
     *
     * <p>Se sigue pudiendo escribir un nombre que no esta: es un jugador nuevo, y
     * el telefono se completa a mano como siempre.
     */
    private void configurePlayerPicker(List<Customer> known) {
        List<PlayerOption> options = new ArrayList<>(known.stream()
                .map(customer -> new PlayerOption(customer.getFullName(), customer))
                .toList());

        player.setRequiredIndicatorVisible(true);
        player.setAllowCustomValue(true);
        player.setClearButtonVisible(true);
        player.setHelperText("Elegí uno del club o escribí un jugador nuevo");
        player.setItemLabelGenerator(PlayerOption::name);
        player.setRenderer(new ComponentRenderer<>(this::optionRow));
        // El filtro corre en el servidor (hace falta para buscar por telefono y sin
        // tildes, ver `matches`), asi que cada pausa al tipear es un viaje de ida y
        // vuelta. 500ms es el default de Vaadin para justamente esto -sin el, se
        // siente un pedido por letra en vez de por pausa.
        player.setFilterTimeout(500);
        player.setItems(ManualBookingDialog::matches, options);

        player.addCustomValueSetListener(event -> {
            String typed = event.getDetail() == null ? "" : event.getDetail().trim();
            if (typed.isEmpty()) {
                return;
            }
            PlayerOption previous = player.getValue();
            PlayerOption fresh = new PlayerOption(typed, null);
            options.add(fresh);
            player.setItems(ManualBookingDialog::matches, options);
            player.setValue(fresh);
            // Venia de un jugador conocido: ese telefono es del otro, y dejarlo
            // cargaria el turno nuevo sobre su ficha.
            if (previous != null && previous.customer() != null) {
                phone.clear();
            }
        });

        player.addValueChangeListener(event -> {
            PlayerOption chosen = event.getValue();
            if (chosen != null && chosen.customer() != null) {
                phone.setValue(phoneNumbers.forDisplay(chosen.customer().getPhoneNumber()));
            }
        });
    }

    /** Busca por nombre, sin tildes ni mayúsculas, o por los dígitos del teléfono. */
    private static boolean matches(PlayerOption option, String filter) {
        String term = PersonNames.searchable(filter);
        if (term.isEmpty() || PersonNames.searchable(option.name()).contains(term)) {
            return true;
        }
        String digits = filter.replaceAll("[^0-9]", "");
        return option.customer() != null && digits.length() >= 3
                && option.customer().getPhoneNumber().contains(digits);
    }

    /** Nombre, teléfono y si tiene cuenta: lo que hace falta para no confundir a dos homónimos. */
    private Component optionRow(PlayerOption option) {
        Span name = new Span(option.name());
        name.addClassNames(LumoUtility.FontWeight.MEDIUM);

        Span detail = new Span(option.customer() == null
                ? "Jugador nuevo"
                : phoneNumbers.forDisplay(option.customer().getPhoneNumber()));
        detail.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY, "tabular");

        VerticalLayout texts = new VerticalLayout(name, detail);
        texts.setPadding(false);
        texts.setSpacing(false);

        HorizontalLayout row = new HorizontalLayout(texts);
        row.setWidthFull();
        row.setPadding(false);
        row.setAlignItems(HorizontalLayout.Alignment.CENTER);
        row.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        if (option.customer() != null && phonesWithAccount.contains(option.customer().getPhoneNumber())) {
            Span account = new Span("Con cuenta");
            account.getElement().getThemeList().add("badge success small");
            row.add(account);
        }
        return row;
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

        Span done = new Span("Turno cargado para " + booking.displayName() + ".");
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
