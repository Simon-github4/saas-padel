package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.BuffetCoverage;
import ar.com.padelnec.support.BuffetCoverage.Line;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cuánto de cada producto de una cuenta del buffet ya está pagado. */
class BuffetCoverageTest {

    private static final Line MILANESA = new Line(new BigDecimal("12000"), 1, false);
    private static final Line GASEOSAS = new Line(new BigDecimal("3000"), 2, false);

    @Test
    @DisplayName("Lo pagado por monto cubre en el orden de carga, y el último al que llega queda a medio pagar")
    void anAmountCoversInLoadingOrder() {
        List<BigDecimal> covered = BuffetCoverage.covered(List.of(MILANESA, MILANESA, GASEOSAS),
                new BigDecimal("20000"));

        assertThat(covered).extracting(BigDecimal::intValue).containsExactly(12000, 8000, 0);
    }

    @Test
    @DisplayName("Lo cobrado eligiéndolo no se cubre dos veces: el monto sigue con lo pendiente")
    void pickedLinesAreNotCoveredAgain() {
        Line paidSoda = new Line(new BigDecimal("3000"), 2, true);

        // 6.000 pagaron las gaseosas elegidas; los otros 5.000 fueron por monto.
        List<BigDecimal> covered = BuffetCoverage.covered(List.of(paidSoda, MILANESA), new BigDecimal("11000"));

        assertThat(BuffetCoverage.unassigned(List.of(paidSoda, MILANESA), new BigDecimal("11000")))
                .isEqualByComparingTo("5000");
        assertThat(covered).extracting(BigDecimal::intValue).containsExactly(0, 5000);
    }

    @Test
    @DisplayName("Elegir lo que quedó a medio pagar cobra solo lo que le falta")
    void pickingAHalfPaidLineChargesWhatIsLeft() {
        assertThat(BuffetCoverage.cost(MILANESA, 1, new BigDecimal("8000"))).isEqualByComparingTo("4000");
        assertThat(BuffetCoverage.cost(GASEOSAS, 1, BigDecimal.ZERO)).isEqualByComparingTo("3000");
        assertThat(BuffetCoverage.cost(GASEOSAS, 1, new BigDecimal("5000"))).isEqualByComparingTo("0");
    }
}
