package ar.com.padelnec.notification.push;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado del contenido de un aviso Web Push (RFC 8291, {@code aes128gcm} de RFC 8188).
 *
 * <p>El servicio de push (FCM, Mozilla, Apple) solo pasa bytes: lo que dice el aviso
 * lo lee unicamente el navegador que se suscribio. Para eso se acuerda una clave
 * ECDH entre una clave efimera nuestra y la del navegador, se mezcla con su secreto
 * de autenticacion (HKDF) y se cifra con AES-128-GCM.
 *
 * <p>Hecho a mano con el JDK en vez de sumar una libreria de Web Push: son pocas
 * lineas, y las que hay arrastran Bouncy Castle o clientes HTTP propios. Lo valida
 * el vector del Apendice A del RFC ({@code WebPushEncryptionTest}).
 */
final class WebPushEncryption {

    /** Tamaño de registro declarado en el encabezado. Todo el aviso entra en uno solo. */
    private static final int RECORD_SIZE = 4096;
    private static final int TAG_BYTES = 16;
    private static final byte LAST_RECORD = 0x02;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {
    }

    /** Cifra para el navegador de la suscripcion: {@code p256dh} y {@code auth} ya decodificados. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, P256.generate(), salt);
    }

    /** Con la clave efimera y la sal fijas, para comparar contra el vector del RFC. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        if (plaintext.length + 1 + TAG_BYTES > RECORD_SIZE) {
            throw new IllegalArgumentException("El aviso es demasiado largo para Web Push");
        }
        byte[] asPublic = P256.encode((ECPublicKey) ephemeral.getPublic());
        byte[] ecdhSecret = P256.ecdh((ECPrivateKey) ephemeral.getPrivate(), P256.publicKey(uaPublic));

        // RFC 8291 seccion 3.4: el secreto de autenticacion del navegador entra al
        // HKDF, asi que sin el no alcanza con conocer su clave publica.
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat(ascii("WebPush: info\0"), uaPublic, asPublic);
        byte[] ikm = expand(prkKey, keyInfo, 32);

        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, ascii("Content-Encoding: aes128gcm\0"), 16);
        byte[] nonce = expand(prk, ascii("Content-Encoding: nonce\0"), 12);

        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
        padded[plaintext.length] = LAST_RECORD;
        byte[] ciphertext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(TAG_BYTES * 8, nonce));
            ciphertext = cipher.doFinal(padded);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el aviso", ex);
        }

        // Encabezado de RFC 8188: sal, tamaño de registro, y como "keyid" nuestra
        // clave publica efimera, que el navegador necesita para el mismo ECDH.
        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 no disponible", ex);
        }
    }

    /** HKDF-Expand de un solo bloque: alcanza para cualquier largo de hasta 32 bytes. */
    static byte[] expand(byte[] prk, byte[] info, int length) {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {0x01})), length);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
