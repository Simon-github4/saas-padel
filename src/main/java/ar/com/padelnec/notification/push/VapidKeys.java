package ar.com.padelnec.notification.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Identidad del servidor ante los servicios de push (VAPID, RFC 8292).
 *
 * <p>El navegador se suscribe con nuestra clave publica ({@code applicationServerKey})
 * y el servicio de push solo acepta avisos firmados con la privada que le
 * corresponde. Si las claves cambian, todas las suscripciones dejan de servir y
 * cada persona tiene que volver a activar los avisos: por eso en produccion son
 * fijas y vienen de la configuracion.
 */
public final class VapidKeys {

    /** Lo que vive la firma. RFC 8292 pide como mucho 24 horas. */
    static final Duration TOKEN_VALIDITY = Duration.ofHours(12);

    private final ECPublicKey publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;

    private VapidKeys(ECPublicKey publicKey, ECPrivateKey privateKey, String subject) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    /**
     * Las claves de la configuracion, en Base64 URL (las que genera, por ejemplo,
     * {@code npx web-push generate-vapid-keys}). Falla si no son pareja: con un par
     * cruzado los avisos saldrian firmados y los servicios de push los rechazarian
     * todos, sin un error a la vista.
     */
    public static VapidKeys of(String publicKey, String privateKey, String subject) {
        VapidKeys keys = new VapidKeys(
                P256.publicKey(P256.decode(publicKey)), P256.privateKey(P256.decode(privateKey)), subject);
        keys.requirePair();
        return keys;
    }

    public static VapidKeys generate(String subject) {
        KeyPair pair = P256.generate();
        return new VapidKeys((ECPublicKey) pair.getPublic(), (ECPrivateKey) pair.getPrivate(), subject);
    }

    /** La clave publica como la pide {@code pushManager.subscribe}: punto sin comprimir en Base64 URL. */
    public String publicKey() {
        return P256.base64Url(P256.encode(publicKey));
    }

    public String privateKey() {
        return P256.base64Url(P256.encode(privateKey));
    }

    public String subject() {
        return subject;
    }

    /**
     * Encabezado {@code Authorization} para mandarle a {@code endpoint}: un JWT ES256
     * cuyo {@code aud} es el origen del servicio de push, mas nuestra clave publica.
     */
    String authorization(URI endpoint, Instant now) {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(audience)
                .expirationTime(Date.from(now.plus(TOKEN_VALIDITY)))
                .subject(subject)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo firmar el token VAPID", ex);
        }
        return "vapid t=" + jwt.serialize() + ", k=" + publicKey();
    }

    private void requirePair() {
        try {
            JWSObject probe = new JWSObject(new JWSHeader(JWSAlgorithm.ES256), new Payload("vapid"));
            probe.sign(new ECDSASigner(privateKey));
            if (!probe.verify(new ECDSAVerifier(publicKey))) {
                throw new IllegalArgumentException(
                        "PUSH_VAPID_PUBLIC_KEY y PUSH_VAPID_PRIVATE_KEY no son pareja");
            }
        } catch (JOSEException ex) {
            throw new IllegalArgumentException("Claves VAPID invalidas", ex);
        }
    }
}
