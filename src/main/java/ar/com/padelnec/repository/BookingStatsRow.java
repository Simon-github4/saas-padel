package ar.com.padelnec.repository;

import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que {@code BookingStatsService} necesita de cada turno para agregar
 * estadisticas, ni un campo mas.
 *
 * <p>La cancha no viaja: no se lee en ningun lado de las estadisticas. De
 * {@code Customer} solo van nombre y telefono (mas el id, como clave de
 * agrupacion), no {@code trusted}/{@code blocked}/{@code noShowCount}/{@code notes}.
 * Antes esto salia de traer la entidad {@code Booking} completa con
 * {@code JOIN FETCH court, customer} -- para un año de turnos, cientos o miles
 * de filas cargando una cancha y un cliente enteros que nadie iba a leer.
 */
public record BookingStatsRow(UUID id, BookingStatus status, Instant startTime, Instant endTime,
                              BigDecimal totalPrice, BigDecimal paidAmount,
                              CancellationReason cancellationReason,
                              UUID customerId, String customerFullName, String customerPhoneNumber) {

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }
}
