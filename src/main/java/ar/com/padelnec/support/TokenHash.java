package ar.com.padelnec.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Huella con la que se guardan los tokens en la base, en vez del token mismo.
 *
 * <p>Un token de sesion o de reseteo es una credencial: quien lo tenga entra como
 * ese jugador o le cambia la contrasena. Guardados en claro, alcanzaba con una
 * copia de la base -- un backup mal guardado, un acceso de soporte, un dump
 * pedido para depurar algo -- para quedarse con todas las sesiones abiertas.
 * Guardando la huella, esa copia no sirve para entrar: el valor que abre la
 * puerta solo existe en el navegador del jugador y en el mail que recibio.
 *
 * <p>SHA-256 y no bcrypt, que es lo que se usa para las contrasenas de al lado.
 * La diferencia esta en el origen del secreto: una contrasena la elige una
 * persona y hay que encarecer cada intento porque se puede adivinar. Estos
 * tokens son 32 bytes de {@link java.security.SecureRandom} ({@link Tokens}), y
 * contra 256 bits de azar no hay diccionario ni tabla precalculada que sirva.
 * Un hash lento ahi no agrega seguridad y si agrega latencia a cada peticion
 * autenticada, que es el camino mas transitado de la API.
 *
 * <p>Tiene que ser determinista, ademas, porque la busqueda es por igualdad: el
 * token llega en la peticion, se calcula su huella y se busca esa. Un bcrypt con
 * sal al azar obligaria a recorrer la tabla comparando de a una.
 */
public final class TokenHash {

    private static final HexFormat HEX = HexFormat.of();

    private TokenHash() {
    }

    /** 64 caracteres hexadecimales, el ancho exacto de las columnas que lo guardan. */
    public static String of(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es obligatorio en toda JVM; si falta, no hay nada que recuperar.
            throw new IllegalStateException("Esta JVM no tiene SHA-256", ex);
        }
    }
}
