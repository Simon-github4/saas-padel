package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.WaitlistEntry;
import ar.com.padelnec.domain.enums.AlertType;
import ar.com.padelnec.repository.OperationalAlertRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import ar.com.padelnec.scheduler.WaitlistRetentionJob;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.WaitlistService;
import ar.com.padelnec.service.WaitlistService.SlotWaitlist;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired private OperationalAlertRepository alertRepository;
    @Autowired private PlatformTransactionManager transactionManager;
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
        assertThat(waitlistEntryRepository.findPending(clock.instant())).isEmpty();
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
        assertThat(waitlistEntryRepository.findPending(clock.instant())).singleElement()
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
        assertThat(waitlistEntryRepository.findPending(clock.instant())).hasSize(1);
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

    // ------------------------------------------------------ panel del club

    @Test
    @DisplayName("Si el jugador cancela por la web un turno con anotados, el panel recibe la alerta")
    void cancellingOnTheWebWithPeopleWaitingRaisesAnAlert() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        Booking taking = fillTheOnlyCourt(startTime);
        waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);
        waitlistService.join(club, startTime, "2262415222", "Otro anotado", player("otro@test.com"));

        bookingService.cancelByManagementToken(taking.getManagementToken());

        assertThat(alertRepository.findPending())
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.getType()).isEqualTo(AlertType.WAITLIST_SLOT_FREED);
                    assertThat(alert.getBooking().getId()).isEqualTo(taking.getId());
                    assertThat(alert.getMessage()).contains("20:00", "Hay 2 anotados");
                });
    }

    @Test
    @DisplayName("Cancelar por la web un turno sin anotados no molesta al panel")
    void cancellingOnTheWebWithNobodyWaitingRaisesNothing() {
        Booking taking = fillTheOnlyCourt(TODAY.atTime(20, 0).atZone(ZONE).toInstant());

        bookingService.cancelByManagementToken(taking.getManagementToken());

        assertThat(alertRepository.findPending()).isEmpty();
    }

    @Test
    @DisplayName("El panel ve los anotados por horario, en orden de llegada y sin los horarios que ya empezaron")
    void upcomingGroupsBySlotInArrivalOrder() {
        Instant early = TODAY.atTime(18, 30).atZone(ZONE).toInstant();
        Instant late = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(early);
        Booking lateBooking = fillTheOnlyCourt(late);
        // El de las 20:00 se anota antes: el orden entre horarios es por hora del turno.
        waitlistService.join(club, late, "2262415111", "Primero en llegar", player);
        waitlistService.join(club, late, "2262415222", "Segundo en llegar", player("otro@test.com"));
        waitlistService.join(club, early, "2262415333", "Anotado temprano", player("tercero@test.com"));
        bookingService.cancelByClub(club, lateBooking.getId(), "Se cayo el grupo");

        List<SlotWaitlist> slots = waitlistService.upcomingBySlot();

        assertThat(slots).extracting(SlotWaitlist::startsAt).containsExactly(early, late);
        assertThat(slots.get(0).courtFree()).isFalse();
        assertThat(slots.get(1).courtFree()).isTrue();
        assertThat(slots.get(1).entries())
                .extracting(entry -> entry.getCustomer().getFullName())
                .containsExactly("Primero en llegar", "Segundo en llegar");

        // A las 19:00 el de las 18:30 ya empezo: no hay a quien avisarle nada.
        ((MutableClock) clock).set(TODAY.atTime(19, 0).atZone(ZONE).toInstant());
        assertThat(waitlistService.upcomingBySlot()).extracting(SlotWaitlist::startsAt).containsExactly(late);
    }

    // ------------------------------------------------------------- limpieza

    @Test
    @DisplayName("Quien reserva el horario que esperaba sale de la lista de espera")
    void bookingTheSlotTakesThePlayerOffTheWaitlist() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        Booking taking = fillTheOnlyCourt(startTime);
        waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);
        bookingService.cancelByClub(club, taking.getId(), "Se cayo el grupo");

        bookingService.create(club, new NewBooking(court.getId(), startTime,
                "Jugador anotado", "2262415111", PaymentChoice.PAY_AT_CLUB));

        assertThat(waitlistService.upcomingBySlot()).isEmpty();
    }

    @Test
    @DisplayName("Si ya le avisaron y no llego a reservar, puede volver a anotarse, al final de la fila")
    void aNotifiedPlayerCanJoinAgainAtTheBackOfTheLine() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(startTime);
        WaitlistEntry first = waitlistService.join(club, startTime, "2262415111", "Primero", player);
        waitlistService.join(club, startTime, "2262415222", "Segundo", player("otro@test.com"));
        first.markNotified(clock.instant());
        waitlistEntryRepository.saveAndFlush(first);

        WaitlistEntry again = waitlistService.join(club, startTime, "2262415111", "Primero", player);

        assertThat(again.isNotified()).isFalse();
        assertThat(again.getId()).isNotEqualTo(first.getId());
        assertThat(waitlistService.upcomingBySlot().getFirst().entries())
                .extracting(entry -> entry.getCustomer().getFullName())
                .containsExactly("Segundo", "Primero");
    }

    @Test
    @DisplayName("El borrado nocturno se lleva las anotaciones de turnos que ya terminaron, no las que vienen")
    void nightlyCleanupDropsOnlyEndedSlots() {
        Instant early = TODAY.atTime(8, 0).atZone(ZONE).toInstant();
        Instant late = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(early);
        fillTheOnlyCourt(late);
        waitlistService.join(club, early, "2262415111", "Temprano", player);
        waitlistService.join(club, late, "2262415222", "Tarde", player("otro@test.com"));

        // El turno de las 08:00 dura 90 minutos: a las 10:00 ya termino.
        ((MutableClock) clock).set(TODAY.atTime(10, 0).atZone(ZONE).toInstant());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                new WaitlistRetentionJob(waitlistEntryRepository, clock).deleteEndedEntries());

        assertThat(waitlistEntryRepository.findAll())
                .extracting(WaitlistEntry::getStartsAt)
                .containsExactly(late);
    }

    @Test
    @DisplayName("El jugador ve sus anotaciones y se puede bajar; con otra cuenta no se borra nada")
    void playersSeeAndLeaveTheirOwnEntries() {
        Instant startTime = TODAY.atTime(20, 0).atZone(ZONE).toInstant();
        fillTheOnlyCourt(startTime);
        WaitlistEntry entry = waitlistService.join(club, startTime, "2262415111", "Jugador anotado", player);
        PlayerAccount stranger = player("extrano@test.com");

        assertThat(waitlistService.forAccount(player.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getEntryId()).isEqualTo(entry.getId());
                    assertThat(row.getClubSlug()).isEqualTo("club-necochea");
                    assertThat(row.getStartsAt()).isEqualTo(startTime);
                });
        assertThat(waitlistService.forAccount(stranger.getId())).isEmpty();

        assertThatThrownBy(() -> waitlistService.leave(stranger.getId(), entry.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(waitlistEntryRepository.findById(entry.getId())).isPresent();

        waitlistService.leave(player.getId(), entry.getId());
        assertThat(waitlistEntryRepository.findById(entry.getId())).isEmpty();
    }

    private Booking fillTheOnlyCourt(Instant startTime) {
        return bookingService.create(club, new NewBooking(court.getId(), startTime,
                "El que llego primero", "2262415000", PaymentChoice.PAY_AT_CLUB));
    }
}
