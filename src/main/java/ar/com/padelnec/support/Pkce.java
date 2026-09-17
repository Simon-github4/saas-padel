package ar.com.padelnec.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Transformacion PKCE (RFC 7636) del {@code code_verifier} de OAuth al
 * {@code code_challenge} que viaja en la URL de autorizacion.
 *
 * <p>El {@code code_verifier} en si no necesita una clase propia: el alfabeto de
 * {@link Tokens#generate()} (Base64URL sin relleno) ya es un subconjunto de los
 * caracteres "unreserved" que exige el RFC, y sus 43 caracteres caen justo en el
 * piso del largo permitido (43 a 128).
 */
public final class Pkce {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
    }

    /** {@code BASE64URL(SHA256(code_verifier))}, el metodo S256 que MercadoPago exige. */
    public static String codeChallengeS256(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return ENCODER.encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es obligatorio en toda JVM; si falta, no hay nada que recuperar.
            throw new IllegalStateException("Esta JVM no tiene SHA-256", ex);
        }
    }
}
