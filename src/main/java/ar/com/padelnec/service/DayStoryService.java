package ar.com.padelnec.service;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.web.dto.AvailabilityResponse.SlotView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que va escrito en la historia de Instagram de los turnos libres de un dia.
 *
 * <p>Los clubes suben cada dia una historia con los horarios que les quedan, sobre
 * una plantilla propia. Los horarios salen de la misma grilla que ve el jugador
 * ({@link AvailabilityService#freeSlotsFor}): un horario va si queda al menos una
 * cancha libre, y uno que ya empezo no va. La imagen la dibuja el panel en el
 * navegador; esto solo decide los textos.
 */
@Service
@RequiredArgsConstructor
public class DayStoryService {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private final AvailabilityService availabilityService;
    private final Clock clock;

    /** Un horario libre, con cuantas canchas le quedan para que el club decida si lo publica. */
    public record StoryTime(LocalTime start, String label, int freeCourts) {
    }

    /**
     * @param bookable falso si el dia ya paso o todavia no se abrio a reservas: la
     *                 grilla publica no ofrece nada ahi, y la historia queda vacia
     *                 por eso y no porque este todo vendido.
     */
    public record DayStory(LocalDate date, String dayLabel, List<StoryTime> times, boolean bookable) {
    }

    @Transactional(readOnly = true)
    public DayStory storyFor(Tenant club, LocalDate date) {
        LocalDate today = LocalDate.now(clock.withZone(club.zoneId()));
        boolean bookable = !date.isBefore(today) && !date.isAfter(today.plusDays(club.getBookingHorizonDays()));
        List<StoryTime> times = availabilityService.freeSlotsFor(club, date).stream()
                .filter(SlotView::hasAvailability)
                .map(slot -> new StoryTime(slot.startTime(), timeLabel(slot.startTime()), slot.available().size()))
                .toList();
        return new DayStory(date, dayLabel(date), times, bookable);
    }

    /** "17 hs", "18:30 hs": como escriben los clubes los horarios en sus historias. */
    public static String timeLabel(LocalTime time) {
        String hour = time.getHour() == 0 ? "00" : String.valueOf(time.getHour());
        return time.getMinute() == 0
                ? hour + " hs"
                : hour + ":" + String.format("%02d", time.getMinute()) + " hs";
    }

    /** "Viernes 30/10". */
    public static String dayLabel(LocalDate date) {
        String weekday = date.getDayOfWeek().getDisplayName(TextStyle.FULL, ES_AR);
        return Character.toUpperCase(weekday.charAt(0)) + weekday.substring(1)
                + " " + date.getDayOfMonth() + "/" + date.getMonthValue();
    }
}
