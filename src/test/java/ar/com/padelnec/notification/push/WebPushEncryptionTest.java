package ar.com.padelnec.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El cifrado de Web Push contra el ejemplo del RFC, y la firma VAPID. */
class WebPushEncryptionTest {

    // RFC 8291, Apendice A.
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    @DisplayName("Cifra igual que el ejemplo de RFC 8291, byte por byte")
    void matchesTheRfcExample() {
        KeyPair ephemeral = new KeyPair(
                P256.publicKey(P256.decode(AS_PUBLIC)), P256.privateKey(P256.decode(AS_PRIVATE)));

        byte[] body = WebPushEncryption.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                P256.decode(UA_PUBLIC), P256.decode(AUTH_SECRET), ephemeral, P256.decode(SALT));

        assertThat(P256.base64Url(body)).isEqualTo(BODY);
    }

    @Test
    @DisplayName("Con clave efímera y sal al azar, el navegador lo descifra igual")
    void randomKeysStillDecrypt() {
        TestBrowser browser = TestBrowser.create();

        byte[] first = WebPushEncryption.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                P256.decode(browser.p256dh()), P256.decode(browser.auth()));
        byte[] second = WebPushEncryption.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                P256.decode(browser.p256dh()), P256.decode(browser.auth()));

        assertThat(first).isNotEqualTo(second);
        assertThat(browser.decrypt(first)).isEqualTo(PLAINTEXT);
        assertThat(browser.decrypt(second)).isEqualTo(PLAINTEXT);
    }

    @Test
    @DisplayName("Las claves P-256 ida y vuelta quedan iguales")
    void keysRoundTrip() {
        assertThat(P256.base64Url(P256.encode(P256.publicKey(P256.decode(UA_PUBLIC))))).isEqualTo(UA_PUBLIC);
        assertThat(P256.base64Url(P256.encode(P256.privateKey(P256.decode(UA_PRIVATE))))).isEqualTo(UA_PRIVATE);
    }

    @Test
    @DisplayName("La firma VAPID: JWT ES256 para el origen del servicio de push, válido 12 horas")
    void vapidAuthorization() throws Exception {
        VapidKeys keys = VapidKeys.of(AS_PUBLIC, AS_PRIVATE, "mailto:soporte@turnospadel.test");
        Instant now = Instant.parse("2026-10-16T15:00:00Z");

        String header = keys.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc:def"), now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + AS_PUBLIC);
        SignedJWT jwt = SignedJWT.parse(header.substring("vapid t=".length(), header.indexOf(", k=")));
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
        // Un solo destinatario, como texto y no como lista: es lo que esperan los servicios de push.
        assertThat(jwt.getPayload().toJSONObject().get("aud")).isEqualTo("https://fcm.googleapis.com");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(12 * 3600));
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("mailto:soporte@turnospadel.test");
        // ES256 de JOSE: R || S, 64 bytes, no DER.
        assertThat(jwt.getSignature().decode()).hasSize(64);
        assertThat(jwt.verify(new ECDSAVerifier(P256.publicKey(P256.decode(AS_PUBLIC))))).isTrue();
    }

    @Test
    @DisplayName("Un par de claves VAPID cruzado no arranca")
    void mismatchedVapidKeysAreRejected() {
        assertThatThrownBy(() -> VapidKeys.of(AS_PUBLIC, UA_PRIVATE, "mailto:soporte@turnospadel.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no son pareja");
    }

    @Test
    @DisplayName("Solo se acepta como destino el servicio de push de un navegador")
    void onlyBrowserPushServices() {
        List<String> accepted = List.of(
                "https://fcm.googleapis.com/fcm/send/abc",
                "https://updates.push.services.mozilla.com/wpush/v2/abc",
                "https://web.push.apple.com/QGx",
                "https://wns2-par02p.notify.windows.com/w/?token=abc");
        List<String> rejected = List.of(
                "http://fcm.googleapis.com/fcm/send/abc",
                "https://localhost/push",
                "https://169.254.169.254/latest",
                "https://fcm.googleapis.com.evil.test/abc",
                "https://evilfcm.googleapis.com/abc",
                "https://user@fcm.googleapis.com/abc",
                "no es una url");

        accepted.forEach(endpoint -> assertThat(PanelPushService.isPushService(endpoint)).as(endpoint).isTrue());
        rejected.forEach(endpoint -> assertThat(PanelPushService.isPushService(endpoint)).as(endpoint).isFalse());
    }
}
