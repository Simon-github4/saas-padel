package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.PanelLogin;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.PanelLoginRepository;
import ar.com.padelnec.security.RateLimitedAuthenticationProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Cada ingreso al panel queda anotado con el club, la IP y el dispositivo. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PanelLoginTest {

    private static final String PASSWORD = "unaClaveLarga123";
    private static final String IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";

    @Autowired private RateLimitedAuthenticationProvider provider;
    @Autowired private ClubUserRepository clubUserRepository;
    @Autowired private PanelLoginRepository panelLoginRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubFixture fixture;

    private Tenant club;
    private ClubUser owner;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-necochea");
        // Mail unico por corrida: el limite de intentos es compartido con el
        // resto de la suite.
        owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setEmail("dueno-%s@test.com".formatted(UUID.randomUUID()));
        owner.setFullName("Dueño " + UUID.randomUUID());
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash(passwordEncoder.encode(PASSWORD));
        owner = clubUserRepository.saveAndFlush(owner);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("181.45.10.20");
        request.addHeader("User-Agent", IPHONE);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Un ingreso correcto guarda club, usuario, IP y dispositivo")
    void recordsSuccessfulLogin() {
        provider.authenticate(new UsernamePasswordAuthenticationToken(owner.getEmail(), PASSWORD));

        List<PanelLogin> logins = loginsOf(owner);
        assertThat(logins).singleElement().satisfies(login -> {
            assertThat(login.getResult()).isEqualTo(PanelLogin.OK);
            assertThat(login.getClubId()).isEqualTo(club.getId());
            assertThat(login.getUserName()).isEqualTo(owner.getFullName());
            assertThat(login.getIp()).isEqualTo("181.45.10.20");
            assertThat(login.getDevice()).isEqualTo("mobile");
            assertThat(login.getUserAgent()).isEqualTo(IPHONE);
        });
        assertThat(clubUserRepository.findById(owner.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("Una clave equivocada queda anotada como intento fallido")
    void recordsWrongPassword() {
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken(owner.getFullName(), "otraClave")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(loginsOf(owner)).singleElement()
                .extracting(PanelLogin::getResult).isEqualTo(PanelLogin.WRONG_PASSWORD);
        assertThat(clubUserRepository.findById(owner.getId()).orElseThrow().getLastLoginAt()).isNull();
    }

    @Test
    @DisplayName("Lo que se tipea sin coincidir con ningun usuario no se guarda")
    void ignoresUnknownUsers() {
        long before = panelLoginRepository.count();

        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("nadie-" + UUID.randomUUID(), PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(panelLoginRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Los intentos frenados por el limite quedan como bloqueados")
    void recordsLockedAttempts() {
        for (int i = 0; i < 5; i++) {
            provider.authenticate(new UsernamePasswordAuthenticationToken(owner.getEmail(), PASSWORD));
        }
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken(owner.getEmail(), PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(loginsOf(owner)).extracting(PanelLogin::getResult)
                .containsOnly(PanelLogin.OK, PanelLogin.LOCKED)
                .filteredOn(PanelLogin.LOCKED::equals).hasSize(1);
    }

    private List<PanelLogin> loginsOf(ClubUser user) {
        return panelLoginRepository.findAll().stream()
                .filter(login -> user.getId().equals(login.getUserId()))
                .toList();
    }
}
