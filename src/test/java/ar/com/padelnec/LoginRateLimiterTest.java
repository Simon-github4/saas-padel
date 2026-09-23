package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.api.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter(MutableClock.at("2026-09-23T13:00:00Z"));
    }

    @Test
    @DisplayName("Cambiar mayusculas o agregar espacios no da intentos nuevos contra la misma cuenta")
    void caseAndSpacingVariantsShareTheBudget() {
        String[] variants = {"dueno@club.com", "Dueno@club.com", "DUENO@CLUB.COM", " dueno@club.com ", "dUeNo@Club.com"};
        for (String variant : variants) {
            assertThatCode(() -> limiter.check(variant)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.check("DuEnO@cLuB.CoM"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("muchos intentos");
    }

    @Test
    @DisplayName("La i sin punto no sirve para abrir un cupo aparte")
    void dotlessIDoesNotOpenANewBudget() {
        for (int i = 0; i < 5; i++) {
            limiter.check("admin");
        }

        assertThatThrownBy(() -> limiter.check("admın"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Otra cuenta tiene su propio cupo")
    void anotherAccountHasItsOwnBudget() {
        for (int i = 0; i < 5; i++) {
            limiter.check("dueno@club.com");
        }

        assertThatCode(() -> limiter.check("otro@club.com")).doesNotThrowAnyException();
    }
}
