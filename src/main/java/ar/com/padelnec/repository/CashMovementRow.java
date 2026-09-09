package ar.com.padelnec.repository;

import ar.com.padelnec.domain.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un renglon de la caja del dia: la plata que se movio y el turno que la
 * explica.
 *
 * <p>{@code amount} negativo es una devolucion de mostrador (ver
 * {@code PaymentService.registerRefund}), no un cobro con el signo cambiado: en
 * la caja sale como un renglon propio y resta del total del metodo.
 */
public record CashMovementRow(Instant createdAt, PaymentMethod method, BigDecimal amount,
                              UUID registeredBy, UUID bookingId, String customerFullName,
                              String courtName, Instant bookingStartTime) {
}
