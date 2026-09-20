package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ar.com.padelnec.gym.service.GymLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GymLocationTest {

    private static final double NECOCHEA_LAT = -38.5545;
    private static final double NECOCHEA_LON = -58.7396;

    @Test
    @DisplayName("El mismo punto esta a cero metros")
    void samePointIsZero() {
        assertThat(GymLocation.distanceMeters(NECOCHEA_LAT, NECOCHEA_LON, NECOCHEA_LAT, NECOCHEA_LON)).isZero();
    }

    @Test
    @DisplayName("Un grado de latitud son unos 111,2 km, en cualquier lado")
    void oneDegreeOfLatitude() {
        assertThat(GymLocation.distanceMeters(NECOCHEA_LAT, NECOCHEA_LON, NECOCHEA_LAT + 1, NECOCHEA_LON))
                .isCloseTo(111_195, within(50.0));
    }

    @Test
    @DisplayName("Un grado de longitud mide menos a esta latitud: unos 87 km en Necochea")
    void oneDegreeOfLongitudeShrinksWithLatitude() {
        assertThat(GymLocation.distanceMeters(NECOCHEA_LAT, NECOCHEA_LON, NECOCHEA_LAT, NECOCHEA_LON + 1))
                .isCloseTo(111_195 * Math.cos(Math.toRadians(NECOCHEA_LAT)), within(100.0));
    }

    @Test
    @DisplayName("La distancia es la misma de ida y de vuelta, y sirve para el margen de 200 m")
    void distanceIsSymmetricAndUsefulAtTheScaleOfTheMargin() {
        double there = GymLocation.distanceMeters(NECOCHEA_LAT, NECOCHEA_LON, NECOCHEA_LAT + 0.0018, NECOCHEA_LON);
        double back = GymLocation.distanceMeters(NECOCHEA_LAT + 0.0018, NECOCHEA_LON, NECOCHEA_LAT, NECOCHEA_LON);

        assertThat(there).isCloseTo(200, within(1.0));
        assertThat(back).isEqualTo(there);
    }

    @Test
    @DisplayName("Cruzar el antimeridiano no rompe la cuenta")
    void crossingTheAntimeridian() {
        assertThat(GymLocation.distanceMeters(0, 179.9995, 0, -179.9995)).isCloseTo(111, within(1.0));
    }
}
