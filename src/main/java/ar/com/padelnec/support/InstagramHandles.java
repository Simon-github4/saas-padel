package ar.com.padelnec.support;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca el usuario de Instagram de lo que el dueño del club pegue.
 *
 * <p>Nadie escribe el usuario igual: "@clubpadel", "clubpadel", o el link que da
 * "Compartir perfil" en la app, que viene con {@code ?igsh=...} atras. Se guarda
 * siempre el usuario pelado, asi el link que ve el jugador sale bien armado
 * aunque se haya cargado de cualquiera de esas formas.
 */
public final class InstagramHandles {

    /** Letras, numeros, punto y guion bajo, hasta 30: las reglas de Instagram. */
    private static final Pattern HANDLE = Pattern.compile("[a-z0-9._]{1,30}");

    private static final Pattern PROFILE_LINK =
            Pattern.compile("^(?:https?://)?(?:www\\.|m\\.)?instagram\\.com/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Primeros tramos de links de Instagram que no son un perfil: un posteo, un
     * reel o una historia. Pegar uno de esos no puede terminar guardando "p"
     * como si fuera el usuario del club.
     */
    private static final Set<String> NOT_A_PROFILE =
            Set.of("p", "reel", "reels", "stories", "explore", "tv", "accounts", "direct");

    private InstagramHandles() {
    }

    /**
     * @return el usuario en minusculas y sin @, o vacio si lo que se pego no es un
     *         usuario ni el link de un perfil
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String candidate = raw.trim();
        Matcher link = PROFILE_LINK.matcher(candidate);
        if (link.find()) {
            candidate = link.group(1);
        } else if (candidate.contains("/")) {
            // Un link, pero no de Instagram.
            return Optional.empty();
        }
        if (candidate.startsWith("@")) {
            candidate = candidate.substring(1);
        }
        candidate = candidate.toLowerCase(Locale.ROOT);
        if (!HANDLE.matcher(candidate).matches() || NOT_A_PROFILE.contains(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    /** Link al perfil, siempre con la misma forma. */
    public static String profileUrl(String handle) {
        return "https://www.instagram.com/" + handle + "/";
    }
}
