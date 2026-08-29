package ar.com.padelnec.config;

import ar.com.padelnec.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
     * Panel del club.
     *
     * <p>Vaadin aporta su propio configurador: el acceso a cada vista lo decide la
     * anotacion de la vista ({@code @PermitAll}, {@code @RolesAllowed}) y no una
     * lista de rutas repetida aca, que se desincroniza apenas se agrega una pantalla.
     * Una vista sin anotacion queda denegada.
     */
    @Bean
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(),
                configurer -> configurer.loginView(LoginView.class, "/"));
        return http.build();
    }

    /** BCrypt para las contrasenas del panel. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
