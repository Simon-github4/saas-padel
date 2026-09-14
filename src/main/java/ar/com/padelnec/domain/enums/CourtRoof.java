package ar.com.padelnec.domain.enums;

/**
 * Si la cancha tiene techo. Con viento o lluvia, que en la costa es seguido, es lo
 * primero que mira quien busca turno.
 */
public enum CourtRoof {
    /** Cancha bajo techo: se juega llueva o no. */
    COVERED("Techada"),
    /** Cancha descubierta. */
    OUTDOOR("Al aire libre");

    private final String label;

    CourtRoof(String label) {
        this.label = label;
    }

    /** Como lo dice el club y el jugador, para el panel. */
    public String label() {
        return label;
    }
}
