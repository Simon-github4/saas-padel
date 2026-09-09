package ar.com.padelnec.security;

import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.api.LoginRateLimiter;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Login del panel con un techo de intentos por mail.
 *
 * <p>Reusa {@link LoginRateLimiter} -el mismo que ya frena el login del
 * jugador-: sin esto, nada frenaba adivinar contrasenas contra el panel a
 * repeticion.
 *
 * <p>El chequeo va aca y no en {@link ClubUserDetailsService} a proposito:
 * ese servicio tambien lo llama {@code DevAutoLoginFilter} en cada request
 * sin sesion, no solo un submit real del formulario de login -- contar eso
 * agotaria el cupo antes de que alguien llegue a tipear una contrasena.
 * {@link AuthenticationProvider#authenticate} en cambio solo lo invoca
 * Spring Security cuando de verdad hay un intento de autenticacion.
 */
@Component
public class RateLimitedAuthenticationProvider implements AuthenticationProvider {

    private final LoginRateLimiter loginRateLimiter;
    private final DaoAuthenticationProvider delegate;

    public RateLimitedAuthenticationProvider(ClubUserDetailsService userDetailsService,
                                             PasswordEncoder passwordEncoder,
                                             LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
        this.delegate = new DaoAuthenticationProvider(userDetailsService);
        this.delegate.setPasswordEncoder(passwordEncoder);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            loginRateLimiter.check(authentication.getName());
        } catch (BusinessRuleException ex) {
            // Mismo mensaje que una clave equivocada: no hay que contarle a
            // quien esta probando contrasenas que llego al limite.
            throw new BadCredentialsException("Credenciales invalidas");
        }
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
