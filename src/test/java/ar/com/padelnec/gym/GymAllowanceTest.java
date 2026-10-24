package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.gym.service.GymAllowance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La cuenta del tope por mes: dias por semana proporcionales a lo que dura el periodo. */
class GymAllowanceTest {

    @Test
    @DisplayName("El tope del mes es dias por semana x dias del periodo / 7, redondeado")
    void monthlyLimitIsProportionalToThePeriod() {
        // dias por semana -> periodos de 28, 29, 30 y 31 dias
        int[][] expected = {
                {4, 4, 4, 4},
                {8, 8, 9, 9},
                {12, 12, 13, 13},
                {16, 17, 17, 18},
                {20, 21, 21, 22},
                {24, 25, 26, 27},
                {28, 29, 30, 31},
        };
        for (int days = 1; days <= 7; days++) {
            for (int length = 28; length <= 31; length++) {
                assertThat(GymAllowance.proportional(days, length))
                        .as("%d dias por semana en un periodo de %d dias", days, length)
                        .isEqualTo(expected[days - 1][length - 28]);
            }
        }
    }

    @Test
    @DisplayName("El pase de 15 dias usa la misma cuenta")
    void passLimitUsesTheSameRule() {
        assertThat(new int[] {
                GymAllowance.proportional(1, 15), GymAllowance.proportional(2, 15), GymAllowance.proportional(3, 15),
                GymAllowance.proportional(4, 15), GymAllowance.proportional(5, 15), GymAllowance.proportional(6, 15),
                GymAllowance.proportional(7, 15)})
                .containsExactly(2, 4, 6, 9, 11, 13, 15);
    }

    @Test
    @DisplayName("Nunca queda un ,5 que redondear: la regla no depende de para donde se desempata")
    void thereAreNoTies() {
        for (int days = 1; days <= 7; days++) {
            for (int length : new int[] {15, 28, 29, 30, 31}) {
                // days * length / 7 termina en ,5 justo cuando 2 * days * length es 7 por un impar.
                assertThat((2 * days * length) % 14)
                        .as("%d dias por semana en %d dias", days, length)
                        .isNotEqualTo(7);
            }
        }
    }
}
