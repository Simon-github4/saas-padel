package ar.com.padelnec.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Valida la firma con la que MercadoPago sella sus webhooks.
 *
 * <p>Sin esta comprobacion, la URL del webhook es publica y cualquiera puede
 * postear un JSON diciendo que un pago se acredito: se confirmarian turnos que
 * nadie pago. La firma es lo unico que distingue una notificacion real.
 *
 * <p>MercadoPago manda {@code x-signature: ts=<epoch>,v1=<hmac>} y firma el texto
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} con HMAC-SHA256 y el
 * secreto que el club configuro en su panel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoSignature {

    private static final String ALGORITHM = "HmacSHA256";

    /** Una notificacion capturada en algun punto de la cadena (un log, un proxy)
     * sigue teniendo una firma valida para siempre si no se chequea la edad del
     * {@code ts}: esta ventana acota cuanto tiempo sirve para reenviarla. */
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final Clock clock;

    /**
     * @param signatureHeader contenido de {@code x-signature}
     * @param requestId       contenido de {@code x-request-id}
     * @param dataId          parametro {@code data.id} de la URL
     * @param secret          clave secreta del club
     */
    public boolean isValid(String signatureHeader, String requestId, String dataId, String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("El club no tiene configurado el secreto de webhook de MercadoPago: "
                    + "la notificacion se rechaza por las dudas");
            return false;
        }
        if (signatureHeader == null || signatureHeader.isBlank() || dataId == null) {
            return false;
        }

        String timestamp = extract(signatureHeader, "ts");
        String received = extract(signatureHeader, "v1");
        if (timestamp == null || received == null || !isRecent(timestamp)) {
            return false;
        }

        // MercadoPago normaliza el id a minusculas cuando es alfanumerico.
        String manifest = "id:%s;request-id:%s;ts:%s;"
                .formatted(dataId.toLowerCase(), requestId == null ? "" : requestId, timestamp);

        String expected = hmacHex(manifest, secret);
        // Comparacion de tiempo constante: comparar con equals filtra informacion
        // sobre cuantos caracteres acerto quien esta probando firmas.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Rechaza tambien un ts en el futuro y no solo uno viejo: una ventana simetrica
     * cubre el mismo reloj corrido de un lado que del otro, sin ampliar el margen
     * real que le queda a quien reintenta una notificacion capturada.
     *
     * <p>La documentacion de MercadoPago dice que {@code ts} viene en milisegundos,
     * pero sus propios ejemplos no son consistentes: unos traen 10 digitos
     * (segundos) y otros 13 (milisegundos). Tratarlo siempre como milisegundos
     * rechazaria de 401 cualquier webhook real que llegue en segundos, asi que se
     * acepta cualquiera de los dos formatos segun el largo del numero.
     */
    private boolean isRecent(String timestamp) {
        long epochMillis;
        try {
            long value = Long.parseLong(timestamp);
            // Un ts en segundos de 13 digitos describiria una fecha en el año 5138:
            // el largo alcanza para distinguir un formato del otro sin ambiguedad.
            epochMillis = timestamp.length() >= 13 ? value : value * 1000;
        } catch (NumberFormatException ex) {
            return false;
        }
        long ageMillis = Math.abs(clock.millis() - epochMillis);
        return ageMillis <= MAX_AGE.toMillis();
    }

    private String extract(String header, String key) {
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].trim().equals(key)) {
                return pair[1].trim();
            }
        }
        return null;
    }

    private String hmacHex(String manifest, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular la firma del webhook", ex);
        }
    }
}
