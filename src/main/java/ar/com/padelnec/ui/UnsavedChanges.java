package ar.com.padelnec.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Barra de "Guardar cambios" de un formulario de Configuracion, que avisa cuando
 * hay algo sin guardar.
 *
 * <p>El boton quedaba al pie de formularios largos, fuera de la pantalla, y un
 * dueno cambiaba un campo y se iba sin guardar. La barra queda pegada abajo
 * (ver {@code .settings-save-bar} en styles.css) y se resalta mientras algun
 * campo difiere de como estaba guardado. Se compara contra una foto de los
 * valores y no se cuenta cada cambio: si el dueno vuelve un campo a lo que era,
 * la barra se apaga sola.
 */
final class UnsavedChanges {

    private final Map<HasValue<?, ?>, Object> saved = new LinkedHashMap<>();
    private final HorizontalLayout bar;
    private final Runnable onDirtyChange;
    private boolean dirty;

    /**
     * @param onDirtyChange se llama cada vez que el formulario pasa de guardado a
     *                      con cambios, o al reves
     * @param watched       los bloques del formulario; se vigilan todos los campos
     *                      que tengan adentro
     */
    UnsavedChanges(Button save, Runnable onDirtyChange, Component... watched) {
        this.onDirtyChange = onDirtyChange;
        for (Component block : watched) {
            collect(block);
        }
        saved.keySet().forEach(field -> field.addValueChangeListener(event -> refresh()));

        Span hint = new Span("Tenés cambios sin guardar");
        hint.addClassName("settings-save-bar__hint");
        bar = new HorizontalLayout(hint, save);
        bar.addClassName("settings-save-bar");
        bar.setAlignItems(Alignment.CENTER);
        bar.setWidthFull();
    }

    HorizontalLayout bar() {
        return bar;
    }

    boolean isDirty() {
        return dirty;
    }

    /** Lo que muestran los campos ahora es lo guardado. */
    void saved() {
        saved.replaceAll((field, value) -> field.getValue());
        refresh();
    }

    /** Un solo campo quedo guardado por su cuenta (ej. la foto subida), sin tocar los demas. */
    void saved(HasValue<?, ?> field) {
        saved.computeIfPresent(field, (key, value) -> key.getValue());
        refresh();
    }

    private void collect(Component component) {
        if (component instanceof HasValue<?, ?> field) {
            saved.put(field, field.getValue());
        }
        component.getChildren().forEach(this::collect);
    }

    private void refresh() {
        boolean now = saved.entrySet().stream()
                .anyMatch(entry -> !Objects.equals(entry.getKey().getValue(), entry.getValue()));
        if (now == dirty) {
            return;
        }
        dirty = now;
        bar.getElement().setAttribute("dirty", dirty);
        onDirtyChange.run();
    }

    /** Cerrar la pestana o recargar con cambios pendientes: el navegador pregunta antes. */
    static void warnOnClose(boolean on) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        ui.getPage().executeJs(on
                ? "window.onbeforeunload = e => { e.preventDefault(); e.returnValue = ''; };"
                : "window.onbeforeunload = null;");
    }
}
