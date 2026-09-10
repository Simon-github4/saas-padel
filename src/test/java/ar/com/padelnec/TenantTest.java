package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.domain.Tenant;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El link de "Abrir en Google Maps" de la portada: tiene que ganar siempre el
 * mas especifico de los tres datos que lo pueden armar, no el primero que se
 * cargo historicamente.
 */
class TenantTest {

    @Test
    @DisplayName("Con googleMapsUrl cargado, gana por sobre coordenadas y direccion")
    void googleMapsUrlWinsOverEverythingElse() {
        Tenant club = new Tenant();
        club.setGoogleMapsUrl("https://www.google.com/maps/place/Nucleo+p%C3%A1del/@-38.5,-58.7,17z");
        club.setLatitude(new BigDecimal("-38.547337"));
        club.setLongitude(new BigDecimal("-58.763974"));
        club.setAddress("Av. 59 nº 440");
        club.setCity("Necochea");

        assertThat(club.mapsUrl())
                .isEqualTo("https://www.google.com/maps/place/Nucleo+p%C3%A1del/@-38.5,-58.7,17z");
    }

    @Test
    @DisplayName("Sin googleMapsUrl, cae a un link armado con las coordenadas")
    void fallsBackToCoordinatesWithoutAGoogleMapsUrl() {
        Tenant club = new Tenant();
        club.setLatitude(new BigDecimal("-38.547337"));
        club.setLongitude(new BigDecimal("-58.763974"));
        club.setAddress("Av. 59 nº 440");

        // Un pin de coordenadas, no la ficha del lugar: es el fallback de un
        // club que cargo su ubicacion antes de que existiera googleMapsUrl.
        assertThat(club.mapsUrl())
                .isEqualTo("https://www.google.com/maps/search/?api=1&query=-38.547337,-58.763974");
    }

    @Test
    @DisplayName("Sin coordenadas ni googleMapsUrl, cae a una busqueda por direccion y ciudad")
    void fallsBackToAddressWithoutCoordinates() {
        Tenant club = new Tenant();
        club.setAddress("Av. 59 nº 440");
        club.setCity("Necochea");

        assertThat(club.mapsUrl())
                .isEqualTo("https://www.google.com/maps/search/?api=1&query=Av.+59+n%C2%BA+440%2C+Necochea");
    }

    @Test
    @DisplayName("Sin ningun dato de ubicacion, no hay link")
    void nullWithoutAnyLocationData() {
        assertThat(new Tenant().mapsUrl()).isNull();
    }

    @Test
    @DisplayName("El mapa embebido busca el mismo nombre que reusa mapsUrl de una URL de lugar")
    void embedQueryReadsTheNameFromAPlaceUrl() {
        Tenant club = new Tenant();
        club.setGoogleMapsUrl("https://www.google.com/maps/place/Nucleo+p%C3%A1del/@-38.5,-58.7,17z/"
                + "data=!3d-38.5473366!4d-58.7639738");
        club.setLatitude(new BigDecimal("-38.547337"));
        club.setLongitude(new BigDecimal("-58.763974"));

        // Mismo nombre que selecciona mapsUrl(): si difirieran, tocar el mapa
        // embebido llevaria a un lugar distinto del que promete el boton.
        assertThat(club.mapsEmbedQuery()).isEqualTo("Nucleo pádel");
    }

    @Test
    @DisplayName("El mapa embebido busca el mismo nombre que reusa mapsUrl de un link de busqueda")
    void embedQueryReadsTheNameFromASearchUrl() {
        Tenant club = new Tenant();
        club.setGoogleMapsUrl("https://www.google.com/maps/search/?api=1&query=Nucleo+p%C3%A1del");
        club.setLatitude(new BigDecimal("-38.547337"));
        club.setLongitude(new BigDecimal("-58.763974"));

        assertThat(club.mapsEmbedQuery()).isEqualTo("Nucleo pádel");
    }

    @Test
    @DisplayName("Sin nombre identificado, el mapa embebido cae a las coordenadas solas")
    void embedQueryFallsBackToCoordinatesWithoutAName() {
        Tenant club = new Tenant();
        club.setLatitude(new BigDecimal("-38.547337"));
        club.setLongitude(new BigDecimal("-58.763974"));

        assertThat(club.mapsEmbedQuery()).isEqualTo("-38.547337,-58.763974");
    }

    @Test
    @DisplayName("Sin nombre ni coordenadas, el mapa embebido no tiene que buscar")
    void embedQueryNullWithoutAnyLocationData() {
        assertThat(new Tenant().mapsEmbedQuery()).isNull();
    }
}
