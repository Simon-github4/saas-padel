package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.service.BookingClaimService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guardar en una cuenta los turnos reservados sin ella.
 *
 * <p>Lo que autoriza el reclamo es tener el token de gestion, que ya alcanza para
 * ver y cancelar el turno: atarlo a una cuenta no le suma poder a quien lo tiene.
 * Lo que hay que probar, entonces, no es que funcione -- es donde se planta: un
 * token inventado no reclama nada, y un turno que ya es de otra cuenta no se muda
 * de historial.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class BookingClaimServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired private ClubFixture fixture;
    @Autowired private BookingService bookingService;
    @Autowired private BookingClaimService bookingClaimService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PlayerAccountRepository playerAccountRepository;

    private Tenant necochea;
    private Tenant muelle;
    private Court courtNecochea;
    private Court courtMuelle;
    private LocalDate matchDay;

    @BeforeEach
    void setUp() {
        fixture.reset();
        matchDay = LocalDate.now(ZONE).plusDays(2);

        necochea = fixture.club("club-necochea");
        TenantContext.runAs(necochea.getId(), () -> {
            courtNecochea = fixture.court("Cancha 1", 1);
            fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");
        });

        muelle = fixture.club("el-muelle");
        TenantContext.runAs(muelle.getId(), () -> {
            courtMuelle = fixture.court("Cancha unica", 1);
            fixture.allDayPrice(matchDay.getDayOfWeek(), "31000");
        });
    }

    @Test
    @DisplayName("El turno reservado como invitado queda en la cuenta recien creada")
    void aGuestBookingLandsInTheNewAccount() {
        Booking invitado = book(necochea, courtNecochea, LocalTime.of(20, 0), "+5492262415000");
        assertThat(invitado.getPlayerAccountId()).isNull();
        PlayerAccount cuenta = account("recien@test.com");

        int claimed = bookingClaimService.claim(
                cuenta.getId(), List.of(invitado.getManagementToken()));

        assertThat(claimed).isEqualTo(1);
        assertThat(bookingRepository.findHistoryByAccount(cuenta.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getBookingId()).isEqualTo(invitado.getId()));
    }

    @Test
    @DisplayName("Reclama turnos de clubes distintos en una sola vuelta")
    void claimsAcrossClubs() {
        // Cada turno puede ser de otro club, y el filtro por club se fija al abrir
        // la sesion de persistencia: si el reclamo no entrara a cada uno por
        // separado, el segundo turno no aparecería.
        Booking enNecochea = book(necochea, courtNecochea, LocalTime.of(20, 0), "+5492262415000");
        Booking enMuelle = book(muelle, courtMuelle, LocalTime.of(18, 30), "+5492262415001");
        PlayerAccount cuenta = account("dos-clubes@test.com");

        int claimed = bookingClaimService.claim(
                cuenta.getId(),
                List.of(enNecochea.getManagementToken(), enMuelle.getManagementToken()));

        assertThat(claimed).isEqualTo(2);
        assertThat(bookingRepository.findHistoryByAccount(cuenta.getId()))
                .extracting(row -> row.getClubSlug())
                .containsExactlyInAnyOrder("club-necochea", "el-muelle");
    }

    @Test
    @DisplayName("Un token inventado no reclama nada, y no rompe el resto del lote")
    void anInventedTokenClaimsNothing() {
        Booking real = book(necochea, courtNecochea, LocalTime.of(20, 0), "+5492262415000");
        PlayerAccount cuenta = account("con-basura@test.com");

        int claimed = bookingClaimService.claim(
                cuenta.getId(),
                List.of("token-que-no-existe", real.getManagementToken(), "   "));

        assertThat(claimed).isEqualTo(1);
    }

    @Test
    @DisplayName("Un turno que ya es de otra cuenta no cambia de dueño")
    void anAlreadyOwnedBookingIsNotStolen() {
        // Tener el token alcanza para cancelar el turno, pero no para sacarselo
        // del historial a quien ya lo tiene guardado.
        PlayerAccount duena = account("duena@test.com");
        Booking suyo = bookWithAccount(necochea, courtNecochea, LocalTime.of(20, 0), duena);
        PlayerAccount otra = account("otra@test.com");

        int claimed = bookingClaimService.claim(otra.getId(), List.of(suyo.getManagementToken()));

        assertThat(claimed).isZero();
        assertThat(bookingRepository.findHistoryByAccount(otra.getId())).isEmpty();
        assertThat(bookingRepository.findHistoryByAccount(duena.getId())).hasSize(1);
    }

    @Test
    @DisplayName("El mismo token repetido en el lote se reclama una sola vez")
    void aRepeatedTokenCountsOnce() {
        Booking invitado = book(necochea, courtNecochea, LocalTime.of(20, 0), "+5492262415000");
        PlayerAccount cuenta = account("repetido@test.com");
        String token = invitado.getManagementToken();

        assertThat(bookingClaimService.claim(cuenta.getId(), List.of(token, token))).isEqualTo(1);
        // Y reclamarlo de nuevo mas tarde tampoco lo cuenta dos veces.
        assertThat(bookingClaimService.claim(cuenta.getId(), List.of(token))).isZero();
    }

    // ----------------------------------------------------------------- helpers

    private Booking book(Tenant club, Court court, LocalTime time, String phone) {
        return TenantContext.callAs(club.getId(), () -> bookingService.create(club, new NewBooking(
                court.getId(),
                ZonedDateTime.of(matchDay, time, ZONE).toInstant(),
                "Jugador invitado",
                phone,
                PaymentChoice.PAY_AT_CLUB)));
    }

    private Booking bookWithAccount(Tenant club, Court court, LocalTime time, PlayerAccount owner) {
        return TenantContext.callAs(club.getId(), () -> bookingService.create(club, new NewBooking(
                court.getId(),
                ZonedDateTime.of(matchDay, time, ZONE).toInstant(),
                "Jugadora con cuenta",
                "+5492262415099",
                PaymentChoice.PAY_AT_CLUB,
                owner.getId())));
    }

    private PlayerAccount account(String email) {
        PlayerAccount account = new PlayerAccount();
        account.setEmail(email);
        account.setEmailVerified(true);
        account.setDisplayName("Cuenta " + email);
        account.setPhoneNumber("+54926" + UUID.randomUUID().toString().replaceAll("\\D", "")
                .substring(0, 7));
        return playerAccountRepository.saveAndFlush(account);
    }
}
