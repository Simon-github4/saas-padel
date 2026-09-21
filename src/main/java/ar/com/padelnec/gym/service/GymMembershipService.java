package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.gym.repository.GymSedeRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Venta y anulacion de cuotas en el mostrador. */
@Service
@RequiredArgsConstructor
public class GymMembershipService {

    private final GymMembershipRepository membershipRepository;
    private final GymMemberRepository memberRepository;
    private final GymSedeRepository sedeRepository;
    private final GymBillingService billingService;
    private final GymTariffService tariffService;
    private final Clock clock;

    /** Todo lo que carga el mostrador al cobrar una cuota. */
    public record Sale(UUID memberId, LocalDate startsOn, LocalDate endsOn, int daysPerWeek,
                       BigDecimal price, PayMethod payMethod, UUID collectedSedeId,
                       Set<UUID> sedeIds, UUID registeredBy) {
    }

    /** Ultimo dia de un periodo de {@code months} meses que arranca en {@code startsOn}. */
    public static LocalDate endOfPeriod(LocalDate startsOn, int months) {
        return startsOn.plusMonths(months).minusDays(1);
    }

    @Transactional
    public UUID sell(Sale sale) {
        GymMember member = memberRepository.findById(sale.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe ese socio."));
        validate(sale);

        Set<GymSede> sedes = loadSedes(sale.sedeIds());
        GymSede collectedSede = sedeRepository.findById(sale.collectedSedeId())
                .orElseThrow(() -> new BusinessRuleException("Elegí la sede donde se cobró."));

        if (membershipRepository.countOverlapping(member.getId(), sale.startsOn(), sale.endsOn()) > 0) {
            throw overlap();
        }

        GymMembership membership = new GymMembership();
        membership.setMember(member);
        membership.setStartsOn(sale.startsOn());
        membership.setEndsOn(sale.endsOn());
        membership.setDaysPerWeek(sale.daysPerWeek());
        membership.setPrice(sale.price().setScale(2, java.math.RoundingMode.HALF_UP));
        membership.setPayMethod(sale.payMethod());
        membership.setCollectedSede(collectedSede);
        membership.setRegisteredBy(sale.registeredBy());
        membership.setSedes(sedes);
        try {
            return membershipRepository.saveAndFlush(membership).getId();
        } catch (DataIntegrityViolationException ex) {
            // La constraint de exclusion de la base es la que cierra la carrera entre dos ventas.
            throw overlap();
        }
    }

    /**
     * Cobra {@code months} cuotas del ciclo del socio. Cada cuota es una fila de un
     * mes: se cobra la deuda mas vieja primero y, si no hay deuda, la proxima (se
     * puede adelantar de a varias cuotas). La primera cuota fija el ancla (el dia
     * de corte de todos sus meses). {@code price} es opcional: si no viene, se
     * auto-completa con la tarifa de {@code daysPerWeek} (que solo importa para el
     * socio que empieza).
     */
    @Transactional
    public List<UUID> charge(UUID memberId, int months, BigDecimal price, Integer daysPerWeek,
                             PayMethod payMethod, UUID collectedSedeId, Set<UUID> sedeIds,
                             UUID registeredBy, LocalDate today) {
        if (months < 1) {
            throw new BusinessRuleException("Elegí cuántas cuotas cobrar.");
        }
        if (payMethod == null) {
            throw new BusinessRuleException("Elegí cómo se pagó.");
        }
        if (collectedSedeId == null) {
            throw new BusinessRuleException("Elegí la sede donde se cobró.");
        }
        if (sedeIds == null || sedeIds.isEmpty()) {
            throw new BusinessRuleException("Elegí al menos una sede donde vale la cuota.");
        }
        if (price != null && price.signum() < 0) {
            throw new BusinessRuleException("Poné un monto válido.");
        }

        GymMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ese socio."));
        GymBillingService.Status status = billingService.status(member, today);
        List<GymBillingService.Period> pending = status.pending();
        if (months > pending.size()) {
            throw new BusinessRuleException("Estás cobrando más de las "
                    + (pending.size() == 1 ? "una cuota" : pending.size() + " cuotas") + " pendientes.");
        }

        int effectiveDays = status.planDaysPerWeek();
        if (status.plan() == null) {
            if (daysPerWeek == null) {
                throw new BusinessRuleException("Elegí la cantidad de días por semana del socio.");
            }
            effectiveDays = daysPerWeek;
        }
        BigDecimal unit = price != null ? price : tariffService.priceOf(effectiveDays);
        if (price == null && unit == null) {
            throw new BusinessRuleException("Fijá antes la cuota para " + effectiveDays
                    + " días por semana, o poné el monto a mano.");
        }

        Set<GymSede> sedes = loadSedes(sedeIds);
        GymSede collectedSede = sedeRepository.findById(collectedSedeId)
                .orElseThrow(() -> new BusinessRuleException("Elegí la sede donde se cobró."));

        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            GymBillingService.Period period = pending.get(i);
            GymMembership membership = new GymMembership();
            membership.setMember(member);
            membership.setStartsOn(period.start());
            membership.setEndsOn(period.end());
            membership.setDaysPerWeek(effectiveDays);
            membership.setPrice(unit.setScale(2, java.math.RoundingMode.HALF_UP));
            membership.setPayMethod(payMethod);
            membership.setCollectedSede(collectedSede);
            membership.setRegisteredBy(registeredBy);
            membership.setSedes(sedes);
            ids.add(membershipRepository.saveAndFlush(membership).getId());
        }

        if (member.getBillingAnchor() == null) {
            member.setBillingAnchor(pending.getFirst().start());
        }
        return ids;
    }

    /** Anula un cobro cargado por error: deja de valer y deja de contar en la liquidacion. */
    @Transactional
    public void voidMembership(UUID membershipId) {
        GymMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa cuota."));
        if (membership.getVoidedAt() == null) {
            membership.setVoidedAt(clock.instant());
        }
    }

    private Set<GymSede> loadSedes(Set<UUID> sedeIds) {
        Set<GymSede> sedes = new HashSet<>(sedeRepository.findAllById(sedeIds));
        if (sedes.size() != sedeIds.size()) {
            throw new BusinessRuleException("Alguna de las sedes elegidas no existe.");
        }
        return sedes;
    }

    private static void validate(Sale sale) {
        if (sale.startsOn() == null || sale.endsOn() == null) {
            throw new BusinessRuleException("Elegí desde y hasta cuándo vale la cuota.");
        }
        if (sale.endsOn().isBefore(sale.startsOn())) {
            throw new BusinessRuleException("La cuota no puede terminar antes de empezar.");
        }
        if (sale.daysPerWeek() < 1 || sale.daysPerWeek() > 7) {
            throw new BusinessRuleException("Los días por semana van de 1 a 7.");
        }
        if (sale.price() == null || sale.price().signum() < 0) {
            throw new BusinessRuleException("Poné el monto cobrado.");
        }
        if (sale.payMethod() == null) {
            throw new BusinessRuleException("Elegí cómo se pagó.");
        }
        if (sale.collectedSedeId() == null) {
            throw new BusinessRuleException("Elegí la sede donde se cobró.");
        }
        if (sale.sedeIds() == null || sale.sedeIds().isEmpty()) {
            throw new BusinessRuleException("Elegí al menos una sede donde vale la cuota.");
        }
    }

    private static BusinessRuleException overlap() {
        return new BusinessRuleException(
                "Ese socio ya tiene una cuota que se superpone con esas fechas. Empezá la nueva cuando termine la actual.");
    }
}
