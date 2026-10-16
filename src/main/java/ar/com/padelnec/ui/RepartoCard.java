package ar.com.padelnec.ui;

import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.service.CashRegisterService.DayCash;
import ar.com.padelnec.service.CashRegisterService.MethodTotal;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Aside;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.NativeTable;
import com.vaadin.flow.component.html.NativeTableCell;
import com.vaadin.flow.component.html.NativeTableHeaderCell;
import com.vaadin.flow.component.html.NativeTableRow;
import com.vaadin.flow.component.html.Span;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo cobrado en el dia como una planilla: un renglon por medio de pago y una
 * columna para los turnos, otra para el buffet y otra para el total, con lo
 * facturado y lo que falta cobrar al costado.
 *
 * <p>Reemplaza a la fila de tarjetas por metodo. Las tarjetas respondian cuanto
 * entro por cada via; los socios que se reparten el negocio por partes necesitan
 * ademas cruzarlo con de quien es: "del efectivo del cajon, cuanto es del
 * buffet". Eso es una tabla de doble entrada, y en tarjetas habria que sumarlo a
 * mano.
 *
 * <p>Es una tabla HTML y no un vaadin-grid: son cuatro renglones fijos, con un
 * renglon de total, y la grilla no tiene pie de tabla ni encabezados de fila.
 */
class RepartoCard extends Div {

    RepartoCard(DayCash caja) {
        addClassName("caja-reparto");

        H3 title = new H3("Reparto del día");
        title.addClassName("caja-reparto__title");
        Div main = new Div(title);
        main.addClassName("caja-reparto__main");

        Component bar = proportionBar(caja.totalTurnos(), caja.totalBuffet());
        if (bar != null) {
            main.add(bar);
        }
        main.add(table(caja));

        Div layout = new Div(main, summary(caja));
        layout.addClassName("caja-reparto__layout");
        add(layout);
    }

    /**
     * La columna del costado: lo facturado del dia arriba y lo que falta cobrar
     * abajo. La planilla de la izquierda ya da el detalle de lo cobrado por
     * medio de pago; aca van los dos numeros que la completan.
     *
     * <p>Era una tarjeta suelta al lado, con otro alto y otra tipografia que la
     * planilla: se leia como un dato de otra pantalla. Adentro, con el mismo
     * rotulo que los encabezados de la tabla, queda claro que es parte del mismo
     * cierre.
     */
    private Aside summary(DayCash caja) {
        Div billed = stat("Facturado", Money.format(caja.billed()),
                "Turnos y buffet del día, igual que en Estadísticas");
        billed.addClassName("caja-reparto__stat--billed");

        Aside aside = new Aside(billed, pending(caja));
        aside.addClassName("caja-reparto__aside");
        return aside;
    }

    /**
     * Lo que falta cobrar de los turnos y los pedidos de buffet del dia.
     *
     * <p>Va en rojo solo si hay algo: en cero es una buena noticia, no una
     * advertencia, y pintarlo igual entrena a ignorar el color.
     */
    private Div pending(DayCash caja) {
        String detail;
        if (caja.pending().signum() > 0) {
            List<String> parts = new ArrayList<>();
            if (caja.pendingBookings() > 0) {
                parts.add(caja.pendingBookings() == 1 ? "1 turno" : caja.pendingBookings() + " turnos");
            }
            if (caja.pendingOrders() > 0) {
                parts.add(caja.pendingOrders() == 1 ? "1 pedido" : caja.pendingOrders() + " pedidos");
            }
            detail = String.join(" y ", parts) + " con saldo";
        } else {
            detail = "Los turnos y pedidos están al día";
        }
        Div stat = stat("Pendiente de cobro", Money.format(caja.pending()), detail);
        if (caja.pending().signum() > 0) {
            stat.addClassName("caja-reparto__stat--due");
        }
        return stat;
    }

    private Div stat(String label, String value, String detail) {
        Span labelSpan = new Span(label);
        labelSpan.addClassName("caja-reparto__aside-label");
        Span valueSpan = new Span(value);
        valueSpan.addClassNames("caja-reparto__aside-value", "tabular");
        Span note = new Span(detail);
        note.addClassName("caja-reparto__note");

        Div stat = new Div(labelSpan, valueSpan, note);
        stat.addClassName("caja-reparto__stat");
        return stat;
    }

