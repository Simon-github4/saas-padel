package ar.com.padelnec.support;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado simetrico para los secretos que viven en la base: hoy, el access token
 * de produccion de MercadoPago de cada club.
 *
 * <p>AES-256-GCM con IV aleatorio por valor. El texto cifrado se guarda como
 * {@code enc:v1:base64(iv || ciphertext)}. Un valor sin ese prefijo se devuelve
 * tal cual, lo que permite migrar credenciales cargadas en claro sin un backfill.
 */
public class TextCipher {

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key clave AES de 256 bits codificada en Base64. Se genera una vez con
     *                  {@code openssl rand -base64 32} y se inyecta por variable de entorno.
     */
    public TextCipher(String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "app.security.encryption-key debe estar codificada en Base64", ex);
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalArgumentException(
                    "app.security.encryption-key debe decodificar a 16, 24 o 32 bytes; se recibieron "
                            + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cifrar el valor", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            // Valor cargado antes de activar el cifrado: se devuelve sin tocar.
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);

            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "No se pudo descifrar el valor. Revisa que app.security.encryption-key "
                            + "sea la misma con la que se cifro.", ex);
        }
    }
}
