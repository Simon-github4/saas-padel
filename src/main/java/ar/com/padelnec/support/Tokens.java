package ar.com.padelnec.support;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Tokens de los links que viajan por WhatsApp.
 *
 * <p>El de gestion es la unica credencial que tiene el jugador para ver y cancelar
 * su turno: quien adivine uno cancela la reserva de otro. Por eso son 32 bytes de
 * {@link SecureRandom} y no algo derivado del id o de la fecha.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private Tokens() {
    }

    /** 43 caracteres seguros para URL. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
