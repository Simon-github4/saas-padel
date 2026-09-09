package ar.com.padelnec.service;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.RecurringBooking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.RecurringBookingRepository;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    private final RecurringBookingRepository recurringBookingRepository;
    private final BookingRepository bookingRepository;
    private final RecurringOccurrenceWriter occurrenceWriter;
    private final AlertService alertService;
    private final PricingService pricingService;
    private final AppProperties properties;
    private final Clock clock;

    /**
     * Genera los turnos que falten dentro del horizonte, para todos los turnos fijos
     * del club en contexto.
     *
     * @return cuantas reservas se crearon
     */
    public int materializeUpcoming(Tenant club) {
        LocalDate today = today(club);
        LocalDate horizon = today.plusWeeks(properties.getJobs().getRecurringHorizonWeeks());

        int created = 0;
        for (RecurringBooking fixed : recurringBookingRepository.findAllActiveWithDetail()) {
            created += materialize(club, fixed, today, horizon);
        }
        return created;
    }

    /**
     * Genera los turnos de un unico turno fijo, util al darlo de alta desde el panel.
     *
     * <p>Sin transaccion propia a proposito: cada ocurrencia se escribe en la suya y
     * los choques se avisan despues, ya fuera de la transaccion que fallo.
     */
    public int materialize(Tenant club, RecurringBooking fixed, LocalDate from, LocalDate until) {
        Set<LocalDate> alreadyThere = existingDates(club, fixed, from);
        List<LocalDate> conflicts = new ArrayList<>();

        int created = 0;
        for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
            if (!fixed.appliesOn(date) || alreadyThere.contains(date)) {
                continue;
            }
            try {
                occurrenceWriter.write(club, fixed, date, priceFor(club, fixed, date));
                created++;
            } catch (DataAccessException | TransactionException ex) {
                // La franja ya estaba tomada. El choque se atrapa aca, afuera de la
                // transaccion que fallo, que es el unico lugar desde el que todavia
                // se puede escribir la alerta.
                conflicts.add(date);
            }
        }
        conflicts.forEach(date -> reportConflict(fixed, date));
        return created;
    }

    /** No se pisa al que reservo antes: a quien reubicar lo decide una persona del club. */
    private void reportConflict(RecurringBooking fixed, LocalDate date) {
        log.warn("El turno fijo de {} choco el {} en {}",
                fixed.getCustomer().getFullName(), date, fixed.getCourt().getName());
        alertService.recurringConflict(
                ("El turno fijo de %s (%s, %s) no se pudo generar para el %s porque la franja ya "
                        + "estaba ocupada.").formatted(
                        fixed.getCustomer().getFullName(), fixed.getCourt().getName(),
                        fixed.getStartTime(), date));
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
}
