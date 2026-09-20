package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.MutableClock;
import ar.com.padelnec.gym.service.GymRateLimits;
import ar.com.padelnec.web.BusinessRuleException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los topes de la app: frenan el abuso y dejan pasar al socio que reintenta una vez. */
class GymRateLimitsTest {

    private MutableClock clock;
    private GymRateLimits limits;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at("2026-09-14T13:00:00Z");
        limits = new GymRateLimits(clock);
    }

    @Test
    @DisplayName("Probar claves contra un DNI se corta al sexto intento, aunque cambie el origen")
    void loginAttemptsAgainstOneDniAreCapped() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            int n = attempt;
            assertThatCode(() -> limits.checkLogin("los-troncos", "30111222", "10.0.0." + n)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limits.checkLogin("los-troncos", "30111222", "10.0.0.99"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("muchos intentos");
    }

    @Test
    @DisplayName("Otro DNI, u otro club con el mismo DNI, tiene su propio cupo")
    void eachDniAndClubHasItsOwnBudget() {
        for (int i = 0; i < 5; i++) {
            limits.checkLogin("los-troncos", "30111222", "10.0.0.1");
        }

        assertThatCode(() -> limits.checkLogin("los-troncos", "40222333", "10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limits.checkLogin("club-necochea", "30111222", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Barrer DNIs desde un mismo origen se corta aunque cada DNI sea distinto")
    void sweepingDnisFromOneOriginIsCapped() {
        for (int i = 0; i < 30; i++) {
            limits.checkLogin("los-troncos", "3000000" + i, "10.0.0.1");
        }

        assertThatThrownBy(() -> limits.checkLogin("los-troncos", "31000000", "10.0.0.1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("El cupo vuelve pasada la ventana")
    void theBudgetComesBackAfterTheWindow() {
        for (int i = 0; i < 5; i++) {
            limits.checkLogin("los-troncos", "30111222", "10.0.0.1");
        }
        assertThatThrownBy(() -> limits.checkLogin("los-troncos", "30111222", "10.0.0.1"))
                .isInstanceOf(BusinessRuleException.class);

        clock.advance(Duration.ofMinutes(16));

        assertThatCode(() -> limits.checkLogin("los-troncos", "30111222", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El check-in tiene margen para reintentar, pero no para decenas de escaneos")
    void checkInAllowsRetriesButNotAbuse() {
        UUID member = UUID.randomUUID();
        for (int i = 0; i < 12; i++) {
            limits.checkCheckIn(member, "10.0.0.1");
        }

        assertThatThrownBy(() -> limits.checkCheckIn(member, "10.0.0.1"))
                .isInstanceOf(BusinessRuleException.class);
        // Otro socio, desde otro origen, no se ve afectado.
        assertThatCode(() -> limits.checkCheckIn(UUID.randomUUID(), "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sanity: el reloj de prueba arranca en la fecha esperada")
    void clockStartsWhereExpected() {
        assertThat(clock.instant()).isEqualTo(java.time.Instant.parse("2026-09-14T13:00:00Z"));
    }
}
