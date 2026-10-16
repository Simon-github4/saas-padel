package ar.com.padelnec.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un cobro (o una devolucion, en negativo) de un turno, en el orden en que entro.
 *
 * <p>Lo lee la caja del dia para repartir cada cobro entre la cancha y el
 * buffet: para saber que parte de un cobro de hoy fue cancha hay que saber
 * cuanto ya se habia pagado de ese turno antes, aunque haya sido otro dia.
 */
public record BookingPaymentRow(UUID paymentId, UUID bookingId, Instant createdAt, BigDecimal amount) {
}
