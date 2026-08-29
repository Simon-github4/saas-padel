package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.AlertType;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.PaymentStatus;
import ar.com.padelnec.payment.MercadoPagoGateway;
import ar.com.padelnec.payment.MercadoPagoGateway.ApprovedPayment;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.OperationalAlertRepository;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Reconciliacion de pagos.
 *
 * <p>El gateway va mockeado: lo que se prueba aca no es el SDK de MercadoPago sino
 * que hacemos con lo que responde, que es donde estan las decisiones que mueven
 * plata.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, PaymentServiceTest.FixedClockConfig.class})
class PaymentServiceTest {

    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String MP_PAYMENT_ID = "112233445";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @MockitoBean private MercadoPagoGateway gateway;

    @Autowired private PaymentService paymentService;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OperationalAlertRepository alertRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = fixture.club("club-necochea");
        club.setMpAccessToken("APP_USR-token-de-prueba");
        club = fixture.save(club);

        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");

        when(gateway.createDepositCheckout(any(), any()))
                .thenReturn(new MercadoPagoGateway.Checkout("pref-1", "https://mp.test/checkout"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Una sena acreditada confirma el turno y descuenta del saldo")
    void approvedDepositConfirmsTheBooking() {
        Booking booking = draftBooking();
        stubPayment(booking, "approved", "10000");

        paymentService.applyWebhook(club, MP_PAYMENT_ID);

        Booking updated = reload(booking);
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(updated.getPaidAmount()).isEqualByComparingTo("10000");
        assertThat(updated.balanceDue()).isEqualByComparingTo("10000");
        assertThat(updated.getDraftExpiresAt()).isNull();
    }

    @Test
    @DisplayName("El mismo webhook repetido no acredita dos veces")
    void duplicateWebhooksAreIdempotent() {
        Booking booking = draftBooking();
        stubPayment(booking, "approved", "10000");

        // MercadoPago reintenta sus notificaciones: sin idempotencia, el saldo del
        // jugador quedaria en cero y el club cobraria de menos en el mostrador.
        paymentService.applyWebhook(club, MP_PAYMENT_ID);
        paymentService.applyWebhook(club, MP_PAYMENT_ID);
        paymentService.applyWebhook(club, MP_PAYMENT_ID);

        assertThat(reload(booking).getPaidAmount()).isEqualByComparingTo("10000");
        assertThat(paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.APPROVED)).hasSize(1);
    }

    @Test
    @DisplayName("Un pago que llega tarde, con la reserva ya liberada, levanta alerta de devolucion")
    void paymentArrivingAfterExpiryRaisesAnOrphanAlert() {
        Booking booking = draftBooking();

        // La carrera real: vencio el DRAFT a los 10 minutos y la cancha se libero,
        // y recien despues MercadoPago avisa que el pago entro.
        Booking expired = reload(booking);
        expired.markCancelled(ar.com.padelnec.domain.enums.CancellationReason.PAYMENT_TIMEOUT,
                clock.instant());
        bookingRepository.saveAndFlush(expired);

        stubPayment(booking, "approved", "10000");
        paymentService.applyWebhook(club, MP_PAYMENT_ID);

        Booking after = reload(booking);
        // El turno no revive: la cancha pudo haberse vendido a otro en el interin.
        assertThat(after.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // Pero la plata entro, y alguien del club tiene que devolverla.
        assertThat(after.getPaidAmount()).isEqualByComparingTo("10000");
        assertThat(alertRepository.findPending())
                .singleElement()
                .satisfies(alert -> assertThat(alert.getType()).isEqualTo(AlertType.ORPHAN_PAYMENT));
    }

    @Test
    @DisplayName("Un pago rechazado deja la reserva en borrador para reintentar")
    void rejectedPaymentKeepsTheDraftAlive() {
        Booking booking = draftBooking();
        stubPayment(booking, "rejected", "10000");

        paymentService.applyWebhook(club, MP_PAYMENT_ID);

        Booking after = reload(booking);
        assertThat(after.getStatus()).isEqualTo(BookingStatus.DRAFT);
        assertThat(after.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(paymentRepository.findByMpPaymentId(MP_PAYMENT_ID))
                .get()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.REJECTED));
    }

    @Test
    @DisplayName("Un pago pendiente todavia no confirma nada")
    void pendingPaymentChangesNothing() {
        Booking booking = draftBooking();
        stubPayment(booking, "in_process", "10000");

        paymentService.applyWebhook(club, MP_PAYMENT_ID);

        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.DRAFT);
        assertThat(paymentRepository.findByMpPaymentId(MP_PAYMENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Un pago sin reserva detras no rompe nada")
    void paymentWithUnknownReferenceIsIgnored() {
        when(gateway.fetchPayment(any(), eq(MP_PAYMENT_ID))).thenReturn(Optional.of(
                new ApprovedPayment(MP_PAYMENT_ID, "approved",
                        "00000000-0000-0000-0000-000000000000", new BigDecimal("10000"))));

        paymentService.applyWebhook(club, MP_PAYMENT_ID);

        assertThat(paymentRepository.findAll()).noneMatch(p -> p.getMpPaymentId() != null);
    }

    @Test
    @DisplayName("Cobrar el saldo en el mostrador deja el turno saldado")
    void cashPaymentSettlesTheBalance() {
        Booking booking = draftBooking();
        stubPayment(booking, "approved", "10000");
        paymentService.applyWebhook(club, MP_PAYMENT_ID);

        paymentService.registerCashPayment(reload(booking), new BigDecimal("10000"), null);

        Booking settled = reload(booking);
        assertThat(settled.balanceDue()).isEqualByComparingTo("0");
        assertThat(settled.isPaidInFull()).isTrue();
        assertThat(paymentService.paymentsOf(booking.getId())).hasSize(2);
    }

    // ------------------------------------------------------------ utilidades

    private Booking draftBooking() {
        return bookingService.create(club, new NewBooking(
                court.getId(),
                TODAY.atTime(18, 30).atZone(ZONE).toInstant(),
                "Simon Diaz", "2262415000", PaymentChoice.DEPOSIT_ONLINE));
    }

    private void stubPayment(Booking booking, String status, String amount) {
        when(gateway.fetchPayment(any(), eq(MP_PAYMENT_ID))).thenReturn(Optional.of(
                new ApprovedPayment(MP_PAYMENT_ID, status,
                        booking.getId().toString(), new BigDecimal(amount))));
    }

    private Booking reload(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }
}
