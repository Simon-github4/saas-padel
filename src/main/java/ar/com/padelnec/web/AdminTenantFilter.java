package ar.com.padelnec.web;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.security.ClubUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establece el club del panel a partir del usuario autenticado.
 *
 * <p>Va en un filtro aparte del que atiende la API publica por una cuestion de
 * orden: aquel corre primero de todo, antes que Spring Security, cuando todavia no
 * hay nadie autenticado. Este corre despues de la cadena de seguridad, que es
 * cuando el usuario ya existe.
 *
 * <p>El club sale siempre de la sesion y nunca de un parametro de la pantalla: asi
 * nadie puede mirar la agenda de otro club cambiando un id en la URL.
 */
@Component
// Spring Security registra su cadena en -100. Cualquier valor mayor corre
// despues, que es cuando el usuario ya esta autenticado.
@Order(0)
public class AdminTenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof ClubUserPrincipal principal
                && principal.clubId() != null) {
            TenantContext.set(principal.clubId());
        }
        // El borrado lo hace TenantContextFilter, que envuelve a este: es el filtro
        // mas externo y se ejecuta pase lo que pase.
        chain.doFilter(request, response);
    }
}
