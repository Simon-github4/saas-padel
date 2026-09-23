package ar.com.padelnec.service;

import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.CourtSchedule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CourtScheduleRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Arma la grilla de horarios de un dia.
 *
 * <p>Bloques fijos: el primer turno arranca a la hora de apertura y los siguientes
 * se encadenan uno tras otro. Un turno que no entra completo antes del cierre no se
 * ofrece.
 *
 * <p>Una cancha puede tener su propio horario algunos dias ({@link CourtSchedule}),
 * que le gana al del club. La grilla sigue siendo una sola, anclada en la apertura
 * del club: si una cancha abre antes o cierra despues, la grilla se estira hacia
 * ese lado con el mismo paso, y esos turnos de mas solo existen en esa cancha. Asi
 * un turno de las 19:30 es el mismo en todas las canchas que lo ofrecen.
 */
@Component
@RequiredArgsConstructor
public class SlotGenerator {

    private final CourtRepository courtRepository;
    private final CourtScheduleRepository courtScheduleRepository;

    /** Una franja de la grilla, ya resuelta a instantes absolutos. */
    public record Slot(LocalTime startTime, LocalTime endTime, Instant startsAt, Instant endsAt) {

        public boolean isInThePast(Instant now) {
            return !startsAt.isAfter(now);
        }
    }

    /** Un tramo en que una cancha abre, en fecha y hora del club. */
    private record Window(LocalDateTime start, LocalDateTime end) {

        boolean contains(LocalDateTime from, LocalDateTime to) {
            return !from.isBefore(start) && !to.isAfter(end);
        }
    }

    /** La grilla de un dia operativo y en que canchas se juega cada turno. */
    public static final class DayPlan {

        private final List<Slot> slots;
        private final List<Window> general;
        private final Map<UUID, List<Window>> byCourt;
        private final ZoneId zone;
        private final Instant start;
        private final Instant end;

        private DayPlan(List<Slot> slots, List<Window> general, Map<UUID, List<Window>> byCourt,
                        ZoneId zone, Instant start, Instant end) {
            this.slots = slots;
            this.general = general;
            this.byCourt = byCourt;
            this.zone = zone;
            this.start = start;
            this.end = end;
        }

        public List<Slot> slots() {
            return slots;
        }

        /** Si la cancha abre en ese turno: por su horario propio ese dia, o el del club. */
        public boolean opens(Court court, Slot slot) {
            LocalDateTime from = LocalDateTime.ofInstant(slot.startsAt(), zone);
            LocalDateTime to = LocalDateTime.ofInstant(slot.endsAt(), zone);
            return byCourt.getOrDefault(court.getId(), general).stream()
                    .anyMatch(window -> window.contains(from, to));
        }

        /** Cuantos turnos del dia ofrece la cancha. */
        public long slotsOpenIn(Court court) {
            return slots.stream().filter(slot -> opens(court, slot)).count();
        }

        /** Desde la primera apertura del dia, de cualquier cancha. */
        public Instant start() {
            return start;
        }

        /** Hasta el ultimo cierre del dia, contemplando el cierre de madrugada. */
        public Instant end() {
            return end;
        }
    }

    public List<Slot> generate(Tenant club, LocalDate date) {
        return plan(club, date).slots();
    }

    public DayPlan plan(Tenant club, LocalDate date) {
        return plan(club, date, courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc(),
                courtScheduleRepository.findAllWithCourt());
    }

    /**
     * Igual que {@link #plan(Tenant, LocalDate)}, sobre canchas y horarios ya cargados:
     * para quien arma muchos dias seguidos y no quiere consultar dia por dia.
     */
    public DayPlan plan(Tenant club, LocalDate date, List<Court> activeCourts,
                        List<CourtSchedule> schedules) {
        List<Window> general = List.of(window(date, club.getOpenTime(), club.getCloseTime()));
        Map<UUID, List<Window>> byCourt = courtWindows(date, schedules);

        List<Window> offered = new ArrayList<>();
        if (activeCourts.isEmpty()) {
            offered.addAll(general);
        }
        for (Court court : activeCourts) {
            offered.addAll(byCourt.getOrDefault(court.getId(), general));
        }

        List<Window> all = new ArrayList<>(general);
        byCourt.values().forEach(all::addAll);
        LocalDateTime from = all.stream().map(Window::start).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime until = all.stream().map(Window::end).max(Comparator.naturalOrder()).orElseThrow();

        ZoneId zone = club.zoneId();
        return new DayPlan(grid(club, date, from, until, offered), general, byCourt, zone,
                from.atZone(zone).toInstant(), until.atZone(zone).toInstant());
    }

