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
 * que le gana al del club. Cada franja de ese horario encadena sus turnos desde su
 * propio inicio: una cancha que esos dias abre 13:30 tiene turnos 13:30, 15:00,
 * 16:30..., aunque las demas sigan con los del club (14:00, 15:30...). La grilla
 * del dia es la union de los turnos de todas las canchas, y cada turno sabe en que
 * canchas se juega.
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

        boolean overlaps(LocalDateTime from, LocalDateTime to) {
            return from.isBefore(end) && start.isBefore(to);
        }
    }

    /** La grilla de un dia operativo y en que canchas se juega cada turno. */
    public static final class DayPlan {

        private final List<Slot> slots;
        private final Set<Instant> generalStarts;
        private final Map<UUID, Set<Instant>> startsByCourt;
        private final List<Window> generalWindows;
        private final Map<UUID, List<Window>> windowsByCourt;
        private final ZoneId zone;
        private final Instant start;
        private final Instant end;

        private DayPlan(List<Slot> slots, Set<Instant> generalStarts, Map<UUID, Set<Instant>> startsByCourt,
                        List<Window> generalWindows, Map<UUID, List<Window>> windowsByCourt,
                        ZoneId zone, Instant start, Instant end) {
            this.slots = slots;
            this.generalStarts = generalStarts;
            this.startsByCourt = startsByCourt;
            this.generalWindows = generalWindows;
            this.windowsByCourt = windowsByCourt;
            this.zone = zone;
            this.start = start;
            this.end = end;
        }

        public List<Slot> slots() {
            return slots;
        }

        /** Si ese turno es uno de los de la cancha: por su horario propio ese dia, o el del club. */
        public boolean opens(Court court, Slot slot) {
            return startsByCourt.getOrDefault(court.getId(), generalStarts).contains(slot.startsAt());
        }

        /**
         * Si la cancha esta abierta durante ese turno aunque no sea uno de los suyos:
         * pasa cuando sus turnos arrancan a otra hora (13:30 contra el 14:00 del club).
         */
        public boolean openDuring(Court court, Slot slot) {
            LocalDateTime from = LocalDateTime.ofInstant(slot.startsAt(), zone);
            LocalDateTime to = LocalDateTime.ofInstant(slot.endsAt(), zone);
            return windowsByCourt.getOrDefault(court.getId(), generalWindows).stream()
                    .anyMatch(window -> window.overlaps(from, to));
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
        ZoneId zone = club.zoneId();
        long step = club.getDefaultSlotDuration();
        List<Window> generalWindows = List.of(window(date, club.getOpenTime(), club.getCloseTime()));
        Map<UUID, List<Window>> windowsByCourt = courtWindows(date, schedules);

        Map<Instant, Slot> offered = new HashMap<>();
        Set<Instant> generalStarts = chain(generalWindows, step, zone);
        Map<UUID, Set<Instant>> startsByCourt = new HashMap<>();
        windowsByCourt.forEach((courtId, windows) -> startsByCourt.put(courtId, chain(windows, step, zone)));

        // Solo entran a la grilla los turnos de alguna cancha activa: si todas tienen
        // horario propio ese dia, el del club no aporta ninguno.
        if (activeCourts.isEmpty()) {
            addSlots(offered, generalWindows, step, zone);
        }
        for (Court court : activeCourts) {
            addSlots(offered, windowsByCourt.getOrDefault(court.getId(), generalWindows), step, zone);
        }
        List<Slot> slots = offered.values().stream()
                .sorted(Comparator.comparing(Slot::startsAt))
                .toList();

        List<Window> all = new ArrayList<>(generalWindows);
        windowsByCourt.values().forEach(all::addAll);
        LocalDateTime from = all.stream().map(Window::start).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime until = all.stream().map(Window::end).max(Comparator.naturalOrder()).orElseThrow();

        return new DayPlan(slots, generalStarts, startsByCourt, generalWindows, windowsByCourt, zone,
                from.atZone(zone).toInstant(), until.atZone(zone).toInstant());
    }

    /** Los turnos de cada franja, encadenados desde su inicio; el que no entra completo no va. */
    private static void addSlots(Map<Instant, Slot> into, List<Window> windows, long step, ZoneId zone) {
        for (Window window : windows) {
            LocalDateTime cursor = window.start();
            while (!cursor.plusMinutes(step).isAfter(window.end())) {
                LocalDateTime slotEnd = cursor.plusMinutes(step);
                Slot slot = new Slot(cursor.toLocalTime(), slotEnd.toLocalTime(),
                        cursor.atZone(zone).toInstant(), slotEnd.atZone(zone).toInstant());
                into.putIfAbsent(slot.startsAt(), slot);
                cursor = slotEnd;
            }
        }
    }

    private static Set<Instant> chain(List<Window> windows, long step, ZoneId zone) {
        Map<Instant, Slot> slots = new HashMap<>();
        addSlots(slots, windows, step, zone);
        return slots.keySet();
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
