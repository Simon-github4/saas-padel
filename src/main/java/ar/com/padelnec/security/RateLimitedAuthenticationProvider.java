package ar.com.padelnec.security;

import ar.com.padelnec.domain.PanelLogin;
import ar.com.padelnec.service.PanelLoginService;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.api.LoginRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
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
 *
 * <p>Por la misma razon es aca donde se anota cada ingreso al panel
 * ({@link PanelLoginService}): el auto-login de desarrollo no deja rastro.
 */
@Component
@Slf4j
public class RateLimitedAuthenticationProvider implements AuthenticationProvider {

    private final LoginRateLimiter loginRateLimiter;
    private final PanelLoginService panelLoginService;
    private final DaoAuthenticationProvider delegate;

    public RateLimitedAuthenticationProvider(ClubUserDetailsService userDetailsService,
                                             PasswordEncoder passwordEncoder,
                                             LoginRateLimiter loginRateLimiter,
                                             PanelLoginService panelLoginService) {
        this.loginRateLimiter = loginRateLimiter;
        this.panelLoginService = panelLoginService;
        this.delegate = new DaoAuthenticationProvider(userDetailsService);
        this.delegate.setPasswordEncoder(passwordEncoder);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String typedName = authentication.getName();
        try {
            loginRateLimiter.check(typedName);
        } catch (BusinessRuleException ex) {
            note(typedName, PanelLogin.LOCKED);
            // Mismo mensaje que una clave equivocada: no hay que contarle a
            // quien esta probando contrasenas que llego al limite.
            throw new BadCredentialsException("Credenciales invalidas");
        }
        try {
            Authentication result = delegate.authenticate(authentication);
            note(typedName, PanelLogin.OK);
            return result;
        } catch (DisabledException ex) {
            note(typedName, PanelLogin.DISABLED);
            throw ex;
        } catch (AuthenticationException ex) {
            note(typedName, PanelLogin.WRONG_PASSWORD);
            throw ex;
        }
    }

    /** Anotar el ingreso nunca puede impedir entrar al panel. */
    private void note(String typedName, String result) {
        try {
            panelLoginService.record(typedName, result);
        } catch (RuntimeException ex) {
            log.warn("No se pudo anotar el ingreso al panel ({})", result, ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
