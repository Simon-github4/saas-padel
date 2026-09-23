package ar.com.padelnec.gym.ui;

import static ar.com.padelnec.gym.ui.GymViewSupport.SHORT_DAY;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.service.GymBillingService;
import ar.com.padelnec.gym.service.GymBillingService.Period;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymCheckinService.CheckInResult;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymOverviewService.MemberRow;
import ar.com.padelnec.gym.service.GymSedeService;
import ar.com.padelnec.gym.service.GymTariffService;
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
import java.math.BigDecimal;
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
    private final GymTariffService tariffService;
    private final GymBillingService billingService;
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
                          GymTariffService tariffService, GymBillingService billingService,
                          AuthenticationContext authenticationContext) {
        this.gymModule = gymModule;
        this.memberService = memberService;
        this.membershipService = membershipService;
        this.checkinService = checkinService;
        this.sedeService = sedeService;
        this.overviewService = overviewService;
        this.tariffService = tariffService;
        this.billingService = billingService;
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
        Span dni = new Span(row.dni() == null ? "Sin DNI" : "DNI " + row.dni());
        dni.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        if (row.dni() == null) {
            dni.getElement().setAttribute("title", "Sin DNI no puede entrar a la app. Cargáselo en «Editar datos».");
        }
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
        GymBillingService.Status billing = row.billing();
        if (billing.plan() == null) {
            return GymViewSupport.badge(billing.anchor() == null ? "Sin cuota" : "Sin cuota vigente", "contrast");
        }
        if (!billing.started()) {
            return GymViewSupport.badge("Empieza el " + SHORT_DAY.format(billing.plan().getStartsOn()), "contrast");
        }
        if (billing.monthsLate() >= 2) {
            return GymViewSupport.badge("Adeudás " + billing.monthsLate() + " cuotas", "error");
        }
        if (billing.paidCurrent()) {
            LocalDate until = billing.paidUntil() != null ? billing.paidUntil() : billing.periodEnd();
            return GymViewSupport.badge("Al día hasta " + SHORT_DAY.format(until), "success");
        }
        return GymViewSupport.badge("Debe el mes", "contrast");
    }

    private Component weekCell(MemberRow row) {
        if (row.billing().plan() == null || !row.billing().started()) {
            return new Span("—");
        }
        Span usage = new Span(row.weekUsed() + " de " + row.billing().planDaysPerWeek());
        if (row.weekUsed() >= row.billing().planDaysPerWeek()) {
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
        menu.addItem(row.dni() == null ? "Cargar DNI y datos" : "Editar datos", event -> openEdit(row));
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

        // El DNI es opcional: los socios anotados sin DNI se cargan igual y se completa después.
        TextField dni = dniField("DNI (opcional)");
        TextField name = new TextField("Nombre y apellido");
        name.setRequired(true);
        name.setMaxLength(120);
        name.setWidthFull();
        TextField phone = new TextField("Teléfono (opcional)");
        phone.setMaxLength(25);
        phone.setWidthFull();

        VerticalLayout body = new VerticalLayout(name, dni, phone);
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
        name.focus();
    }

    /** DNI opcional: vacío se puede cargar después, pero sin DNI el socio no entra a la app. */
    private static TextField dniField(String label) {
        TextField dni = new TextField(label);
        dni.setMaxLength(12);
        dni.setHelperText("Solo números. Sin DNI no puede entrar a la app: lo podés cargar después.");
        dni.setWidthFull();
        return dni;
    }

    private void openSell(MemberRow row) {
        List<GymSede> sedes = sedeService.active();
        if (sedes.isEmpty()) {
            GymViewSupport.error("Primero creá una sede en «Sedes y QR».");
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Cobrar cuota · " + row.fullName());
        dialog.setWidth("65vw");
        dialog.setHeight("80vh");

        // Mutable: si el mostrador arranca el mes de nuevo, las cuotas salen de otro ciclo.
        GymBillingService.Status[] cycleOf = {row.billing()};
        GymBillingService.Status billing = row.billing();
        boolean firstCharge = billing.anchor() == null;

        // A donde apunta el ciclo, para que el mostrador sepa que esta cobrando
        String cycleText;
        if (firstCharge) {
            cycleText = "Todavía no pagó ninguna cuota. Este primer pago arranca su ciclo.";
        } else if (billing.monthsLate() >= 2) {
            cycleText = "Adeudás " + billing.monthsLate() + " cuotas: se cobran de la más vieja a la más nueva.";
        } else if (billing.monthsLate() == 1) {
            cycleText = "Le falta el mes corriente (vence el " + SHORT_DAY.format(billing.periodEnd()) + ").";
        } else {
            cycleText = "Está al día. Podés pagar de a una cuota o adelantar de a varias.";
        }
        Span cycle = new Span(cycleText);
        cycle.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        CheckboxGroup<Period> periods = new CheckboxGroup<>("Cuotas a cobrar");
        periods.setItemLabelGenerator(period -> {
            LocalDate nextStart = cycleOf[0].periodEnd().plusDays(1);
            String state = period.end().isBefore(today) ? "Vencida"
                    : !today.isBefore(period.start()) ? "Corriente"
                    : period.start().equals(nextStart) ? "Próxima" : "Adelanto";
            return state + " · del " + SHORT_DAY.format(period.start()) + " al "
                    + SHORT_DAY.format(period.end());
        });
        showPeriods(periods, billing.pending());
        periods.setWidthFull();

        // Vacío, el ciclo sigue como venía. Con una fecha, el mes arranca de nuevo ese día:
        // el socio que ya venía se carga con su mes real, y el que vuelve después de un
        // tiempo no arrastra los meses que no vino. No puede pisar lo que ya tiene pago.
        DatePicker newStart = new DatePicker("Nuevo inicio de mes (opcional)");
        LocalDate earliest = today.minusYears(1);
        if (billing.plan() != null && billing.plan().getEndsOn().isAfter(earliest)) {
            earliest = billing.plan().getEndsOn().plusDays(1);
        }
        newStart.setMin(earliest);
        newStart.setMax(today.plusYears(1));
        newStart.setClearButtonVisible(true);
        newStart.setHelperText(firstCharge
                ? "Vacío: arranca hoy. Si el socio ya venía, poné el día en que empezó su mes."
                : "Vacío: sigue su ciclo como venía. Con una fecha, su mes arranca de nuevo ese día.");
        newStart.setWidthFull();

        // La fecha de cobro es aparte del período: un pago de otro día va a la caja de ese día.
        DatePicker paidOn = new DatePicker("Fecha de cobro");
        paidOn.setValue(today);
        paidOn.setMax(today);
        paidOn.setWidthFull();

        BigDecimalField price = new BigDecimalField("Monto por cuota (opcional: usa la tarifa)");
        price.setPrefixComponent(new Span("$"));
        price.setWidthFull();

        TextField total = new TextField("Total a cobrar");
        total.setReadOnly(true);
        total.setWidthFull();
        total.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD,
                LumoUtility.TextColor.PRIMARY);

        // El socio que arranca define acá sus días por semana; el que ya pagó los ve
        // preseleccionados y puede cambiarlos: la próxima cuota marca el plan nuevo.
        boolean newPlan = billing.plan() == null;
        IntegerField days = new IntegerField("Días por semana");
        days.setMin(1);
        days.setMax(7);
        days.setValue(newPlan ? DEFAULT_DAYS_PER_WEEK : billing.planDaysPerWeek());
        days.setStepButtonsVisible(true);
        days.setWidthFull();

        // La tarifa aparece como sugerencia (placeholder), no como monto cargado:
        // cambia solo si cambian los días por semana. El total usa la tarifa
        // cuando el mostrador no tipea el monto a mano.
        Runnable refresh = () -> {
            BigDecimal tariff = tariffFor(days);
            price.setPlaceholder(tariff == null ? null : GymViewSupport.amount(tariff));
            updateTotal(total, price, periods, tariff);
        };
        days.addValueChangeListener(event -> refresh.run());
        newStart.addValueChangeListener(event -> {
            LocalDate start = event.getValue();
            cycleOf[0] = start == null ? row.billing() : billingService.status(row.billing().member(), today, start);
            if (start == null) {
                cycle.setText(cycleText);
            } else if (firstCharge) {
                cycle.setText("Todavía no pagó ninguna cuota. Este primer pago arranca su ciclo el "
                        + SHORT_DAY.format(start) + ".");
            } else {
                cycle.setText("Su mes arranca de nuevo el " + SHORT_DAY.format(start)
                        + ": los meses de antes sin pagar ya no se cobran.");
            }
            showPeriods(periods, cycleOf[0].pending());
        });
        periods.addValueChangeListener(event -> updateTotal(total, price, periods, tariffFor(days)));
        price.addValueChangeListener(event -> updateTotal(total, price, periods, tariffFor(days)));
        refresh.run();

        CheckboxGroup<GymSede> sedeGroup = new CheckboxGroup<>("Vale en");
        sedeGroup.setItems(sedes);
        sedeGroup.setItemLabelGenerator(GymSede::getName);
        sedeGroup.select(sedes);

        Select<PayMethod> method = new Select<>();
        method.setLabel("Cómo pagó");
        method.setItems(PayMethod.values());
        method.setItemLabelGenerator(PayMethod::label);
        method.setValue(PayMethod.CASH);
        method.setWidthFull();

        // Con una sola sede no se pregunta dónde se cobró: se asigna esa. Con varias, sí.
        final Select<GymSede> collectedAt;
        if (sedes.size() > 1) {
            collectedAt = new Select<>();
            collectedAt.setLabel("Cobrado en");
            collectedAt.setItems(sedes);
            collectedAt.setItemLabelGenerator(GymSede::getName);
            collectedAt.setValue(sedes.getFirst());
            collectedAt.setWidthFull();
        } else {
            collectedAt = null;
        }

        // Cómo, dónde y cuándo se cobró, en una sola fila.
        HorizontalLayout payment = new HorizontalLayout(method);
        if (collectedAt != null) {
            payment.add(collectedAt);
        }
        payment.add(paidOn);
        payment.setWidthFull();
        payment.setPadding(false);
        payment.getChildren().forEach(field -> payment.setFlexGrow(1, field));

        // Días por semana y monto por cuota, en una sola fila.
        HorizontalLayout plan = new HorizontalLayout(days, price);
        plan.setWidthFull();
        plan.setPadding(false);
        plan.setFlexGrow(1, days, price);

        VerticalLayout body = new VerticalLayout(cycle, newStart, periods, plan, total, sedeGroup, payment);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Registrar cobro", event -> {
            Set<Period> selected = periods.getSelectedItems();
            if (selected.isEmpty()) {
                GymViewSupport.error("Elegí al menos una cuota.");
                return;
            }
            Set<UUID> sedeIds = sedeGroup.getValue().stream().map(GymSede::getId).collect(Collectors.toSet());
            UUID collectedSedeId = collectedAt == null ? sedeIds.iterator().next() : collectedAt.getValue().getId();
            try {
                membershipService.charge(row.id(), selected.size(), price.getValue(),
                        days.getValue(), method.getValue(),
                        collectedSedeId, sedeIds,
                        GymViewSupport.currentUserId(authenticationContext).orElse(null), today,
                        newStart.getValue(), paidOn.getValue());
                dialog.close();
                refresh();
                GymViewSupport.ok(selected.size() == 1 ? "Cuota registrada"
                        : selected.size() + " cuotas registradas");
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
        price.focus();
    }

    /**
     * Carga las cuotas que se pueden cobrar. Por defecto se cobra la deuda entera (o la
     * próxima cuota si está al día): el mostrador suma los adelantos marcando cuotas a futuro.
     */
    private void showPeriods(CheckboxGroup<Period> periods, List<Period> pending) {
        periods.setItems(pending);
        List<Period> owed = pending.stream().filter(period -> !period.start().isAfter(today)).toList();
        periods.select(owed.isEmpty() ? Set.of(pending.getFirst()) : Set.copyOf(owed));
    }

    /** Los días por semana que definen la tarifa: los que muestra el campo. */
    private static int effectiveDpw(IntegerField days) {
        return days.getValue() == null ? 0 : days.getValue();
    }

    /** La tarifa que corresponde: null si no está fijada para esos días. */
    private BigDecimal tariffFor(IntegerField days) {
        int dpw = effectiveDpw(days);
        return dpw > 0 ? tariffService.priceOf(dpw) : null;
    }

    /** Total = monto por cuota x cuotas; si no se tipeó el monto, se usa la tarifa. */
    private void updateTotal(TextField total, BigDecimalField price, CheckboxGroup<Period> periods,
                             BigDecimal tariffUnit) {
        BigDecimal unit = price.getValue() != null ? price.getValue() : tariffUnit;
        if (unit == null || periods.getSelectedItems().isEmpty()) {
            total.setValue("");
            return;
        }
        BigDecimal sum = unit.multiply(BigDecimal.valueOf(periods.getSelectedItems().size()));
        total.setValue(GymViewSupport.money(sum));
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

        TextField dni = dniField("DNI");
        dni.setValue(row.dni() == null ? "" : row.dni());
        TextField name = new TextField("Nombre y apellido");
        name.setValue(row.fullName());
        name.setMaxLength(120);
        name.setWidthFull();
        TextField phone = new TextField("Teléfono");
        phone.setValue(row.phone() == null ? "" : row.phone());
        phone.setMaxLength(25);
        phone.setWidthFull();

        VerticalLayout body = new VerticalLayout(name, dni, phone);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Guardar", event -> {
            try {
                memberService.updateDetails(row.id(), dni.getValue(), name.getValue(), phone.getValue());
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
                        || (row.dni() != null && row.dni().contains(term)))
                .toList();
        grid.setItems(shown);
        count.setText(!term.isEmpty() && shown.size() != all.size()
                ? "%d de %d socios".formatted(shown.size(), all.size())
                : shown.size() == 1 ? "1 socio" : "%d socios".formatted(shown.size()));
    }
}
