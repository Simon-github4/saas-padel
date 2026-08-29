package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.payment.MercadoPagoSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La firma es lo unico que separa una acreditacion real de un POST cualquiera
 * contra una URL publica. Si esto se afloja, se confirman turnos que nadie pago.
 *
 * <p>Los HMAC esperados se calcularon por fuera, con otra implementacion, para que
 * el test no termine comprobando que el codigo coincide consigo mismo.
 */
class MercadoPagoSignatureTest {

    private static final String SECRET = "secreto-del-club";
    private static final String REQUEST_ID = "req-abc-123";
    private static final String TIMESTAMP = "1756400000";
    private static final String VALID_HMAC =
            "7125a65c05b5c2e6d889a66fde6901e3c7505551aea96afe24c402d212258ede";

    private final MercadoPagoSignature signature = new MercadoPagoSignature();

    @Test
    @DisplayName("Una firma correcta se acepta")
    void validSignatureIsAccepted() {
        assertThat(signature.isValid(header(VALID_HMAC), REQUEST_ID, "123456789", SECRET)).isTrue();
    }

    @Test
    @DisplayName("Una firma alterada se rechaza")
    void tamperedSignatureIsRejected() {
        String tampered = VALID_HMAC.substring(0, VALID_HMAC.length() - 1) + "f";

        assertThat(signature.isValid(header(tampered), REQUEST_ID, "123456789", SECRET)).isFalse();
    }

    @Test
    @DisplayName("Cambiar el id del pago invalida la firma")
    void changingThePaymentIdBreaksTheSignature() {
        // Es el ataque concreto: tomar una notificacion legitima y apuntarla a otra reserva.
        assertThat(signature.isValid(header(VALID_HMAC), REQUEST_ID, "999999999", SECRET)).isFalse();
    }

    @Test
    @DisplayName("Firmar con otro secreto no sirve")
    void anotherClubSecretDoesNotValidate() {
        assertThat(signature.isValid(header(VALID_HMAC), REQUEST_ID, "123456789", "otro-secreto"))
                .isFalse();
    }

    @Test
    @DisplayName("El id alfanumerico se normaliza a minusculas antes de firmar")
    void alphanumericIdsAreLowercased() {
        String hmac = "79b4dc7126912b497df2d4619ba2144cec69fcdc8437c7c4990fe38aae1041f9";

        assertThat(signature.isValid(header(hmac), REQUEST_ID, "ABC123", SECRET)).isTrue();
    }

    @Test
    @DisplayName("Sin secreto configurado, toda notificacion se rechaza")
    void missingSecretRejectsEverything() {
        // Falla cerrado: un club a medio configurar no acredita pagos sin verificar.
        assertThat(signature.isValid(header(VALID_HMAC), REQUEST_ID, "123456789", null)).isFalse();
        assertThat(signature.isValid(header(VALID_HMAC), REQUEST_ID, "123456789", "  ")).isFalse();
    }

    @Test
    @DisplayName("Un encabezado ausente o mal formado se rechaza")
    void malformedHeadersAreRejected() {
        assertThat(signature.isValid(null, REQUEST_ID, "123456789", SECRET)).isFalse();
        assertThat(signature.isValid("", REQUEST_ID, "123456789", SECRET)).isFalse();
        assertThat(signature.isValid("basura", REQUEST_ID, "123456789", SECRET)).isFalse();
        assertThat(signature.isValid("ts=1756400000", REQUEST_ID, "123456789", SECRET)).isFalse();
    }

    private String header(String hmac) {
        return "ts=" + TIMESTAMP + ",v1=" + hmac;
    }
}
