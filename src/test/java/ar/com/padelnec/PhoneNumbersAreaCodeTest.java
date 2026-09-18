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
    @DisplayName("Un numero ilegible no rompe el cobro: no hay telefono")
    void unreadableNumber() {
        assertThat(phoneNumbers.splitAreaCode("no es un telefono")).isEmpty();
    }
}
