package ar.com.padelnec.notification.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.KeyAgreement;

/**
 * Claves de curva P-256 en el formato de Web Push: la publica como punto sin
 * comprimir de 65 bytes ({@code 0x04 || X || Y}) y la privada como los 32 bytes del
 * escalar, las dos en Base64 URL sin relleno. Es lo que entrega el navegador y lo
 * que espera {@code applicationServerKey}; el JDK trabaja con objetos de clave, y
 * esto traduce entre los dos.
 */
final class P256 {

    private static final int COORDINATE = 32;
    private static final ECParameterSpec PARAMS = curve();

    private P256() {
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("El JDK no genera claves P-256", ex);
        }
    }

    static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 1 + 2 * COORDINATE || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("La clave publica no es un punto P-256 sin comprimir");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 1 + COORDINATE));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1 + COORDINATE, uncompressed.length));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), PARAMS));
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Clave publica P-256 invalida", ex);
        }
    }

    static ECPrivateKey privateKey(byte[] scalar) {
        if (scalar.length != COORDINATE) {
            throw new IllegalArgumentException("La clave privada P-256 tiene que tener 32 bytes");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), PARAMS));
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Clave privada P-256 invalida", ex);
        }
    }

    static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[1 + 2 * COORDINATE];
        out[0] = 0x04;
        unsigned(key.getW().getAffineX(), out, 1);
        unsigned(key.getW().getAffineY(), out, 1 + COORDINATE);
        return out;
    }

    static byte[] encode(ECPrivateKey key) {
        byte[] out = new byte[COORDINATE];
        unsigned(key.getS(), out, 0);
        return out;
    }

    /** Secreto compartido ECDH: la coordenada X del punto, 32 bytes. */
    static byte[] ecdh(ECPrivateKey own, ECPublicKey other) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(own);
            agreement.doPhase(other, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("No se pudo acordar la clave con el navegador", ex);
        }
    }

    static byte[] decode(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url.trim());
    }

    static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Big-endian sin signo, con ceros a la izquierda hasta 32 bytes. */
    private static void unsigned(BigInteger value, byte[] out, int offset) {
        byte[] raw = value.toByteArray();
        int start = Math.max(0, raw.length - COORDINATE);
        int length = raw.length - start;
        System.arraycopy(raw, start, out, offset + COORDINATE - length, length);
    }

    private static ECParameterSpec curve() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("El JDK no conoce la curva P-256", ex);
        }
    }
}
