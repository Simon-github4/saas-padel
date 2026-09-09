package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Payment;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.CashRegisterService;
import ar.com.padelnec.service.CashRegisterService.DayCash;
import ar.com.padelnec.service.CashRegisterService.KioskLine;
import ar.com.padelnec.service.CashRegisterService.MethodTotal;
import ar.com.padelnec.service.CashRegisterService.Movement;
import ar.com.padelnec.service.ClubUserService;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
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

/**
 * La caja del dia.
 *
 * <p>Es el numero contra el que alguien cuenta billetes antes de irse: si suma
 * mal, la diferencia se la come el club o se la come el empleado, y en los dos
 * casos se entera tarde.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, CashRegisterServiceTest.FixedClockConfig.class})
class CashRegisterServiceTest {

    /** Martes 01/09/2026, misma fecha base que el resto de la suite. */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private ProductService productService;
    @Autowired private ClubUserService clubUserService;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbc;

    private Tenant club;
    private Court court;
    private ClubUser mostrador;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
        mostrador = clubUserService.createStaff(club, "Ana Mostrador", "ana@clubnecochea.test", "padel1234");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Un cobro en efectivo entra a la caja con el nombre de quien lo cargo")
    void aCashPaymentShowsUpWithWhoTookIt() {
        Booking booking = confirmed(LocalTime.of(12, 30), "Cliente A");
        payCash(booking, "20000", LocalTime.of(13, 0));

        DayCash caja = cashRegisterService.of(club, TUESDAY);

        assertThat(caja.movements())
                .extracting(Movement::customerName, Movement::method, Movement::amount,
                        Movement::registeredByName)
                .containsExactly(tuple("Cliente A", PaymentMethod.CASH, new BigDecimal("20000.00"),
                        "Ana Mostrador"));
        assertThat(caja.cash()).isEqualByComparingTo("20000");
        assertThat(caja.total()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Los tres metodos salen siempre, y el efectivo separado del resto")
    void everyMethodIsReportedEvenAtZero() {
        Booking booking = confirmed(LocalTime.of(12, 30), "Cliente A");
        payCash(booking, "20000", LocalTime.of(13, 0));

        DayCash caja = cashRegisterService.of(club, TUESDAY);

        // Efectivo primero y siempre los tres: una tarjeta que falta se lee como
        // que el dato no se calculo, y es justo el numero del cierre.
        assertThat(caja.byMethod())
                .extracting(MethodTotal::method, MethodTotal::total)
                .containsExactly(
                        tuple(PaymentMethod.CASH, new BigDecimal("20000.00")),
                        tuple(PaymentMethod.TRANSFER, BigDecimal.ZERO),
                        tuple(PaymentMethod.MERCADOPAGO, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Una devolucion resta del total del dia y se distingue de un cobro")
    void aRefundSubtractsFromTheDay() {
        Booking booking = confirmed(LocalTime.of(12, 30), "Cliente A");
        payCash(booking, "20000", LocalTime.of(13, 0));
        refundCash(booking, "5000", LocalTime.of(13, 30));

        DayCash caja = cashRegisterService.of(club, TUESDAY);

        assertThat(caja.movements()).hasSize(2);
        assertThat(caja.movements().get(1).isRefund()).isTrue();
        assertThat(caja.cash()).isEqualByComparingTo("15000");
        assertThat(caja.total()).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("Un cobro de otro dia no aparece en la caja de hoy")
    void yesterdaysCashStaysInYesterday() {
        Booking booking = confirmed(LocalTime.of(12, 30), "Cliente A");
        Payment payment = paymentService.registerManualPayment(
                booking, new BigDecimal("20000"), PaymentMethod.CASH, mostrador.getId());
        backdate("payment", payment.getId(), TUESDAY.minusDays(1).atTime(21, 0));

        assertThat(cashRegisterService.of(club, TUESDAY).movements()).isEmpty();
        assertThat(cashRegisterService.of(club, TUESDAY.minusDays(1)).cash())
                .isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Lo que falta cobrar de los turnos del dia sale aparte de lo cobrado")
    void pendingIsWhatIsStillOwedForTheDay() {
        Booking booking = confirmed(LocalTime.of(12, 30), "Cliente A");
        payCash(booking, "8000", LocalTime.of(13, 0));
        confirmed(LocalTime.of(20, 0), "Cliente B");

        DayCash caja = cashRegisterService.of(club, TUESDAY);

        // 12000 que le faltan al primero mas los 20000 enteros del segundo.
        assertThat(caja.pending()).isEqualByComparingTo("32000");
        assertThat(caja.pendingBookings()).isEqualTo(2);
        assertThat(caja.total()).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("El kiosco se lista aparte: es venta, no plata cobrada")
    void kioskIsListedApartFromTheCash() {
        Booking booking = confirmed(LocalTime.of(12, 30), "Cliente A");
        Product agua = productService.createProduct("Agua", new BigDecimal("1500"));
        ProductSale sale = productService.registerSale(booking, agua, 2, mostrador.getId());
        backdate("product_sale", sale.getId(), TUESDAY.atTime(13, 0));

        DayCash caja = cashRegisterService.of(club, TUESDAY);

        assertThat(caja.kiosk())
                .extracting(KioskLine::productName, KioskLine::quantity, KioskLine::total)
                .containsExactly(tuple("Agua", 2, new BigDecimal("3000.00")));
        assertThat(caja.kioskTotal()).isEqualByComparingTo("3000");
        // La consumicion todavia no se cobro: sube lo que el turno debe, no la caja.
        assertThat(caja.total()).isEqualByComparingTo("0");
        assertThat(caja.pending()).isEqualByComparingTo("23000");
    }

    // ------------------------------------------------------------ utilidades

    private Booking confirmed(LocalTime time, String name) {
        Booking booking = bookingService.create(club, new NewBooking(court.getId(),
                TUESDAY.atTime(time).atZone(ZONE).toInstant(), name,
                "22624150" + String.format("%02d", time.getHour()), PaymentChoice.PAY_AT_CLUB));
        bookingService.confirmByToken(booking.getConfirmationToken());
        return bookingService.findByIdWithDetails(booking.getId()).orElseThrow();
    }

    private void payCash(Booking booking, String amount, LocalTime at) {
        Payment payment = paymentService.registerManualPayment(
                fresh(booking), new BigDecimal(amount), PaymentMethod.CASH, mostrador.getId());
        backdate("payment", payment.getId(), TUESDAY.atTime(at));
    }

    private void refundCash(Booking booking, String amount, LocalTime at) {
        Payment payment = paymentService.registerRefund(
                fresh(booking), new BigDecimal(amount), PaymentMethod.CASH, mostrador.getId());
        backdate("payment", payment.getId(), TUESDAY.atTime(at));
    }

    /**
     * La reserva releida. Cada cobro le sube {@code paid_amount} y con eso su
     * version: reusar la copia que devolvio el paso anterior choca contra el
     * bloqueo optimista, igual que le pasaria a dos pestañas del panel.
     */
    private Booking fresh(Booking booking) {
        return bookingService.findByIdWithDetails(booking.getId()).orElseThrow();
    }

    /**
     * Fija la hora de un movimiento por SQL.
     *
     * <p>{@code created_at} lo escribe BaseEntity con {@code Instant.now()}, el
     * reloj de verdad y no el Clock que estos tests congelan. En produccion es lo
     * correcto -la plata se movio cuando se escribio la fila- pero deja al test
     * sin otra forma de decir en que dia y a que hora cayo cada movimiento.
     */
    private void backdate(String table, UUID id, java.time.LocalDateTime at) {
        jdbc.update("UPDATE " + table + " SET created_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(at.atZone(ZONE).toInstant(), ZoneOffset.UTC), id);
    }
}
