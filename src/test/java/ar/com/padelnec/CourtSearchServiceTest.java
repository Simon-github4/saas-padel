package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.service.CourtSearchService;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.dto.CourtSearchResponse;
import ar.com.padelnec.web.dto.CourtSearchResponse.Match;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
 * Verifica la busqueda que cruza clubes.
 *
 * <p>Lo que de verdad esta en juego aca no son los filtros sino el multi-tenant:
 * Hibernate fija el club al abrir la sesion, asi que un recorrido mal armado
 * devolveria los turnos del primer club repetidos, o directamente nada. Por eso el
 * primer test compara los resultados de dos clubes distintos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, CourtSearchServiceTest.FixedClockConfig.class})
class CourtSearchServiceTest {

    /** Martes 01/09/2026, 07:00 hora de Necochea (10:00 UTC): el club acaba de abrir. */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final LocalTime TODO_EL_DIA_DESDE = LocalTime.of(0, 0);
    private static final LocalTime TODO_EL_DIA_HASTA = LocalTime.of(23, 59);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @Autowired private CourtSearchService courtSearchService;
    @Autowired private ClubFixture fixture;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private Clock clock;

    private Tenant necochea;
    private Tenant quequen;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();

        necochea = withClub("club-necochea", club -> {
            fixture.court("Cancha 1", 1);
            fixture.court("Cancha 2", 2);
            fixture.allDayPrice(TODAY.getDayOfWeek(), "20000");
        });

