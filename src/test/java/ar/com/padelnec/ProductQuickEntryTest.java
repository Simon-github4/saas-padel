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
    @DisplayName("Lo tipeado es el producto, sin cantidad: para más de uno se repite el Enter")
    void theTextIsTheProduct() {
        assertThat(ProductQuickEntry.parse(" agua ")).isEqualTo(new Command(false, "agua"));
        assertThat(ProductQuickEntry.parse("3 agua")).isEqualTo(new Command(false, "3 agua"));
        assertThat(ProductQuickEntry.matches(CATALOG, "3 agua")).isEmpty();
        assertThat(ProductQuickEntry.parse("12")).isEqualTo(new Command(false, "12"));
    }

    @Test
    @DisplayName("Un guion adelante saca en vez de sumar")
    void aLeadingDashRemoves() {
        assertThat(ProductQuickEntry.parse("-agua")).isEqualTo(new Command(true, "agua"));
        assertThat(ProductQuickEntry.parse("- 2")).isEqualTo(new Command(true, "2"));
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
