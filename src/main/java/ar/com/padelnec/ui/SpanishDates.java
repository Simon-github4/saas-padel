package ar.com.padelnec.ui;

import com.vaadin.flow.component.datepicker.DatePicker;
import java.util.List;

/**
 * Textos en castellano para los selectores de fecha.
 *
 * <p>Vaadin viene en ingles por defecto y con la semana arrancando en domingo. El
 * panel lo usa gente del mostrador de un club, no desarrolladores.
 */
final class SpanishDates {

    private SpanishDates() {
    }

    static DatePicker.DatePickerI18n datePicker() {
        return new DatePicker.DatePickerI18n()
                .setMonthNames(List.of("enero", "febrero", "marzo", "abril", "mayo", "junio",
                        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"))
                .setWeekdays(List.of("domingo", "lunes", "martes", "miercoles", "jueves",
                        "viernes", "sabado"))
                .setWeekdaysShort(List.of("do", "lu", "ma", "mi", "ju", "vi", "sa"))
                // En Argentina la semana empieza el lunes.
                .setFirstDayOfWeek(1)
                .setToday("Hoy")
                .setCancel("Cancelar")
                .setDateFormat("dd/MM/yyyy");
    }
}
