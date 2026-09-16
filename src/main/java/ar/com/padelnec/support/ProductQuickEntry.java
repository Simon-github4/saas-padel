package ar.com.padelnec.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que se tipea en el buscador del buffet para cargar un producto sin mouse.
 *
 * <p>En un día de torneo el mostrador vende de a decenas y cada click cuenta:
 * "agua" agrega un agua, "3 agua" (o "3x agua", "agua x3") agrega tres, "2" agrega
 * el producto número 2 de la pantalla y "-agua" saca uno. Sin mayúsculas ni tildes,
 * como se escribe apurado.
 */
public final class ProductQuickEntry {

    private static final Pattern QUANTITY_FIRST = Pattern.compile("^(\\d{1,3})\\s*(?:[x*×]\\s*|\\s+)(.+)$");
    private static final Pattern QUANTITY_LAST = Pattern.compile("^(.+?)\\s*[x*×]\\s*(\\d{1,3})$");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private ProductQuickEntry() {
    }

    /**
     * Lo tipeado, ya separado.
     *
     * @param quantity cuántas unidades; cero si se escribió "0 agua", que no es válido
     * @param remove   si empezaba con "-": sacar en vez de agregar
     * @param query    lo que queda para buscar el producto, por nombre o número
     */
    public record Command(int quantity, boolean remove, String query) {

        public boolean isEmpty() {
            return query.isBlank();
        }
    }

    public static Command parse(String raw) {
        String text = raw == null ? "" : raw.strip();
        boolean remove = text.startsWith("-");
        if (remove) {
            text = text.substring(1).strip();
        }
        Matcher first = QUANTITY_FIRST.matcher(text);
        if (first.matches()) {
            return new Command(Integer.parseInt(first.group(1)), remove, first.group(2).strip());
        }
        Matcher last = QUANTITY_LAST.matcher(text);
        if (last.matches()) {
            return new Command(Integer.parseInt(last.group(2)), remove, last.group(1).strip());
        }
        return new Command(1, remove, text);
    }

    /**
     * Las posiciones de los nombres que coinciden con la búsqueda, la mejor primero.
     *
     * <p>Un número es la posición en pantalla (empezando en 1). Un texto gana más
     * cuanto más se parece al comienzo del nombre: "ca" pone primero "Café" que
     * "Coca Cola", y con dos palabras ("coca lig") alcanza con que cada una sea
     * el comienzo de alguna.
     */
    public static List<Integer> matches(List<String> names, String query) {
        String wanted = PersonNames.searchable(query);
        if (wanted.isEmpty()) {
            return List.of();
        }
        if (NUMBER.matcher(wanted).matches()) {
            int position = wanted.length() > 3 ? 0 : Integer.parseInt(wanted);
            return position >= 1 && position <= names.size() ? List.of(position - 1) : List.of();
        }
        record Ranked(int index, int rank) {
        }
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int rank = rank(PersonNames.searchable(names.get(i)), wanted);
            if (rank >= 0) {
                ranked.add(new Ranked(i, rank));
            }
        }
        return ranked.stream()
                .sorted(Comparator.comparingInt(Ranked::rank).thenComparingInt(Ranked::index))
                .map(Ranked::index)
                .toList();
    }

    /** 0 es el nombre exacto; cuanto más alto, más flojo; -1 no coincide. */
    private static int rank(String name, String wanted) {
        if (name.equals(wanted)) {
            return 0;
        }
        if (name.startsWith(wanted)) {
            return 1;
        }
        List<String> words = List.of(name.split(" "));
        if (words.stream().anyMatch(word -> word.startsWith(wanted))) {
            return 2;
        }
        List<String> parts = List.of(wanted.split(" "));
        if (parts.size() > 1 && parts.stream()
                .allMatch(part -> words.stream().anyMatch(word -> word.startsWith(part)))) {
            return 3;
        }
        return name.contains(wanted) ? 4 : -1;
    }
}
