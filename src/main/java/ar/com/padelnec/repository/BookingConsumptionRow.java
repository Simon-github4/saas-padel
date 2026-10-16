package ar.com.padelnec.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Lo que se consumio del buffet en un turno, sumado: lo que hay que restarle al total para llegar a la cancha. */
public record BookingConsumptionRow(UUID bookingId, BigDecimal total) {
}
