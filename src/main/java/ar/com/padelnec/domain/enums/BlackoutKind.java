package ar.com.padelnec.domain.enums;

/**
 * Por que una franja no se vende. Para la grilla los dos bloquean igual; el tipo
 * cambia como lo muestra el panel.
 */
public enum BlackoutKind {
    /** Lluvia, feriado, arreglo: la cancha no se usa. */
    SUSPENSION("Suspensión"),
    /** La cancha se usa, pero para un torneo del club: no se vende turno por turno. */
    TOURNAMENT("Torneo");

    private final String label;

    BlackoutKind(String label) {
        this.label = label;
    }

    /** Como lo dice el club, para el panel. */
    public String label() {
        return label;
    }
}
