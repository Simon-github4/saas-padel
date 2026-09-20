package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.MutableClock;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymSession;
import ar.com.padelnec.gym.repository.GymSessionRepository;
import ar.com.padelnec.gym.service.GymAuthService;
import ar.com.padelnec.gym.service.GymAuthService.IssuedSession;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * La sesion del socio es deslizante: dura un año desde el ULTIMO uso, asi quien va al
 * gimnasio no vuelve a entrar nunca, y la que se abandona vence sola.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, GymSessionTest.FixedClockConfig.class})
class GymSessionTest {

    private static final Instant START = Instant.parse("2026-09-14T13:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at("2026-09-14T13:00:00Z");
        }
    }

    @Autowired private GymAuthService authService;
    @Autowired private GymSessionRepository sessionRepository;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;
    @Autowired private Clock clock;

    private Tenant club;
    private CreatedMember member;

    @BeforeEach
    void setUp() {
        mutableClock().set(START);
        clubFixture.reset();
        club = clubFixture.club("los-troncos");
        gym.enable(club);
        member = gym.member(club, "30111222", "Ana Gómez");
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MutableClock mutableClock() {
        return (MutableClock) clock;
    }

    private Instant expiryOf(String ignored) {
        return sessionRepository.findAll().stream().map(GymSession::getExpiresAt)
                .max(Comparator.naturalOrder()).orElseThrow();
    }

    @Test
    @DisplayName("La sesion dura un año desde que se abre")
    void aSessionLastsAYear() {
        IssuedSession session = authService.login(member.dni(), null);

        assertThat(session.expiresAt()).isEqualTo(START.plus(Duration.ofDays(365)));
    }

    @Test
    @DisplayName("Cada uso la extiende: pasados 200 dias, sigue vigente y vence un año despues de ESE uso")
    void everyUseSlidesTheExpiry() {
        IssuedSession session = authService.login(member.dni(), null);

        mutableClock().set(START.plus(Duration.ofDays(200)));
        authService.requireMember(session.token());

        assertThat(expiryOf(session.token())).isEqualTo(START.plus(Duration.ofDays(565)));

        // Y sigue viva mucho mas alla de los 365 dias del login original.
        mutableClock().set(START.plus(Duration.ofDays(500)));
        assertThat(authService.requireMember(session.token()).getFullName()).isEqualTo("Ana Gómez");
    }

    @Test
    @DisplayName("Una sesion que nadie usa en un año vence")
    void anAbandonedSessionExpires() {
        IssuedSession session = authService.login(member.dni(), null);

        mutableClock().set(START.plus(Duration.ofDays(366)));

        assertThatThrownBy(() -> authService.requireMember(session.token()))
                .isInstanceOf(UnauthorizedSessionException.class);
    }

    @Test
    @DisplayName("No escribe en la base en cada pedido: renueva a lo sumo una vez por dia")
    void itDoesNotRewriteOnEveryRequest() {
        IssuedSession session = authService.login(member.dni(), null);
        Instant original = expiryOf(session.token());

        mutableClock().set(START.plus(Duration.ofHours(5)));
        authService.requireMember(session.token());

        assertThat(expiryOf(session.token())).isEqualTo(original);
    }
}
