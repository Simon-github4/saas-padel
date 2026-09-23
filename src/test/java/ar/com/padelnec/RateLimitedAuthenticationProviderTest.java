package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.security.RateLimitedAuthenticationProvider;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * El login del panel reusa {@link ar.com.padelnec.web.api.LoginRateLimiter}
 * (el mismo que ya frena el login del jugador): antes de este cambio, nada
 * frenaba adivinar contrasenas contra el panel a repeticion.
 *
 * <p>El chequeo vive en {@link RateLimitedAuthenticationProvider#authenticate},
 * no en {@code ClubUserDetailsService.loadUserByUsername} -ver el javadoc de
 * esa clase para el porque: ese metodo tambien lo llama {@code
 * DevAutoLoginFilter} en cada request sin sesion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class RateLimitedAuthenticationProviderTest {

    private static final String PASSWORD = "unaClaveLarga123";

    @Autowired private RateLimitedAuthenticationProvider provider;
    @Autowired private ClubUserRepository clubUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubFixture fixture;

    @Test
    @DisplayName("Despues de varios intentos seguidos con el mismo mail, el login del panel se frena")
    void locksOutAfterRepeatedAttempts() {
        fixture.reset();
        Tenant club = fixture.club("club-necochea");
        // Mail unico por corrida: LoginRateLimiter es un singleton compartido con
        // el resto de la suite, y esto evita que otro test ya haya gastado el
        // cupo de un mail fijo.
        String email = "dueno-%s@test.com".formatted(UUID.randomUUID());

        ClubUser owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setEmail(email);
        owner.setFullName("Dueño de prueba");
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash(passwordEncoder.encode(PASSWORD));
        clubUserRepository.saveAndFlush(owner);

        // Las primeras cinco (el limite) autentican de verdad.
        for (int i = 0; i < 5; i++) {
            Authentication result = provider.authenticate(
                    new UsernamePasswordAuthenticationToken(email, PASSWORD));
            assertThat(result.isAuthenticated()).isTrue();
        }

        // La sexta se frena, aunque la contraseña sea la correcta.
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken(email, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Escribir el mail o el usuario con otras mayusculas no da intentos nuevos")
    void caseVariantsDoNotResetTheBudget() {
        fixture.reset();
        Tenant club = fixture.club("club-necochea");
        String suffix = UUID.randomUUID().toString();
        String email = "dueno-%s@test.com".formatted(suffix);
        String username = "Dueño " + suffix;

        ClubUser owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setEmail(email);
        owner.setFullName(username);
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash(passwordEncoder.encode(PASSWORD));
        clubUserRepository.saveAndFlush(owner);

        // El login busca sin distinguir mayusculas: cada variante entra a la misma
        // cuenta, asi que tiene que gastar el mismo cupo. Mail y usuario llevan
        // cupos separados; se prueba cada uno por su lado.
        assertVariantsShareOneBudget(new String[] {
                email, email.toUpperCase(), " " + email + " ", "Dueno-" + suffix + "@Test.com",
                "DUENO-" + suffix + "@test.com"}, "dUeNo-" + suffix + "@TEST.com");
        assertVariantsShareOneBudget(new String[] {
                username, username.toUpperCase(), username.toLowerCase(), " " + username,
                "dUEÑO " + suffix}, "DUEÑO " + suffix.toUpperCase());
    }

    private void assertVariantsShareOneBudget(String[] wrongPasswordVariants, String lastVariant) {
        for (String variant : wrongPasswordVariants) {
            assertThatThrownBy(() -> provider.authenticate(
                    new UsernamePasswordAuthenticationToken(variant, "claveEquivocada")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        // La sexta se frena aunque traiga la contraseña correcta: el cupo ya se gasto.
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken(lastVariant, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }
}
