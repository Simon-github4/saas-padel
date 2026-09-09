package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.PricingRuleRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code findRulesForDay} tiene que traer la cancha y los dias en un solo
 * viaje: antes de la subquery-y-fetch-join, el {@code ElementCollection}
 * EAGER de {@code days} disparaba una consulta aparte por cada regla
 * devuelta, en el camino mas caliente del sistema (la grilla publica).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PricingRuleRepositoryTest {

    @Autowired private PricingRuleRepository pricingRuleRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Tenant club;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        fixture.priceWindow(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(14, 0), "5000");
        fixture.priceWindow(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(20, 0), "6000");
        fixture.priceWindow(DayOfWeek.TUESDAY, LocalTime.of(20, 0), LocalTime.of(23, 59), "7000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Trae varias reglas del mismo dia en una sola consulta, con los dias ya poblados")
    void fetchesMatchingRulesInOneRoundTrip() {
        Statistics stats = statistics();
        stats.clear();

        List<PricingRule> rules = pricingRuleRepository.findRulesForDay(DayOfWeek.TUESDAY.getValue());

        // Tocar days es lo que revelaria el N+1 si el fetch join no funciono:
        // sin JOIN FETCH, esto dispararia una consulta extra por regla.
        for (PricingRule rule : rules) {
            assertThat(rule.getDays()).contains(DayOfWeek.TUESDAY);
        }

        assertThat(rules).hasSize(3);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