    /**
     * La proporcion entre las dos partes, de un vistazo.
     *
     * <p>Solo cuando las dos partes son positivas: con un dia sin cobros, o con
     * mas devuelto que cobrado en una de las partes, una barra no tiene nada
     * honesto para mostrar.
     */
    private Component proportionBar(BigDecimal turnos, BigDecimal buffet) {
        BigDecimal total = turnos.add(buffet);
        if (total.signum() <= 0 || turnos.signum() < 0 || buffet.signum() < 0) {
            return null;
        }
        int buffetPercent = buffet.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP).intValue();
        int turnosPercent = 100 - buffetPercent;

        Div track = new Div();
        track.addClassName("caja-reparto__bar");
        track.getElement().setAttribute("aria-hidden", "true");
        Span turnosPart = new Span();
        turnosPart.addClassNames("caja-reparto__bar-part", "caja-reparto__bar-part--turnos");
        turnosPart.getStyle().set("flex-grow", String.valueOf(turnosPercent));
        Span buffetPart = new Span();
        buffetPart.addClassNames("caja-reparto__bar-part", "caja-reparto__bar-part--buffet");
        buffetPart.getStyle().set("flex-grow", String.valueOf(buffetPercent));
        track.add(turnosPart, buffetPart);

        Span turnosLabel = new Span("Turnos " + turnosPercent + " %");
        Span buffetLabel = new Span("Buffet " + buffetPercent + " %");
        Div legend = new Div(turnosLabel, buffetLabel);
        legend.addClassName("caja-reparto__legend");

        Div block = new Div(track, legend);
        block.addClassName("caja-reparto__proportion");
        return block;
    }

    private NativeTable table(DayCash caja) {
        NativeTable table = new NativeTable();
        table.addClassNames("caja-reparto__table", "tabular");

        NativeTableRow head = new NativeTableRow();
        head.addHeaderCell().addClassName("caja-reparto__corner");
        head.add(columnHeader("Turnos", "turnos"), columnHeader("Buffet", "buffet"),
                columnHeader("Total", "total"));
        table.getHead().add(head);

        for (MethodTotal row : caja.byMethod()) {
            table.getBody().add(methodRow(row));
        }

        NativeTableRow total = new NativeTableRow();
        total.add(rowHeader("Total cobrado", null));
        total.add(amount(caja.totalTurnos()), amount(caja.totalBuffet()), amount(caja.total()));
        total.addClassName("caja-reparto__total-row");
        table.getFoot().add(total);
        return table;
    }

    private NativeTableRow methodRow(MethodTotal row) {
        NativeTableRow tr = new NativeTableRow();
        tr.add(rowHeader(CajaView.readable(row.method()), noteOf(row.method())));
        tr.add(amount(row.turnos()), amount(row.buffet()), amount(row.total()));
        return tr;
    }

    /**
     * Las mismas aclaraciones que tenian las tarjetas: el efectivo es el unico
     * numero que se contrasta contra algo fisico, y MercadoPago el unico que no
     * hay que buscar en el cajon aunque lo haya cargado el mostrador.
     */
    private String noteOf(PaymentMethod method) {
        return switch (method) {
            case CASH -> "Lo que tiene que haber en el cajón";
            case MERCADOPAGO -> "No pasa por el cajón";
            case TRANSFER -> null;
        };
    }

    private NativeTableHeaderCell columnHeader(String text, String part) {
        NativeTableHeaderCell cell = new NativeTableHeaderCell(text);
        cell.getElement().setAttribute("scope", "col");
        cell.addClassName("caja-reparto__col--" + part);
        return cell;
    }

    private NativeTableHeaderCell rowHeader(String text, String note) {
        NativeTableHeaderCell cell = new NativeTableHeaderCell(new Span(text));
        cell.getElement().setAttribute("scope", "row");
        if (note != null) {
            Span noteSpan = new Span(note);
            noteSpan.addClassName("caja-reparto__note");
            cell.add(noteSpan);
        }
        return cell;
    }

    /** Un importe negativo es un dia con mas devuelto que cobrado en esa parte: se marca como una devolucion. */
    private NativeTableCell amount(BigDecimal value) {
        NativeTableCell cell = new NativeTableCell(Money.format(value));
        if (value.signum() < 0) {
            cell.addClassName("caja-reparto__amount--negative");
        }
        return cell;
    }
}
