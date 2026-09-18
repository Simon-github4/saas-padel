package ar.com.padelnec.domain.enums;

/** El piso de la cancha: alfombra de cesped sintetico o cemento. */
public enum CourtSurface {
    /** Alfombra de cesped sintetico con arena, la de casi todas las canchas. */
    CARPET("Alfombra"),
    /**
     * Sin alfombra: cemento o material, como en algunas canchas antiguas. Se
     * muestra "Cemento", que es como lo dicen los clubes.
     */
    NO_CARPET("Cemento");

    private final String label;

    CourtSurface(String label) {
        this.label = label;
    }

    /** Como lo dice el club y el jugador, para el panel. */
    public String label() {
        return label;
    }
}
