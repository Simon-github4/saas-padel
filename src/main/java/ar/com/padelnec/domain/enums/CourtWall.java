package ar.com.padelnec.domain.enums;

/**
 * De que estan hechas las paredes de la cancha. Cambia el juego (el rebote en
 * pared es mas vivo y desparejo) y hay jugadores que buscan una u otra.
 */
public enum CourtWall {
    /** Paredes de vidrio templado, el "blindex". */
    GLASS("Blindex"),
    /** Paredes de material, como las de las canchas mas antiguas. */
    WALL("Pared");

    private final String label;

    CourtWall(String label) {
        this.label = label;
    }

    /** Como lo dice el club y el jugador, para el panel. */
    public String label() {
        return label;
    }
}
