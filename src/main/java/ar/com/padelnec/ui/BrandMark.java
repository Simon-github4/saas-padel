package ar.com.padelnec.ui;

import com.vaadin.flow.component.Svg;

/**
 * La lupa de TurnosPadel, la misma del favicon, para la marca del panel.
 *
 * <p>Va como SVG en linea y no como imagen: no depende de que el build de la app
 * del jugador haya dejado el favicon en el classpath, ni de que la ruta del
 * archivo este abierta sin sesion (el login tambien la muestra).
 */
class BrandMark extends Svg {

    BrandMark() {
        super(BrandMark.class.getResourceAsStream("/brand/lupa.svg"));
        setClassName("brand-mark");
        getElement().setAttribute("aria-hidden", "true");
    }
}
