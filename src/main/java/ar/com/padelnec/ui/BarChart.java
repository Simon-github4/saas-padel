package ar.com.padelnec.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.List;

/**
 * Grafico de columnas hecho a mano, sin libreria de charts.
 *
 * <p>Vaadin Charts es un addon comercial y meter una libreria de JavaScript por
 * un grafico de una serie no se justifica: una barra es un div con alto en
 * porcentaje, y asi ademas hereda los colores del tema sin configurar nada.
 *
 * <p>Una sola serie a proposito. Dos metricas de escalas distintas (turnos y
 * pesos) en el mismo grafico necesitarian dos ejes, que es la forma mas comun de
 * mentir con un grafico: se elige la metrica arriba y se dibuja una sola.
 */
public class BarChart extends Div {

    /** Una columna: el valor manda el alto, el resto es lo que lee el usuario. */
    public record Bar(String label, String tooltip, double value, String formattedValue) {
    }

    private static final String BAR_AREA_HEIGHT = "160px";
    /** Arriba de este numero de columnas los rotulos se pisan, y se saltean. */
    private static final int CROWDED = 14;

    private final Div values = new Div();
    private final Div bars = new Div();
    private final Div labels = new Div();

    public BarChart() {
        setWidthFull();
        row(values);
        row(labels);

        row(bars);
        bars.getStyle().set("align-items", "flex-end").set("height", BAR_AREA_HEIGHT);

        labels.getStyle().set("margin-top", "var(--lumo-space-xs)");

        add(values, bars, labels);
    }

    /** Redibuja el grafico entero: son pocas columnas y evita estados a medio actualizar. */
    public void setBars(List<Bar> data) {
        values.removeAll();
        bars.removeAll();
        labels.removeAll();

        double max = data.stream().mapToDouble(Bar::value).max().orElse(0);
        // Dos condiciones para dibujar. Sin datos no hay nada que mostrar: la tabla
        // de abajo ya avisa que el rango esta vacio, y un grafico de ceros solo
        // ocupa lugar. Y una sola columna tampoco es un grafico: al escalarse
        // contra si misma sale siempre llena, valga lo que valga, asi que muestra
        // un alto que no significa nada. Ese numero ya esta en las tarjetas.
        setVisible(max > 0 && data.size() > 1);
        if (max <= 0 || data.size() < 2) {
            return;
        }

        int peak = 0;
        for (int index = 1; index < data.size(); index++) {
            if (data.get(index).value() > data.get(peak).value()) {
                peak = index;
            }
        }
        int labelEvery = data.size() > CROWDED ? (data.size() + 9) / 10 : 1;

        for (int index = 0; index < data.size(); index++) {
            Bar bar = data.get(index);
            String align = alignment(index, data.size());
            // Solo el valor de la barra mas alta: un numero sobre cada columna es
            // ruido, y para el resto estan el tooltip y la tabla de abajo.
            values.add(cell(index == peak ? bar.formattedValue() : "", true, align));
            bars.add(column(bar, max));
            boolean visible = index % labelEvery == 0 || index == data.size() - 1;
            labels.add(cell(visible ? bar.label() : "", false, align));
        }
    }

    private Div column(Bar bar, double max) {
        Div fill = new Div();
        boolean empty = bar.value() <= 0;
        fill.getStyle()
                .set("width", "100%")
                .set("max-width", "40px")
                // Las barras en cero dejan un rastro de 2px en vez de un hueco: el
                // periodo sin actividad es un dato, no una fila que falta.
                .set("height", empty ? "2px" : (bar.value() / max) * 100 + "%")
                .set("border-radius", "var(--lumo-border-radius-s) var(--lumo-border-radius-s) 0 0")
                .set("background-color", empty
                        ? "var(--lumo-contrast-20pct)"
                        : "var(--lumo-primary-color)");

        Div slot = new Div(fill);
        slot.getStyle()
                .set("flex", "1")
                .set("min-width", "0")
                .set("height", "100%")
                .set("display", "flex")
                .set("align-items", "flex-end")
                .set("justify-content", "center");
        slot.getElement().setAttribute("title", bar.tooltip());
        return slot;
    }

    /**
     * Una celda de las filas de texto, alineada con su columna.
     *
     * <p>Sin recorte: con muchas columnas la celda es mas angosta que su propio
     * rotulo, y cortarlo con puntos suspensivos lo vuelve ilegible. Como las
     * celdas vecinas van vacias (solo se rotula una de cada tantas), el texto
     * desborda sobre ellas y se lee entero.
     */
    private Div cell(String text, boolean strong, String align) {
        Span span = new Span(text);
        span.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.Whitespace.NOWRAP);
        span.addClassNames(strong
                ? LumoUtility.TextColor.PRIMARY
                : LumoUtility.TextColor.SECONDARY);
        if (strong) {
            span.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
        }

        Div cell = new Div(span);
        cell.getStyle()
                .set("flex", "1")
                .set("min-width", "0")
                .set("display", "flex")
                .set("justify-content", align);
        return cell;
    }

    /** El rotulo de las puntas se alinea para adentro, si no se sale del grafico. */
    private String alignment(int index, int total) {
        if (index == 0) {
            return "flex-start";
        }
        if (index == total - 1) {
            return "flex-end";
        }
        return "center";
    }

    private void row(Div div) {
        div.setWidthFull();
        div.getStyle().set("display", "flex").set("gap", "2px");
    }
}
