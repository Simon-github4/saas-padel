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

    private final GymMemberRepository memberRepository;
    private final GymMembershipRepository membershipRepository;
    private final GymCheckinRepository checkinRepository;
    private final GymBillingService billingService;
    private final TenantService tenantService;
    private final Clock clock;

    /**
     * Un socio con su estado de cuotas. {@code billing} es el ciclo completo del
     * socio: el periodo corriente, la deuda, si puede entrar y que le falta pagar.
     * {@code sedes} son las sedes donde vale la ultima cuota paga.
     */
    public record MemberRow(UUID id, String dni, String fullName, String phone, boolean enabled,
                            boolean mustChangePassword, int weekUsed,
                            GymBillingService.Status billing, List<String> sedes) {
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
        GymWeek week = GymWeek.of(today);
        Map<UUID, Integer> weekUsed = new HashMap<>();
        checkinRepository.countByMemberBetween(week.monday(), week.sunday())
                .forEach(row -> weekUsed.put(row.getMemberId(), (int) row.getTotal()));

        return memberRepository.findAllByOrderByFullNameAsc().stream()
                .map(member -> row(member, billingService.status(member, today),
                        weekUsed.getOrDefault(member.getId(), 0)))
                .toList();
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

    private static MemberRow row(GymMember member, GymBillingService.Status billing, int weekUsed) {
        GymMembership plan = billing.plan();
        List<String> sedes = plan == null ? List.of() : plan.getSedes().stream()
                .map(GymSede::getName).sorted(Comparator.naturalOrder()).toList();
        return new MemberRow(member.getId(), member.getDni(), member.getFullName(), member.getPhone(),
                member.isEnabled(), member.isMustChangePassword(), weekUsed, billing, sedes);
    }

    private static DayCheckin dayCheckin(GymCheckin checkin) {
        return new DayCheckin(checkin.getCheckedInAt(), checkin.getMember().getFullName(),
                checkin.getMember().getDni(), checkin.getSede().getName(), checkin.isOverride(),
                checkin.getRegisteredBy() != null);
    }
}