package ar.com.padelnec.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Entra solo al panel, para no tipear la clave en cada recarga.
 *
 * <p>ATAJO DE DESARROLLO. Existe unicamente bajo el perfil {@code dev}, que es el
 * mismo que levanta el PostgreSQL embebido y carga el club de ejemplo: fuera de
 * ese perfil la clase ni siquiera se instancia, asi que no hay forma de que un
 * despliegue real quede abierto por olvidarse de sacarla.
 *
 * <p>Se apaga con {@code app.dev-auto-login=false} sin cambiar de perfil, para
 * cuando haga falta probar el login de verdad.
 *
 * <p>No reemplaza a la cadena de seguridad: la deja intacta y solo le pone un
 * usuario ya autenticado. Los permisos por vista ({@code @RolesAllowed}) y el
 * club que instala {@link ar.com.padelnec.web.AdminTenantFilter} a partir del
 * principal siguen funcionando igual que con un login normal.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.dev-auto-login", havingValue = "true", matchIfMissing = true)
public class DevAutoLoginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DevAutoLoginFilter.class);

    private final UserDetailsService users;
    private final String email;

    public DevAutoLoginFilter(UserDetailsService users,
                              @Value("${app.dev-auto-login-user:dueno@clubnecochea.test}") String email) {
        this.users = users;
        this.email = email;
    }

    @PostConstruct
    void announce() {
        log.warn("""

                ****************************************************************
                  LOGIN DESACTIVADO (perfil dev): se entra al panel como {}
                  Apagalo con --app.dev-auto-login=false
                ****************************************************************""", email);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (needsUser()) {
            try {
                UserDetails user = users.loadUserByUsername(email);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                user, null, user.getAuthorities()));
            } catch (UsernameNotFoundException notSeeded) {
                // La base de desarrollo se puede haber recreado sin el seeder.
                log.warn("No existe el usuario {}: el panel va a pedir login", email);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean needsUser() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        return current == null || current instanceof AnonymousAuthenticationToken;
    }
}
