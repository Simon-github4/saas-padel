package ar.com.padelnec.web;

/**
 * Una regla de negocio impide la operacion: la cancha ya se tomo, el plazo para
 * cancelar vencio, el club no acepta reservas sin sena.
 *
 * <p>El mensaje esta escrito para que el jugador lo lea tal cual en pantalla.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
