package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.support.PhoneNumbers.AreaCodeAndNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El corte entre codigo de area y abonado que viaja a MercadoPago.
 *
 * <p>En Argentina el codigo de area tiene 2, 3 o 4 digitos segun la ciudad: un
 * corte fijo dejaria mal a la mitad del pais.
 */
class PhoneNumbersAreaCodeTest {

    private final PhoneNumbers phoneNumbers = new PhoneNumbers();

    @Test
    @DisplayName("Celular de Necochea: codigo de area de 4 digitos, sin el 9")
    void fourDigitAreaCode() {
        assertThat(phoneNumbers.splitAreaCode("+5492262415000"))
                .contains(new AreaCodeAndNumber("2262", "415000"));
    }

    @Test
    @DisplayName("Celular de Buenos Aires: codigo de area de 2 digitos")
    void twoDigitAreaCode() {
        assertThat(phoneNumbers.splitAreaCode("+5491155551234"))
                .contains(new AreaCodeAndNumber("11", "55551234"));
    }

    @Test
    @DisplayName("Celular de Cordoba: codigo de area de 3 digitos")
    void threeDigitAreaCode() {
        assertThat(phoneNumbers.splitAreaCode("+5493514123456"))
                .contains(new AreaCodeAndNumber("351", "4123456"));
    }

    @Test
    @DisplayName("En pantalla, como lo escribe un argentino, con cualquier largo de codigo de area")
    void localDisplay() {
        // Una ciudad por largo de codigo de area y por region, incluidas las que
        // cambiaron de caracteristica (Bariloche 294, Comodoro 297).
        assertThat(phoneNumbers.forLocalDisplay("+5491155551234")).isEqualTo("11 5555-1234");
        assertThat(phoneNumbers.forLocalDisplay("+5493514123456")).isEqualTo("351 412-3456");
        assertThat(phoneNumbers.forLocalDisplay("+5492214123456")).isEqualTo("221 412-3456");
        assertThat(phoneNumbers.forLocalDisplay("+5492614123456")).isEqualTo("261 412-3456");
        assertThat(phoneNumbers.forLocalDisplay("+5493794123456")).isEqualTo("379 412-3456");
        assertThat(phoneNumbers.forLocalDisplay("+5492944123456")).isEqualTo("294 412-3456");
        assertThat(phoneNumbers.forLocalDisplay("+5492974123456")).isEqualTo("297 412-3456");
        assertThat(phoneNumbers.forLocalDisplay("+5492262212345")).isEqualTo("2262 21-2345");
        assertThat(phoneNumbers.forLocalDisplay("+5492901412345")).isEqualTo("2901 41-2345");
        assertThat(phoneNumbers.forLocalDisplay("+5493543412345")).isEqualTo("3543 41-2345");
    }

    @Test
    @DisplayName("Un numero del exterior se muestra en formato internacional")
    void foreignDisplay() {
        assertThat(phoneNumbers.forLocalDisplay("+59899123456")).isEqualTo("+598 99 123 456");
        assertThat(phoneNumbers.forLocalDisplay("+5511912345678")).startsWith("+55 11");
    }

    @Test
    @DisplayName("Lo que no se entiende se muestra tal cual, sin romper la pantalla")
    void unreadableDisplay() {
        assertThat(phoneNumbers.forLocalDisplay("no es un telefono")).isEqualTo("no es un telefono");
    }

    @Test
    @DisplayName("Un numero ilegible no rompe el cobro: no hay telefono")
    void unreadableNumber() {
        assertThat(phoneNumbers.splitAreaCode("no es un telefono")).isEmpty();
    }
}
