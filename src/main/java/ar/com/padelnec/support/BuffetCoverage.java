package ar.com.padelnec.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuánto de cada producto de una cuenta del buffet ya está pagado.
 *
 * <p>Una cuenta se paga de a partes, de dos formas que se pueden mezclar:
 * eligiendo productos («estas 3 milanesas las paga él»), que quedan marcados
 * como cobrados, o por monto («te doy 20.000»), que no elige nada. Lo pagado
 * por monto cubre los productos pendientes en el orden en que se cargaron, y el
 * último al que llega puede quedar a medio pagar: así el mostrador ve qué falta
 * y de qué producto.
 *
 * <p>La misma cuenta para la pantalla, que la muestra, y para el servicio, que
 * cobra con ella: si no coincidieran, lo que se ve no sería lo que se cobra.
 */
public final class BuffetCoverage {

    private BuffetCoverage() {
    }

    /** Un producto de la cuenta: precio, cantidad y si ya se cobró eligiéndolo. */
    public record Line(BigDecimal unitPrice, int quantity, boolean paid) {

        public BigDecimal subtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Lo pagado por monto que todavía no quedó marcado en ningún producto: lo
     * cobrado menos lo que suman los productos ya cobrados.
     */
    public static BigDecimal unassigned(List<Line> lines, BigDecimal paidAmount) {
        BigDecimal marked = lines.stream()
                .filter(Line::paid)
                .map(Line::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return paidAmount.subtract(marked).max(BigDecimal.ZERO);
    }

    /**
     * Cuánto le cubre lo pagado por monto a cada producto, en el mismo orden:
     * todo su subtotal, una parte o nada. A los ya cobrados no les falta nada,
     * así que no se les reparte: dan cero.
     */
    public static List<BigDecimal> covered(List<Line> lines, BigDecimal paidAmount) {
        BigDecimal left = unassigned(lines, paidAmount);
        List<BigDecimal> covered = new ArrayList<>(lines.size());
        for (Line line : lines) {
            BigDecimal part = line.paid() ? BigDecimal.ZERO : left.min(line.subtotal());
            covered.add(part);
            left = left.subtract(part);
        }
        return covered;
    }

    /**
     * Lo que cuesta cobrar ahora algunas unidades de un producto. Lo que ya le
     * cubre el pago por monto se descuenta de esas unidades primero: elegir la
     * milanesa que quedó a medio pagar cobra solo lo que le falta.
     */
    public static BigDecimal cost(Line line, int units, BigDecimal covered) {
        return line.unitPrice().multiply(BigDecimal.valueOf(units)).subtract(covered).max(BigDecimal.ZERO);
    }
}
