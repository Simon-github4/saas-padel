package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.AlertType;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.repository.OperationalAlertRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import ar.com.padelnec.web.SlotUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

/** Reglas del checkout: los dos caminos de reserva, la confirmacion y la baja. */
@SpringBootTest
@Import({TestDatabaseConfig.class, ClubFixture.class, BookingServiceTest.FixedClockConfig.class})
class BookingServiceTest {

    /** Martes 01/09/2026, 07:00 en Necochea. */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @Autowired private BookingService bookingService;
    @Autowired private CustomerService customerService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private OperationalAlertRepository alertRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court1;
    private Court court2;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court1 = fixture.court("Cancha 1", 1);
        court2 = fixture.court("Cancha 2", 2);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------- reserva sin pago

    @Test
    @DisplayName("Reservar de palabra deja el turno esperando el link de WhatsApp")
    void payAtClubCreatesAwaitingConfirmation() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_CONFIRMATION);
        assertThat(booking.getConfirmationToken()).isNotBlank();
        assertThat(booking.getDepositAmount()).isEqualByComparingTo("0");
        assertThat(booking.getTotalPrice()).isEqualByComparingTo("20000");
        // 15 minutos por defecto para tocar el link.
        assertThat(booking.getConfirmationExpiresAt())
                .isEqualTo(Instant.parse(NOW).plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("El link de gestion se emite desde el vamos, sin depender del WhatsApp")
    void managementTokenExistsFromTheStart() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);

        // Si el mensaje nunca llega, el jugador igual tiene como acceder a su turno.
        assertThat(booking.getManagementToken()).isNotBlank().hasSizeGreaterThan(30);
    }

    @Test
    @DisplayName("Tocar el link confirma el turno y quema el token")
    void confirmingByTokenConfirmsTheBooking() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);

        Booking confirmed = bookingService
                .confirmByToken(booking.getConfirmationToken()).booking();

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getConfirmationToken()).isNull();
        assertThat(confirmed.getConfirmationExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Confirmar despues de los 15 minutos ya no sirve")
    void expiredConfirmationTokenIsRejected() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);
        ((MutableClock) clock).advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> bookingService.confirmByToken(booking.getConfirmationToken()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vencio el plazo");
    }

    @Test
    @DisplayName("Un club que exige sena no acepta reservas de palabra")
    void payAtClubIsRejectedWhenTheClubRequiresDeposit() {
        club.setAllowUnpaidBooking(false);
        club.setMpAccessToken("APP_USR-token-de-prueba");
        club = fixture.save(club);

        assertThatThrownBy(() -> reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sena");
    }

    @Test
    @DisplayName("Un jugador de confianza reserva sin sena aunque el club la exija")
    void trustedCustomersBypassTheDepositRequirement() {
        club.setAllowUnpaidBooking(false);
        club.setMpAccessToken("APP_USR-token-de-prueba");
        club = fixture.save(club);

        Customer regular = customerService.findOrCreate("2262415000", "Grupo del martes");
        customerService.setTrusted(regular, true);

        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_CONFIRMATION);
    }

    // --------------------------------------------------- reserva con sena

    @Test
    @DisplayName("Pagar online deja el turno en borrador con la sena calculada")
    void depositOnlineCreatesDraftWithDeposit() {
        club.setMpAccessToken("APP_USR-token-de-prueba");
        club = fixture.save(club);

        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.DEPOSIT_ONLINE);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DRAFT);
        // 50% de 20000.
        assertThat(booking.getDepositAmount()).isEqualByComparingTo("10000");
        assertThat(booking.getDraftExpiresAt())
                .isEqualTo(Instant.parse(NOW).plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("Sin MercadoPago cargado, el pago online cae al flujo de palabra")
    void depositOnlineFallsBackWhenTheClubHasNoMercadoPago() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.DEPOSIT_ONLINE);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_CONFIRMATION);
    }

    @Test
    @DisplayName("Sin MercadoPago y sin reservas de palabra, no hay forma de reservar online")
    void bookingIsImpossibleWithoutAnyPaymentPath() {
        club.setAllowUnpaidBooking(false);
        club = fixture.save(club);

        assertThatThrownBy(() -> reserve(court1, LocalTime.of(18, 30), PaymentChoice.DEPOSIT_ONLINE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pago online");
    }

    // ------------------------------------------------------ disponibilidad

    @Test
    @DisplayName("Dos jugadores no pueden quedarse con la misma cancha a la misma hora")
    void theSecondBookingOnTheSameSlotIsRejected() {
        reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);

        assertThatThrownBy(() -> reserve(court1, LocalTime.of(18, 30), "2262415111", "Otro jugador"))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    @DisplayName("La misma hora en otra cancha se reserva sin problema")
    void theSameSlotOnAnotherCourtIsFine() {
        reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);
        Booking second = reserve(court2, LocalTime.of(18, 30), "2262415111", "Otro jugador");

        assertThat(second.getStatus()).isEqualTo(BookingStatus.AWAITING_CONFIRMATION);
    }

    @Test
    @DisplayName("Un horario que no cae en la grilla no se puede reservar por API")
    void offGridStartTimesAreRejected() {
        Instant offGrid = TODAY.atTime(18, 7).atZone(ZONE).toInstant();

        assertThatThrownBy(() -> bookingService.create(club, new NewBooking(
                court1.getId(), offGrid, "Jugador", "2262415000", PaymentChoice.PAY_AT_CLUB)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("grilla");
    }

    @Test
    @DisplayName("Un horario que ya arranco no se puede reservar")
    void pastSlotsCannotBeBooked() {
        ((MutableClock) clock).set(TODAY.atTime(19, 0).atZone(ZONE).toInstant());

        assertThatThrownBy(() -> reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya paso");
    }

    @Test
    @DisplayName("Un horario sin tarifa no se puede reservar")
    void slotsWithoutPriceCannotBeBooked() {
        // Miercoles: no se cargo tarifa para ese dia.
        Instant wednesday = TODAY.plusDays(1).atTime(18, 30).atZone(ZONE).toInstant();

        assertThatThrownBy(() -> bookingService.create(club, new NewBooking(
                court1.getId(), wednesday, "Jugador", "2262415000", PaymentChoice.PAY_AT_CLUB)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tarifa");
    }

    // ------------------------------------------------------------- cupo

    @Test
    @DisplayName("Un mismo telefono no puede bloquear la agenda entera")
    void aSinglePhoneCannotHoldTheWholeAgenda() {
        reserve(court1, LocalTime.of(15, 30), PaymentChoice.PAY_AT_CLUB);
        reserve(court1, LocalTime.of(17, 0), PaymentChoice.PAY_AT_CLUB);
        reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);

        assertThatThrownBy(() -> reserve(court1, LocalTime.of(20, 0), PaymentChoice.PAY_AT_CLUB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3 turnos");
    }

    @Test
    @DisplayName("Un jugador bloqueado por el club no reserva online")
    void blockedCustomersCannotBook() {
        Customer troublesome = customerService.findOrCreate("2262415000", "Jugador");
        customerService.setBlocked(troublesome, true);

        assertThatThrownBy(() -> reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Comunicate con el club");
    }

    // -------------------------------------------------------- cancelacion

    @Test
    @DisplayName("Con tiempo de sobra, el jugador cancela solo y libera la cancha")
    void customerCanCancelWellInAdvance() {
        // Turno de las 21:30 con el reloj en las 07:00: 14 horas y media de margen,
        // holgadamente por encima del limite de 12 que fija el club.
        Booking booking = reserve(court1, LocalTime.of(21, 30), PaymentChoice.PAY_AT_CLUB);
        bookingService.confirmByToken(booking.getConfirmationToken());

        var result = bookingService.cancelByManagementToken(booking.getManagementToken());

        assertThat(result.booking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.booking().getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER);
        assertThat(result.refundNeeded()).isFalse();
    }

    @Test
    @DisplayName("Dentro del limite de horas, la cancelacion la tiene que hacer el club")
    void cancellationIsBlockedInsideTheLimit() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);
        bookingService.confirmByToken(booking.getConfirmationToken());

        // Faltan 2 horas para el turno y el limite del club son 12.
        ((MutableClock) clock).set(TODAY.atTime(16, 30).atZone(ZONE).toInstant());

        assertThatThrownBy(() -> bookingService.cancelByManagementToken(booking.getManagementToken()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("+542262400000");
    }

    @Test
    @DisplayName("Cancelar un turno con sena paga levanta la alerta de devolucion")
    void cancellingAPaidBookingRaisesARefundAlert() {
        Booking booking = reserve(court1, LocalTime.of(21, 30), PaymentChoice.PAY_AT_CLUB);
        bookingService.confirmByToken(booking.getConfirmationToken());
        markAsPaid(booking, "10000");

        var result = bookingService.cancelByManagementToken(booking.getManagementToken());

        assertThat(result.refundNeeded()).isTrue();
        assertThat(result.clubWhatsapp()).isEqualTo("+542262400000");
        assertThat(alertRepository.findPending())
                .singleElement()
                .satisfies(alert -> assertThat(alert.getType()).isEqualTo(AlertType.REFUND_REQUIRED));
    }

    @Test
    @DisplayName("La cancha cancelada vuelve a estar disponible para revender")
    void cancelledSlotCanBeSoldAgain() {
        Booking booking = reserve(court1, LocalTime.of(21, 30), PaymentChoice.PAY_AT_CLUB);
        bookingService.cancelByManagementToken(booking.getManagementToken());

        Booking replacement = reserve(court1, LocalTime.of(21, 30), "2262415111", "Otro jugador");

        assertThat(replacement.getStatus()).isEqualTo(BookingStatus.AWAITING_CONFIRMATION);
    }

    @Test
    @DisplayName("Un token que no existe no revela nada")
    void unknownTokensAreRejected() {
        assertThatThrownBy(() -> bookingService.findByManagementToken("token-inventado"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------ panel del club

    @Test
    @DisplayName("El club carga un turno a mano y nace confirmado")
    void manualBookingsAreConfirmedImmediately() {
        Booking booking = bookingService.createManual(club, court1.getId(),
                slotAt(LocalTime.of(20, 0)), "Grupo del martes", "2262415000", null, "Vino por telefono");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getSource().name()).isEqualTo("ADMIN");
        assertThat(booking.getTotalPrice()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Marcar ausente libera la cancha y suma el ausente al historial del jugador")
    void noShowReleasesTheSlotAndCountsAgainstTheCustomer() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);
        bookingService.confirmByToken(booking.getConfirmationToken());

        bookingService.markNoShow(booking.getId());

        assertThat(bookingService.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.NO_SHOW);
        assertThat(customerRepository.findByPhoneNumber("+5492262415000").orElseThrow()
                .getNoShowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("El club da de baja un turno y queda registrado el motivo")
    void clubCancellationRecordsTheReason() {
        Booking booking = reserve(court1, LocalTime.of(18, 30), PaymentChoice.PAY_AT_CLUB);
        bookingService.confirmByToken(booking.getConfirmationToken());

        Booking cancelled = bookingService.cancelByClub(club, booking.getId(), "Se inundo la cancha");

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo(CancellationReason.CLUB);
        assertThat(cancelled.getAdminNotes()).isEqualTo("Se inundo la cancha");
    }

    // ------------------------------------------------------------ telefono

    @Test
    @DisplayName("El mismo telefono escrito distinto es un solo jugador")
    void phoneVariantsResolveToTheSameCustomer() {
        reserve(court1, LocalTime.of(15, 30), "2262415000", "Simon");
        reserve(court2, LocalTime.of(15, 30), "02262 15-415000", "Simon Diaz");

        assertThat(customerRepository.findAll()).hasSize(1);
        assertThat(customerRepository.findAll().getFirst().getPhoneNumber())
                .isEqualTo("+5492262415000");
    }

    // ------------------------------------------------------------ utilidades

    private Booking reserve(Court court, LocalTime time, PaymentChoice choice) {
        return reserve(court, time, "2262415000", "Simon Diaz", choice);
    }

    private Booking reserve(Court court, LocalTime time, String phone, String name) {
        return reserve(court, time, phone, name, PaymentChoice.PAY_AT_CLUB);
    }

    private Booking reserve(Court court, LocalTime time, String phone, String name,
                            PaymentChoice choice) {
        return bookingService.create(club,
                new NewBooking(court.getId(), slotAt(time), name, phone, choice));
    }

    private Instant slotAt(LocalTime time) {
        return TODAY.atTime(time).atZone(ZONE).toInstant();
    }

    /** Simula la acreditacion de una sena sin pasar por MercadoPago. */
    private void markAsPaid(Booking booking, String amount) {
        Booking managed = bookingRepository.findById(booking.getId()).orElseThrow();
        managed.setPaidAmount(new BigDecimal(amount));
        bookingRepository.saveAndFlush(managed);
    }
}
