package ar.com.padelnec.gym.service;

/**
 * El socio todavia tiene la clave temporal del mostrador: hasta que la cambie no
 * puede usar el resto de la app. Se responde 403 con un codigo propio para que la
 * app lo lleve a la pantalla de cambio de clave.
 */
public class PasswordChangeRequiredException extends RuntimeException {

    public PasswordChangeRequiredException() {
        super("Tenés que cambiar la clave que te dieron en el mostrador antes de seguir.");
    }
}
