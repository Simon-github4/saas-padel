package ar.com.padelnec.service;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.RecurringBooking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.RecurringBookingRepository;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convierte los turnos fijos en reservas concretas.
 *
 * <p>El turno fijo es una regla, no una reserva. Un horizonte movil lo materializa
 * como {@link Booking} comunes, de modo que la grilla publica, el panel y los
 * cobros ven turnos normales y no tienen que entender nada de recurrencia.
 *
 * <p>El horizonte se acota a proposito: generar un ano de turnos por adelantado
 * llenaria la agenda de reservas que nadie va a honrar cuando el grupo se disuelva
 * a mitad de temporada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringBookingService {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final Set<BookingStatus> OCCUPYING = EnumSet.copyOf(
            Arrays.stream(BookingStatus.values()).filter(BookingStatus::occupiesSlot).toList());

    private final RecurringBookingRepository recurringBookingRepository;
    private final BookingRepository bookingRepository;
    private final RecurringOccurrenceWriter occurrenceWriter;
    private final AlertService alertService;
    private final PricingService pricingService;
    private final AppProperties properties;
    private final Clock clock;

    /** Una fecha del turno fijo que no se pudo generar porque la cancha ya estaba tomada, y por quien. */
    public record Conflict(LocalDate date, String occupant) {
    }

    /**
     * Genera los turnos que falten dentro del horizonte, para todos los turnos fijos
     * del club en contexto. Es la corrida de cada noche.
     *
     * <p>Una fecha que choca se vuelve a intentar cada noche (por si se libero la
     * cancha), pero se avisa una sola vez: cuando entra al horizonte. Antes se avisaba
     * en cada intento, y un club con varios turnos fijos cargados terminaba con cientos
     * de alertas repetidas del mismo choque. Los choques vigentes estan siempre a la
     * vista en Turnos fijos ({@link #conflicts}).
     *
     * @return cuantas reservas se crearon
     */
    public int materializeUpcoming(Tenant club) {
        LocalDate today = today(club);
        LocalDate horizon = horizon(today);

        int created = 0;
        for (RecurringBooking fixed : recurringBookingRepository.findAllActiveWithDetail()) {
            List<LocalDate> conflicts = new ArrayList<>();
            created += materialize(club, fixed, today, horizon, conflicts);
            conflicts.stream()
                    .filter(horizon::equals)
                    .forEach(date -> reportConflict(fixed, date));
        }
        return created;
    }

    /**
     * Genera las semanas de un turno fijo recien dado de alta, y solo las suyas.
     *
     * <p>Sin alertas: los choques el club ya los vio antes de confirmar, y quedan a la
     * vista en Turnos fijos.
     */
    public int materializeNew(Tenant club, RecurringBooking fixed) {
        LocalDate today = today(club);
        return materialize(club, fixed, today, horizon(today), new ArrayList<>());
    }

    /**
     * Sin transaccion propia a proposito: cada ocurrencia se escribe en la suya, y un
     * choque se atrapa afuera de la transaccion que fallo.
     */
    private int materialize(Tenant club, RecurringBooking fixed, LocalDate from, LocalDate until,
                            List<LocalDate> conflicts) {
        Set<LocalDate> alreadyThere = existingDates(club, fixed, from);

        int created = 0;
        for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
            if (!fixed.appliesOn(date) || alreadyThere.contains(date)) {
                continue;
            }
            try {
                occurrenceWriter.write(club, fixed, date, priceFor(club, fixed, date));
                created++;
            } catch (DataAccessException | TransactionException ex) {
                conflicts.add(date);
            }
        }
        return created;
    }

    /** No se pisa al que reservo antes: a quien reubicar lo decide una persona del club. */
    private void reportConflict(RecurringBooking fixed, LocalDate date) {
        log.warn("El turno fijo de {} choco el {} en {}",
                fixed.getCustomer().getFullName(), date, fixed.getCourt().getName());
        alertService.recurringConflict(
                ("El turno fijo de %s (%s, %s) no se pudo generar para el %s porque la franja ya "
                        + "estaba ocupada. Lo ves en Turnos fijos.").formatted(
                        fixed.getCustomer().getFullName(), fixed.getCourt().getName(),
                        fixed.getStartTime(), date.format(DAY_MONTH)));
    }

    /**
     * Las fechas del horizonte en que el turno fijo no tiene su turno porque la cancha
     * ya esta tomada por otro, por turno fijo.
     *
     * <p>Sirve igual para un turno fijo que todavia no se guardo: es lo que el panel le
     * muestra al club antes de confirmar el alta.
     */
    @Transactional(readOnly = true)
    public Map<RecurringBooking, List<Conflict>> conflicts(Tenant club, Collection<RecurringBooking> fixedBookings) {
        ZoneId zone = club.zoneId();
        Instant now = clock.instant();
        LocalDate today = today(club);
        LocalDate horizon = horizon(today);
        // Un dia de mas al final: un turno fijo de madrugada termina al dia siguiente.
        List<Booking> taken = bookingRepository.findOverlapping(
                today.atStartOfDay(zone).toInstant(), horizon.plusDays(2).atStartOfDay(zone).toInstant(),
                OCCUPYING);

        Map<RecurringBooking, List<Conflict>> result = new HashMap<>();
        for (RecurringBooking fixed : fixedBookings) {
            List<Conflict> found = new ArrayList<>();
            for (LocalDate date = today; !date.isAfter(horizon); date = date.plusDays(1)) {
                if (!fixed.appliesOn(date)) {
                    continue;
                }
                Instant start = date.atTime(fixed.getStartTime()).atZone(zone).toInstant();
                Instant end = start.plus(Duration.ofMinutes(fixed.getDurationMinutes()));
                if (!start.isAfter(now)) {
                    continue;
                }
                List<Booking> onCourt = taken.stream()
                        .filter(booking -> booking.getCourt().getId().equals(fixed.getCourt().getId())
                                && booking.overlaps(start, end))
                        .toList();
                if (onCourt.stream().anyMatch(booking -> isOccurrenceOf(booking, fixed))) {
                    continue;
                }
                if (!onCourt.isEmpty()) {
                    found.add(new Conflict(date, occupantOf(onCourt.getFirst())));
                }
            }
            result.put(fixed, found);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Conflict> conflicts(Tenant club, RecurringBooking fixed) {
        return conflicts(club, List.of(fixed)).get(fixed);
    }

    private static boolean isOccurrenceOf(Booking booking, RecurringBooking fixed) {
        return fixed.getId() != null && booking.getRecurringBooking() != null
                && fixed.getId().equals(booking.getRecurringBooking().getId());
    }

    private static String occupantOf(Booking booking) {
        String origin = switch (booking.getSource()) {
            case RECURRING -> "turno fijo";
            case WEB -> "reserva web";
            default -> "cargado en el panel";
        };
        return "%s (%s)".formatted(booking.displayName(), origin);
    }

    /**
     * El grupo avisa que una semana no juega.
     *
     * <p>Ademas de anotar la excepcion, se cancela la ocurrencia ya generada: si no,
     * la cancha seguiria figurando ocupada y el club perderia la posibilidad de
     * revenderla.
     */
    @Transactional
    public void skipDate(Tenant club, UUID recurringId, LocalDate date, String reason) {
        RecurringBooking fixed = require(recurringId);
        fixed.skip(date, reason);
        recurringBookingRepository.save(fixed);

        occurrencesFrom(recurringId).stream()
                .filter(booking -> localDateOf(club, booking).equals(date))
                .forEach(booking -> {
                    booking.markCancelled(CancellationReason.CLUB, clock.instant());
                    booking.setAdminNotes(reason);
                    bookingRepository.save(booking);
                });
    }

    /**
     * Baja del turno fijo.
     *
     * @return cuantas ocurrencias futuras se cancelaron
     */
    @Transactional
    public int deactivate(UUID recurringId) {
        RecurringBooking fixed = require(recurringId);
        fixed.setActive(false);
        recurringBookingRepository.save(fixed);

        List<Booking> upcoming = occurrencesFrom(recurringId);
        upcoming.forEach(booking -> booking.markCancelled(CancellationReason.CLUB, clock.instant()));
        bookingRepository.saveAll(upcoming);
        return upcoming.size();
    }

    @Transactional
    public RecurringBooking save(RecurringBooking fixed) {
        return recurringBookingRepository.save(fixed);
    }

    /** Solo los vigentes: uno dado de baja ya no participa de nada operativo,
     * asi que no tiene sentido dejarlo ensuciando el listado para siempre. */
    @Transactional(readOnly = true)
    public List<RecurringBooking> all() {
        return recurringBookingRepository.findAllActiveWithDetail();
    }

    // ------------------------------------------------------------ internos

    private RecurringBooking require(UUID recurringId) {
        return recurringBookingRepository.findById(recurringId)
                .orElseThrow(() -> new ResourceNotFoundException("El turno fijo no existe"));
    }

    /** Ocurrencias todavia no jugadas, que son las unicas que tiene sentido tocar. */
    private List<Booking> occurrencesFrom(UUID recurringId) {
        return bookingRepository.findMaterialized(recurringId, clock.instant());
    }

    private Set<LocalDate> existingDates(Tenant club, RecurringBooking fixed, LocalDate from) {
        Instant fromInstant = from.atStartOfDay(club.zoneId()).toInstant();
        Set<LocalDate> dates = new HashSet<>();
        for (Booking booking : bookingRepository.findMaterialized(fixed.getId(), fromInstant)) {
            dates.add(localDateOf(club, booking));
        }
        return dates;
    }

    /** El precio pactado con el grupo, o la tarifa vigente de la franja. */
    private BigDecimal priceFor(Tenant club, RecurringBooking fixed, LocalDate date) {
        if (fixed.getPriceOverride() != null) {
            return fixed.getPriceOverride();
        }
        return pricingService.resolve(
                        club, pricingService.rulesFor(club, date.getDayOfWeek()),
                        fixed.getCourt(), date.getDayOfWeek(), fixed.getStartTime())
                .map(PricingService.ResolvedPrice::totalPrice)
                .orElse(BigDecimal.ZERO);
    }

    /** Siempre en la zona del club: comparar fechas en UTC corre los turnos de noche. */
    private LocalDate localDateOf(Tenant club, Booking booking) {
        return booking.getStartTime().atZone(club.zoneId()).toLocalDate();
    }

    private LocalDate today(Tenant club) {
        return clock.instant().atZone(club.zoneId()).toLocalDate();
    }

    private LocalDate horizon(LocalDate today) {
        return today.plusWeeks(properties.getJobs().getRecurringHorizonWeeks());
    }
}
