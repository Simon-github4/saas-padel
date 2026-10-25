package ar.com.padelnec.service;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.CourtSchedule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BlackoutKind;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CourtScheduleRepository;
import ar.com.padelnec.web.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva turnos puntuales de algunas canchas para un torneo del club.
 *
 * <p>Un torneo es una suspension de tipo {@link BlackoutKind#TOURNAMENT}: para la
 * grilla publica, el alta de turnos, los turnos fijos y la lista de espera bloquea
 * igual que cualquier otra, y ninguno de ellos tuvo que cambiar. Lo que agrega este
 * servicio es como se carga: el club elige turnos en la agenda, uno por uno o por
 * fila o columna, y aca se convierten en suspensiones.
 */
@Service
@RequiredArgsConstructor
public class TournamentService {

    /** El largo de {@code blackout.reason}, que es donde va el nombre. */
    private static final int NAME_MAX_LENGTH = 160;

    private static final Set<BookingStatus> OCCUPYING = Arrays.stream(BookingStatus.values())
            .filter(BookingStatus::occupiesSlot)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(BookingStatus.class)));

    private final CourtRepository courtRepository;
    private final CourtScheduleRepository courtScheduleRepository;
    private final BookingRepository bookingRepository;
    private final BlackoutRepository blackoutRepository;
    private final SlotGenerator slotGenerator;
    private final Clock clock;

    /** Un turno elegido en la agenda: la cancha y el inicio. El fin lo pone la grilla. */
    public record TournamentSlot(UUID courtId, Instant startsAt) {
    }

    /**
     * Cuantos turnos quedaron para el torneo, y los que no: los que se reservaron o
     * se suspendieron mientras el club marcaba, los que ya empezaron y los que
     * dejaron de estar en la grilla.
     */
    public record MarkResult(int marked, List<TournamentSlot> leftOut) {
    }

    /** Un turno elegido, ya resuelto contra la grilla. */
    private record Resolved(TournamentSlot requested, Court court, Instant start, Instant end) {
    }

    /**
     * Guarda los turnos elegidos como torneo.
     *
     * <p>Los turnos seguidos de una misma cancha se guardan como una sola
     * suspension: Cancha 1 de 17:00 a 21:30 es una fila, no tres. Asi la lista de
     * Suspensiones se sigue leyendo, y un turno que se agrega despues al lado queda
     * como otra fila sin tocar la anterior.
     *
     * <p>Lo que ya no esta libre queda afuera sin frenar el resto: entre que el club
     * marco y guardo, un jugador pudo haber reservado uno de los turnos, y no tiene
     * sentido perder toda la seleccion por eso. La agenda le dice cuales fueron.
     */
    @Transactional
    public MarkResult mark(Tenant club, String name, Collection<TournamentSlot> slots) {
        String tournament = name == null ? "" : name.strip();
        if (tournament.isEmpty()) {
            throw new BusinessRuleException("Poné el nombre del torneo.");
        }
        if (tournament.length() > NAME_MAX_LENGTH) {
            throw new BusinessRuleException(
                    "El nombre del torneo puede tener hasta %d letras.".formatted(NAME_MAX_LENGTH));
        }
        if (slots.isEmpty()) {
            throw new BusinessRuleException("Elegí al menos un turno.");
        }

        Instant now = clock.instant();
        List<Court> courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        Map<UUID, Court> courtsById = courts.stream()
                .collect(Collectors.toMap(Court::getId, Function.identity()));
        List<CourtSchedule> schedules = courtScheduleRepository.findAllWithCourt();

        List<TournamentSlot> leftOut = new ArrayList<>();
        List<Resolved> resolved = new ArrayList<>();
        for (TournamentSlot slot : new LinkedHashSet<>(slots)) {
            Optional<Resolved> match = resolve(club, slot, courtsById, courts, schedules)
                    .filter(candidate -> candidate.start().isAfter(now));
            match.ifPresentOrElse(resolved::add, () -> leftOut.add(slot));
        }

        if (!resolved.isEmpty()) {
            Instant from = resolved.stream().map(Resolved::start).min(Comparator.naturalOrder()).orElseThrow();
            Instant until = resolved.stream().map(Resolved::end).max(Comparator.naturalOrder()).orElseThrow();
            List<Booking> taken = bookingRepository.findOverlapping(from, until, OCCUPYING);
            List<Blackout> blocked = blackoutRepository.findOverlapping(from, until);
            resolved.removeIf(slot -> {
                boolean free = isFree(slot, taken, blocked);
                if (!free) {
                    leftOut.add(slot.requested());
                }
                return !free;
            });
        }

        save(tournament, resolved);
        leftOut.sort(Comparator.comparing(TournamentSlot::startsAt));
        return new MarkResult(resolved.size(), List.copyOf(leftOut));
    }

    /** El turno de la grilla que arranca a esa hora, si esa cancha abre en el. */
    private Optional<Resolved> resolve(Tenant club, TournamentSlot slot, Map<UUID, Court> courtsById,
                                       List<Court> courts, List<CourtSchedule> schedules) {
        Court court = courtsById.get(slot.courtId());
        if (court == null) {
            return Optional.empty();
        }
        return slotGenerator.resolveWithPlan(club, slot.startsAt(), courts, schedules)
                .filter(plan -> plan.opens(court))
                .map(plan -> new Resolved(slot, court,
                        plan.resolved().slot().startsAt(), plan.resolved().slot().endsAt()));
    }

    private static boolean isFree(Resolved slot, List<Booking> taken, List<Blackout> blocked) {
        boolean occupied = taken.stream()
                .anyMatch(booking -> booking.getCourt().getId().equals(slot.court().getId())
                        && booking.overlaps(slot.start(), slot.end()));
        return !occupied && blocked.stream()
                .noneMatch(blackout -> blackout.appliesTo(slot.court())
                        && blackout.overlaps(slot.start(), slot.end()));
    }

    /** Una suspension por cada tramo de turnos seguidos de una misma cancha. */
    private void save(String tournament, List<Resolved> slots) {
        Map<UUID, List<Resolved>> byCourt = new LinkedHashMap<>();
        slots.stream()
                .sorted(Comparator.comparing(Resolved::start))
                .forEach(slot -> byCourt.computeIfAbsent(slot.court().getId(), id -> new ArrayList<>()).add(slot));

        List<Blackout> blackouts = new ArrayList<>();
        for (List<Resolved> courtSlots : byCourt.values()) {
            Blackout current = null;
            for (Resolved slot : courtSlots) {
                if (current != null && current.getEndTime().equals(slot.start())) {
                    current.setEndTime(slot.end());
                    continue;
                }
                current = new Blackout();
                current.setKind(BlackoutKind.TOURNAMENT);
                current.setCourt(slot.court());
                current.setStartTime(slot.start());
                current.setEndTime(slot.end());
                current.setReason(tournament);
                blackouts.add(current);
            }
        }
        blackoutRepository.saveAll(blackouts);
    }
}
