package ar.com.padelnec.service;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.ClubAmenity;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.ClubAmenityRepository;
import ar.com.padelnec.repository.CourtRepository;
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
        List<Slot> slots = slotGenerator.generate(club, date);

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
                    freeCourts(club, courts, slot, taken, blackouts, rules, date.getDayOfWeek()),
                    pricingService.isSlotPromo(club, rules, date.getDayOfWeek(), slot.startTime())));
        }
        return views;
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

    /**
     * Indica si alguna cancha activa esta libre en una franja. Lo usa la lista de
     * espera, a la que no le importa cual cancha se libero, solo que haya alguna.
     */
    @Transactional(readOnly = true)
    public boolean anyCourtFree(Instant start, Instant end) {
        return courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .anyMatch(court -> isCourtFree(court, start, end));
    }

    // ------------------------------------------------------------- internos

    private List<CourtAvailability> freeCourts(Tenant club, List<Court> courts, Slot slot,
                                               List<Booking> taken, List<Blackout> blackouts,
                                               List<PricingRule> rules, DayOfWeek day) {
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
            Optional<PricingService.ResolvedPrice> price =
                    pricingService.resolve(club, rules, court, day, slot.startTime());
            // Sin tarifa configurada (ni regla ni general) el turno no se publica:
            // no se vende en cero.
            price.ifPresent(value -> free.add(
                    new CourtAvailability(court.getId(), court.getName(), value.totalPrice())));
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
