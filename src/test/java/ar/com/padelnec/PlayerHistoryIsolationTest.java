package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.BookingRepository.PlayerBookingHistoryRow;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * El historial del jugador muestra lo suyo y nada mas.
 *
 * <p>Esta clase existe por un agujero concreto: el historial cruzaba clubes
 * emparejando el telefono de la cuenta con el del cliente de cada reserva, y el
 * telefono no lo verifica nadie -- el alta lo acepta tal cual y solo se confirma
 * el email. Alcanzaba con registrarse poniendo el numero de otro para recibir sus
 * turnos en todos los clubes, con el {@code managementToken} de cada uno, que es
 * la credencial con la que se cancela.
 *
 * <p>Lo que se prueba entonces no es "el historial anda": es que poner el telefono
 * de otro en la propia cuenta no alcanza para ver un solo turno ajeno.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PlayerHistoryIsolationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String PHONE = "+5492262415000";

    @Autowired private ClubFixture fixture;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PlayerAccountRepository playerAccountRepository;

    private Tenant club;
    private Court court;
    private LocalDate matchDay;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        matchDay = LocalDate.now(ZONE).plusDays(2);
        fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");
    }

    @Test
    @DisplayName("Quien se registra con el telefono de otro no ve ni un turno ajeno")
    void aStolenPhoneShowsNothing() {
        // El jugador de verdad reserva como invitado, que es el camino normal.
        Booking guestBooking = book(LocalTime.of(18, 30), null);
        assertThat(guestBooking.getPlayerAccountId()).isNull();

        // El atacante se registra con el telefono del otro y confirma SU propio mail.
        PlayerAccount attacker = accountWith("atacante@test.com", PHONE);

        List<PlayerBookingHistoryRow> history =
                bookingRepository.findHistoryByAccount(attacker.getId());

        assertThat(history)
                .as("el telefono no es una identidad: no da acceso a la agenda de nadie")
                .isEmpty();
    }

    @Test
    @DisplayName("El jugador ve los turnos que reservo con su cuenta, con el token para abrirlos")
    void ownBookingsAreListed() {
        PlayerAccount player = accountWith("jugador@test.com", PHONE);
        Booking own = book(LocalTime.of(20, 0), player);

        List<PlayerBookingHistoryRow> history =
                bookingRepository.findHistoryByAccount(player.getId());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getBookingId()).isEqualTo(own.getId());
        assertThat(history.get(0).getClubSlug()).isEqualTo("club-necochea");
        // El token viaja a proposito: /account lo usa para abrir cada turno. Es
        // seguro justamente porque la fila ya es de quien pregunta.
        assertThat(history.get(0).getManagementToken()).isEqualTo(own.getManagementToken());
    }

    @Test
    @DisplayName("Dos cuentas con turnos en el mismo club no se ven entre si")
    void oneAccountNeverSeesAnother() {
        PlayerAccount uno = accountWith("uno@test.com", "+5492262415001");
        PlayerAccount otro = accountWith("otro@test.com", "+5492262415002");
        book(LocalTime.of(18, 30), uno);
        Booking delOtro = book(LocalTime.of(21, 30), otro);

        assertThat(bookingRepository.findHistoryByAccount(otro.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getBookingId()).isEqualTo(delOtro.getId()));
    }

    // ----------------------------------------------------------------- helpers

    private Booking book(LocalTime time, PlayerAccount account) {
        return TenantContext.callAs(club.getId(), () -> bookingService.create(club, new NewBooking(
                court.getId(),
                ZonedDateTime.of(matchDay, time, ZONE).toInstant(),
                "Jugador de prueba",
                // El mismo telefono en todas: es justamente el dato que ya no alcanza.
                PHONE,
                PaymentChoice.PAY_AT_CLUB,
                account != null ? account.getId() : null)));
    }

    private PlayerAccount accountWith(String email, String phone) {
        PlayerAccount account = new PlayerAccount();
        account.setEmail(email);
        account.setPhoneNumber(phone);
        account.setEmailVerified(true);
        account.setDisplayName("Cuenta " + email);
        return playerAccountRepository.saveAndFlush(account);
    }
}
