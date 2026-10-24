package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymCheckin;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.service.TenantService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lo que muestra el panel: la grilla de socios, los ingresos y los cobros del dia. */
@Service
@RequiredArgsConstructor
public class GymOverviewService {

    /**
     * Cuantos dias para atras se traen de una vez los ingresos de todos los socios. Alcanza para la
     * semana y para el mes de cualquiera que hoy pueda entrar: un periodo dura 31 dias como mucho y
     * hoy esta adentro. Una ventana que arranca antes (un pase ya vencido) se cuenta aparte.
     */
    private static final int RECENT_DAYS = 31;

    private final GymMemberRepository memberRepository;
    private final GymMembershipRepository membershipRepository;
    private final GymCheckinRepository checkinRepository;
    private final GymBillingService billingService;
    private final TenantService tenantService;
    private final Clock clock;

    /**
     * Un socio con su estado de cuotas. {@code billing} es el ciclo completo del
     * socio: el periodo corriente, la deuda, si puede entrar y que le falta pagar.
     * {@code sedes} son las sedes donde vale la cuota que rige hoy. {@code used} son los ingresos
     * en la ventana del tope ({@code allowance}): la semana, el mes de la cuota o el pase.
     * {@code lastCheckin} es el ultimo dia en que vino, o null si nunca vino.
     */
    public record MemberRow(UUID id, String dni, String fullName, String phone, boolean enabled,
                            boolean mustChangePassword, int used, GymAllowance allowance,
                            GymBillingService.Status billing, List<String> sedes, LocalDate lastCheckin) {

        /** En que situacion esta hoy, para los filtros del panel. Cada socio cae en una sola. */
        public Standing standing() {
            if (!enabled) {
                return Standing.DISABLED;
            }
            if (billing.plan() == null) {
                return Standing.NO_FEE;
            }
            if (billing.paidToday()) {
                return Standing.ACTIVE;
            }
            if (!billing.started() && !billing.canEnter()) {
                return Standing.UPCOMING;
            }
            // Un pase vencido no es deuda; lo que queda es el mensual que debe el mes o mas.
            return billing.passRules() ? Standing.EXPIRED_PASS : Standing.OWING;
        }
    }

    /**
     * La situacion de un socio en la grilla. Activo: tiene la cuota de hoy paga (mensual o pase).
     * Adeudan: el plan mensual le debe el mes o mas (con el mes corriente impago entra igual).
     * Por empezar: su cuota todavia no arranco. Pase vencido: se le termino el pase y no debe nada.
     */
    public enum Standing {
        ACTIVE("Activos"),
        OWING("Adeudan"),
        UPCOMING("Por empezar"),
        EXPIRED_PASS("Pase vencido"),
        NO_FEE("Sin cuota"),
        DISABLED("Deshabilitados");

        private final String label;

        Standing(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record DayCheckin(Instant at, String memberName, String dni, String sedeName, boolean override,
                             boolean manual) {
    }

    /**
     * Un cobro del dia. {@code at} es cuando se cargo, que puede ser otro dia que
     * {@code paidOn} (un cobro de ayer cargado hoy va a la caja de ayer).
     */
    public record DayPayment(Instant at, LocalDate paidOn, String memberName, BigDecimal price, PayMethod method,
                             String collectedAt) {
    }

    /** Hoy en la zona horaria del club. */
    @Transactional(readOnly = true)
    public LocalDate today() {
        return clock.instant().atZone(zone()).toLocalDate();
    }

    @Transactional(readOnly = true)
    public List<MemberRow> members() {
        LocalDate today = today();
        LocalDate since = today.minusDays(RECENT_DAYS - 1);
        Map<UUID, List<LocalDate>> recentDays = new HashMap<>();
        checkinRepository.findMemberDaysSince(since).forEach(row ->
                recentDays.computeIfAbsent(row.getMemberId(), id -> new ArrayList<>()).add(row.getLocalDate()));
        Map<UUID, LocalDate> lastDays = new HashMap<>();
        checkinRepository.findLastDayPerMember().forEach(row -> lastDays.put(row.getMemberId(), row.getLocalDate()));

        return memberRepository.findAllByOrderByFullNameAsc().stream()
                .map(member -> {
                    GymBillingService.Status billing = billingService.status(member, today);
                    GymAllowance allowance = GymAllowance.of(billing, today);
                    return row(member, billing, allowance, used(member, allowance, since, recentDays),
                            lastDays.get(member.getId()));
                })
                .toList();
    }

    /** Los ingresos del socio en su ventana; los dias recientes ya vienen traidos de una vez. */
    private int used(GymMember member, GymAllowance allowance, LocalDate since,
                     Map<UUID, List<LocalDate>> recentDays) {
        if (allowance.from().isBefore(since)) {
            return (int) checkinRepository.countByMemberIdAndLocalDateBetween(
                    member.getId(), allowance.from(), allowance.to());
        }
        return (int) recentDays.getOrDefault(member.getId(), List.of()).stream()
                .filter(day -> !day.isBefore(allowance.from()) && !day.isAfter(allowance.to()))
                .count();
    }

    @Transactional(readOnly = true)
    public List<DayCheckin> checkinsOf(LocalDate day) {
        return checkinRepository.findAllByDay(day).stream().map(GymOverviewService::dayCheckin).toList();
    }

    @Transactional(readOnly = true)
    public List<DayPayment> paymentsOf(LocalDate day) {
        return membershipRepository.findPaidOn(day).stream()
                .map(m -> new DayPayment(m.getCreatedAt(), m.getPaidOn(), m.getMember().getFullName(), m.getPrice(),
                        m.getPayMethod(), m.getCollectedSede().getName()))
                .toList();
    }

    /** Zona horaria del club: las horas del panel se muestran en ella. */
    @Transactional(readOnly = true)
    public ZoneId zone() {
        return tenantService.requireCurrent().zoneId();
    }

    private static MemberRow row(GymMember member, GymBillingService.Status billing, GymAllowance allowance,
                                 int used, LocalDate lastCheckin) {
        GymMembership plan = billing.active();
        List<String> sedes = plan == null ? List.of() : plan.getSedes().stream()
                .map(GymSede::getName).sorted(Comparator.naturalOrder()).toList();
        return new MemberRow(member.getId(), member.getDni(), member.getFullName(), member.getPhone(),
                member.isEnabled(), member.isMustChangePassword(), used, allowance, billing, sedes, lastCheckin);
    }

    private static DayCheckin dayCheckin(GymCheckin checkin) {
        return new DayCheckin(checkin.getCheckedInAt(), checkin.getMember().getFullName(),
                checkin.getMember().getDni(), checkin.getSede().getName(), checkin.isOverride(),
                checkin.getRegisteredBy() != null);
    }
}