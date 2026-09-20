package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.TestDatabaseConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Las garantias que el esquema del gimnasio promete, probadas sobre Postgres de
 * verdad: dos cuotas del mismo socio no se pisan, un DNI es unico por club, un
 * socio entra una sola vez por dia y borrar un club se lleva todo lo del gimnasio.
 *
 * <p>Confirma tambien que Hibernate ({@code ddl-auto: validate}) arranca con la
 * constraint de exclusion, que no es una columna y que {@code validate} no mira.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class GymSchemaIntegrityTest {

    private static final LocalDate SEPT_1 = LocalDate.of(2026, 9, 1);

    @Autowired private JdbcTemplate jdbc;

    private UUID clubId;
    private UUID sedeId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM tenant");
        clubId = newClub("los-troncos");
        sedeId = newSede(clubId, "Necochea", "qr-necochea");
        memberId = newMember(clubId, "30111222");
    }

    private UUID newClub(String slug) {
        return jdbc.queryForObject(
                "INSERT INTO tenant (name, slug, whatsapp_number) VALUES (?, ?, ?) RETURNING id",
                UUID.class, "Club " + slug, slug, "+542262400000");
    }

    private UUID newSede(UUID club, String name, String token) {
        return jdbc.queryForObject(
                "INSERT INTO gym_sede (club_id, name, qr_token) VALUES (?, ?, ?) RETURNING id",
                UUID.class, club, name, token);
    }

    private UUID newMember(UUID club, String dni) {
        return jdbc.queryForObject(
                "INSERT INTO gym_member (club_id, dni, full_name, password_hash) VALUES (?, ?, ?, ?) RETURNING id",
                UUID.class, club, dni, "Socio " + dni, "hash");
    }

    private UUID newMembership(UUID member, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                INSERT INTO gym_membership (club_id, member_id, starts_on, ends_on, days_per_week, price,
                                            pay_method, collected_sede_id)
                VALUES (?, ?, ?, ?, 3, 30000, 'CASH', ?) RETURNING id
                """, UUID.class, clubId, member, from, to, sedeId);
    }

    @Test
    @DisplayName("Dos cuotas del mismo socio que se pisan no se pueden guardar")
    void overlappingMembershipsAreRejected() {
        newMembership(memberId, SEPT_1, SEPT_1.plusDays(29));

        assertThatThrownBy(() -> newMembership(memberId, SEPT_1.plusDays(10), SEPT_1.plusDays(40)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una cuota que empieza el dia siguiente al ultimo de la anterior si se puede guardar")
    void backToBackMembershipsAreAccepted() {
        newMembership(memberId, SEPT_1, SEPT_1.plusDays(29));

        // El ultimo dia es inclusivo: si termina el 30/09, la siguiente arranca el 01/10.
        assertThat(newMembership(memberId, SEPT_1.plusDays(30), SEPT_1.plusDays(59))).isNotNull();
    }

    @Test
    @DisplayName("Una cuota anulada ya no bloquea las fechas")
    void voidedMembershipsDoNotBlock() {
        UUID first = newMembership(memberId, SEPT_1, SEPT_1.plusDays(29));
        jdbc.update("UPDATE gym_membership SET voided_at = now() WHERE id = ?", first);

        assertThat(newMembership(memberId, SEPT_1, SEPT_1.plusDays(29))).isNotNull();
    }

    @Test
    @DisplayName("Socios distintos si pueden tener cuotas en las mismas fechas")
    void differentMembersMayOverlap() {
        UUID other = newMember(clubId, "40222333");
        newMembership(memberId, SEPT_1, SEPT_1.plusDays(29));

        assertThat(newMembership(other, SEPT_1, SEPT_1.plusDays(29))).isNotNull();
    }

    @Test
    @DisplayName("El DNI es unico por club: se repite entre clubes, no dentro de uno")
    void dniIsUniquePerClub() {
        UUID otherClub = newClub("otro-club");

        assertThat(newMember(otherClub, "30111222")).isNotNull();
        assertThatThrownBy(() -> newMember(clubId, "30111222"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("El DNI son solo digitos")
    void dniMustBeDigits() {
        assertThatThrownBy(() -> newMember(clubId, "30.111.222"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Los dias por semana van de 1 a 7")
    void daysPerWeekIsBounded() {
        for (int invalid : new int[] {0, 8}) {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO gym_membership (club_id, member_id, starts_on, ends_on, days_per_week, price,
                                                pay_method, collected_sede_id)
                    VALUES (?, ?, ?, ?, ?, 1000, 'CASH', ?)
                    """, clubId, memberId, SEPT_1, SEPT_1.plusDays(29), invalid, sedeId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("Un socio no puede tener dos ingresos el mismo dia")
    void oneCheckInPerMemberPerDay() {
        UUID membership = newMembership(memberId, SEPT_1, SEPT_1.plusDays(29));
        String insert = """
                INSERT INTO gym_checkin (club_id, member_id, membership_id, sede_id, checked_in_at, local_date)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(insert, clubId, memberId, membership, sedeId, java.sql.Timestamp.from(Instant.now()), SEPT_1);

        assertThatThrownBy(() -> jdbc.update(insert, clubId, memberId, membership, sedeId,
                java.sql.Timestamp.from(Instant.now()), SEPT_1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La ubicacion de una sede son las dos coordenadas juntas, o ninguna")
    void aSedeLocationIsBothCoordinatesOrNone() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO gym_sede (club_id, name, qr_token, latitude) VALUES (?, ?, ?, ?)",
                clubId, "Solo latitud", "qr-1", -38.55))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("INSERT INTO gym_sede (club_id, name, qr_token, latitude, longitude) "
                + "VALUES (?, ?, ?, ?, ?)", clubId, "Completa", "qr-2", -38.55, -58.73)).isEqualTo(1);
    }

    @Test
    @DisplayName("Las coordenadas y el radio tienen que ser posibles; el radio de fabrica es de 200 m")
    void coordinatesAndRadiusAreBounded() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO gym_sede (club_id, name, qr_token, latitude, longitude) VALUES (?, ?, ?, ?, ?)",
                clubId, "Latitud imposible", "qr-3", 91.0, 10.0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO gym_sede (club_id, name, qr_token, radius_meters) VALUES (?, ?, ?, ?)",
                clubId, "Radio de 5 m", "qr-4", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT radius_meters FROM gym_sede WHERE id = ?", Integer.class, sedeId))
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Por defecto el club no pide clave: entran solo con el DNI")
    void passwordIsNotRequiredByDefault() {
        jdbc.update("INSERT INTO gym_club_config (club_id) VALUES (?)", clubId);

        assertThat(jdbc.queryForObject("SELECT password_required FROM gym_club_config WHERE club_id = ?",
                Boolean.class, clubId)).isFalse();
    }

    @Test
    @DisplayName("Borrar un club se lleva todo lo del gimnasio, aunque las tablas se referencien entre si")
    void deletingAClubCascadesEverything() {
        UUID membership = newMembership(memberId, SEPT_1, SEPT_1.plusDays(29));
        jdbc.update("INSERT INTO gym_membership_sede (membership_id, sede_id) VALUES (?, ?)", membership, sedeId);
        jdbc.update("""
                INSERT INTO gym_checkin (club_id, member_id, membership_id, sede_id, checked_in_at, local_date)
                VALUES (?, ?, ?, ?, now(), ?)
                """, clubId, memberId, membership, sedeId, SEPT_1);
        jdbc.update("INSERT INTO gym_club_config (club_id) VALUES (?)", clubId);
        jdbc.update("""
                INSERT INTO gym_session (club_id, member_id, token_hash, expires_at)
                VALUES (?, ?, 'hash-de-sesion', now() + interval '1 day')
                """, clubId, memberId);

        jdbc.update("DELETE FROM tenant WHERE id = ?", clubId);

        for (String table : new String[] {"gym_club_config", "gym_sede", "gym_member", "gym_session",
                "gym_membership", "gym_membership_sede", "gym_checkin"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                    .as(table).isZero();
        }
    }
}
