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
 *
 * <p>{@code playerAccountId} viaja aparte de {@code customerId} porque top
 * clientes necesita agrupar por cuenta cuando el turno esta vinculado a una
 * (ver {@code BookingStatsService#topCustomers}): un mismo jugador logueado
 * puede terminar en mas de un {@code Customer} del club si alguna vez
 * reservo con otro telefono, y sin la cuenta esas filas se ven como dos
 * personas distintas.
 */
public record BookingStatsRow(UUID id, BookingStatus status, Instant startTime, Instant endTime,
                              BigDecimal totalPrice, BigDecimal paidAmount,
                              CancellationReason cancellationReason,
                              UUID customerId, String customerFullName, String customerPhoneNumber,
                              UUID playerAccountId) {

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }
}
