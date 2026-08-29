package ar.com.padelnec.web;

/** El recurso pedido no existe o no pertenece al club en contexto. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
