package ar.com.padelnec.security;

import java.util.Optional;

/**
 * Verifica un ID token de Google Sign-In del lado del servidor.
 *
 * <p>Interfaz aparte de la implementacion real para poder probar el login con
 * Google en tests sin pegarle a la red: nada distinto de como
 * {@code WhatsAppSender} separa la logica de notificacion del proveedor.
 */
public interface GoogleIdTokenVerifier {

    /** Nunca lanza: un token invalido o Google caido son el mismo {@code Optional.empty()}. */
    Optional<GoogleIdentity> verify(String idToken);

    record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
    }
}
