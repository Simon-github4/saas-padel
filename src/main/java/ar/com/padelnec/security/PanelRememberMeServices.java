package ar.com.padelnec.security;

import ar.com.padelnec.service.PanelRememberService;
import ar.com.padelnec.support.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.stereotype.Component;

/**
 * "Recordarme" del panel: despues de entrar con la clave, el dispositivo vuelve a
 * entrar solo durante {@link PanelRememberService#VALIDITY}.
 *
 * <p>La sesion del panel vive en la memoria del proceso: se pierde a los 30 minutos
 * sin uso (en el celular, apenas Chrome congela la pestaña con la pantalla
 * apagada) y con cada deploy. Esta cookie es la que la vuelve a armar sin pedir la
 * clave.
 *
 * <p>Siempre activo, sin casilla: el formulario de login de Vaadin no tiene donde
 * ponerla, y el panel se usa en dispositivos del propio club. "Salir" lo corta en
 * ese dispositivo.
 *
 * <p>La cookie lleva un token al azar y nada mas -ni el usuario ni la clave-, y
 * la base guarda solo su huella. Es {@code HttpOnly}, asi que ningun script de la
 * pagina la puede leer, y viaja solo por HTTPS cuando el pedido llega por HTTPS
 * (detras de Render lo dice {@code X-Forwarded-Proto}).
 *
 * <p>La clave de firma se genera en cada arranque. No protege nada guardado: es la
 * que comparten este servicio y el {@code RememberMeAuthenticationProvider} para
 * reconocer los ingresos que arma este mismo proceso.
 */
@Component
@Slf4j
public class PanelRememberMeServices extends AbstractRememberMeServices {

    public static final String COOKIE_NAME = "panel-recordado";

    private final PanelRememberService rememberService;

    public PanelRememberMeServices(ClubUserDetailsService userDetailsService,
                                   PanelRememberService rememberService) {
        super(Tokens.generate(), userDetailsService);
        this.rememberService = rememberService;
        setCookieName(COOKIE_NAME);
        setAlwaysRemember(true);
        setTokenValiditySeconds((int) PanelRememberService.VALIDITY.toSeconds());
        setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
    }

    @Override
    protected void onLoginSuccess(HttpServletRequest request, HttpServletResponse response,
                                  Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof ClubUserPrincipal user)) {
            return;
        }
        // Si este navegador ya estaba recordado (entro de nuevo con la clave, o con
        // otro usuario), el token anterior queda sin uso: se borra en vez de esperar
        // a que venza.
        forgetThisDevice(request);
        String token = rememberService.remember(user.userId());
        setCookie(new String[] {token}, getTokenValiditySeconds(), request, response);
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                 HttpServletResponse response) {
        if (cookieTokens.length != 1) {
            throw new InvalidCookieException("Cookie de recordar con formato inesperado");
        }
        return rememberService.resolve(cookieTokens[0])
                .map(ClubUserPrincipal::of)
                // Vencido, olvidado o de un usuario deshabilitado: Spring borra la
                // cookie y el pedido sigue sin sesion, o sea, al login.
                .orElseThrow(() -> new RememberMeAuthenticationException("Dispositivo no recordado"));
    }

    /** "Salir": ademas de borrar la cookie, el token deja de servir aunque alguien lo haya copiado. */
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        forgetThisDevice(request);
        super.logout(request, response, authentication);
    }

    private void forgetThisDevice(HttpServletRequest request) {
        String cookie = extractRememberMeCookie(request);
        if (cookie == null || cookie.isEmpty()) {
            return;
        }
        try {
            String[] tokens = decodeCookie(cookie);
            if (tokens.length == 1) {
                rememberService.forget(tokens[0]);
            }
        } catch (InvalidCookieException ex) {
            log.debug("Cookie de recordar ilegible, no hay token que olvidar");
        }
    }
}
