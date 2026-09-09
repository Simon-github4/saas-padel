package ar.com.padelnec.repository;

import ar.com.padelnec.domain.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un movimiento de plata visto por las estadisticas: cuando entro, por que via
 * y cuanto.
 *
 * <p>No trae el turno que lo origino: sumar "lo cobrado" no necesita saber de
 * quien era, y sobre un año de pagos esas dos uniones (cliente y cancha) se
 * pagan por fila. La caja del dia si las muestra, pero nunca mira mas de un dia,
 * asi que tiene su propia proyeccion en {@link CashMovementRow}.
 */
public record PaymentCashRow(Instant createdAt, PaymentMethod method, BigDecimal amount) {
}
