package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.gym.GymFixture;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.service.DayStoryService;
import ar.com.padelnec.service.DayStoryService.DayStory;
import ar.com.padelnec.service.DayStoryService.StoryTime;
import java.math.BigDecimal;
import java.time.Clock;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Lo que va escrito en la historia de turnos libres que el club sube a Instagram. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, TestClockConfig.class})
class DayStoryServiceTest {

    /** Martes 01/09/2026, 13:00 en Necochea: los turnos de la mañana ya empezaron. */
    private static final String NOW = "2026-09-01T16:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired private DayStoryService dayStoryService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court1;
    private Court court2;
    private Customer customer;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();
        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        club = fixture.setGeneralPricePerPerson(club, "5000");
        court1 = fixture.court("Cancha 1", 1);
        court2 = fixture.court("Cancha 2", 2);

        customer = new Customer();
        customer.setFullName("Jugador de prueba");
        customer.setPhoneNumber("+5492262415000");
        customer = customerRepository.saveAndFlush(customer);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Van los horarios con alguna cancha libre, sin los que ya empezaron ni los llenos")
    void onlyFreeTimesFromNowOn() {
        bookAt(court1, LocalTime.of(18, 30));
        bookAt(court2, LocalTime.of(18, 30));
        bookAt(court1, LocalTime.of(20, 0));

        DayStory story = dayStoryService.storyFor(club, TODAY);

        // De 8 a 23 con turnos de 90 minutos; a las 13 ya empezaron los de 8, 9:30, 11 y 12:30.
        assertThat(story.times()).extracting(StoryTime::label)
                .containsExactly("14 hs", "15:30 hs", "17 hs", "20 hs", "21:30 hs");
        assertThat(story.times()).filteredOn(t -> t.start().equals(LocalTime.of(20, 0)))
                .singleElement().extracting(StoryTime::freeCourts).isEqualTo(1);
        assertThat(story.dayLabel()).isEqualTo("Martes 1/9");
        assertThat(story.bookable()).isTrue();
    }

    @Test
    @DisplayName("Un dia fuera de las reservas online sale vacio y avisa por que")
    void dayOutsideBookingWindow() {
        DayStory past = dayStoryService.storyFor(club, TODAY.minusDays(1));
        DayStory tooFar = dayStoryService.storyFor(club, TODAY.plusDays(club.getBookingHorizonDays() + 1));

        assertThat(past.times()).isEmpty();
        assertThat(past.bookable()).isFalse();
        assertThat(tooFar.times()).isEmpty();
        assertThat(tooFar.bookable()).isFalse();
    }

    @Test
    @DisplayName("Los horarios se escriben como en las historias de los clubes")
    void labels() {
        assertThat(DayStoryService.timeLabel(LocalTime.of(17, 0))).isEqualTo("17 hs");
        assertThat(DayStoryService.timeLabel(LocalTime.of(9, 30))).isEqualTo("9:30 hs");
        assertThat(DayStoryService.timeLabel(LocalTime.of(0, 30))).isEqualTo("00:30 hs");
        assertThat(DayStoryService.dayLabel(LocalDate.of(2026, 10, 30))).isEqualTo("Viernes 30/10");
        assertThat(DayStoryService.dayLabel(LocalDate.of(2026, 11, 4))).isEqualTo("Miércoles 4/11");
    }

    private void bookAt(Court court, LocalTime start) {
        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setCustomer(customer);
        booking.setStartTime(TODAY.atTime(start).atZone(ZONE).toInstant());
        booking.setEndTime(TODAY.atTime(start.plusMinutes(90)).atZone(ZONE).toInstant());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalPrice(new BigDecimal("20000"));
        bookingRepository.saveAndFlush(booking);
    }
}
