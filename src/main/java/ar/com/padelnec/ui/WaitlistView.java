package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.service.WaitlistService;
import ar.com.padelnec.service.WaitlistService.SlotWaitlist;
import ar.com.padelnec.support.PhoneNumbers;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Quién está esperando cada horario lleno, en orden de llegada.
 *
 * <p>El aviso automático sale por WhatsApp o por mail, pero con el WhatsApp del
 * club en stand by solo sale el mail. Esta pantalla es para que el mostrador
 * les escriba a mano: cuando un horario
 * tiene cancha libre, cada anotado tiene un botón que abre WhatsApp con el
 * mensaje y el link para reservar ese turno ya escritos.
 *
 * <p>No muestra si el aviso automático ya salió: la entrada queda marcada como
 * avisada también cuando el WhatsApp del club está apagado y el mail no se
 * pudo mandar, así que decir "enviado" podía ser mentira.
 *
 * <p>Con {@code ?turno=<segundos epoch>} muestra solo ese horario: es a donde
 * lleva la alerta de un turno cancelado con gente anotada.
 */
@Route(value = "lista-de-espera", layout = MainLayout.class)
@PageTitle("Lista de espera | Panel del club")
@PermitAll
public class WaitlistView extends VerticalLayout implements BeforeEnterObserver {

