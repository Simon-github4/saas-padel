package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.support.PersonNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cuando el nombre de una reserva cuenta como el del jugador dueño del telefono. */
class PersonNamesTest {

    @Test
    @DisplayName("Mayusculas, tildes y espacios de mas no hacen otro nombre")
    void ignoresCaseAccentsAndSpacing() {
        assertThat(PersonNames.samePerson("  JUAN   perez ", "Juan Pérez")).isTrue();
    }

    @Test
    @DisplayName("Solo el nombre, o nombre y apellido, es la misma persona")
    void aShorterVersionOfTheSameNameMatches() {
        assertThat(PersonNames.samePerson("Juan", "Juan Pérez")).isTrue();
        assertThat(PersonNames.samePerson("Juan Pérez", "juan")).isTrue();
    }

    @Test
    @DisplayName("Otro nombre, u otro apellido, es otra persona")
    void differentNamesDoNotMatch() {
        assertThat(PersonNames.samePerson("Pedro", "Juan Pérez")).isFalse();
        assertThat(PersonNames.samePerson("Juan Gómez", "Juan Pérez")).isFalse();
        // El apellido solo no alcanza: tiene que coincidir desde el principio.
        assertThat(PersonNames.samePerson("Pérez", "Juan Pérez")).isFalse();
    }
}