        // Turnos de 60 minutos: los horarios de los dos clubes no caen en la misma
        // grilla, que es lo que pasa entre clubes de verdad.
        quequen = withClub("costa-verde", club -> {
            club.setDefaultSlotDuration(60);
            club.setOpenTime(LocalTime.of(9, 0));
            fixture.save(club);
            fixture.court("Cancha Roja", 1);
            fixture.allDayPrice(TODAY.getDayOfWeek(), "16000");
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("La busqueda devuelve turnos de todos los clubes, no solo del primero")
    void searchesEveryClub() {
        List<Match> matches = searchAllDay().matches();

        assertThat(matches).extracting(Match::clubSlug)
                .contains("club-necochea", "costa-verde");
        // Cada club aporta sus propios horarios: 90 minutos contra 60.
        assertThat(matches).filteredOn(match -> match.clubSlug().equals("club-necochea"))
                .extracting(Match::startTime)
                .contains(LocalTime.of(8, 0), LocalTime.of(9, 30));
        assertThat(matches).filteredOn(match -> match.clubSlug().equals("costa-verde"))
                .extracting(Match::startTime)
                .contains(LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("El rango horario se mide contra la hora de inicio del turno")
    void filtersByStartTime() {
        List<Match> matches = courtSearchService.search(
                TODAY, LocalTime.of(18, 0), LocalTime.of(21, 30), Set.of()).matches();

        assertThat(matches).isNotEmpty();
        assertThat(matches).allSatisfy(match -> assertThat(match.startTime())
                .isBetween(LocalTime.of(18, 0), LocalTime.of(21, 30)));
        // El de las 21:30 entra aunque termine a las 23:00: lo que se pide es empezar
        // a esa hora, no que el turno ya haya terminado.
        assertThat(matches).extracting(Match::startTime).contains(LocalTime.of(21, 30));
    }

    @Test
    @DisplayName("Elegir clubes deja afuera a los demas")
    void filtersByClub() {
        List<Match> matches = courtSearchService.search(
                TODAY, TODO_EL_DIA_DESDE, TODO_EL_DIA_HASTA, Set.of("costa-verde")).matches();

        assertThat(matches).isNotEmpty();
        assertThat(matches).extracting(Match::clubSlug).containsOnly("costa-verde");
    }

    @Test
    @DisplayName("El filtro de clubes ofrece todos los activos, aunque se busque en uno solo")
    void alwaysOffersEveryClubInTheFilter() {
        CourtSearchResponse result = courtSearchService.search(
                TODAY, TODO_EL_DIA_DESDE, TODO_EL_DIA_HASTA, Set.of("costa-verde"));

        assertThat(result.clubs()).extracting(CourtSearchResponse.ClubOption::slug)
                .containsExactlyInAnyOrder("club-necochea", "costa-verde");
    }

    @Test
    @DisplayName("Un horario sin canchas libres no aparece en los resultados")
    void hidesFullyBookedSlots() {
        // Quequen tiene una sola cancha: ocuparla borra ese horario del mapa.
        TenantContext.set(quequen.getId());
        Customer customer = saveCustomer();
        bookAt(onlyCourtOf(), customer, LocalTime.of(20, 0), 60);
        TenantContext.clear();

        List<Match> matches = searchAllDay().matches();

        assertThat(matches)
                .filteredOn(match -> match.clubSlug().equals("costa-verde"))
                .extracting(Match::startTime)
                .doesNotContain(LocalTime.of(20, 0))
                .contains(LocalTime.of(21, 0));
    }

    @Test
    @DisplayName("Un club dado de baja no aparece ni en los resultados ni en el filtro")
    void skipsInactiveClubs() {
        quequen.setActive(false);
        fixture.save(quequen);

        CourtSearchResponse result = searchAllDay();

        assertThat(result.matches()).extracting(Match::clubSlug).doesNotContain("costa-verde");
        assertThat(result.clubs()).extracting(CourtSearchResponse.ClubOption::slug)
                .containsExactly("club-necochea");
    }

    @Test
    @DisplayName("Los resultados vienen ordenados por horario, mezclando los clubes")
    void ordersByStartTime() {
        List<Match> matches = searchAllDay().matches();

        assertThat(matches).isSortedAccordingTo(Comparator.comparing(Match::startsAt));

        // Y de verdad se mezclan: el de las 09:00 de Quequen tiene que caer entre el de
        // las 08:00 y el de las 09:30 de Necochea. Agrupados por club no pasaria.
        List<String> slugs = matches.stream().map(Match::clubSlug).toList();
        assertThat(slugs.getFirst()).isEqualTo("club-necochea");
        assertThat(slugs.indexOf("costa-verde")).isLessThan(slugs.lastIndexOf("club-necochea"));
    }

    @Test
    @DisplayName("Un rango al reves se rechaza como regla de negocio")
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> courtSearchService.search(
                TODAY, LocalTime.of(21, 0), LocalTime.of(18, 0), Set.of()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Cada turno trae el precio mas barato y cuantas canchas quedan")
    void carriesPriceAndFreeCourts() {
        Match slot = searchAllDay().matches().stream()
                .filter(match -> match.clubSlug().equals("club-necochea"))
                .findFirst()
                .orElseThrow();

        assertThat(slot.freeCourts()).isEqualTo(2);
        assertThat(slot.cheapestPrice()).isEqualByComparingTo("20000");
        assertThat(slot.playersPerCourt()).isEqualTo(4);
        assertThat(slot.clubName()).isEqualTo("Club club-necochea");
    }

    // ------------------------------------------------------------- helpers

    private CourtSearchResponse searchAllDay() {
        return courtSearchService.search(TODAY, TODO_EL_DIA_DESDE, TODO_EL_DIA_HASTA, Set.of());
    }

    /** Crea el club y carga sus canchas y tarifas con el tenant puesto. */
    private Tenant withClub(String slug, java.util.function.Consumer<Tenant> contents) {
        Tenant club = fixture.club(slug);
        TenantContext.set(club.getId());
        try {
            contents.accept(club);
        } finally {
            TenantContext.clear();
        }
        return club;
    }

    private Customer saveCustomer() {
        Customer customer = new Customer();
        customer.setFullName("Jugador de prueba");
        customer.setPhoneNumber("+5492262415000");
        return customerRepository.saveAndFlush(customer);
    }

    /** La unica cancha del club. El test depende de que sea unica. */
    private Court onlyCourtOf() {
        List<Court> courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        assertThat(courts).hasSize(1);
        return courts.getFirst();
    }

    private void bookAt(Court court, Customer customer, LocalTime start, int minutes) {
        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setCustomer(customer);
        booking.setStartTime(TODAY.atTime(start).atZone(ZONE).toInstant());
        booking.setEndTime(TODAY.atTime(start.plusMinutes(minutes)).atZone(ZONE).toInstant());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalPrice(new BigDecimal("16000"));
        bookingRepository.saveAndFlush(booking);
    }
}
