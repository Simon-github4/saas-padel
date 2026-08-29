package ar.com.padelnec.web;

/**
 * Alguien tomo la cancha entre que el jugador vio la grilla y toco reservar.
 *
 * <p>Se distingue del resto de las reglas de negocio porque la app responde
 * distinto: refresca la grilla en vez de mostrar un cartel de error.
 */
public class SlotUnavailableException extends BusinessRuleException {

    public SlotUnavailableException(String message) {
        super(message);
    }
}
