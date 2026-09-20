package ar.com.padelnec.gym.service;

import ar.com.padelnec.web.BusinessRuleException;

/**
 * La sede verifica la ubicacion y el celular no la informo. Es una regla de negocio mas (su mensaje
 * se muestra tal cual), pero con codigo propio en la API para que la app le explique al socio como
 * activar el permiso, sin depender del texto del mensaje.
 */
public class LocationRequiredException extends BusinessRuleException {

    public LocationRequiredException(String message) {
        super(message);
    }
}
