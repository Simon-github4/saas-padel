package ar.com.padelnec.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Un unico formato de plata para todo el panel.
 *
 * <p>Habia dos, y no coincidian: las estadisticas usaban {@code NumberFormat} en
 * es-AR y el detalle de un turno concatenaba {@code "$" + toPlainString()}, que
 * imprimia "$1500.5" -sin separador de miles y con el punto decimal ingles- para
 * el mismo importe que la otra pantalla mostraba "$ 1.500,50". Son numeros de la
 * misma plata y el mostrador los compara entre pantallas.
 */
final class Money {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private Money() {
    }

    /**
     * Los centavos se muestran solo si el importe los tiene.
     *
     * <p>El precio de un turno es redondo y llenarlo de ",00" le saca legibilidad
     * a una pantalla que es toda numeros. Un saldo partido, en cambio, tiene que
     * poder leerse exacto: es el numero que el mostrador cobra.
     */
    static String format(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        int digits = value.stripTrailingZeros().scale() > 0 ? 2 : 0;
        NumberFormat format = NumberFormat.getCurrencyInstance(ES_AR);
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        return format.format(value);
    }
}
