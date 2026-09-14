package ar.com.padelnec.repository;

import java.math.BigDecimal;

/**
 * Una linea del buffet vendida dentro de una ventana, en un turno o en un pedido suelto.
 *
 * <p>Nombre y precio son los que quedaron congelados en la venta, no los del
 * catalogo de hoy: si el club actualizo el precio, el corte de un dia pasado
 * tiene que seguir dando lo mismo que dio ese dia.
 */
public record BuffetSaleRow(String productName, int quantity, BigDecimal unitPrice) {

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
