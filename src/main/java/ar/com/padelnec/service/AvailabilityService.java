package ar.com.padelnec.service;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.service.SlotGenerator.Slot;
import ar.com.padelnec.web.dto.AvailabilityResponse;
import ar.com.padelnec.web.dto.AvailabilityResponse.CourtAvailability;
import ar.com.padelnec.web.dto.AvailabilityResponse.CourtSummary;
import ar.com.padelnec.web.dto.AvailabilityResponse.SlotView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de disponibilidad: cruza horario del club, turnos tomados, bloqueos y
 * precios, y devuelve la grilla lista para mostrar.
 *
 * <p>Toda la resolucion ocurre en memoria a partir de tres consultas acotadas al
 * dia (canchas, reservas y bloqueos), de modo que agregar canchas u horarios no
 * multiplica los viajes a la base.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    /** Estados que mantienen la cancha tomada, incluida la reserva a medio pagar. */
    private static final Set<BookingStatus> BLOCKING = EnumSet.of(
            BookingStatus.DRAFT,
            BookingStatus.AWAITING_CONFIRMATION,
            BookingStatus.CONFIRMED,
            BookingStatus.COMPLETED);

    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final BlackoutRepository blackoutRepository;
    private final PricingService pricingService;
    private final SlotGenerator slotGenerator;
    private final Clock clock;

    /**
     * Grilla publica de un dia. Requiere que el club ya este en
     * {@code TenantContext}: quien la invoca lo resolvio por slug.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse availabilityFor(Tenant club, LocalDate date) {
        Instant now = clock.instant();
        List<Court> courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        List<Slot> slots = slotGenerator.generate(club, date);

        if (courts.isEmpty() || slots.isEmpty() || isOutsideBookingWindow(club, date, now)) {
            return emptyGrid(club, date, courts);
        }

        Instant from = slots.getFirst().startsAt();
        Instant until = slots.getLast().endsAt();

        List<Booking> taken = bookingRepository.findOverlapping(from, until, BLOCKING);
        List<Blackout> blackouts = blackoutRepository.findOverlapping(from, until);
        List<PricingRule> rules = pricingService.rulesFor(date.getDayOfWeek());

        List<SlotView> views = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            if (slot.isInThePast(now)) {
                // Un horario que ya empezo no se ofrece, aunque la cancha este libre.
                continue;
            }
            views.add(new SlotView(
                    slot.startTime(), slot.endTime(), slot.startsAt(), slot.endsAt(),
                    freeCourts(courts, slot, taken, blackouts, rules, date.getDayOfWeek())));
        }

        return new AvailabilityResponse(
                summarize(club), date, club.getDefaultSlotDuration(), toSummaries(courts), views);
    }

    /** Indica si una cancha concreta esta libre en una franja concreta. */
    @Transactional(readOnly = true)
    public boolean isCourtFree(Court court, Instant start, Instant end) {
        boolean taken = bookingRepository.findOverlapping(start, end, BLOCKING).stream()
                .anyMatch(booking -> booking.getCourt().getId().equals(court.getId()));
        if (taken) {
            return false;
        }
        return blackoutRepository.findOverlapping(start, end).stream()
                .noneMatch(blackout -> blackout.appliesTo(court) && blackout.overlaps(start, end));
    }

    // ------------------------------------------------------------- internos

    private List<CourtAvailability> freeCourts(List<Court> courts, Slot slot, List<Booking> taken,
                                               List<Blackout> blackouts, List<PricingRule> rules,
                                               DayOfWeek day) {
        List<CourtAvailability> free = new ArrayList<>();
        for (Court court : courts) {
            boolean occupied = taken.stream()
                    .anyMatch(booking -> booking.getCourt().getId().equals(court.getId())
                            && booking.overlaps(slot.startsAt(), slot.endsAt()));
            if (occupied) {
                continue;
            }
            boolean blocked = blackouts.stream()
                    .anyMatch(blackout -> blackout.appliesTo(court)
                            && blackout.overlaps(slot.startsAt(), slot.endsAt()));
            if (blocked) {
                continue;
            }
            Optional<BigDecimal> price = pricingService.resolve(rules, court, day, slot.startTime());
            // Sin tarifa configurada el turno no se publica: no se vende en cero.
            price.ifPresent(value -> free.add(new CourtAvailability(court.getId(), court.getName(), value)));
        }
        return free;
    }

    private boolean isOutsideBookingWindow(Tenant club, LocalDate date, Instant now) {
        LocalDate today = now.atZone(club.zoneId()).toLocalDate();
        return date.isBefore(today) || date.isAfter(today.plusDays(club.getBookingHorizonDays()));
    }

    private AvailabilityResponse emptyGrid(Tenant club, LocalDate date, List<Court> courts) {
        return new AvailabilityResponse(
                summarize(club), date, club.getDefaultSlotDuration(), toSummaries(courts), List.of());
    }

    private List<CourtSummary> toSummaries(List<Court> courts) {
        return courts.stream().map(c -> new CourtSummary(c.getId(), c.getName())).toList();
    }

    private AvailabilityResponse.ClubSummary summarize(Tenant club) {
        return new AvailabilityResponse.ClubSummary(
                club.getSlug(),
                club.getName(),
                club.getTimeZone(),
                club.getWhatsappNumber(),
                club.isAllowUnpaidBooking(),
                club.acceptsOnlinePayments(),
                club.getDepositPercentage(),
                club.getCancellationLimitHours());
    }
}
