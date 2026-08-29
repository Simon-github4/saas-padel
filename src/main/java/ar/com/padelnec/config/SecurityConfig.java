package ar.com.padelnec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dos mundos con reglas distintas conviviendo en la misma aplicacion.
 *
 * <p>La API del jugador es publica y sin sesion: se autoriza por el token secreto
 * que viaja en la URL, no por login. El panel del club es todo lo contrario, con
 * sesion y usuario. Por eso son cadenas de filtros separadas y no un unico
 * conjunto de reglas lleno de excepciones.
 */
@Configuration
public class SecurityConfig {

    /**
     * API publica y webhooks.
     *
     * <p>Sin CSRF porque no hay sesion ni cookies: la protege el token de la URL,
     * que no viaja en un encabezado que el navegador adjunte solo. El webhook de
     * MercadoPago, ademas, no podria mandar un token CSRF ni aunque quisiera; lo
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

    /** Sondas de salud del proceso, sin el resto de actuator expuesto. */
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
     * Todo lo demas: el panel del club y las pantallas del jugador servidas por la
     * SPA. Los recursos estaticos quedan abiertos y el panel exige login.
     */
    @Bean
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/assets/**", "/favicon.ico",
                                "/manage/**", "/confirm/**", "/club/**").permitAll()
                        .requestMatchers("/admin/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults())
                .build();
    }

    /**
     * BCrypt para las contrasenas del panel. Nunca se guarda una contrasena en
     * claro ni un hash sin sal.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
