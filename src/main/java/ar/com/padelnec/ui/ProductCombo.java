package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Product;
import com.vaadin.flow.component.combobox.ComboBox;
import java.util.List;

/**
 * El selector de producto para sumarle a un turno o a un pedido del buffet: se
 * escribe parte del nombre y quedan los que coinciden. Con un catálogo largo,
 * recorrer la lista entera para encontrar uno era lo más lento.
 *
 * <p>Filtra en el navegador, al tipear. Con un filtro hecho en Java el combo
 * espera medio segundo después de cada letra y recién ahí le pregunta al
 * servidor: se notaba. Busca con el mismo criterio que el buscador de la
 * pantalla de buffet ({@link ar.com.padelnec.support.ProductQuickEntry}): sin
 * mayúsculas ni tildes, y «coca lig» encuentra «Coca Cola Light».
 */
final class ProductCombo {

    /**
     * Reemplaza el filtro del combo, que solo busca el texto tal cual. Va al
     * engancharse, cuando el combo ya armó su conector.
     */
    private static final String FILTER = """
            const plain = (text) => (text || '').toString().normalize('NFD').replace(/\\p{M}/gu, '')
                .toLowerCase().trim().split(/\\s+/).join(' ');
            this.$connector.filter = (item, typed) => {
              const wanted = plain(typed);
              if (!wanted) {
                return true;
              }
              const name = plain(this._getItemLabel(item, this.itemLabelPath));
              if (name.includes(wanted)) {
                return true;
              }
              const words = name.split(' ');
              const parts = wanted.split(' ');
              return parts.length > 1 && parts.every((part) => words.some((word) => word.startsWith(part)));
            };
            """;

    private ProductCombo() {
    }

    static ComboBox<Product> create(List<Product> catalog) {
        ComboBox<Product> combo = new ComboBox<>("Producto");
        // Todo el catálogo en una página: así el combo filtra en el navegador.
        combo.setPageSize(Math.max(50, catalog.size()));
        combo.setItems(catalog);
        combo.setItemLabelGenerator(p -> "%s (%s)".formatted(p.getName(), Money.format(p.getUnitPrice())));
        combo.setPlaceholder("Buscá por nombre");
        combo.setClearButtonVisible(true);
        combo.addAttachListener(event -> combo.getElement().executeJs(FILTER));
        return combo;
    }
}
