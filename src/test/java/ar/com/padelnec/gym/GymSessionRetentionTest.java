package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.MutableClock;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.repository.GymSessionRepository;
import ar.com.padelnec.gym.service.GymAuthService;
import ar.com.padelnec.gym.service.GymAuthService.IssuedSession;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymSessionRetentionJob;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

/** El job diario borra las sesiones que ya no sirven y deja las que si. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, GymSessionRetentionTest.FixedClockConfig.class})
class GymSessionRetentionTest {

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
    private GymSessionRetentionJob job;

    @BeforeEach
    void setUp() {
        mutableClock().set(START);
        clubFixture.reset();
        club = clubFixture.club("los-troncos");
        gym.enable(club);
        member = gym.member(club, "30111222", "Ana Gómez");
        // El job real no existe en los tests (app.jobs.enabled = false): se arma a mano.
        job = new GymSessionRetentionJob(sessionRepository, clock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MutableClock mutableClock() {
        return (MutableClock) clock;
    }

    private IssuedSession login() {
        return TenantContext.callAs(club.getId(), () -> authService.login(member.dni(), member.temporaryPassword()));
    }

    private long sessions() {
        return TenantContext.callAs(TenantContext.ROOT, sessionRepository::count);
    }

    @Test
    @DisplayName("Borra las sesiones cerradas hace mas de un mes y las vencidas, y conserva las vigentes")
    void deletesOnlyWhatIsStale() {
        IssuedSession closed = login();
        login(); // sigue abierta
        TenantContext.runAs(club.getId(), () -> authService.logout(closed.token()));
        assertThat(sessions()).isEqualTo(2);

        mutableClock().set(START.plus(Duration.ofDays(10)));
        job.deleteStaleSessions();
        assertThat(sessions()).as("cerrada hace 10 dias: todavia se guarda").isEqualTo(2);

        mutableClock().set(START.plus(Duration.ofDays(35)));
        job.deleteStaleSessions();
        assertThat(sessions()).as("la cerrada ya paso el mes; la abierta vale un año").isEqualTo(1);

        mutableClock().set(START.plus(Duration.ofDays(400)));
        job.deleteStaleSessions();
        assertThat(sessions()).as("la abierta ya vencio").isZero();
    }
}
