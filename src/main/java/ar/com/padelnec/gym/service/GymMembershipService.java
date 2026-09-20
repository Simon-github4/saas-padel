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
import java.util.HashSet;
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
