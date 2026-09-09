package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.RecurringBooking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.RecurringBookingRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.CustomerService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Las horas de reloj de pared se guardan tal cual, sin corrimiento de zona.
 *
 * <p>Los horarios de apertura, las franjas de tarifa y la hora de un turno fijo no
 * son instantes: son "las seis de la tarde" para el club. Si el driver las convierte
 * a UTC, un club de Necochea que abre a las 18:00 queda guardado como si abriera a
 * las 21:00.
 *
 * <p>Se verifica leyendo con SQL crudo a proposito. Comprobarlo guardando y leyendo
 * con JPA no sirve de nada: la conversion de ida se deshace en la vuelta y el error
 * queda invisible aunque en la base este mal.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class WallClockPersistenceTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private RecurringBookingRepository recurringBookingRepository;
    @Autowired private CustomerService customerService;
    @Autowired private ClubFixture fixture;

    private Tenant club;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("El horario de atencion del club se guarda sin correrse")
    void openingHoursAreStoredAsWrittenn() {
        club.setOpenTime(LocalTime.of(8, 0));
        club.setCloseTime(LocalTime.of(23, 30));
        tenantRepository.saveAndFlush(club);

        assertThat(rawTime("SELECT open_time FROM tenant WHERE id = ?", club.getId()))
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(rawTime("SELECT close_time FROM tenant WHERE id = ?", club.getId()))
                .isEqualTo(LocalTime.of(23, 30));
    }

    @Test
    @DisplayName("La franja de tarifa de la tarde se guarda sin correrse")
    void pricingWindowsAreStoredAsWritten() {
        fixture.priceWindow(DayOfWeek.SATURDAY, LocalTime.of(18, 0), LocalTime.of(23, 59), "24000");

        String saturday = "SELECT r.start_time FROM pricing_rule r "
                + "JOIN pricing_rule_day d ON d.pricing_rule_id = r.id WHERE d.day_of_week = 6";
        assertThat(rawTime(saturday, null)).isEqualTo(LocalTime.of(18, 0));
        String saturdayEnd = "SELECT r.end_time FROM pricing_rule r "
                + "JOIN pricing_rule_day d ON d.pricing_rule_id = r.id WHERE d.day_of_week = 6";
        assertThat(rawTime(saturdayEnd, null)).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    @DisplayName("Una franja nocturna no viola el CHECK de que el fin sea posterior al inicio")
    void lateNightWindowsSurviveTheCheckConstraint() {
        // Este era el caso que reventaba: 18:00-23:59 convertido a UTC daba
        // 21:00-02:59, y la base rechazaba la fila por tener el fin antes del inicio.
        fixture.priceWindow(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(23, 59), "24000");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pricing_rule_day WHERE day_of_week = 5", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("La hora del turno fijo se guarda sin correrse")
    void recurringBookingTimesAreStoredAsWritten() {
        RecurringBooking fixed = new RecurringBooking();
        fixed.setCourt(fixture.court("Cancha 1", 1));
        fixed.setCustomer(customerService.findOrCreate("2262415000", "Grupo del martes"));
        fixed.setDay(DayOfWeek.TUESDAY);
        fixed.setStartTime(LocalTime.of(20, 0));
        fixed.setDurationMinutes(90);
        fixed.setValidFrom(java.time.LocalDate.of(2026, 9, 1));
        recurringBookingRepository.saveAndFlush(fixed);

        assertThat(rawTime("SELECT start_time FROM recurring_booking WHERE id = ?", fixed.getId()))
                .isEqualTo(LocalTime.of(20, 0));
    }

    private LocalTime rawTime(String sql, Object id) {
        return id != null
                ? jdbc.queryForObject(sql, LocalTime.class, id)
                : jdbc.queryForObject(sql, LocalTime.class);
    }
}
