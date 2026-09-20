package ar.com.padelnec.gym.ui;

import static ar.com.padelnec.gym.ui.GymViewSupport.DAY;
import static ar.com.padelnec.gym.ui.GymViewSupport.SHORT_DAY;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymCheckinService.CheckInResult;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymMembershipService.Sale;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymOverviewService.MemberRow;
import ar.com.padelnec.gym.service.GymSedeService;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Socios del gimnasio: alta con clave temporal, cobro de cuotas en el mostrador,
 * ingreso manual y reseteo de clave.
 *
 * <p>Es la pantalla del mostrador del gimnasio: lo que se hace aca es lo que
 * despues valida la app cuando el socio escanea el QR.
 */
@Route(value = "gimnasio", layout = ar.com.padelnec.ui.MainLayout.class)
@PageTitle("Socios del gimnasio | Panel del club")
@PermitAll
public class GymMembersView extends VerticalLayout implements BeforeEnterObserver {

    private static final int DEFAULT_DAYS_PER_WEEK = 3;

    private final GymModule gymModule;
    private final GymMemberService memberService;
    private final GymMembershipService membershipService;
    private final GymCheckinService checkinService;
    private final GymSedeService sedeService;
    private final GymOverviewService overviewService;
    private final transient AuthenticationContext authenticationContext;

    private final Grid<MemberRow> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Span count = new Span();

    private List<MemberRow> all = List.of();
    private LocalDate today = LocalDate.now();
    /** El club pide clave ademas del DNI: recien ahi tienen sentido las claves temporales. */
    private boolean passwordRequired;

    public GymMembersView(GymModule gymModule, GymMemberService memberService,
                          GymMembershipService membershipService, GymCheckinService checkinService,
                          GymSedeService sedeService, GymOverviewService overviewService,
                          AuthenticationContext authenticationContext) {
        this.gymModule = gymModule;
        this.memberService = memberService;
        this.membershipService = membershipService;
        this.checkinService = checkinService;
        this.sedeService = sedeService;
        this.overviewService = overviewService;
        this.authenticationContext = authenticationContext;

        setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        add(toolbar(), grid);
        setFlexGrow(1, grid);
        addClassNames(LumoUtility.Gap.MEDIUM);

        buildColumns();
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        GymViewSupport.requireEnabled(event, gymModule);
    }

    // ------------------------------------------------------------ pantalla

    private HorizontalLayout toolbar() {
        search.setPlaceholder("Buscar por nombre o DNI");
        search.setAriaLabel("Buscar socios");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> applyFilter());
        search.setWidth("22em");

        count.getElement().getThemeList().add("badge contrast");

        Button newMember = new Button("Nuevo socio", VaadinIcon.PLUS.create(), event -> openNewMember());
        newMember.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout left = new HorizontalLayout(search, count);
        left.setAlignItems(Alignment.CENTER);
        left.setPadding(false);

