package ar.com.padelnec.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * La tarjeta de un numero grande con su rotulo debajo.
 *
 * <p>Vivia dentro de {@code DashboardView} en tres variantes que se armaban a
 * mano. La caja del dia necesita las mismas, y dos juegos de spans con las
 * mismas clases de Lumo en dos pantallas que el club compara entre si es como
 * empiezan a separarse los estilos.
 *
 * <p>Los agregados se piden encadenados ({@code new KpiCard(...).footnote(...)})
 * porque casi ninguna tarjeta los lleva todos: un constructor con cuatro
 * parametros nulos no dice cual es cual.
 */
class KpiCard extends VerticalLayout {

    private final Span valueSpan;

    KpiCard(String label, String value) {
        valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD,
                LumoUtility.Whitespace.NOWRAP);

        Span labelSpan = new Span(label);
        labelSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY,
                LumoUtility.FontWeight.SEMIBOLD, LumoUtility.Whitespace.NOWRAP);

        add(valueSpan, labelSpan);
        setPadding(false);
        setSpacing(false);
        addClassNames(LumoUtility.Gap.XSMALL, LumoUtility.Padding.MEDIUM,
                LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.BASE);
    }

    /**
     * Una aclaracion de como se cuenta el numero, apagada.
     *
     * <p>"Facturado" y "Cobrado" son dos plata distintas contadas con dos criterios
     * distintos, y sin esto la unica forma de saber cual es cual es leer el
     * codigo.
     */
    KpiCard footnote(String text) {
        Span span = new Span(text);
        span.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY);
        add(span);
        return this;
    }

    /** Un segundo numero que acompaña al principal, ej. el porcentaje de ocupacion. */
    KpiCard emphasis(String text) {
        Span span = new Span(text);
        span.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.SEMIBOLD,
                LumoUtility.TextColor.PRIMARY, LumoUtility.Whitespace.NOWRAP);
        add(span);
        return this;
    }

    /** Un desglose debajo del total, separado por una linea. */
    KpiCard detail(Component content) {
        content.getElement().getClassList().addAll(java.util.List.of(
                LumoUtility.Margin.Top.XSMALL, LumoUtility.Padding.Top.XSMALL,
                LumoUtility.Border.TOP, LumoUtility.BorderColor.CONTRAST_10));
        add(content);
        return this;
    }

    /** Para el numero que reclama una accion, no solo informa: ej. plata sin cobrar. */
    KpiCard alert() {
        valueSpan.addClassNames(LumoUtility.TextColor.ERROR);
        return this;
    }
}
