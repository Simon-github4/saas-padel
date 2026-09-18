package ar.com.padelnec.web;

import java.net.URI;
import java.util.Optional;

/**
 * Manda a un solo dominio las paginas publicas que tambien se sirven desde el
 * host que asigna Render ({@code algo.onrender.com}).
 *
 * <p>Sin esto, el mismo contenido vive en dos direcciones y Google tiene que
 * elegir cual indexar, repartiendo las senales entre las dos. El canonical ya
 * apunta al dominio propio, pero un 301 lo resuelve sin depender de que el
 * buscador haga caso a la pista.
 *
 * <p>Se usa solo en las paginas publicas de la SPA, nunca en {@code /api},
 * {@code /actuator} ni en el panel: el webhook de Mercado Pago, el health check
 * del keep-alive y los links de los clubes que todavia usen el host de Render
 * tienen que seguir andando ahi.
 */
final class CanonicalHost {

    private static final String RENDER_SUFFIX = ".onrender.com";

    private CanonicalHost() {}

    /**
     * @param requestHost host con el que llego el pedido
     * @param baseUrl     URL publica configurada ({@code app.base-url})
     * @return la URL a la que redirigir, o vacio si el pedido ya esta en el dominio correcto
     */
    static Optional<String> redirectTarget(String requestHost, String baseUrl, String path, String query) {
        if (requestHost == null || !requestHost.toLowerCase().endsWith(RENDER_SUFFIX)) {
            return Optional.empty();
        }
        String baseHost;
        try {
            baseHost = URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // Si la base publica es el propio host de Render (o no hay dominio propio
        // configurado), no hay a donde mandarlo.
        // Sin punto es localhost (el valor por defecto de app.base-url): mandar a un
        // pedido publico a "localhost" seria dejar el sitio inaccesible.
        if (baseHost == null || !baseHost.contains(".") || baseHost.toLowerCase().endsWith(RENDER_SUFFIX)) {
            return Optional.empty();
        }
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return Optional.of(root + path + (query == null || query.isBlank() ? "" : "?" + query));
    }
}
