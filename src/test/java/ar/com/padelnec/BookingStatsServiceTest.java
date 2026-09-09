package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Payment;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.BookingStatsService;
import ar.com.padelnec.service.BookingStatsService.CancellationStat;
import ar.com.padelnec.service.BookingStatsService.CustomerStat;
import ar.com.padelnec.service.BookingStatsService.HourlyStat;
import ar.com.padelnec.service.BookingStatsService.PaymentMethodStat;
import ar.com.padelnec.service.BookingStatsService.PeriodStats;
import ar.com.padelnec.service.BookingStatsService.Periodo;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.SlotGenerator;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Estadisticas del club: si esto agrega mal, el dueño toma decisiones de plata con
 * un numero que no es el real.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({TestDatabaseConfig.class, ClubFixture.class, BookingStatsServiceTest.FixedClockConfig.class})
class BookingStatsServiceTest {

    /** Martes 01/09/2026, misma fecha base que el resto de la suite. */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 1);
    private static final LocalDate MONDAY = TUESDAY.minusDays(1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @Autowired private BookingStatsService statsService;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BlackoutRepository blackoutRepository;
    @Autowired private SlotGenerator slotGenerator;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Tenant club;
    private Court court;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
        // Para el turno del miercoles del test de acotamiento de buckets.
        fixture.allDayPrice(DayOfWeek.WEDNESDAY, "20000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Un turno jugado, uno reservado, uno cancelado y un ausente se cuentan aparte")
    void aggregatesOneWeekCorrectly() {
        markCompleted(reserve(LocalTime.of(12, 30), "2262415000", "Cliente A"));
        // El turno cancelado del mismo cliente no debe sumar a su facturado.
        bookingService.cancelByClub(club, reserve(LocalTime.of(20, 0), "2262415000", "Cliente A")
                .getId(), "Se cayo");
        confirm(reserve(LocalTime.of(17, 0), "2262415111", "Cliente B"));
        markNoShow(reserve(LocalTime.of(8, 0), "2262415111", "Cliente B"));

        List<PeriodStats> stats = statsService.statsFor(club, Periodo.SEMANA, TUESDAY, TUESDAY);

        assertThat(stats).hasSize(1);
        PeriodStats week = stats.get(0);
        assertThat(week.jugados()).isEqualTo(1);
        assertThat(week.reservados()).isEqualTo(1);
        assertThat(week.cancelados()).isEqualTo(1);
        assertThat(week.noShows()).isEqualTo(1);
        assertThat(week.facturado()).isEqualByComparingTo("40000");
        assertThat(week.cobrado()).isEqualByComparingTo("0");
        assertThat(week.horasJugadas()).isEqualTo(Duration.ofMinutes(180));
        // El bucket de la semana se acota a "desde"/"hasta" (los dos son TUESDAY):
        // un solo dia, 2 de 10 slots posibles.
        assertThat(week.slotsOcupados()).isEqualTo(2);
        assertThat(week.slotsPosibles()).isEqualTo(10);
        assertThat(week.ocupacionPromedio()).isEqualByComparingTo("20.0");

        // total_price sale de la base con escala 2 (precision=12, scale=2): comparar
        // BigDecimal por equals() en la tupla exige repetir esa escala, no alcanza con
        // el valor numerico.
        List<HourlyStat> hours = statsService.topHours(club, TUESDAY, TUESDAY, 10);
        assertThat(hours)
                .extracting(HourlyStat::hour, HourlyStat::turnos, HourlyStat::facturado)
                .containsExactlyInAnyOrder(
                        tuple(LocalTime.of(12, 30), 1, new BigDecimal("20000.00")),
                        tuple(LocalTime.of(17, 0), 1, new BigDecimal("20000.00")));

        List<CustomerStat> customers = statsService.topCustomers(club, TUESDAY, TUESDAY, 10);
        assertThat(customers)
                .extracting(CustomerStat::name, CustomerStat::turnos, CustomerStat::facturado)
                .containsExactlyInAnyOrder(
                        tuple("Cliente A", 1, new BigDecimal("20000.00")),
                        tuple("Cliente B", 1, new BigDecimal("20000.00")));
    }

    @Test
    @DisplayName("Un turno de la semana siguiente no se mezcla con la anterior")
    void weekBucketsDoNotBleedIntoEachOther() {
        markCompleted(reserve(LocalTime.of(12, 30), "2262415000", "Cliente A"));
        markCompleted(reserve(TUESDAY.plusWeeks(1), LocalTime.of(12, 30), "2262415111", "Cliente B"));

        List<PeriodStats> stats = statsService.statsFor(club, Periodo.SEMANA, TUESDAY, TUESDAY.plusWeeks(1));

        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).jugados()).isEqualTo(1);
        assertThat(stats.get(1).jugados()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un bucket parcial no cuenta dias posteriores a \"hasta\", y coincide con el resumen")
    void statsForClampsPartialBucketsToTheChosenRange() {
        // Miercoles 2/9, dentro de la misma semana ISO que TUESDAY (que arranca el
        // lunes 31/8), pero un dia despues de "hasta".
        LocalDate wednesday = TUESDAY.plusDays(1);
        markCompleted(reserve(LocalTime.of(12, 30), "2262415000", "Cliente A"));
        markCompleted(reserve(wednesday, LocalTime.of(12, 30), "2262415111", "Cliente B"));

        List<PeriodStats> stats = statsService.statsFor(club, Periodo.SEMANA, TUESDAY, TUESDAY);
        PeriodStats summary = statsService.summary(club, TUESDAY, TUESDAY);

        assertThat(stats).hasSize(1);
        // El turno del miercoles queda afuera: "hasta" es TUESDAY.
        assertThat(stats.get(0).jugados()).isEqualTo(1);
        assertThat(stats.get(0).facturado()).isEqualByComparingTo(summary.facturado());
        assertThat(stats.get(0).jugados()).isEqualTo(summary.jugados());
    }

    @Test
    @DisplayName("Una reserva cancelada sin motivo no rompe las estadisticas")
    void cancelledBookingWithoutReasonDoesNotBreakStats() {
        // El flujo normal siempre cancela via Booking.markCancelled, que carga un
        // motivo. Esto simula una fila que entro por otro lado (una migracion, una
        // correccion manual en la base): la columna es nullable, asi que la base lo
        // permite aunque la aplicacion nunca lo produzca.
        Booking booking = reserve(LocalTime.of(12, 30), "2262415000", "Cliente A");
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(null);
        bookingRepository.saveAndFlush(booking);

        List<CancellationStat> byReason = statsService.cancellationsByReason(club, TUESDAY, TUESDAY);
        assertThat(byReason).hasSize(1);
        assertThat(byReason.get(0).reason()).isNull();
        assertThat(byReason.get(0).count()).isEqualTo(1);

        // El resto del dashboard tampoco deberia romperse: statsFor y summary
        // agregan el mismo turno cancelado sin pasar por cancellationsByReason.
        PeriodStats summary = statsService.summary(club, TUESDAY, TUESDAY);
        assertThat(summary.cancelados()).isEqualTo(1);
        List<PeriodStats> stats = statsService.statsFor(club, Periodo.SEMANA, TUESDAY, TUESDAY);
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).cancelados()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un dia con blackout de club entero no suma capacidad a la ocupacion")
    void fullDayBlackoutExcludesCapacityFromOccupancy() {
        Blackout blackout = new Blackout();
        blackout.setStartTime(slotGenerator.dayStart(club, TUESDAY));
        blackout.setEndTime(slotGenerator.dayEnd(club, TUESDAY));
        blackout.setReason("Cierre por refaccion");
        blackoutRepository.save(blackout);

        PeriodStats summary = statsService.summary(club, TUESDAY, TUESDAY);

        assertThat(summary.slotsPosibles()).isZero();
        assertThat(summary.slotsOcupados()).isZero();
        assertThat(summary.ocupacionPromedio()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("El numero de consultas de statsFor no crece con el largo del rango")
    void queryCountDoesNotScaleWithRangeLength() {
        // Antes del fix, cada bucket de DIA disparaba su propio findBetween() y su
        // propio findOverlapping() de blackouts dia por dia: un rango de 90 dias
        // costaba muchas mas consultas que uno de 7. Ahora las dos se traen una
        // sola vez por llamada, sin importar cuantos buckets salgan del loop.
        Statistics stats = statistics();

        stats.clear();
        statsService.statsFor(club, Periodo.DIA, TUESDAY, TUESDAY.plusDays(7));
        long queriesForAWeek = stats.getPrepareStatementCount();

        stats.clear();
        statsService.statsFor(club, Periodo.DIA, TUESDAY, TUESDAY.plusDays(90));
        long queriesForNinetyDays = stats.getPrepareStatementCount();

        assertThat(queriesForNinetyDays).isEqualTo(queriesForAWeek);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("Un cobro cuenta en el dia que entro, no en el dia del turno")
    void cashIsCountedOnTheDayItCameIn() {
        Booking booking = reserve(LocalTime.of(12, 30), "2262415000", "Cliente A");
        confirm(booking);
        // El turno es del martes y vale 20000; la sena se cobro el lunes.
        payCash(booking, "10000", MONDAY);

        PeriodStats martes = statsService.summary(club, TUESDAY, TUESDAY);
        assertThat(martes.facturado()).isEqualByComparingTo("20000");
        assertThat(martes.cobrado()).isEqualByComparingTo("0");

        PeriodStats lunes = statsService.summary(club, MONDAY, MONDAY);
        assertThat(lunes.facturado()).isEqualByComparingTo("0");
        assertThat(lunes.cobrado()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Lo cobrado sobre un turno que despues se cancela sigue contando como cobrado")
    void cashOutlivesTheCancellationOfItsBooking() {
        Booking booking = reserve(LocalTime.of(12, 30), "2262415000", "Cliente A");
        confirm(booking);
        payCash(booking, "10000", TUESDAY);
        bookingService.cancelByClub(club, booking.getId(), "Se lesiono");

        PeriodStats summary = statsService.summary(club, TUESDAY, TUESDAY);
        // El turno ya no ocupa la cancha, asi que no factura...
        assertThat(summary.facturado()).isEqualByComparingTo("0");
        // ...pero la plata entro igual, y hasta que el club la devuelva la tiene el
        // club. Antes esto desaparecia de todos los totales.
        assertThat(summary.cobrado()).isEqualByComparingTo("10000");
        assertThat(statsService.paymentsByMethod(club, TUESDAY, TUESDAY))
                .extracting(PaymentMethodStat::method, PaymentMethodStat::total)
                .containsExactly(tuple(PaymentMethod.CASH, new BigDecimal("10000.00")));
    }

    // ------------------------------------------------------------ utilidades

    private Booking reserve(LocalTime time, String phone, String name) {
        return reserve(TUESDAY, time, phone, name);
    }

    private Booking reserve(LocalDate date, LocalTime time, String phone, String name) {
        return bookingService.create(club, new NewBooking(court.getId(),
                date.atTime(time).atZone(ZONE).toInstant(), name, phone, PaymentChoice.PAY_AT_CLUB));
    }

    private void confirm(Booking booking) {
        bookingService.confirmByToken(booking.getConfirmationToken());
    }

    private void markCompleted(Booking booking) {
        confirm(booking);
        bookingService.markCompleted(booking.getId());
    }

    private void markNoShow(Booking booking) {
        confirm(booking);
        bookingService.markNoShow(booking.getId());
    }

    /**
     * Un cobro en efectivo fechado a mano.
     *
     * <p>{@code created_at} lo escribe BaseEntity con {@code Instant.now()}, el
     * reloj de verdad, no el Clock que estos tests congelan: es correcto en
     * produccion -la plata se movio cuando se escribio la fila- pero deja al test
     * sin forma de decir en que dia cayo un cobro, salvo por SQL.
     */
    private void payCash(Booking booking, String amount, LocalDate on) {
        // Releida: confirmar el turno ya le subio la version, y la copia que
        // devolvio create() choca contra el bloqueo optimista.
        Booking fresh = bookingService.findByIdWithDetails(booking.getId()).orElseThrow();
        Payment payment = paymentService.registerManualPayment(
                fresh, new BigDecimal(amount), PaymentMethod.CASH, null);
        jdbc.update("UPDATE payment SET created_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(on.atTime(20, 0).atZone(ZONE).toInstant(), ZoneOffset.UTC),
                payment.getId());
    }
}
