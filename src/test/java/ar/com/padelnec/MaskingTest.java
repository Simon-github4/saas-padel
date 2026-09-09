package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.Masking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que un log de fallo puede mostrar de un telefono o un email sin exponerlo entero. */
class MaskingTest {

    @Test
    @DisplayName("El telefono deja ver solo los ultimos 4 digitos")
    void masksAllButTheLastFourDigits() {
        assertThat(Masking.phone("+5492262415111")).isEqualTo("**********5111");
    }

    @Test
    @DisplayName("Un telefono mas corto que lo visible no revienta")
    void handlesAShortPhoneWithoutThrowing() {
        assertThat(Masking.phone("123")).isEqualTo("123");
        assertThat(Masking.phone(null)).isNull();
    }

    @Test
    @DisplayName("El email deja ver solo la primera letra y el dominio")
    void masksTheLocalPartOfAnEmail() {
        assertThat(Masking.email("simon.diaz@ufasta.edu.ar")).isEqualTo("s***@ufasta.edu.ar");
    }

    @Test
    @DisplayName("Un email sin arroba no revienta")
    void handlesAnInvalidEmailWithoutThrowing() {
        assertThat(Masking.email("no-es-un-email")).isEqualTo("***");
        assertThat(Masking.email(null)).isNull();
    }
}
