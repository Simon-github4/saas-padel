package ar.com.padelnec.support;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Compara nombres de personas tal como los escribe la gente en el celular.
 *
 * <p>Sirve para decidir si el nombre con el que se reservó un turno es el del
 * jugador dueño del teléfono o el de otra persona que se equivocó de número.
 * No puede ser una comparación exacta: el mismo jugador escribe "juan",
 * "Juan Pérez" o "JUAN  PEREZ", y marcar cada una de esas como sospechosa
 * llenaría la agenda de avisos que nadie lee.
 */
public final class PersonNames {

    private PersonNames() {
    }

    /**
     * Misma persona si, sin mayúsculas, tildes ni espacios de más, las palabras
     * del nombre más corto son el comienzo del más largo: "Juan" y "Juan Pérez"
     * sí; "Juan Pérez" y "Juan Gómez" no; "Pedro" y "Juan Pérez" no.
     */
    public static boolean samePerson(String first, String second) {
        List<String> a = words(first);
        List<String> b = words(second);
        if (a.isEmpty() || b.isEmpty()) {
            return a.equals(b);
        }
        List<String> shorter = a.size() <= b.size() ? a : b;
        List<String> longer = shorter == a ? b : a;
        return longer.subList(0, shorter.size()).equals(shorter);
    }

    /**
     * El nombre listo para buscar: sin mayúsculas, tildes ni espacios de más. Así
     * "perez" encuentra a "Pérez", que es como se tipea apurado en el mostrador.
     */
    public static String searchable(String name) {
        return String.join(" ", words(name));
    }

    private static List<String> words(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return Arrays.asList(plain.split("\\s+"));
    }
}
