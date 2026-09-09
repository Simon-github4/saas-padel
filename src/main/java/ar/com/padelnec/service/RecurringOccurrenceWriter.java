package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.RecurringBooking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingSource;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.support.Tokens;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe una unica ocurrencia de un turno fijo, en su propia transaccion.
 *
 * <p>Vive en su propio bean para que {@code REQUIRES_NEW} se aplique de verdad: si
 * el metodo estuviera en la clase que lo llama, la invocacion seria interna y
 * Spring nunca abriria la transaccion separada. Esa separacion es el punto: cuando
 * una fecha choca contra un turno ya vendido, se pierde solo esa semana y el resto
 * del grupo se genera igual.
 *
 * <p>El choque se deja propagar. Atraparlo aca no alcanzaria: una vez que la
 * restriccion falla, la transaccion queda marcada para rollback y volver de este
 * metodo como si nada solo consigue que el commit falle despues. Quien decide que
 * hacer con el conflicto es el llamador, ya fuera de esta transaccion.
 */
@Component
@RequiredArgsConstructor
public class RecurringOccurrenceWriter {

    private final BookingRepository bookingRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Tenant club, RecurringBooking fixed, LocalDate date, BigDecimal price) {
        Instant start = date.atTime(fixed.getStartTime()).atZone(club.zoneId()).toInstant();

        Booking booking = new Booking();
        booking.setCourt(fixed.getCourt());
        booking.setCustomer(fixed.getCustomer());
        booking.setRecurringBooking(fixed);
        booking.setStartTime(start);
        booking.setEndTime(start.plus(Duration.ofMinutes(fixed.getDurationMinutes())));
        // Nace confirmado: el grupo ya tiene el turno pactado con el club, no hay
        // nada que confirmar ni sena que esperar.
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(BookingSource.RECURRING);
        booking.setTotalPrice(price);
        booking.setManagementToken(Tokens.generate());
        booking.setShareToken(Tokens.generate());

        bookingRepository.saveAndFlush(booking);
    }
}
