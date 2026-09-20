package ar.com.padelnec.gym.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Cadena de seguridad propia de la app del gimnasio.
 *
 * <p>Tiene que declararse antes que la del panel, que no lleva {@code securityMatcher}
 * y se queda con todo lo que no matcheo antes: sin esto, el link del QR mandaria al
 * socio al login del panel del club. Las rutas son de una SPA y la API que llama ya es
 * de acceso libre ({@code /api/public/**}); lo que autoriza es el token de sesion.
 *
 * <p>Es una cadena aparte, y no una ampliacion de la de la app del jugador, para que el
 * gimnasio traiga sus propias reglas: una CSP sin nada de Google, y permiso de camara
 * para el escaner de QR.
 */
@Configuration
public class GymSecurityConfig {

    /**
     * Sin scripts de terceros. {@code style-src} permite inline porque React aplica
     * {@code style={{...}}}; {@code img-src} permite {@code data:} y {@code blob:} para
     * los iconos y el cuadro de la camara; {@code media-src} permite {@code blob:} por el
     * mismo motivo.
     */
    private static final String GYM_APP_CSP = "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "media-src 'self' blob:; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 3)
    public SecurityFilterChain gymAppChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/gym/**", "/gym-app/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(GYM_APP_CSP))
                        // La camara (escaner de QR) y la ubicacion (verificar que el socio esta en la
                        // sede) solo para esta misma pagina.
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(self), geolocation=(self)")))
                .build();
    }
}
