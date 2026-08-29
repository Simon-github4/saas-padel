package ar.com.padelnec.service;

import ar.com.padelnec.domain.Tenant;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Arma la grilla de horarios de un dia.
 *
 * <p>Bloques fijos: el primer turno arranca a la hora de apertura y los siguientes
 * se encadenan uno tras otro. Un turno que no entra completo antes del cierre no se
 * ofrece.
 */
@Component
public class SlotGenerator {

    /** Una franja de la grilla, ya resuelta a instantes absolutos. */
    public record Slot(LocalTime startTime, LocalTime endTime, Instant startsAt, Instant endsAt) {

        public boolean isInThePast(Instant now) {
            return !startsAt.isAfter(now);
        }
    }

    public List<Slot> generate(Tenant club, LocalDate date) {
        ZoneId zone = club.zoneId();
        Duration slotLength = Duration.ofMinutes(club.getDefaultSlotDuration());

        LocalDateTime cursor = date.atTime(club.getOpenTime());
        LocalDateTime closing = club.closesAfterMidnight()
                // El club cierra de madrugada: el cierre pertenece al dia siguiente.
                ? date.plusDays(1).atTime(club.getCloseTime())
                : date.atTime(club.getCloseTime());

        List<Slot> slots = new ArrayList<>();
        while (!cursor.plus(slotLength).isAfter(closing)) {
            LocalDateTime slotEnd = cursor.plus(slotLength);
            slots.add(new Slot(
                    cursor.toLocalTime(),
                    slotEnd.toLocalTime(),
                    cursor.atZone(zone).toInstant(),
                    slotEnd.atZone(zone).toInstant()));
            cursor = slotEnd;
        }
        return slots;
    }

    /** Instante en que arranca el dia operativo, para acotar las consultas a la base. */
    public Instant dayStart(Tenant club, LocalDate date) {
        return date.atTime(club.getOpenTime()).atZone(club.zoneId()).toInstant();
    }

    /** Instante en que termina el dia operativo, contemplando el cierre de madrugada. */
    public Instant dayEnd(Tenant club, LocalDate date) {
        LocalDateTime closing = club.closesAfterMidnight()
                ? date.plusDays(1).atTime(club.getCloseTime())
                : date.atTime(club.getCloseTime());
        return closing.atZone(club.zoneId()).toInstant();
    }
}
