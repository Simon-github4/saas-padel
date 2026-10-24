package ar.com.padelnec.service;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.BusinessRuleException.Reason;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Libera un turno puntual de una suspension o de un torneo, y la deja vender.
 *
 * <p>No hay un concepto nuevo de "excepcion a la suspension": se recorta la
 * suspension misma. Si el turno estaba al principio o al final, se acorta; si
 * estaba en el medio, queda partida en dos con el mismo motivo y el mismo tipo; si
 * era lo unico que cubria, se borra. Asi todo lo que lee suspensiones (grilla
 * publica, alta de turnos, turnos fijos, lista de espera, estadisticas) sigue
 * igual, y la lista de Suspensiones muestra lo que de verdad queda cerrado.
 *
 * <p>La lista de espera se entera sola: su barrido de cada minuto ve el horario
 * libre. Un turno fijo que no se genero por la suspension se genera en la corrida
 * de esa noche.
 */
@Service
@RequiredArgsConstructor
public class BlackoutService {

    private final CourtRepository courtRepository;
    private final BlackoutRepository blackoutRepository;
    private final SlotGenerator slotGenerator;
    private final Clock clock;

    /** El turno que quedo libre, ya resuelto contra la grilla. */
    public record Released(Court court, Instant startsAt, Instant endsAt) {
    }

    @Transactional
    public Released release(Tenant club, UUID courtId, Instant startsAt) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("La cancha no existe"));
        SlotGenerator.Slot slot = slotGenerator.resolveWithPlan(club, startsAt)
                .filter(plan -> plan.opens(court))
                .map(plan -> plan.resolved().slot())
                .orElseThrow(() -> new BusinessRuleException(Reason.SLOT_NOT_IN_GRID,
                        "Ese horario no forma parte de la grilla de %s.".formatted(court.getName())));
        if (slot.isInThePast(clock.instant())) {
            throw new BusinessRuleException(Reason.SLOT_PAST, "Ese horario ya empezó.");
        }

        List<Blackout> covering = blackoutRepository.findOverlapping(slot.startsAt(), slot.endsAt()).stream()
                .filter(blackout -> blackout.appliesTo(court))
                .toList();
        if (covering.isEmpty()) {
            throw new BusinessRuleException("Ese horario ya estaba libre.");
        }
        for (Blackout blackout : covering) {
            Blackout own = blackout.getCourt() == null ? splitByCourt(blackout, court) : blackout;
            cut(own, slot.startsAt(), slot.endsAt());
        }
        return new Released(court, slot.startsAt(), slot.endsAt());
    }

    /**
     * Una suspension de todo el club no se puede recortar para una sola cancha:
     * pasa a ser una por cancha, con el mismo horario, y se devuelve la de la
     * cancha que se libera. Una cancha que se agregue despues ya no queda cubierta
     * por esta suspension, que es lo esperable de algo cargado antes de que exista.
     */
    private Blackout splitByCourt(Blackout clubWide, Court court) {
        List<Court> courts = new ArrayList<>(courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc());
        if (courts.stream().noneMatch(candidate -> candidate.getId().equals(court.getId()))) {
            courts.add(court);
        }
        Blackout own = null;
        for (Court candidate : courts) {
            Blackout copy = copy(clubWide, candidate);
            copy.setStartTime(clubWide.getStartTime());
            copy.setEndTime(clubWide.getEndTime());
            Blackout saved = blackoutRepository.save(copy);
            if (candidate.getId().equals(court.getId())) {
                own = saved;
            }
        }
        blackoutRepository.delete(clubWide);
        return own;
    }

    /** Saca [from, until) de la suspension: la borra, la acorta o la parte en dos. */
    private void cut(Blackout blackout, Instant from, Instant until) {
        boolean startsInside = !blackout.getStartTime().isBefore(from);
        boolean endsInside = !blackout.getEndTime().isAfter(until);
        if (startsInside && endsInside) {
            blackoutRepository.delete(blackout);
        } else if (startsInside) {
            blackout.setStartTime(until);
            blackoutRepository.save(blackout);
        } else if (endsInside) {
            blackout.setEndTime(from);
            blackoutRepository.save(blackout);
        } else {
            Blackout tail = copy(blackout, blackout.getCourt());
            tail.setStartTime(until);
            tail.setEndTime(blackout.getEndTime());
            blackout.setEndTime(from);
            blackoutRepository.save(blackout);
            blackoutRepository.save(tail);
        }
    }

    /** Mismo motivo y mismo tipo, en otra cancha o en otro tramo. */
    private static Blackout copy(Blackout source, Court court) {
        Blackout copy = new Blackout();
        copy.setCourt(court);
        copy.setReason(source.getReason());
        copy.setKind(source.getKind());
        return copy;
    }
}
