package ar.com.padelnec.domain.enums;

/** Como se escriben los horarios sobre la plantilla de la historia de turnos libres. */
public enum StoryStyle {
    /** Recuadros blancos redondeados con el texto oscuro: el que usa la mayoria. */
    PILLS,
    /** Recuadros del color primario del club con el texto blanco. */
    COLOR,
    /** Texto blanco con sombra, sin recuadro: para plantillas oscuras. */
    LIGHT,
    /** Texto negro sin recuadro: para plantillas claras. */
    DARK
}
