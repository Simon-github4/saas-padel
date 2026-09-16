package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.ProductQuickEntry;
import ar.com.padelnec.support.ProductQuickEntry.Command;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que se tipea en el buscador del buffet para cargar productos sin mouse. */
class ProductQuickEntryTest {

    private static final List<String> CATALOG = List.of("Agua", "Café", "Coca Cola", "Coca Cola Light", "Gatorade");

    @Test
    @DisplayName("La cantidad va adelante o atrás, con x, * o un espacio")
    void quantityGoesBeforeOrAfter() {
        assertThat(ProductQuickEntry.parse("agua")).isEqualTo(new Command(1, false, "agua"));
        assertThat(ProductQuickEntry.parse("3 agua")).isEqualTo(new Command(3, false, "agua"));
        assertThat(ProductQuickEntry.parse("3x agua")).isEqualTo(new Command(3, false, "agua"));
        assertThat(ProductQuickEntry.parse(" 3 * coca cola ")).isEqualTo(new Command(3, false, "coca cola"));
        assertThat(ProductQuickEntry.parse("agua x3")).isEqualTo(new Command(3, false, "agua"));
        assertThat(ProductQuickEntry.parse("3*2")).isEqualTo(new Command(3, false, "2"));
        assertThat(ProductQuickEntry.parse("12")).isEqualTo(new Command(1, false, "12"));
        assertThat(ProductQuickEntry.parse("7up")).isEqualTo(new Command(1, false, "7up"));
    }

    @Test
    @DisplayName("Un guion adelante saca en vez de sumar")
    void aLeadingDashRemoves() {
        assertThat(ProductQuickEntry.parse("-agua")).isEqualTo(new Command(1, true, "agua"));
        assertThat(ProductQuickEntry.parse("- 2 cafe")).isEqualTo(new Command(2, true, "cafe"));
        assertThat(ProductQuickEntry.parse("-").isEmpty()).isTrue();
        assertThat(ProductQuickEntry.parse("   ").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Un número es la posición en pantalla, empezando en 1")
    void aNumberIsThePositionOnScreen() {
        assertThat(ProductQuickEntry.matches(CATALOG, "2")).containsExactly(1);
        assertThat(ProductQuickEntry.matches(CATALOG, "5")).containsExactly(4);
        assertThat(ProductQuickEntry.matches(CATALOG, "0")).isEmpty();
        assertThat(ProductQuickEntry.matches(CATALOG, "6")).isEmpty();
        assertThat(ProductQuickEntry.matches(CATALOG, "99999999999")).isEmpty();
    }

    @Test
    @DisplayName("Sin tildes ni mayúsculas, y primero lo que empieza como lo tipeado")
    void bestMatchFirstIgnoringAccents() {
        assertThat(ProductQuickEntry.matches(CATALOG, "CAFE")).containsExactly(1);
        // "ca" empieza "Café" y está adentro de "Coca Cola": Café va primero.
        assertThat(ProductQuickEntry.matches(CATALOG, "ca")).containsExactly(1, 2, 3);
        // El nombre exacto le gana a uno más largo que empieza igual.
        assertThat(ProductQuickEntry.matches(CATALOG, "coca cola")).containsExactly(2, 3);
        assertThat(ProductQuickEntry.matches(CATALOG, "light")).containsExactly(3);
        assertThat(ProductQuickEntry.matches(CATALOG, "coca lig")).containsExactly(3);
        assertThat(ProductQuickEntry.matches(CATALOG, "cerveza")).isEmpty();
    }
}
