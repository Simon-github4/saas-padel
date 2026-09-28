package ar.com.padelnec.support;

import java.util.regex.Pattern;

/** Lo poco que se lee del User-Agent del navegador. */
public final class UserAgents {

    /**
     * Telefono o computadora, nada mas fino. Las tablets no se distinguen de forma
     * confiable -- el iPad moderno se anuncia como escritorio -- y una categoria
     * que miente es peor que no tenerla.
     */
    private static final Pattern MOBILE = Pattern.compile(
            "mobi|android|iphone|ipod", Pattern.CASE_INSENSITIVE);

    private UserAgents() {
    }

    /** {@code mobile}, {@code desktop}, o nulo sin User-Agent. */
    public static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return MOBILE.matcher(userAgent).find() ? "mobile" : "desktop";
    }
}
