package ar.com.padelnec.service;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.BusinessRuleException.Reason;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Libera un turno puntual de una suspension o de un torneo, y copia las
 * suspensiones de un dia al siguiente.
 *
 * <p>Para liberar no hay un concepto nuevo de "excepcion a la suspension": se recorta la
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

    private static final Set<BookingStatus> OCCUPYING = Arrays.stream(BookingStatus.values())
            .filter(BookingStatus::occupiesSlot)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(BookingStatus.class)));

    private final CourtRepository courtRepository;
    private final BlackoutRepository blackoutRepository;
    private final BookingRepository bookingRepository;
    private final SlotGenerator slotGenerator;
    private final Clock clock;

    /** El turno que quedo libre, ya resuelto contra la grilla. */
    public record Released(Court court, Instant startsAt, Instant endsAt) {
    }

    /**
     * Lo que se copiaria del dia anterior: las suspensiones ya corridas un dia (sin
     * guardar) y cuantos turnos ya reservados quedan adentro, que siguen vigentes.
     */
    public record DayCopy(LocalDate source, List<Blackout> blackouts, int bookingsInside) {

        public boolean isEmpty() {
            return blackouts.isEmpty();
        }
    }

    // ---------------------------------------------------- copiar del dia anterior

    /**
     * Las suspensiones y torneos del dia anterior, pasados a {@code target}: misma
     * cancha, mismo horario, mismo motivo y mismo tipo. Pensado para un torneo de
     * viernes a domingo: se marca el viernes y los otros dos dias se copian.
     *
     * <p>Se toma lo que cae dentro de la jornada del dia anterior (de la primera
     * apertura al ultimo cierre, contando la madrugada), recortado a ella: una
     * suspension de varios dias aporta solo su parte de ese dia. Lo que
     * {@code target} ya tiene cubierto con una suspension del mismo tipo no se
     * vuelve a copiar, asi que copiar dos veces no duplica nada.
     */
    @Transactional(readOnly = true)
    public DayCopy previewCopyFromPreviousDay(Tenant club, LocalDate target) {
        LocalDate source = target.minusDays(1);
        if (target.isBefore(LocalDate.now(clock.withZone(club.zoneId())))) {
            return new DayCopy(source, List.of(), 0);
        }
        SlotGenerator.DayPlan sourcePlan = slotGenerator.plan(club, source);
        Instant from = sourcePlan.start();
        Instant until = sourcePlan.end();
        Instant now = clock.instant();

        List<Blackout> copies = new ArrayList<>();
        List<Blackout> onTarget = new ArrayList<>(blackoutRepository.findOverlapping(
                nextDay(from, club), nextDay(until, club)));
        for (Blackout blackout : blackoutRepository.findOverlapping(from, until)) {
            Instant start = nextDay(max(blackout.getStartTime(), from), club);
            Instant end = nextDay(min(blackout.getEndTime(), until), club);
            if (!end.isAfter(now) || alreadyCovered(blackout, start, end, onTarget)) {
                continue;
            }
            Blackout copy = copy(blackout, blackout.getCourt());
            copy.setStartTime(max(start, now));
            copy.setEndTime(end);
            copies.add(copy);
            // Dos suspensiones iguales el dia anterior no se copian dos veces.
            onTarget.add(copy);
        }
        copies.sort(Comparator.comparing(Blackout::getStartTime));

        int bookingsInside = copies.isEmpty() ? 0 : (int) bookingRepository.findOverlapping(
                        copies.getFirst().getStartTime(),
                        copies.stream().map(Blackout::getEndTime).max(Comparator.naturalOrder()).orElseThrow(),
                        OCCUPYING).stream()
                .filter(booking -> copies.stream().anyMatch(copy -> copy.appliesTo(booking.getCourt())
                        && copy.overlaps(booking.getStartTime(), booking.getEndTime())))
                .count();
        return new DayCopy(source, copies, bookingsInside);
    }

    /**
     * Guarda la copia del dia anterior y devuelve lo que se guardo. Se vuelve a
     * calcular aca y no se reusa la vista previa: entre que el club la vio y
     * confirmo, otro del mostrador pudo haber copiado o cargado algo.
     */
    @Transactional
    public List<Blackout> copyFromPreviousDay(Tenant club, LocalDate target) {
        if (target.isBefore(LocalDate.now(clock.withZone(club.zoneId())))) {
            throw new BusinessRuleException("No se puede copiar a un día que ya pasó.");
        }
        List<Blackout> copies = previewCopyFromPreviousDay(club, target).blackouts();
        return blackoutRepository.saveAll(copies);
    }

    /** Ya hay en el dia de destino una suspension del mismo tipo que cubre todo ese horario. */
    private static boolean alreadyCovered(Blackout source, Instant start, Instant end, List<Blackout> onTarget) {
        return onTarget.stream().anyMatch(existing -> existing.getKind() == source.getKind()
                && (existing.getCourt() == null
                        || source.getCourt() != null && existing.getCourt().getId().equals(source.getCourt().getId()))
                && !existing.getStartTime().isAfter(start)
                && !existing.getEndTime().isBefore(end));
    }

    /** El mismo horario del reloj del club un dia despues: un cambio de hora no lo corre. */
    private static Instant nextDay(Instant instant, Tenant club) {
        return instant.atZone(club.zoneId()).plusDays(1).toInstant();
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    // ------------------------------------------------------------------ liberar

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
