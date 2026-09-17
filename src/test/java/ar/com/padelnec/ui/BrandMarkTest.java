package ar.com.padelnec.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandMarkTest {

    @Test
    @DisplayName("La marca del panel carga la lupa del classpath")
    void loadsTheMagnifierSvg() {
        // Si el recurso faltara, el menu y el login fallarian al abrirse, no solo la marca.
        BrandMark mark = new BrandMark();

        assertThat(mark.getElement().getProperty("innerHTML"))
                .startsWith("<svg")
                .contains("panel-pelota");
        assertThat(mark.getClassName()).isEqualTo("brand-mark");
        assertThat(mark.getElement().getAttribute("aria-hidden")).isEqualTo("true");
    }
}
