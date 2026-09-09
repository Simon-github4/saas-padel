package ar.com.padelnec.web;

/** La sesion del jugador no existe, vencio o se cerro. */
public class UnauthorizedSessionException extends RuntimeException {

    public UnauthorizedSessionException(String message) {
        super(message);
    }
}
