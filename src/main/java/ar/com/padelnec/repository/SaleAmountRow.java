package ar.com.padelnec.repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una venta del buffet vista por las estadisticas: cuando se vendio y cuanto.
 *
 * <p>Como {@link PaymentCashRow} para los cobros: sin el producto ni el pedido,
 * que para sumar lo facturado de un periodo no hacen falta.
 */
public record SaleAmountRow(Instant createdAt, int quantity, BigDecimal unitPrice) {

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