        HorizontalLayout toolbar = new HorizontalLayout(left, newMember);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.setPadding(false);
        return toolbar;
    }

    private void buildColumns() {
        grid.addComponentColumn(this::nameCell).setHeader("Socio").setFlexGrow(3)
                .setComparator(MemberRow::fullName);
        grid.addComponentColumn(this::membershipCell).setHeader("Cuota").setFlexGrow(2);
        grid.addComponentColumn(this::weekCell).setHeader("Esta semana").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(row -> String.join(", ", row.sedes())).setHeader("Vale en").setFlexGrow(2);
        grid.addComponentColumn(this::actionsCell).setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setSizeFull();
        grid.setEmptyStateText("Todavía no hay socios. Tocá «Nuevo socio» para dar de alta al primero.");
    }

    private Component nameCell(MemberRow row) {
        Span name = new Span(row.fullName());
        Span dni = new Span("DNI " + row.dni());
        dni.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        VerticalLayout lines = new VerticalLayout(name, dni);
        lines.setPadding(false);
        lines.setSpacing(false);

        HorizontalLayout cell = new HorizontalLayout(lines);
        cell.setPadding(false);
        cell.setAlignItems(Alignment.CENTER);
        cell.addClassNames(LumoUtility.Gap.SMALL);
        if (!row.enabled()) {
            cell.add(GymViewSupport.badge("Deshabilitado", "error small"));
        } else if (passwordRequired && row.mustChangePassword()) {
            Span temp = GymViewSupport.badge("Clave temporal", "contrast small");
            temp.getElement().setAttribute("title", "Todavía no cambió la clave que le dieron en el mostrador");
            cell.add(temp);
        }
        return cell;
    }

    private Component membershipCell(MemberRow row) {
        if (row.endsOn() == null) {
            return GymViewSupport.badge("Sin cuota", "contrast");
        }
        if (row.current()) {
            return GymViewSupport.badge("Vigente hasta " + SHORT_DAY.format(row.endsOn()), "success");
        }
        if (row.startsOn().isAfter(today)) {
            return GymViewSupport.badge("Empieza el " + SHORT_DAY.format(row.startsOn()), "contrast");
        }
        return GymViewSupport.badge("Venció el " + SHORT_DAY.format(row.endsOn()), "error");
    }

    private Component weekCell(MemberRow row) {
        if (!row.current() || row.daysPerWeek() == null) {
            return new Span("—");
        }
        Span usage = new Span(row.weekUsed() + " de " + row.daysPerWeek());
        if (row.weekUsed() >= row.daysPerWeek()) {
            usage.getElement().getThemeList().add("badge contrast small");
        }
        return usage;
    }

    private Component actionsCell(MemberRow row) {
        Button charge = new Button("Cobrar", event -> openSell(row));
        charge.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        charge.setEnabled(row.enabled());

        // Un boton con menu contextual y no un MenuBar: el MenuBar colapsa su unico item en un
        // "···" con el submenu adentro, y abre en dos niveles.
        Button more = new Button(VaadinIcon.ELLIPSIS_DOTS_V.create());
        more.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        more.setAriaLabel("Más acciones de " + row.fullName());
        ContextMenu menu = new ContextMenu(more);
        menu.setOpenOnClick(true);
        menu.addItem("Registrar ingreso hoy", event -> openManualCheckIn(row)).setEnabled(row.enabled());
        menu.addItem("Editar nombre y teléfono", event -> openEdit(row));
        if (passwordRequired) {
            menu.addItem("Resetear clave", event -> confirmReset(row));
        }
        menu.addItem(row.enabled() ? "Deshabilitar" : "Habilitar", event -> toggleEnabled(row));

        HorizontalLayout cell = new HorizontalLayout(charge, more);
        cell.setPadding(false);
        cell.setAlignItems(Alignment.CENTER);
        return cell;
    }

    // -------------------------------------------------------------- acciones

    private void openNewMember() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nuevo socio");

        TextField dni = new TextField("DNI");
        dni.setRequired(true);
        dni.setMaxLength(12);
        dni.setHelperText("Solo números");
        dni.setWidthFull();
        TextField name = new TextField("Nombre y apellido");
        name.setRequired(true);
        name.setMaxLength(120);
        name.setWidthFull();
        TextField phone = new TextField("Teléfono (opcional)");
        phone.setMaxLength(25);
        phone.setWidthFull();

        VerticalLayout body = new VerticalLayout(dni, name, phone);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Dar de alta", event -> {
            try {
                CreatedMember created = memberService.create(dni.getValue(), name.getValue(), phone.getValue());
                dialog.close();
                refresh();
                Runnable charge = () -> memberRow(created.id()).ifPresent(this::openSell);
                if (passwordRequired) {
                    GymViewSupport.showTemporaryPassword(created.fullName(), created.dni(),
                            created.temporaryPassword(), charge);
                } else {
                    GymViewSupport.showRegistered(created.fullName(), created.dni(), charge);
                }
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
        dni.focus();
    }

    private void openSell(MemberRow row) {
        List<GymSede> sedes = sedeService.active();
        if (sedes.isEmpty()) {
            GymViewSupport.error("Primero creá una sede en «Sedes y QR».");
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Cobrar cuota · " + row.fullName());

        LocalDate start = row.current() && row.endsOn() != null ? row.endsOn().plusDays(1) : today;
        DatePicker startsOn = new DatePicker("Desde");
        startsOn.setValue(start);
        startsOn.setWidthFull();

        IntegerField months = new IntegerField("Meses");
        months.setMin(1);
        months.setMax(12);
        months.setValue(1);
        months.setStepButtonsVisible(true);
        months.setWidthFull();

        Span until = new Span();
        until.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        Runnable refreshUntil = () -> until.setText(startsOn.getValue() != null && months.getValue() != null
                ? "Vale hasta el " + DAY.format(GymMembershipService.endOfPeriod(startsOn.getValue(), months.getValue()))
                : "");
        startsOn.addValueChangeListener(event -> refreshUntil.run());
        months.addValueChangeListener(event -> refreshUntil.run());
        refreshUntil.run();

        IntegerField days = new IntegerField("Días por semana");
        days.setMin(1);
        days.setMax(7);
        days.setValue(row.daysPerWeek() != null ? row.daysPerWeek() : DEFAULT_DAYS_PER_WEEK);
        days.setStepButtonsVisible(true);
        days.setWidthFull();

        CheckboxGroup<GymSede> sedeGroup = new CheckboxGroup<>("Vale en");
        sedeGroup.setItems(sedes);
        sedeGroup.setItemLabelGenerator(GymSede::getName);
        sedeGroup.select(sedes);

        BigDecimalField price = new BigDecimalField("Monto cobrado");
        price.setPrefixComponent(new Span("$"));
        price.setWidthFull();

        Select<PayMethod> method = new Select<>();
        method.setLabel("Cómo pagó");
        method.setItems(PayMethod.values());
        method.setItemLabelGenerator(PayMethod::label);
        method.setValue(PayMethod.CASH);
        method.setWidthFull();

        Select<GymSede> collectedAt = new Select<>();
        collectedAt.setLabel("Cobrado en");
        collectedAt.setItems(sedes);
        collectedAt.setItemLabelGenerator(GymSede::getName);
        collectedAt.setValue(sedes.getFirst());
        collectedAt.setWidthFull();

        VerticalLayout body = new VerticalLayout(startsOn, months, until, days, sedeGroup, price, method);
        if (sedes.size() > 1) {
            body.add(collectedAt);
        }
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Registrar cobro", event -> {
            if (startsOn.getValue() == null || months.getValue() == null || days.getValue() == null) {
                GymViewSupport.error("Completá desde cuándo, los meses y los días por semana.");
                return;
            }
            Set<UUID> sedeIds = sedeGroup.getValue().stream().map(GymSede::getId).collect(Collectors.toSet());
            try {
                membershipService.sell(new Sale(row.id(), startsOn.getValue(),
                        GymMembershipService.endOfPeriod(startsOn.getValue(), months.getValue()),
                        days.getValue(), price.getValue(), method.getValue(), collectedAt.getValue().getId(),
                        sedeIds, GymViewSupport.currentUserId(authenticationContext).orElse(null)));
                dialog.close();
                refresh();
                GymViewSupport.ok("Cuota registrada");
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
        price.focus();
    }

    private void openManualCheckIn(MemberRow row) {
        List<GymSede> sedes = sedeService.active();
        if (sedes.isEmpty()) {
            GymViewSupport.error("Primero creá una sede en «Sedes y QR».");
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Registrar ingreso de hoy · " + row.fullName());

        Select<GymSede> sede = new Select<>();
        sede.setLabel("Sede");
        sede.setItems(sedes);
        sede.setItemLabelGenerator(GymSede::getName);
        sede.setValue(sedes.getFirst());
        sede.setWidthFull();

        Paragraph note = new Paragraph("Sirve para cuando el socio no puede escanear. Si ya usó los días de esta "
                + "semana, queda registrado como excepción.");
        note.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout body = new VerticalLayout(sede, note);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Registrar ingreso", event -> {
            try {
                CheckInResult result = checkinService.forceCheckIn(row.id(), sede.getValue().getId(),
                        GymViewSupport.currentUserId(authenticationContext).orElse(null));
                dialog.close();
                refresh();
                GymViewSupport.ok(result.alreadyRegistered()
                        ? "Ya tenía el ingreso de hoy (" + result.sedeName() + ")"
                        : "Ingreso registrado en " + result.sedeName() + " · " + result.weekUsed() + " de "
                                + result.weekLimit() + " esta semana");
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
    }

    private void openEdit(MemberRow row) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Datos del socio");

        TextField name = new TextField("Nombre y apellido");
        name.setValue(row.fullName());
        name.setMaxLength(120);
        name.setWidthFull();
        TextField phone = new TextField("Teléfono");
        phone.setValue(row.phone() == null ? "" : row.phone());
        phone.setMaxLength(25);
        phone.setWidthFull();

        Span dni = new Span("DNI " + row.dni() + " (no se puede cambiar)");
        dni.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout body = new VerticalLayout(name, phone, dni);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Guardar", event -> {
            try {
                memberService.updateDetails(row.id(), name.getValue(), phone.getValue());
                dialog.close();
                refresh();
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
    }

    private void confirmReset(MemberRow row) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Resetear la clave de " + row.fullName());
        dialog.add(new Paragraph("Se genera una clave temporal nueva y se cierran las sesiones que tenga abiertas "
                + "en la app. Va a tener que cambiarla al volver a entrar."));

        Button reset = new Button("Resetear", event -> {
            String password = memberService.resetPassword(row.id());
            dialog.close();
            refresh();
            GymViewSupport.showTemporaryPassword(row.fullName(), row.dni(), password, null);
        });
        reset.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), reset);
        dialog.open();
    }

    private void toggleEnabled(MemberRow row) {
        memberService.setEnabled(row.id(), !row.enabled());
        refresh();
    }

    // ---------------------------------------------------------------- datos

    private java.util.Optional<MemberRow> memberRow(UUID id) {
        return overviewService.members().stream().filter(row -> row.id().equals(id)).findFirst();
    }

    private void refresh() {
        passwordRequired = gymModule.isPasswordRequired();
        today = overviewService.today();
        all = overviewService.members();
        applyFilter();
    }

    private void applyFilter() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase();
        List<MemberRow> shown = all.stream()
                .filter(row -> term.isEmpty() || row.fullName().toLowerCase().contains(term)
                        || row.dni().contains(term))
                .toList();
        grid.setItems(shown);
        count.setText(!term.isEmpty() && shown.size() != all.size()
                ? "%d de %d socios".formatted(shown.size(), all.size())
                : shown.size() == 1 ? "1 socio" : "%d socios".formatted(shown.size()));
    }
}
