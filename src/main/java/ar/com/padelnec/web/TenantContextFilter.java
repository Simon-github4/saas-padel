package ar.com.padelnec.web;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establece el club de la peticion antes de que se abra ninguna transaccion.
 *
 * <p>Tiene que ser aca y no dentro de un servicio por como funciona Hibernate: el
 * identificador de tenant se resuelve una sola vez, al crear la sesion. Establecerlo
 * despues, ya dentro de una transaccion abierta, no cambia el filtro de esa sesion,
 * y las consultas siguen buscando en un club que no existe.
 *
 * <p>El otro motivo es igual de importante: Tomcat reutiliza los hilos entre
 * peticiones, y el contexto vive en un {@link ThreadLocal}. Sin el borrado del
 * {@code finally}, una peticion heredaria el club de la anterior y le mostraria a un
 * club la agenda de otro.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String PUBLIC_PREFIX = "/api/public/";
    private static final String WEBHOOK_PREFIX = "/api/webhooks/mercadopago/";

    /** Segmentos reservados: son endpoints de plataforma, no el slug de un club. */
    private static final Set<String> PLATFORM_ENDPOINTS = Set.of("search", "events");

    private final TenantRepository tenantRepository;
    private final BookingRepository bookingRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            resolve(request.getRequestURI()).ifPresent(TenantContext::set);
            chain.doFilter(request, response);
        } finally {
            // Siempre, pase lo que pase: el hilo vuelve al pool sin rastro del club.
            TenantContext.clear();
        }
    }

    private Optional<UUID> resolve(String path) {
        if (path.startsWith(WEBHOOK_PREFIX)) {
            return bySlug(firstSegment(path.substring(WEBHOOK_PREFIX.length())));
        }
        if (!path.startsWith(PUBLIC_PREFIX)) {
            return Optional.empty();
        }

        String rest = path.substring(PUBLIC_PREFIX.length());
        String first = firstSegment(rest);

        // La busqueda global cruza clubes: no tiene uno solo que instalar, y cada club
        // se activa despues, de a uno. La bitacora de visitas es parecida: la mitad de
        // los eventos son de la portada o de la busqueda, que no son de ningun club.
        // Sin esta excepcion el segmento se leeria como un slug, y el dia que alguien
        // registre un club llamado "search" o "events", esos endpoints quedarian
        // atados a ese club.
        if (PLATFORM_ENDPOINTS.contains(first)) {
            return Optional.empty();
        }

        // Los links que le llegan al jugador por WhatsApp no llevan el slug del club,
        // asi que el tenant se deduce del propio token.
        if ("manage".equals(first) || "confirm".equals(first) || "share".equals(first)) {
            String token = firstSegment(rest.substring(first.length() + 1));
            return token.isBlank() ? Optional.empty() : byToken(token);
        }
        return bySlug(first);
    }

    private Optional<UUID> bySlug(String slug) {
        if (slug.isBlank()) {
            return Optional.empty();
        }
        return tenantRepository.findBySlugIgnoreCaseAndActiveTrue(slug)
                .map(club -> club.getId());
    }

    private Optional<UUID> byToken(String token) {
        return bookingRepository.findClubIdByAnyToken(token);
    }

    private String firstSegment(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }
}