    /** Query parameter con el horario a mostrar solo, en segundos epoch. */
    public static final String SLOT_PARAMETER = "turno";

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter SLOT_DAY = DateTimeFormatter.ofPattern("EEEE d/MM", ES_AR);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm", ES_AR);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d/MM HH:mm", ES_AR);

    private final WaitlistService waitlistService;
    private final NotificationService notificationService;
    private final TenantService tenantService;
    private final PhoneNumbers phoneNumbers;

    private final Span count = new Span();
    private final HorizontalLayout toolbar = new HorizontalLayout();
    private final VerticalLayout slots = new VerticalLayout();

    private Tenant club;
    /** Horario que vino en el link, o nulo para ver la lista entera. */
    private Instant focus;

    public WaitlistView(WaitlistService waitlistService, NotificationService notificationService,
                        TenantService tenantService, PhoneNumbers phoneNumbers) {
        this.waitlistService = waitlistService;
        this.notificationService = notificationService;
        this.tenantService = tenantService;
        this.phoneNumbers = phoneNumbers;

        setWidthFull();
        addClassNames(LumoUtility.Gap.MEDIUM);

        count.getElement().getThemeList().add("badge contrast");
        toolbar.setWidthFull();
        toolbar.setPadding(false);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        slots.setPadding(false);
        slots.setSpacing(false);
        slots.addClassNames(LumoUtility.Gap.LARGE);

        // Dice como funciona el aviso, no si ya salio: eso no se puede afirmar
        // por anotado (ver el javadoc de la clase).
        Paragraph automaticNotice = new Paragraph("Cuando se libera un horario, a los anotados les llega "
                + "un aviso automático por mail. Con el botón de cada uno les podés escribir también "
                + "por WhatsApp.");
        automaticNotice.addClassNames(LumoUtility.Margin.NONE, LumoUtility.FontSize.SMALL,
                LumoUtility.TextColor.SECONDARY);

        add(automaticNotice, toolbar, slots);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        focus = event.getLocation().getQueryParameters()
                .getSingleParameter(SLOT_PARAMETER)
                .flatMap(WaitlistView::parseEpochSeconds)
                .orElse(null);
        refresh();
    }

    /** Link a esta vista con un solo horario, para la alerta de turno cancelado. */
    public static QueryParameters focusOn(Instant startsAt) {
        return QueryParameters.simple(Map.of(SLOT_PARAMETER, String.valueOf(startsAt.getEpochSecond())));
    }

    private void refresh() {
        club = tenantService.requireCurrent();
        List<SlotWaitlist> upcoming = waitlistService.upcomingBySlot(club);
        List<SlotWaitlist> shown = focus == null
                ? upcoming
                : upcoming.stream().filter(slot -> slot.startsAt().equals(focus)).toList();

        int people = shown.stream().mapToInt(slot -> slot.entries().size()).sum();
        count.setText(switch (people) {
            case 0 -> "Nadie anotado";
            case 1 -> "1 anotado";
            default -> people + " anotados";
        });

        toolbar.removeAll();
        if (focus != null) {
            // Vino de una alerta: un solo horario, con la salida a la lista entera a mano.
            toolbar.add(new RouterLink("‹ Ver toda la lista de espera", WaitlistView.class));
        } else {
            toolbar.add(new Span());
        }
        toolbar.add(count);

        slots.removeAll();
        if (shown.isEmpty()) {
            Span empty = new Span(focus == null
                    ? "Nadie está anotado en la lista de espera de los próximos turnos."
                    : "Ya no queda nadie anotado para ese horario.");
            empty.addClassNames(LumoUtility.TextColor.SECONDARY);
            slots.add(empty);
            return;
        }
        shown.forEach(slot -> slots.add(slotSection(slot)));
    }

    private VerticalLayout slotSection(SlotWaitlist slot) {
        H3 heading = new H3("%s · %s hs".formatted(
                SLOT_DAY.format(slot.startsAt().atZone(club.zoneId())),
                HH_MM.format(slot.startsAt().atZone(club.zoneId()))));
        heading.addClassNames(LumoUtility.Margin.NONE, "first-letter-caps");

        // Lo que decide si se les escribe: con el horario todavia lleno no hay
        // nada que avisar.
        Span state = new Span(slot.courtFree() ? "Hay cancha libre" : "Sigue lleno");
        state.getElement().getThemeList().add(slot.courtFree() ? "badge success" : "badge contrast");

        HorizontalLayout header = new HorizontalLayout(heading, state);
        header.setPadding(false);
        header.setAlignItems(Alignment.CENTER);
        header.addClassNames(LumoUtility.Gap.MEDIUM, LumoUtility.FlexWrap.WRAP);

        VerticalLayout block = new VerticalLayout(header, entriesGrid(slot));
        block.setPadding(false);
        block.addClassNames(LumoUtility.Gap.SMALL, LumoUtility.Border.TOP,
                LumoUtility.BorderColor.CONTRAST_10, LumoUtility.Padding.Top.LARGE);
        return block;
    }

    private Grid<WaitlistEntry> entriesGrid(SlotWaitlist slot) {
        Grid<WaitlistEntry> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.NONE);

        // El orden de llegada es el criterio justo para a quien escribirle primero.
        grid.addColumn(entry -> slot.entries().indexOf(entry) + 1)
                .setHeader("#")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setPartNameGenerator(entry -> "tabular");
        grid.addColumn(entry -> entry.getCustomer().getFullName())
                .setHeader("Jugador")
                .setFlexGrow(2);
        grid.addColumn(entry -> phoneNumbers.forDisplay(entry.getCustomer().getPhoneNumber()))
                .setHeader("Teléfono")
                .setAutoWidth(true)
                .setPartNameGenerator(entry -> "tabular");
        grid.addColumn(entry -> WHEN.format(entry.getCreatedAt().atZone(club.zoneId())))
                .setHeader("Se anotó")
                .setAutoWidth(true)
                .setPartNameGenerator(entry -> "tabular");
        grid.addComponentColumn(entry -> notifyAction(slot, entry))
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);

        grid.setItems(slot.entries());
        grid.setAllRowsVisible(true);
        return grid;
    }

    private Component notifyAction(SlotWaitlist slot, WaitlistEntry entry) {
        if (!slot.courtFree()) {
            return new Span();
        }
        Anchor link = new Anchor(
                phoneNumbers.whatsappLink(entry.getCustomer().getPhoneNumber(),
                        notificationService.waitlistManualMessage(club, entry)),
                "Avisar por WhatsApp");
        link.setTarget("_blank");
        link.getElement().getThemeList().add("button");
        return link;
    }

    private static Optional<Instant> parseEpochSeconds(String value) {
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(value)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
