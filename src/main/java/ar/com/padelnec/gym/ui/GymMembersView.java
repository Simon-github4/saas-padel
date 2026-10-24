package ar.com.padelnec.gym.ui;

import static ar.com.padelnec.gym.ui.GymViewSupport.SHORT_DAY;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.EntryLimit;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.domain.PlanLength;
import ar.com.padelnec.gym.service.GymAllowance;
import ar.com.padelnec.gym.service.GymBillingService;
import ar.com.padelnec.gym.service.GymBillingService.Period;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymCheckinService.CheckInResult;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymMemberService.DeletionImpact;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymMembershipService.Fee;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymOverviewService.MemberRow;
import ar.com.padelnec.gym.service.GymOverviewService.Standing;
import ar.com.padelnec.gym.service.GymSedeService;
import ar.com.padelnec.gym.service.GymTariffService;
import ar.com.padelnec.web.BusinessRuleException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    /** Los socios con la cuota de hoy paga, de todo el club (no cambia con la busqueda). */
    private final Span active = new Span();
    /** Filtro por situacion; vacio = todos. Cada opcion muestra cuantos socios tiene. */
    private final Select<Standing> standing = new Select<>();
    private final Map<Standing, Long> standingCounts = new EnumMap<>(Standing.class);
    /** Mientras se recargan las opciones del filtro: sus cambios de valor no son del mostrador. */
    private boolean offeringStandings;

    private List<MemberRow> all = List.of();
    private LocalDate today = LocalDate.now();
    /** El club pide clave ademas del DNI: recien ahi tienen sentido las claves temporales. */
    private boolean passwordRequired;
    /**
     * Hay socios contados por semana y otros por mes (el club cambio de modo y cada uno pasa con su
     * proxima cuota): la columna de ingresos dice de cual es cada cuenta.
     */
    private boolean mixedLimits;
    private Grid.Column<MemberRow> usageColumn;

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
        active.getElement().getThemeList().add("badge success");
        Tooltip.forComponent(active).setText("Socios con la cuota de hoy paga, mensual o pase de 15 días. "
                + "No cuenta a los que deben el mes, a los que todavía no empezaron ni a los deshabilitados.");

        Button newMember = new Button("Nuevo socio", VaadinIcon.PLUS.create(), event -> openNewMember());
        newMember.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        standing.setAriaLabel("Filtrar socios por situación");
        standing.setEmptySelectionAllowed(true);
        standing.setItemLabelGenerator(option -> option == null ? "Todos (" + all.size() + ")"
                : option.label() + " (" + standingCounts.getOrDefault(option, 0L) + ")");
        standing.addThemeName("small");
        standing.setWidth("13em");
        standing.addValueChangeListener(event -> {
            if (offeringStandings) {
                return;
            }
            // Una opcion que quedo en cero sale del menu recien cuando se elige otra.
            offerStandings();
            applyFilter();
        });

        HorizontalLayout left = new HorizontalLayout(search, standing, count, active);
        left.setAlignItems(Alignment.CENTER);
        left.setPadding(false);
        // En una pantalla angosta la barra baja de renglon en vez de correr la pagina de costado.
        left.setWrap(true);

        HorizontalLayout toolbar = new HorizontalLayout(left, newMember);
        toolbar.setWrap(true);
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
        usageColumn = grid.addComponentColumn(this::usageCell).setHeader("Esta semana").setAutoWidth(true)
                .setFlexGrow(0);
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
            Tooltip.forComponent(dni).setText("Sin DNI no puede entrar a la app. Cargáselo en «Editar datos».");
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
            Tooltip.forComponent(temp).setText("Todavía no cambió la clave que le dieron en el mostrador");
            cell.add(temp);
        }
        return cell;
    }

    private Component membershipCell(MemberRow row) {
        GymBillingService.Status billing = row.billing();
        if (billing.plan() == null) {
            return GymViewSupport.badge(billing.anchor() == null ? "Sin cuota" : "Sin cuota vigente", "contrast");
        }
        if (billing.passRules()) {
            return passCell(billing);
        }
        if (!billing.started()) {
            // El inicio del ciclo, no el de la cuota activa: puede ser una vieja ya vencida.
            return GymViewSupport.badge("Empieza el " + SHORT_DAY.format(billing.periodStart()), "contrast");
        }
        if (billing.monthsLate() >= 2) {
            return GymViewSupport.badge("Adeudás " + billing.monthsLate() + " cuotas", "error");
        }
        if (billing.paidCurrent()) {
            LocalDate until = billing.paidUntil() != null ? billing.paidUntil() : billing.periodEnd();
            Span paid = GymViewSupport.badge("Al día hasta " + SHORT_DAY.format(until), "success");
            return billing.gap() == null ? paid : withGapWarning(paid, billing);
        }
        return GymViewSupport.badge("Debe el mes", "contrast");
    }

    /**
     * El que se rige por un pase: solo cuenta la cobertura. Al día, por empezar o vencido (en gris:
     * un pase vencido no es deuda). Si lo pago de hoy deja un hueco antes de otra cuota, se avisa.
     */
    private static Component passCell(GymBillingService.Status billing) {
        // Una sola etiqueta, que entra en la columna. "Pase hasta" es el pase de hoy y nada despues;
        // si lo sigue otra cuota pegada, "Al día hasta" el fin de esa cobertura.
        if (billing.canEnter()) {
            boolean onlyThePass = billing.active().isPass() && billing.paidUntil().equals(billing.active().getEndsOn());
            Span paid = GymViewSupport.badge((onlyThePass ? "Pase hasta " : "Al día hasta ")
                    + SHORT_DAY.format(billing.paidUntil()), "success");
            return billing.gap() == null ? paid : withGapWarning(paid, billing);
        }
        if (!billing.started()) {
            return GymViewSupport.badge((billing.active().isPass() ? "Pase desde " : "Empieza el ")
                    + SHORT_DAY.format(billing.active().getStartsOn()), "contrast");
        }
        return GymViewSupport.badge("Pase vencido el " + SHORT_DAY.format(billing.active().getEndsOn()), "contrast");
    }

    /**
     * "Al día" con un aviso: quedan días sin cuota antes de lo que ya tiene pago más adelante (por
     * un nuevo inicio de mes, o un pase que arranca después), y esos días no entra. El detalle va
     * en el tooltip.
     */
    private static Component withGapWarning(Span paid, GymBillingService.Status billing) {
        Period gap = billing.gap();
        Period ahead = billing.paidAhead();
        String detail = gapDays(gap) + " no tiene cuota: " + (oneDay(gap) ? "ese día" : "esos días")
                + " no puede entrar. Ya tiene pago del " + SHORT_DAY.format(ahead.start()) + " al "
                + SHORT_DAY.format(ahead.end()) + (billing.onPasses() ? "." : " por un nuevo inicio de mes.");
        Span warning = new Span(VaadinIcon.WARNING.create());
        warning.addClassNames("gap-warning", LumoUtility.TextColor.WARNING);
        warning.getElement().setAttribute("aria-label", detail);
        warning.getElement().setAttribute("role", "img");
        Tooltip.forComponent(warning).setText(detail);

        HorizontalLayout cell = new HorizontalLayout(paid, warning);
        cell.setPadding(false);
        cell.setAlignItems(Alignment.CENTER);
        cell.addClassNames(LumoUtility.Gap.XSMALL);
        return cell;
    }

    private Component usageCell(MemberRow row) {
        // Con un pase que corre y el plan mensual por empezar, el ciclo no arranco pero entra.
        if (row.billing().plan() == null || (!row.billing().started() && !row.billing().canEnter())) {
            return new Span("—");
        }
        // El tope de la cuota que rige hoy, no el de un adelanto con otros días.
        GymAllowance allowance = row.allowance();
        Span usage = new Span(row.used() + " de " + allowance.limit()
                + (mixedLimits ? " · " + windowName(allowance.window()) : ""));
        if (row.used() >= allowance.limit()) {
            usage.getElement().getThemeList().add("badge contrast small");
        }
        if (allowance.window() != GymAllowance.Window.WEEK) {
            // Cada socio tiene su mes: se dice de qué días es la cuenta.
            String window = allowance.window() == GymAllowance.Window.PASS ? "Su pase" : "Su mes";
            Tooltip.forComponent(usage).setText(window + ": del " + SHORT_DAY.format(allowance.from())
                    + " al " + SHORT_DAY.format(allowance.to()));
        }
        return usage;
    }

    private static String windowName(GymAllowance.Window window) {
        return switch (window) {
            case WEEK -> "semana";
            case MONTH -> "mes";
            case PASS -> "pase";
        };
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
        menu.addItem("Cuotas cobradas", event -> openFees(row));
        menu.addItem(row.enabled() ? "Deshabilitar" : "Habilitar", event -> toggleEnabled(row));
        menu.addItem("Eliminar socio", event -> confirmDelete(row));

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

        GymBillingService.Status billing = row.billing();
        // El que viene con pases no tiene ciclo mensual: si pasa al mensual, arranca uno nuevo
        // despues de lo pago (o hoy). Es tambien desde cuando puede arrancar un pase nuevo.
        boolean fromPasses = billing.onPasses();
        LocalDate nextFree = billingService.nextStart(billing.member(), today);
        GymBillingService.Status passesToMonth = fromPasses
                ? billingService.status(billing.member(), today, nextFree) : null;
        // Mutable: si el mostrador arranca el mes de nuevo, las cuotas salen de otro ciclo.
        GymBillingService.Status[] cycleOf = {fromPasses ? passesToMonth : billing};
        boolean firstCharge = billing.anchor() == null && !fromPasses;

        RadioButtonGroup<PlanLength> planType = new RadioButtonGroup<>("Plan");
        planType.setItems(PlanLength.values());
        planType.setItemLabelGenerator(PlanLength::label);
        planType.setValue(fromPasses ? PlanLength.FIFTEEN_DAYS : PlanLength.MONTH);

        // A donde apunta el ciclo, para que el mostrador sepa que esta cobrando
        String cycleText;
        if (fromPasses) {
            cycleText = "Viene con pases de 15 días. Si pasa al plan mensual, su mes arranca el "
                    + SHORT_DAY.format(nextFree) + ".";
        } else if (firstCharge) {
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
            // La próxima es la que sigue a la corriente; si el ciclo todavía no empezó (un nuevo
            // inicio a futuro), es su primera cuota.
            LocalDate nextStart = cycleOf[0].periodStart().isAfter(today)
                    ? cycleOf[0].periodStart()
                    : cycleOf[0].periodEnd().plusDays(1);
            String state = period.end().isBefore(today) ? "Vencida"
                    : !today.isBefore(period.start()) ? "Corriente"
                    : period.start().equals(nextStart) ? "Próxima" : "Adelanto";
            return state + " · del " + SHORT_DAY.format(period.start()) + " al "
                    + SHORT_DAY.format(period.end());
        });
        showPeriods(periods, cycleOf[0].pending());
        periods.setWidthFull();

        // Vacío, el ciclo sigue como venía. Con una fecha, el mes arranca de nuevo ese día:
        // el socio que ya venía se carga con su mes real, y el que vuelve después de un
        // tiempo no arrastra los meses que no vino. No puede pisar lo que ya tiene pago.
        DatePicker newStart = new DatePicker("Nuevo inicio de mes (opcional)");
        LocalDate earliest = today.minusYears(1);
        if (billing.plan() != null && billing.plan().getEndsOn().isAfter(earliest)) {
            earliest = billing.plan().getEndsOn().plusDays(1);
        }
        newStart.setI18n(GymViewSupport.spanishDates());
        newStart.setMin(earliest);
        newStart.setMax(today.plusYears(1));
        newStart.setClearButtonVisible(true);
        newStart.setHelperText(fromPasses
                ? "Del pase al mensual el mes arranca de nuevo: por defecto, el día después de lo pago."
                : firstCharge
                ? "Vacío: arranca hoy. Si el socio ya venía, poné el día en que empezó su mes."
                : "Vacío: sigue su ciclo como venía. Con una fecha, su mes arranca de nuevo ese día.");
        newStart.setWidthFull();
        if (fromPasses) {
            newStart.setValue(nextFree);
        }

        // El pase vale 15 dias desde que arranca, sin fechas para atras: lo cobrado otro dia va
        // en "Fecha de cobro". Por defecto arranca el dia despues de lo pago, o hoy.
        DatePicker passFrom = new DatePicker("Desde");
        passFrom.setI18n(GymViewSupport.spanishDates());
        passFrom.setMin(nextFree);
        passFrom.setMax(today.plusYears(1));
        passFrom.setValue(nextFree);
        passFrom.setWidthFull();
        Runnable passRange = () -> passFrom.setHelperText(passFrom.getValue() == null ? null
                : "Vale del " + SHORT_DAY.format(passFrom.getValue()) + " al "
                        + SHORT_DAY.format(passFrom.getValue().plusDays(PlanLength.PASS_DAYS - 1))
                        + ". Cuando vence no queda debiendo nada.");
        passRange.run();
        passFrom.addValueChangeListener(event -> passRange.run());

        // Pasar a un pase deja de cobrar lo que debe del plan mensual: el mostrador lo confirma.
        int owedMonths = GymBillingService.owedMonths(billing, today);
        Checkbox dropDebt = new Checkbox(owedMonths == 1
                ? "Dejar de cobrar la cuota mensual impaga"
                : "Dejar de cobrar las " + owedMonths + " cuotas mensuales impagas");

        // Un nuevo inicio después del día siguiente a lo pago deja días sin cuota, y esos días no
        // entra: se avisa antes de cobrar, por si el socio sí va a venir.
        Span gapWarning = new Span();
        gapWarning.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Background.WARNING_10,
                LumoUtility.Padding.SMALL, LumoUtility.BorderRadius.MEDIUM);
        gapWarning.setWidthFull();
        gapWarning.setVisible(false);

        // La fecha de cobro es aparte del período: un pago de otro día va a la caja de ese día.
        DatePicker paidOn = new DatePicker("Fecha de cobro");
        paidOn.setI18n(GymViewSupport.spanishDates());
        paidOn.setValue(today);
        paidOn.setMax(today);
        paidOn.setWidthFull();

        BigDecimalField price = new BigDecimalField();
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
        if (!newPlan) {
            days.setHelperText("Vale para las cuotas que cobrás ahora. Para corregir una ya cobrada: "
                    + "menú ⋮ → «Cuotas cobradas».");
        }

        // Con el tope por mes, cuántos ingresos le dan esos días por semana en lo que se cobra. Si
        // hoy se le cuenta por semana, es el momento de avisarle que con esta cuota cambia.
        boolean monthlyLimit = gymModule.entryLimit() == EntryLimit.MONTH;
        boolean switchesToMonthly = monthlyLimit && billing.active() != null
                && billing.active().getEntryLimit() == EntryLimit.WEEK;
        Span entries = new Span();
        entries.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        entries.setVisible(false);

        // La tarifa aparece como sugerencia (placeholder), no como monto cargado:
        // cambia solo si cambian los días por semana o el plan. El total usa la tarifa
        // cuando el mostrador no tipea el monto a mano.
        Runnable refresh = () -> {
            BigDecimal tariff = tariffFor(planType.getValue(), days);
            price.setPlaceholder(tariff == null ? null : GymViewSupport.amount(tariff));
            updateTotal(total, price, planType.getValue(), periods, tariff);
            if (monthlyLimit) {
                String text = entriesText(planType.getValue(), effectiveDpw(days), periods.getSelectedItems());
                if (switchesToMonthly && !text.isEmpty()) {
                    text = "Desde esta cuota su tope se cuenta por mes. " + text;
                }
                entries.setText(text);
                entries.setVisible(!text.isEmpty());
            }
        };
        days.addValueChangeListener(event -> refresh.run());
        newStart.addValueChangeListener(event -> {
            LocalDate start = event.getValue();
            cycleOf[0] = start != null ? billingService.status(row.billing().member(), today, start)
                    : fromPasses ? passesToMonth : row.billing();
            if (fromPasses) {
                cycle.setText("Pasa al plan mensual: su mes arranca el "
                        + SHORT_DAY.format(start != null ? start : nextFree) + ".");
            } else if (start == null) {
                cycle.setText(cycleText);
            } else if (firstCharge) {
                cycle.setText("Todavía no pagó ninguna cuota. Este primer pago arranca su ciclo el "
                        + SHORT_DAY.format(start) + ".");
            } else {
                cycle.setText("Su mes arranca de nuevo el " + SHORT_DAY.format(start)
                        + ": los meses de antes sin pagar ya no se cobran.");
            }
            Period gap = GymBillingService.gapBefore(billing.plan(), today, start);
            gapWarning.setVisible(gap != null);
            if (gap != null) {
                gapWarning.setText(gapText(gap));
            }
            showPeriods(periods, cycleOf[0].pending());
        });
        periods.addValueChangeListener(event -> refresh.run());
        price.addValueChangeListener(event -> refresh.run());

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

        // Días por semana y monto, en una sola fila.
        HorizontalLayout plan = new HorizontalLayout(days, price);
        plan.setWidthFull();
        plan.setPadding(false);
        plan.setFlexGrow(1, days, price);

        // Mensual: el ciclo y sus cuotas. 15 días: desde cuándo, y el aviso de la deuda.
        Runnable showPlan = () -> {
            boolean pass = planType.getValue() == PlanLength.FIFTEEN_DAYS;
            cycle.setVisible(!pass);
            newStart.setVisible(!pass);
            gapWarning.setVisible(!pass
                    && GymBillingService.gapBefore(billing.plan(), today, newStart.getValue()) != null);
            periods.setVisible(!pass);
            passFrom.setVisible(pass);
            dropDebt.setVisible(pass && owedMonths > 0);
            price.setLabel(pass ? "Monto del pase (opcional: usa la tarifa)"
                    : "Monto por cuota (opcional: usa la tarifa)");
            refresh.run();
        };
        planType.addValueChangeListener(event -> showPlan.run());
        showPlan.run();

        VerticalLayout body = new VerticalLayout(planType, cycle, newStart, gapWarning, periods, passFrom, dropDebt,
                plan, entries, total, sedeGroup, payment);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Registrar cobro", event -> {
            boolean pass = planType.getValue() == PlanLength.FIFTEEN_DAYS;
            Set<Period> selected = periods.getSelectedItems();
            if (!pass && selected.isEmpty()) {
                GymViewSupport.error("Elegí al menos una cuota.");
                return;
            }
            if (pass && days.getValue() == null) {
                GymViewSupport.error("Poné los días por semana.");
                return;
            }
            if (pass && owedMonths > 0 && !dropDebt.getValue()) {
                GymViewSupport.error("Debe cuotas del plan mensual: cobralas antes, o tildá que dejan de cobrarse.");
                return;
            }
            Set<UUID> sedeIds = sedeGroup.getValue().stream().map(GymSede::getId).collect(Collectors.toSet());
            UUID collectedSedeId = collectedAt == null ? sedeIds.iterator().next() : collectedAt.getValue().getId();
            UUID userId = GymViewSupport.currentUserId(authenticationContext).orElse(null);
            try {
                if (pass) {
                    membershipService.chargePass(row.id(), passFrom.getValue(), price.getValue(), days.getValue(),
                            method.getValue(), collectedSedeId, sedeIds, userId, today, paidOn.getValue(),
                            dropDebt.getValue());
                } else {
                    membershipService.charge(row.id(), selected.size(), price.getValue(),
                            days.getValue(), method.getValue(),
                            collectedSedeId, sedeIds, userId, today,
                            newStart.getValue(), paidOn.getValue());
                }
                dialog.close();
                refresh();
                GymViewSupport.ok(pass ? "Pase de 15 días registrado"
                        : selected.size() == 1 ? "Cuota registrada" : selected.size() + " cuotas registradas");
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
        if (pending.isEmpty()) {
            return;
        }
        List<Period> owed = pending.stream().filter(period -> !period.start().isAfter(today)).toList();
        periods.select(owed.isEmpty() ? Set.of(pending.getFirst()) : Set.copyOf(owed));
    }

    /**
     * Con el tope por mes: los ingresos que dan esos días por semana en el pase, o en cada cuota
     * elegida (dependen de los días de cada período). Vacío si todavía no hay días o cuotas.
     */
    private static String entriesText(PlanLength length, int daysPerWeek, Set<Period> selected) {
        if (daysPerWeek < 1) {
            return "";
        }
        if (length == PlanLength.FIFTEEN_DAYS) {
            return "Son " + GymAllowance.proportional(daysPerWeek, PlanLength.PASS_DAYS)
                    + " ingresos en los 15 días del pase.";
        }
        List<Period> chosen = selected.stream().sorted(Comparator.comparing(Period::start)).toList();
        if (chosen.isEmpty()) {
            return "";
        }
        if (chosen.size() == 1) {
            Period period = chosen.getFirst();
            return "Son " + entriesIn(daysPerWeek, period) + " ingresos del " + SHORT_DAY.format(period.start())
                    + " al " + SHORT_DAY.format(period.end()) + ".";
        }
        return "Ingresos de cada mes: " + chosen.stream()
                .map(period -> entriesIn(daysPerWeek, period) + " del " + SHORT_DAY.format(period.start())
                        + " al " + SHORT_DAY.format(period.end()))
                .collect(Collectors.joining(" · ")) + ".";
    }

    private static int entriesIn(int daysPerWeek, Period period) {
        return GymAllowance.proportional(daysPerWeek, ChronoUnit.DAYS.between(period.start(), period.end()) + 1);
    }

    /** El aviso de los días que quedan sin cuota entre lo pago y el nuevo inicio de mes. */
    private static String gapText(Period gap) {
        return gapDays(gap) + " queda sin cuota: " + (oneDay(gap) ? "ese día" : "esos días")
                + " no va a poder entrar. Si va a venir, poné el " + SHORT_DAY.format(gap.start())
                + " como inicio de mes.";
    }

    /** Los días de un hueco: "El 11/11" o "Del 11/11 al 14/11". */
    private static String gapDays(Period gap) {
        return oneDay(gap)
                ? "El " + SHORT_DAY.format(gap.start())
                : "Del " + SHORT_DAY.format(gap.start()) + " al " + SHORT_DAY.format(gap.end());
    }

    private static boolean oneDay(Period gap) {
        return gap.start().equals(gap.end());
    }

    /** Los días por semana que definen la tarifa: los que muestra el campo. */
    private static int effectiveDpw(IntegerField days) {
        return days.getValue() == null ? 0 : days.getValue();
    }

    /** La tarifa que corresponde a ese plan: null si no está fijada para esos días. */
    private BigDecimal tariffFor(PlanLength length, IntegerField days) {
        int dpw = effectiveDpw(days);
        return dpw > 0 ? tariffService.priceOf(length, dpw) : null;
    }

    /**
     * Total = monto por cuota x cuotas (el pase es uno solo); si no se tipeó el monto, se usa
     * la tarifa.
     */
    private void updateTotal(TextField total, BigDecimalField price, PlanLength length,
                             CheckboxGroup<Period> periods, BigDecimal tariffUnit) {
        BigDecimal unit = price.getValue() != null ? price.getValue() : tariffUnit;
        int count = length == PlanLength.FIFTEEN_DAYS ? 1 : periods.getSelectedItems().size();
        if (unit == null || count == 0) {
            total.setValue("");
            return;
        }
        BigDecimal sum = unit.multiply(BigDecimal.valueOf(count));
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

        Paragraph note = new Paragraph("Sirve para cuando el socio no puede escanear. Si ya usó "
                + switch (row.allowance().window()) {
                    case WEEK -> "los días de esta semana";
                    case MONTH -> "los ingresos de su mes";
                    case PASS -> "los ingresos de su pase";
                }
                + ", queda registrado como excepción.");
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
                        : "Ingreso registrado en " + result.sedeName() + " · " + result.used() + " de "
                                + result.limit() + switch (result.window()) {
                                    case WEEK -> " esta semana";
                                    case MONTH -> " este mes";
                                    case PASS -> " del pase";
                                });
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
    }

    /**
     * Las cuotas cobradas del socio, para corregir un error de carga: cambiar días por
     * semana y monto sin mover el período, o anular la que se cobró de más.
     */
    private void openFees(MemberRow row) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Cuotas cobradas · " + row.fullName());
        dialog.setWidth("50em");

        Paragraph note = new Paragraph("Corregí los días por semana o el monto de una cuota sin cambiar sus "
                + "fechas, o anulá la que se cobró de más: deja de valer y sale de la caja. Las anuladas "
                + "quedan tachadas, de constancia.");
        note.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Grid<Fee> fees = new Grid<>();
        fees.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        fees.setSelectionMode(Grid.SelectionMode.NONE);
        fees.addComponentColumn(fee -> feeCell(fee,
                        SHORT_DAY.format(fee.startsOn()) + " al " + SHORT_DAY.format(fee.endsOn())))
                .setHeader("Período").setAutoWidth(true);
        fees.addComponentColumn(fee -> feeCell(fee, fee.planLength().label()))
                .setHeader("Plan").setAutoWidth(true).setFlexGrow(0);
        fees.addComponentColumn(fee -> feeCell(fee, String.valueOf(fee.daysPerWeek())))
                .setHeader("Días").setAutoWidth(true).setFlexGrow(0);
        fees.addComponentColumn(fee -> feeCell(fee, fee.entryLimit().label()))
                .setHeader("Tope").setAutoWidth(true).setFlexGrow(0);
        fees.addComponentColumn(fee -> feeCell(fee, GymViewSupport.money(fee.price())))
                .setHeader("Monto").setAutoWidth(true);
        fees.addComponentColumn(fee -> feeCell(fee, SHORT_DAY.format(fee.paidOn()) + " · " + fee.payMethod().label()))
                .setHeader("Cobrada").setAutoWidth(true);
        Runnable reload = () -> fees.setItems(membershipService.feesOf(row.id()));
        Runnable changed = () -> {
            reload.run();
            refresh();
        };
        fees.addComponentColumn(fee -> {
            if (fee.voided()) {
                return GymViewSupport.badge("Anulada el " + SHORT_DAY.format(fee.voidedOn()), "error small");
            }
            Button correct = new Button("Corregir", event -> openCorrectFee(fee, changed));
            correct.addThemeVariants(ButtonVariant.LUMO_SMALL);
            Button cancel = new Button("Anular", event -> confirmVoidFee(fee, changed));
            cancel.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            HorizontalLayout actions = new HorizontalLayout(correct, cancel);
            actions.setPadding(false);
            return actions;
        }).setAutoWidth(true).setFlexGrow(0);
        fees.setEmptyStateText("No tiene cuotas cobradas.");
        fees.setAllRowsVisible(true);
        reload.run();

        VerticalLayout body = new VerticalLayout(note, fees);
        body.setPadding(false);
        dialog.add(body);
        dialog.getFooter().add(new Button("Cerrar", event -> dialog.close()));
        dialog.open();
    }

    /** Un dato de la cuota; si está anulada, tachado y apagado. */
    private static Span feeCell(Fee fee, String text) {
        Span cell = new Span(text);
        if (fee.voided()) {
            cell.getStyle().set("text-decoration", "line-through");
            cell.addClassNames(LumoUtility.TextColor.TERTIARY);
        }
        return cell;
    }

    private void openCorrectFee(Fee fee, Runnable onSaved) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Corregir cuota del " + SHORT_DAY.format(fee.startsOn()) + " al "
                + SHORT_DAY.format(fee.endsOn()));

        IntegerField days = new IntegerField("Días por semana");
        days.setMin(1);
        days.setMax(7);
        days.setValue(fee.daysPerWeek());
        days.setStepButtonsVisible(true);
        BigDecimalField price = new BigDecimalField("Monto");
        price.setPrefixComponent(new Span("$"));
        price.setValue(fee.price());
        // Al cambiar los días, propone la tarifa de esos días (se puede pisar a mano).
        days.addValueChangeListener(event -> {
            BigDecimal tariff = tariffFor(fee.planLength(), days);
            if (tariff != null) {
                price.setValue(tariff);
            }
        });

        HorizontalLayout fields = new HorizontalLayout(days, price);
        fields.setWidthFull();
        fields.setPadding(false);
        fields.setFlexGrow(1, days, price);
        Paragraph note = new Paragraph("Las fechas de la cuota no cambian.");
        note.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        VerticalLayout body = new VerticalLayout(fields, note);
        body.setPadding(false);
        dialog.add(body);

        Button save = new Button("Guardar", event -> {
            if (days.getValue() == null) {
                GymViewSupport.error("Poné los días por semana.");
                return;
            }
            try {
                membershipService.correct(fee.id(), days.getValue(), price.getValue());
                dialog.close();
                onSaved.run();
                GymViewSupport.ok("Cuota corregida");
            } catch (BusinessRuleException ex) {
                GymViewSupport.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), save);
        dialog.open();
    }

    private void confirmVoidFee(Fee fee, Runnable onVoided) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Anular la cuota del " + SHORT_DAY.format(fee.startsOn()) + " al "
                + SHORT_DAY.format(fee.endsOn()));
        GymMembershipService.VoidEffect effect = membershipService.voidEffect(fee.id());
        if (!effect.allowed()) {
            dialog.add(new Paragraph(effect.message()));
            dialog.getFooter().add(new Button("Cerrar", event -> dialog.close()));
            dialog.open();
            return;
        }
        dialog.add(new Paragraph("Deja de valer para entrar y el cobro de " + GymViewSupport.money(fee.price())
                + " sale de la caja del " + SHORT_DAY.format(fee.paidOn()) + ". No se puede deshacer."));
        if (effect.message() != null) {
            Paragraph warning = new Paragraph(effect.message());
            warning.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
            dialog.add(warning);
        }

        Button confirm = new Button("Anular cuota", event -> {
            try {
                membershipService.voidMembership(fee.id());
                dialog.close();
                onVoided.run();
                GymViewSupport.ok("Cuota anulada");
            } catch (BusinessRuleException ex) {
                dialog.close();
                GymViewSupport.error(ex.getMessage());
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), confirm);
        dialog.open();
    }

    private void confirmDelete(MemberRow row) {
        DeletionImpact impact = memberService.deletionImpact(row.id());
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Eliminar a " + row.fullName());

        if (impact.hasCheckins()) {
            // Con ingresos no se elimina: se ofrece directamente lo que sí se puede hacer.
            dialog.add(new Paragraph("Ya registró ingresos: no se puede eliminar sin perder sus visitas y sus "
                    + "cobros. Si no viene más, deshabilitalo: deja de poder entrar y conserva su historia."));
            Button disable = new Button("Deshabilitar", event -> {
                memberService.setEnabled(row.id(), false);
                dialog.close();
                refresh();
                GymViewSupport.ok(row.fullName() + " deshabilitado");
            });
            disable.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            disable.setEnabled(row.enabled());
            dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), disable);
            dialog.open();
            return;
        }

        String fees = impact.fees() == 0
                ? "No tiene cuotas cobradas."
                : "Se borra" + (impact.fees() == 1 ? " 1 cuota" : "n " + impact.fees() + " cuotas") + " por "
                        + GymViewSupport.money(impact.total()) + ", que sale" + (impact.fees() == 1 ? "" : "n")
                        + " de la caja " + cashDays(impact.paidDays()) + ".";
        Paragraph impactLine = new Paragraph(fees);
        impactLine.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
        Paragraph note = new Paragraph("Es para un socio cargado por error o repetido. No se puede deshacer.");
        note.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        VerticalLayout body = new VerticalLayout(impactLine, note);
        body.setPadding(false);
        dialog.add(body);

        Button confirm = new Button("Eliminar", event -> {
            try {
                memberService.delete(row.id());
                dialog.close();
                refresh();
                GymViewSupport.ok(row.fullName() + " eliminado");
            } catch (BusinessRuleException ex) {
                dialog.close();
                GymViewSupport.error(ex.getMessage());
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button("Cancelar", event -> dialog.close()), confirm);
        dialog.open();
    }

    /** "del 22/09", "del 20/09 y 22/09" o "del 18/09, 20/09 y 22/09". */
    private static String cashDays(List<LocalDate> days) {
        List<String> labels = days.stream().map(SHORT_DAY::format).toList();
        String joined = labels.size() == 1 ? labels.getFirst()
                : String.join(", ", labels.subList(0, labels.size() - 1)) + " y " + labels.getLast();
        return "del " + joined;
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
        // Todos por semana, todos por mes (o pase), o mezclados mientras el club cambia de modo.
        Set<GymAllowance.Window> windows = all.stream().filter(row -> row.billing().plan() != null)
                .map(row -> row.allowance().window()).collect(Collectors.toSet());
        boolean anyWeek = windows.contains(GymAllowance.Window.WEEK);
        mixedLimits = anyWeek && windows.size() > 1;
        usageColumn.setHeader(mixedLimits ? "Ingresos" : anyWeek || windows.isEmpty() ? "Esta semana" : "Este mes");
        standingCounts.clear();
        all.forEach(row -> standingCounts.merge(row.standing(), 1L, Long::sum));
        long paid = standingCounts.getOrDefault(Standing.ACTIVE, 0L);
        active.setText(paid == 1 ? "1 activo" : paid + " activos");
        standing.setEmptySelectionCaption("Todos (" + all.size() + ")");
        offerStandings();
        applyFilter();
    }

    /**
     * El menu del filtro ofrece solo las situaciones que tienen socios, con sus cantidades. La
     * elegida se queda aunque llegue a cero (no se le cambia el filtro al mostrador en la cara) y
     * se va cuando elige otra. Cargar las opciones de nuevo vacia el valor: se vuelve a poner.
     */
    private void offerStandings() {
        Standing chosen = standing.getValue();
        offeringStandings = true;
        try {
            standing.setItems(Arrays.stream(Standing.values())
                    .filter(option -> option == chosen || standingCounts.getOrDefault(option, 0L) > 0)
                    .toList());
            standing.setValue(chosen);
        } finally {
            offeringStandings = false;
        }
    }

    private void applyFilter() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase();
        Standing only = standing.getValue();
        List<MemberRow> shown = all.stream()
                .filter(row -> only == null || row.standing() == only)
                .filter(row -> term.isEmpty() || row.fullName().toLowerCase().contains(term)
                        || (row.dni() != null && row.dni().contains(term)))
                .toList();
        grid.setItems(shown);
        grid.setEmptyStateText(term.isEmpty() && only == null
                ? "Todavía no hay socios. Tocá «Nuevo socio» para dar de alta al primero."
                : "Ningún socio coincide con la búsqueda o el filtro.");
        count.setText((!term.isEmpty() || only != null) && shown.size() != all.size()
                ? "%d de %d socios".formatted(shown.size(), all.size())
                : shown.size() == 1 ? "1 socio" : "%d socios".formatted(shown.size()));
    }
}
