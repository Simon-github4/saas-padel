package ar.com.padelnec.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Direccion del jugador que hizo el pedido, para los topes de intentos por origen.
 *
 * <p>No sale de {@code getRemoteAddr()}: con {@code forward-headers-strategy:
 * framework}, Spring lo arma con el primer valor de {@code Forwarded} o
 * {@code X-Forwarded-For}, y ese valor lo escribe el cliente. Render agrega su
 * direccion al final del header en vez de reemplazarlo, asi que mandando
 * {@code Forwarded: for=<cualquier cosa>} distinto en cada pedido, cada uno
 * contaba como un origen nuevo y ningun tope por origen frenaba nada.
 *
 * <p>Render esta detras de Cloudflare, y Cloudflare pisa {@code CF-Connecting-IP}
 * en cada pedido con la direccion real que se le conecto: lo que mande el cliente
 * en ese header no llega. Sin el header (desarrollo local, tests) no hay proxy
 * adelante y queda la direccion del socket.
 */
public final class ClientIp {

    static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String cloudflare = request.getHeader(CLOUDFLARE_HEADER);
        if (cloudflare != null && !cloudflare.isBlank()) {
            return cloudflare.trim();
        }
        return request.getRemoteAddr();
    }
}
