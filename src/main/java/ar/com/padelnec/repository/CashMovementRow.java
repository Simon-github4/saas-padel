package ar.com.padelnec.repository;

import ar.com.padelnec.domain.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un renglon de la caja del dia: la plata que se movio y el turno o el pedido de
 * buffet que la explica.
 *
 * <p>{@code amount} negativo es una devolucion de mostrador (ver
 * {@code PaymentService.registerRefund}), no un cobro con el signo cambiado: en
 * la caja sale como un renglon propio y resta del total del metodo.
 *
 * <p>Uno de {@code bookingId} y {@code buffetOrderId} es nulo. En un cobro de
 * buffet, {@code courtName}, {@code bookingStartTime} y
 * {@code bookingTotalPrice} tambien: no hay turno, y {@code customerFullName} es
 * el "a nombre de" del pedido. {@code bookingTotalPrice} es el total del turno
 * con sus consumiciones, el que hace falta para repartir el cobro entre la
 * cancha y el buffet.
 */
public record CashMovementRow(UUID paymentId, Instant createdAt, PaymentMethod method, BigDecimal amount,
                              UUID registeredBy, UUID bookingId, UUID buffetOrderId,
                              String customerFullName, String courtName, Instant bookingStartTime,
                              BigDecimal bookingTotalPrice) {
}
