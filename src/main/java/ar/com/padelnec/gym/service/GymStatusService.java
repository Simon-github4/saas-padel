package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymCheckin;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lo que el socio ve en la pantalla "mi estado" de la app. */
@Service
@RequiredArgsConstructor
public class GymStatusService {

    private final GymMemberRepository memberRepository;
    private final GymCheckinRepository checkinRepository;
    private final GymBillingService billingService;
    private final TenantService tenantService;
    private final Clock clock;

    public record MembershipView(LocalDate startsOn, LocalDate endsOn, int daysPerWeek, List<String> sedes) {
    }

    public record RecentCheckin(LocalDate date, String sedeName) {
    }

    /**
     * {@code membership} es la ultima cuota paga del socio (su plan), o null si
     * nunca pago. {@code valid} y {@code canEnter} dicen si puede entrar hoy:
     * con la corriente impaga entra (gracia); con dos o mas cuotas, no.
     */
    public record Status(String fullName, MembershipView membership, boolean valid, int weekUsed,
                         int weekLimit, boolean checkedInToday, List<RecentCheckin> recent,
                         LocalDate cycleStart, LocalDate periodStart, LocalDate periodEnd,
                         LocalDate paidUntil, boolean paidCurrent, int monthsLate, boolean canEnter,
                         BigDecimal owedTotal) {
    }

    @Transactional(readOnly = true)
    public Status of(UUID memberId) {
        GymMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión."));
        LocalDate today = clock.instant().atZone(tenantService.requireCurrent().zoneId()).toLocalDate();
        GymBillingService.Status billing = billingService.status(member, today);
        GymMembership plan = billing.plan();

        GymWeek week = GymWeek.of(today);
        int weekUsed = (int) checkinRepository.countByMemberIdAndLocalDateBetween(
                memberId, week.monday(), week.sunday());
        boolean checkedInToday = checkinRepository.findByMemberIdAndLocalDate(memberId, today).isPresent();
        List<RecentCheckin> recent = checkinRepository.findTop10ByMemberIdOrderByLocalDateDesc(memberId).stream()
                .map(checkin -> new RecentCheckin(checkin.getLocalDate(), checkin.getSede().getName()))
                .toList();

        boolean canEnter = billing.canEnter();
        return new Status(member.getFullName(),
                plan == null ? null : view(plan), canEnter, weekUsed,
                plan == null ? 0 : plan.getDaysPerWeek(), checkedInToday, recent,
                billing.anchor(), billing.periodStart(), billing.periodEnd(), billing.paidUntil(),
                billing.paidCurrent(), billing.monthsLate(), canEnter, billing.owedTotal());
    }

    private static MembershipView view(GymMembership membership) {
        List<String> sedes = membership.getSedes().stream()
                .map(GymSede::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new MembershipView(membership.getStartsOn(), membership.getEndsOn(),
                membership.getDaysPerWeek(), sedes);
    }
}
