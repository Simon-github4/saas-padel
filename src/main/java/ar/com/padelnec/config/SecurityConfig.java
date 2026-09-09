package ar.com.padelnec.config;

import ar.com.padelnec.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Dos mundos con reglas opuestas conviviendo en la misma aplicacion.
 *
 * <p>La API del jugador es publica y sin sesion: lo que autoriza es el token
 * secreto que viaja en la URL, no un login. El panel del club es lo contrario,
 * con sesion, usuario y rol. Por eso son cadenas de filtros separadas y no un
 * unico conjunto de reglas lleno de excepciones.
 */
@Configuration
public class SecurityConfig {

    /**
     * {@code style-src} necesita {@code 'unsafe-inline'}: React aplica el prop
     * {@code style={{...}}} y {@code ClubPage} sus colores de marca via
     * {@code element.style.setProperty(...)} - probado en el navegador, esa
     * mutacion via CSSOM SI cuenta como estilo inline para CSP, igual que un
     * atributo {@code style=""} en el HTML. {@code img-src} permite cualquier
     * origen https porque el dueno del club puede pegar una URL externa como
     * foto de portada (ver {@code Tenant.heroImageUrl}). Google Identity
     * Services necesita su propio origen en script/connect/frame, y el mapa
     * de "como llegar" embebe un iframe de OpenStreetMap
     * ({@code HowToGetThereSection.tsx}).
     */
    private static final String PLAYER_APP_CSP = "default-src 'self'; "
            + "script-src 'self' https://accounts.google.com; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; "
            + "font-src 'self' data:; "
            + "connect-src 'self' https://accounts.google.com; "
            + "frame-src https://accounts.google.com https://www.openstreetmap.org; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

    /**
     * El motor cliente de Vaadin Flow (bootstrap de {@code VAADIN/build/})
     * arranca con {@code <script>} inline y algo de codigo evaluado en tiempo
     * de ejecucion - probado en el navegador: sin {@code 'unsafe-inline'} Y
     * {@code 'unsafe-eval'} en {@code script-src}, el panel queda en blanco.
     * Ese es un limite real del framework, no una eleccion - reduce lo que
     * CSP protege aca a las otras directivas (origenes externos de
     * script/conexion/imagen, {@code frame-ancestors}, {@code object-src}),
     * que igual valen. Mismo motivo para {@code 'unsafe-inline'} en
     * {@code style-src}: Flow y el tema Lumo inyectan {@code <style>} propios
     * por componente.
     */
    private static final String ADMIN_CSP = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

    /**
     * API publica y webhooks.
     *
     * <p>Sin CSRF porque no hay sesion ni cookies que un navegador adjunte solo. El
     * webhook de MercadoPago tampoco podria mandar un token CSRF aunque quisiera: lo
     * que lo autentica es su firma HMAC.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain publicApiChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/api/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))
                .build();
    }

    /** Sondas de salud, sin exponer el resto de actuator. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain healthChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/health/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /**
     * App del jugador.
     *
     * <p>Tiene que declararse antes que la cadena del panel, porque esa ultima no
     * lleva {@code securityMatcher} y se queda con todo lo que no matcheo antes. Sin
     * esto, el link que le llega al jugador por WhatsApp lo manda al login del club.
     *
     * <p>Son rutas de una SPA, no recursos del servidor: las resuelve React y el
     * backend solo devuelve el index. Lo que protege el turno es el token de la URL.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public SecurityFilterChain playerAppChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/", "/index.html", "/favicon.ico", "/assets/**",
                        "/buscar", "/club/**", "/manage/**", "/confirm/**", "/turno/**",
                        "/login", "/account", "/forgot-password", "/reset-password/**",
                        "/privacidad", "/terminos")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(PLAYER_APP_CSP)))
                .build();
    }

    /**
     * Panel del club.
     *
     * <p>Vaadin aporta su propio configurador: el acceso a cada vista lo decide la
     * anotacion de la vista ({@code @PermitAll}, {@code @RolesAllowed}) y no una
     * lista de rutas repetida aca, que se desincroniza apenas se agrega una pantalla.
     * Una vista sin anotacion queda denegada.
     *
     * <p>El filtro de auto-login solo existe bajo el perfil {@code dev}; en
     * cualquier otro perfil el ObjectProvider viene vacio y la cadena queda tal
     * cual, con su login normal.
     */
    @Bean
    public SecurityFilterChain adminChain(HttpSecurity http,
                                          ObjectProvider<DevAutoLoginFilter> devAutoLogin)
            throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(),
                configurer -> configurer.loginView(LoginView.class, "/"));
        devAutoLogin.ifAvailable(filter -> http.addFilterBefore(filter, AuthorizationFilter.class));
        http.headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives(ADMIN_CSP)));
        return http.build();
    }

    /** BCrypt para las contrasenas del panel. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
