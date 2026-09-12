package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.WaitlistService;
import ar.com.padelnec.web.BusinessRuleException;
import java.time.Clock;
import java.time.DayOfWeek;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Anotarse en la lista de espera de un horario lleno.
 *
 * <p>Solo tiene sentido anotarse cuando de verdad no hay cancha: si el servicio
 * dejara anotar a alguien con canchas libres todavia, esa persona nunca recibiria
 * el aviso porque el horario ya esta disponible desde el vamos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, WaitlistServiceTest.FixedClockConfig.class})
class WaitlistServiceTest {

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

    @Autowired private WaitlistService waitlistService;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private BookingService bookingService;
    @Autowired private PlayerAccountRepository playerAccountRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court;
    private PlayerAccount player;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
        player = player("jugador@test.com");
    }

    /** Anotarse exige sesion (ver WaitlistService): la cuenta es global, no del club. */
    private PlayerAccount player(String email) {
        PlayerAccount account = new PlayerAccount();
        account.setEmail(email);
        account.setEmailVerified(true);
        account.setDisplayName("Jugador de prueba");
        return playerAccountRepository.saveAndFlush(account);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("No se puede anotar a un horario que todavia tiene canchas libres")
    void cannotJoinWhenCourtsAreStillFree() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();

        assertThatThrownBy(() -> waitlistService.join(club, startTime, "2262415000", "Jugador", player))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("libres");
        assertThat(waitlistEntryRepository.findPending()).isEmpty();
    }

    @Test
    @DisplayName("Anotarse en un horario lleno queda pendiente de aviso")
    void joiningAFullSlotCreatesAPendingEntry() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(startTime);

        WaitlistEntry entry = waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);

        assertThat(entry.isNotified()).isFalse();
        assertThat(entry.getStartsAt()).isEqualTo(startTime);
        // El mail de la cuenta, no algo que el formulario le pida al jugador:
        // es el respaldo para cuando el WhatsApp del club esta apagado.
        assertThat(entry.getEmail()).isEqualTo("jugador@test.com");
        assertThat(waitlistEntryRepository.findPending()).singleElement()
                .satisfies(pending -> assertThat(pending.getCustomer().getFullName())
                        .isEqualTo("Jugador anotado"));
    }

    @Test
    @DisplayName("El mismo jugador no se puede anotar dos veces al mismo horario")
    void cannotJoinTheSameSlotTwice() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(startTime);
        waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);

        assertThatThrownBy(() -> waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anotado");
        assertThat(waitlistEntryRepository.findPending()).hasSize(1);
    }

    @Test
    @DisplayName("Un horario que ya paso no admite anotarse")
    void cannotJoinAPastSlot() {
        Instant startTime = TODAY.atTime(8, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(startTime);
        ((MutableClock) clock).advance(java.time.Duration.ofHours(1));

        assertThatThrownBy(() -> waitlistService.join(club, startTime, "2262415111", "Jugador", player))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pasó");
    }

    private void fillTheOnlyCourt(Instant startTime) {
        bookingService.create(club, new NewBooking(court.getId(), startTime,
                "El que llego primero", "2262415000", PaymentChoice.PAY_AT_CLUB));
    }
}