    /**
     * Turnos anclados en la apertura del club, con su paso, estirados hacia atras o
     * hacia adelante hasta cubrir {@code from}–{@code until}. Solo quedan los que
     * entran completos en algun horario que se ofrece ese dia.
     */
    private List<Slot> grid(Tenant club, LocalDate date, LocalDateTime from, LocalDateTime until,
                            List<Window> offered) {
        ZoneId zone = club.zoneId();
        long step = club.getDefaultSlotDuration();
        LocalDateTime anchor = date.atTime(club.getOpenTime());
        long offset = java.time.Duration.between(anchor, from).toMinutes();
        LocalDateTime cursor = anchor.plusMinutes(-Math.floorDiv(-offset, step) * step);

        List<Slot> slots = new ArrayList<>();
        while (!cursor.plusMinutes(step).isAfter(until)) {
            LocalDateTime slotStart = cursor;
            LocalDateTime slotEnd = cursor.plusMinutes(step);
            if (offered.stream().anyMatch(window -> window.contains(slotStart, slotEnd))) {
                slots.add(new Slot(
                        slotStart.toLocalTime(),
                        slotEnd.toLocalTime(),
                        slotStart.atZone(zone).toInstant(),
                        slotEnd.atZone(zone).toInstant()));
            }
            cursor = slotEnd;
        }
        return slots;
    }

    /** Horarios propios de cada cancha que tiene alguna regla ese dia; "cerrada" le gana a todo. */
    private Map<UUID, List<Window>> courtWindows(LocalDate date, List<CourtSchedule> schedules) {
        DayOfWeek day = date.getDayOfWeek();
        Set<UUID> closed = new HashSet<>();
        Map<UUID, List<Window>> byCourt = new HashMap<>();
        for (CourtSchedule schedule : schedules) {
            if (!schedule.containsDay(day)) {
                continue;
            }
            UUID courtId = schedule.getCourt().getId();
            List<Window> windows = byCourt.computeIfAbsent(courtId, id -> new ArrayList<>());
            if (schedule.isClosed()) {
                closed.add(courtId);
            } else {
                windows.add(window(date, schedule.getStartTime(), schedule.getEndTime()));
            }
        }
        closed.forEach(courtId -> byCourt.put(courtId, List.of()));
        return byCourt;
    }

    /** Un cierre menor o igual a la apertura pertenece al dia siguiente. */
    private static Window window(LocalDate date, LocalTime open, LocalTime close) {
        LocalDate closingDate = close.isAfter(open) ? date : date.plusDays(1);
        return new Window(date.atTime(open), closingDate.atTime(close));
    }

    /**
     * Un turno de la grilla junto con el dia operativo al que pertenece.
     *
     * <p>Los dos datos no siempre coinciden: en un club que cierra a la 01:00, el
     * turno que arranca 00:30 del miercoles es parte de la noche del martes, y es la
     * tarifa del martes la que corresponde cobrarle.
     */
    public record ResolvedSlot(LocalDate operatingDate, Slot slot) {
    }

    /**
     * Determina a que turno de la grilla corresponde un instante.
     *
     * <p>Es lo que impide que alguien reserve a las 18:07 llamando a la API por fuera
     * de la app: si el horario no es exactamente el inicio de un bloque, no existe.
     */
    public Optional<ResolvedSlot> resolve(Tenant club, Instant startsAt) {
        return resolveWithPlan(club, startsAt).map(ResolvedPlan::resolved);
    }

    /** Un turno resuelto junto con el plan de su dia, para ver en que canchas se juega. */
    public record ResolvedPlan(ResolvedSlot resolved, DayPlan plan) {

        public boolean opens(Court court) {
            return plan.opens(court, resolved.slot());
        }
    }

    public Optional<ResolvedPlan> resolveWithPlan(Tenant club, Instant startsAt) {
        return resolveWithPlan(club, startsAt, courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc(),
                courtScheduleRepository.findAllWithCourt());
    }

    /** Igual que {@link #resolveWithPlan(Tenant, Instant)}, sobre canchas y horarios ya cargados. */
    public Optional<ResolvedPlan> resolveWithPlan(Tenant club, Instant startsAt, List<Court> courts,
                                                  List<CourtSchedule> schedules) {
        LocalDate localDate = startsAt.atZone(club.zoneId()).toLocalDate();

        // Se prueba tambien el dia anterior, porque un turno de madrugada pertenece
        // a la jornada que arranco la tarde previa.
        for (LocalDate candidate : List.of(localDate, localDate.minusDays(1))) {
            DayPlan plan = plan(club, candidate, courts, schedules);
            Optional<Slot> match = plan.slots().stream()
                    .filter(slot -> slot.startsAt().equals(startsAt))
                    .findFirst();
            if (match.isPresent()) {
                return Optional.of(new ResolvedPlan(new ResolvedSlot(candidate, match.get()), plan));
            }
        }
        return Optional.empty();
    }

    /** Instante en que arranca el dia operativo, para acotar las consultas a la base. */
    public Instant dayStart(Tenant club, LocalDate date) {
        return plan(club, date).start();
    }

    /** Instante en que termina el dia operativo, contemplando el cierre de madrugada. */
    public Instant dayEnd(Tenant club, LocalDate date) {
        return plan(club, date).end();
    }
}
