package ar.com.padelnec.notification.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Un navegador de mentira para los tests: tiene sus claves de suscripcion y
 * descifra lo que le llega, como haria Chrome al recibir el aviso.
 */
public final class TestBrowser {

    private final KeyPair keys;
    private final byte[] auth;

    private TestBrowser(KeyPair keys, byte[] auth) {
        this.keys = keys;
        this.auth = auth;
    }

    public static TestBrowser create() {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return new TestBrowser(P256.generate(), auth);
    }

    public String p256dh() {
        return P256.base64Url(P256.encode((ECPublicKey) keys.getPublic()));
    }

    public String auth() {
        return P256.base64Url(auth);
    }

    /** Lo que devuelve {@code JSON.stringify(pushSubscription)}. */
    public String subscriptionJson(String endpoint) {
        return """
                {"endpoint":"%s","expirationTime":null,"keys":{"p256dh":"%s","auth":"%s"}}"""
                .formatted(endpoint, p256dh(), auth());
    }

    /** Descifra un cuerpo {@code aes128gcm} de un solo registro (RFC 8188 y 8291). */
    public String decrypt(byte[] body) {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] asPublic = new byte[buffer.get() & 0xff];
        buffer.get(asPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        byte[] uaPublic = P256.encode((ECPublicKey) keys.getPublic());
        byte[] ecdh = P256.ecdh((ECPrivateKey) keys.getPrivate(), P256.publicKey(asPublic));
        byte[] prkKey = WebPushEncryption.hmac(auth, ecdh);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = WebPushEncryption.expand(prkKey, keyInfo, 32);
        byte[] prk = WebPushEncryption.hmac(salt, ikm);
        byte[] cek = WebPushEncryption.expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushEncryption.expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] padded = cipher.doFinal(ciphertext);
            int end = padded.length - 1;
            while (end >= 0 && padded[end] == 0) {
                end--;
            }
            if (end < 0 || padded[end] != 0x02) {
                throw new IllegalStateException("Falta el delimitador de ultimo registro");
            }
            return new String(Arrays.copyOf(padded, end), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo descifrar el aviso", ex);
        }
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer out = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            out.put(part);
        }
        return out.array();
    }
}
