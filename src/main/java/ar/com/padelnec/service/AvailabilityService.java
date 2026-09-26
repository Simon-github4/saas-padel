package ar.com.padelnec.service;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.ClubAmenity;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.CourtSchedule;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.ClubAmenityRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CourtScheduleRepository;
import ar.com.padelnec.service.SlotGenerator.Slot;
import ar.com.padelnec.web.dto.AvailabilityResponse;
import ar.com.padelnec.web.dto.AvailabilityResponse.AmenityView;
import ar.com.padelnec.web.dto.AvailabilityResponse.CourtAvailability;
import ar.com.padelnec.web.dto.AvailabilityResponse.CourtSummary;
import ar.com.padelnec.web.dto.AvailabilityResponse.SlotView;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final CourtScheduleRepository courtScheduleRepository;
    private final BookingRepository bookingRepository;
    private final BlackoutRepository blackoutRepository;
    private final ClubAmenityRepository clubAmenityRepository;
    private final PricingService pricingService;
    private final SlotGenerator slotGenerator;
    private final Clock clock;

    /**
     * Grilla publica de un dia. Requiere que el club ya este en
     * {@code TenantContext}: quien la invoca lo resolvio por slug.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse availabilityFor(Tenant club, LocalDate date) {
        List<Court> courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        List<SlotView> views = freeSlots(club, date, courts);
        return new AvailabilityResponse(
                summarize(club), date, club.getDefaultSlotDuration(), toSummaries(courts), views);
    }

    /**
     * Solo los horarios libres del dia, sin nada del club.
     *
     * <p>Lo usa {@code CourtSearchService}, que recorre todos los clubes activos
     * buscando turno: ya tiene nombre, ciudad y demas datos del propio
     * {@code Tenant} que cargo el, y de la respuesta completa de
     * {@link #availabilityFor} solo lee {@code slots()} — el resumen del club
     * (con su consulta aparte de servicios, y el desencriptado de las
     * credenciales de MercadoPago que trae {@code Tenant}) se armaba y se
     * tiraba en cada club de cada busqueda.
     */
    @Transactional(readOnly = true)
    public List<SlotView> freeSlotsFor(Tenant club, LocalDate date) {
        return freeSlots(club, date, courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc());
    }

    private List<SlotView> freeSlots(Tenant club, LocalDate date, List<Court> courts) {
        Instant now = clock.instant();
        SlotGenerator.DayPlan plan = slotGenerator.plan(club, date);
        List<Slot> slots = plan.slots();

        if (courts.isEmpty() || slots.isEmpty() || isOutsideBookingWindow(club, date, now)) {
            return List.of();
        }

        Instant from = slots.getFirst().startsAt();
        Instant until = slots.getLast().endsAt();

        List<Booking> taken = bookingRepository.findOverlapping(from, until, BLOCKING);
        List<Blackout> blackouts = blackoutRepository.findOverlapping(from, until);
        List<PricingRule> rules = pricingService.rulesFor(club, date.getDayOfWeek());

        List<SlotView> views = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            if (slot.isInThePast(now)) {
                // Un horario que ya empezo no se ofrece, aunque la cancha este libre.
                continue;
            }
            views.add(new SlotView(
                    slot.startTime(), slot.endTime(), slot.startsAt(), slot.endsAt(),
                    freeCourts(club, plan, courts, slot, taken, blackouts, rules, date.getDayOfWeek()),
                    pricingService.isSlotPromo(club, rules, date.getDayOfWeek(), slot.startTime())));
        }
        return views;
    }

    /** Indica si una cancha concreta esta libre en una franja concreta. */
    @Transactional(readOnly = true)
    public boolean isCourtFree(Court court, Instant start, Instant end) {
        return !isCourtOccupied(court, start, end) && !isCourtSuspended(court, start, end);
    }

    /** Una excepcion administrativa puede ignorar cierres, nunca otra reserva. */
    @Transactional(readOnly = true)
    public boolean isCourtOccupied(Court court, Instant start, Instant end) {
        return bookingRepository.findOverlapping(start, end, BLOCKING).stream()
                .anyMatch(booking -> booking.getCourt().getId().equals(court.getId()));
    }

    /** Bloqueo puntual por torneo, mantenimiento, feriado u otro motivo. */
    @Transactional(readOnly = true)
    public boolean isCourtSuspended(Court court, Instant start, Instant end) {
        return blackoutRepository.findOverlapping(start, end).stream()
                .anyMatch(blackout -> blackout.appliesTo(court) && blackout.overlaps(start, end));
    }

    /**
     * Indica si alguna cancha activa, de las que abren en ese turno, esta libre. Lo
     * usa la lista de espera, a la que no le importa cual cancha se libero, solo
     * que haya alguna.
     */
    @Transactional(readOnly = true)
    public boolean anyCourtFree(SlotGenerator.ResolvedPlan slot) {
        Instant start = slot.resolved().slot().startsAt();
        Instant end = slot.resolved().slot().endsAt();
        return courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .anyMatch(court -> slot.opens(court) && isCourtFree(court, start, end));
    }

    /** Una franja de horario, tal como la necesita {@link #anyCourtFreeForSlots}. */
    public record SlotWindow(Instant startsAt, Instant endsAt) {
    }

    /**
     * Igual que {@link #anyCourtFree}, pero para varios horarios a la vez: trae
     * canchas, turnos y bloqueos una sola vez para todo el rango pedido y resuelve
     * cada horario en memoria, en vez de repetir las mismas 2 consultas por cancha
     * y por horario. Lo usa la lista de espera, donde antes un barrido con varios
     * horarios llenos y varias canchas terminaba en decenas de consultas
     * identicas. Mismo patron que ya usa {@link #freeSlots}.
     */
    @Transactional(readOnly = true)
    public Map<SlotWindow, Boolean> anyCourtFreeForSlots(Tenant club, Collection<SlotWindow> windows) {
        if (windows.isEmpty()) {
            return Map.of();
        }
        List<Court> courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        Instant from = windows.stream().map(SlotWindow::startsAt).min(Instant::compareTo).orElseThrow();
        Instant until = windows.stream().map(SlotWindow::endsAt).max(Instant::compareTo).orElseThrow();
        List<Booking> taken = bookingRepository.findOverlapping(from, until, BLOCKING);
        List<Blackout> blackouts = blackoutRepository.findOverlapping(from, until);

        List<CourtSchedule> schedules = courtScheduleRepository.findAllWithCourt();

        Map<SlotWindow, Boolean> result = new LinkedHashMap<>();
        for (SlotWindow window : windows) {
            // Una cancha que ese dia no abre a esa hora no cuenta como libre. Un horario
            // que ya no esta en la grilla (el club cambio su horario despues de que se
            // anotaran) se sigue mirando cancha por cancha, como siempre.
            Optional<SlotGenerator.ResolvedPlan> slot =
                    slotGenerator.resolveWithPlan(club, window.startsAt(), courts, schedules);
            boolean free = courts.stream()
                    .anyMatch(court -> slot.map(s -> s.opens(court)).orElse(true)
                            && isFreeInMemory(court, window, taken, blackouts));
            result.put(window, free);
        }
        return result;
    }

    private boolean isFreeInMemory(Court court, SlotWindow window, List<Booking> taken,
                                   List<Blackout> blackouts) {
        boolean occupied = taken.stream()
                .anyMatch(booking -> booking.getCourt().getId().equals(court.getId())
                        && booking.overlaps(window.startsAt(), window.endsAt()));
        if (occupied) {
            return false;
        }
        return blackouts.stream()
                .noneMatch(blackout -> blackout.appliesTo(court)
                        && blackout.overlaps(window.startsAt(), window.endsAt()));
    }

    // ------------------------------------------------------------- internos

    private List<CourtAvailability> freeCourts(Tenant club, SlotGenerator.DayPlan plan, List<Court> courts,
                                               Slot slot, List<Booking> taken, List<Blackout> blackouts,
                                               List<PricingRule> rules, DayOfWeek day) {
        List<CourtAvailability> free = new ArrayList<>();
        for (Court court : courts) {
            if (!plan.opens(court, slot)) {
                continue;
            }
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
            Optional<PricingService.ResolvedPrice> price =
                    pricingService.resolve(club, rules, court, day, slot.startTime());
            // Sin tarifa configurada (ni regla ni general) el turno no se publica:
            // no se vende en cero.
            price.ifPresent(value -> free.add(new CourtAvailability(
                    court.getId(), court.getName(), value.totalPrice(),
                    court.getWall(), court.getSurface(), court.getRoof())));
        }
        return free;
    }

    private boolean isOutsideBookingWindow(Tenant club, LocalDate date, Instant now) {
        LocalDate today = now.atZone(club.zoneId()).toLocalDate();
        return date.isBefore(today) || date.isAfter(today.plusDays(club.getBookingHorizonDays()));
    }

    private List<CourtSummary> toSummaries(List<Court> courts) {
        return courts.stream().map(c -> new CourtSummary(c.getId(), c.getName())).toList();
    }

    private AvailabilityResponse.ClubSummary summarize(Tenant club) {
        List<AmenityView> amenities = clubAmenityRepository.findAllByOrderByDisplayOrderAscTitleAsc()
                .stream()
                .map(this::toAmenityView)
                .toList();
        return new AvailabilityResponse.ClubSummary(
                club.getSlug(),
                club.getName(),
                club.getTimeZone(),
                club.getWhatsappNumber(),
                club.getInstagramHandle(),
                club.instagramUrl(),
                club.isAllowUnpaidBooking(),
                club.acceptsOnlinePayments(),
                club.getDepositPercentage(),
                club.getCancellationLimitHours(),
                club.getBookingHorizonDays(),
                club.getTagline(),
                club.getAddress(),
                club.getCity(),
                club.mapsUrl(),
                club.mapsEmbedQuery(),
                club.getLatitude(),
                club.getLongitude(),
                club.getHeroImageUrl(),
                club.getHeroHeadline(),
                club.getHeroCtaLabel(),
                club.getHeroOverlay(),
                club.getHeroVariant().name(),
                club.getPlayersPerCourt(),
                club.getThemeMode().name(),
                club.getPrimaryColor(),
                club.getSecondaryColor(),
                amenities);
    }

    private AmenityView toAmenityView(ClubAmenity amenity) {
        return new AmenityView(amenity.getIcon(), amenity.getTitle(), amenity.getDescription());
    }
}
