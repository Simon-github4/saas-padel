package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.web.dto.AvailabilityResponse;
import ar.com.padelnec.web.dto.AvailabilityResponse.SlotView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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

/** Verifica que la grilla que ve el jugador refleje la realidad del club. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, AvailabilityServiceTest.FixedClockConfig.class})
class AvailabilityServiceTest {

    /** Martes 01/09/2026, 07:00 hora de Necochea (10:00 UTC): el club acaba de abrir. */
    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        /**
         * Nombre distinto del bean de produccion a proposito: convive con el reloj
         * real y gana por {@code @Primary}, en vez de pisar su definicion.
         */
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at(NOW);
        }
    }

    @Autowired private AvailabilityService availabilityService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BlackoutRepository blackoutRepository;
    @Autowired private Clock clock;

    private Tenant club;
    private Court court1;
    private Court court2;
    private Customer customer;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));

        TenantContext.set(TenantContext.ROOT);
        bookingRepository.deleteAll();
        blackoutRepository.deleteAll();
        pricingRuleRepository.deleteAll();
        customerRepository.deleteAll();
        courtRepository.deleteAll();
        tenantRepository.deleteAll();
        TenantContext.clear();

        club = new Tenant();
        club.setName("Club Necochea");
        club.setSlug("club-necochea");
        club.setWhatsappNumber("+542262400000");
        club.setOpenTime(LocalTime.of(8, 0));
        club.setCloseTime(LocalTime.of(23, 0));
        club.setDefaultSlotDuration(90);
        club = tenantRepository.saveAndFlush(club);

        TenantContext.set(club.getId());

        court1 = saveCourt("Cancha 1", 1);
        court2 = saveCourt("Cancha 2", 2);

        customer = new Customer();
        customer.setFullName("Jugador de prueba");
        customer.setPhoneNumber("+5492262415000");
        customer = customerRepository.saveAndFlush(customer);

        // Tarifa general del martes para todo el horario del club.
        savePricingRule(null, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(23, 59), "20000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Los bloques de 90 minutos se encadenan desde la apertura hasta el cierre")
    void gridIsBuiltFromFixedBlocks() {
        List<SlotView> slots = availabilityService.availabilityFor(club, TODAY).slots();

        // 08:00 a 23:00 son 15 horas: entran 10 turnos de 90 minutos.
        assertThat(slots).hasSize(10);
        assertThat(slots.getFirst().startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(slots.getFirst().endTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(slots.getLast().startTime()).isEqualTo(LocalTime.of(21, 30));
        assertThat(slots.getLast().endTime()).isEqualTo(LocalTime.of(23, 0));
    }

    @Test
    @DisplayName("Los horarios que ya arrancaron desaparecen de la grilla")
    void pastSlotsAreHidden() {
        // Son las 19:00 de Necochea: solo quedan el de 19:30 y el de 21:00.
        ((MutableClock) clock).set(Instant.parse("2026-09-01T22:00:00Z"));

        List<SlotView> slots = availabilityService.availabilityFor(club, TODAY).slots();

        assertThat(slots).hasSize(2);
        assertThat(slots.getFirst().startTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("Un turno confirmado saca esa cancha de ese horario, no de los demas")
    void confirmedBookingRemovesOnlyThatCourt() {
        bookAt(court1, LocalTime.of(18, 30), BookingStatus.CONFIRMED);

        SlotView slot = slotAt(LocalTime.of(18, 30));
        assertThat(slot.available()).hasSize(1);
        assertThat(slot.available().getFirst().courtName()).isEqualTo("Cancha 2");

        assertThat(slotAt(LocalTime.of(17, 0)).available()).hasSize(2);
    }

    @Test
    @DisplayName("Una reserva esperando pago retiene la cancha igual que una confirmada")
    void unpaidHoldsAlsoBlockTheSlot() {
        bookAt(court1, LocalTime.of(18, 30), BookingStatus.DRAFT);
        bookAt(court2, LocalTime.of(18, 30), BookingStatus.AWAITING_CONFIRMATION);

        assertThat(slotAt(LocalTime.of(18, 30)).available()).isEmpty();
    }

    @Test
    @DisplayName("Un turno cancelado devuelve la cancha a la grilla")
    void cancelledBookingsFreeTheSlot() {
        bookAt(court1, LocalTime.of(18, 30), BookingStatus.CANCELLED);

        assertThat(slotAt(LocalTime.of(18, 30)).available()).hasSize(2);
    }

    @Test
    @DisplayName("Un bloqueo del club tapa la franja aunque no haya reservas")
    void blackoutsHideTheSlot() {
        Blackout blackout = new Blackout();
        blackout.setCourt(court1);
        blackout.setStartTime(instantAt(LocalTime.of(18, 0)));
        blackout.setEndTime(instantAt(LocalTime.of(20, 0)));
        blackout.setReason("Mantenimiento de la superficie");
        blackoutRepository.saveAndFlush(blackout);

        assertThat(slotAt(LocalTime.of(18, 30)).available())
                .singleElement()
                .satisfies(c -> assertThat(c.courtName()).isEqualTo("Cancha 2"));
    }

    @Test
    @DisplayName("Un bloqueo sin cancha afecta a todo el club")
    void clubWideBlackoutHidesEveryCourt() {
        Blackout blackout = new Blackout();
        blackout.setStartTime(instantAt(LocalTime.of(8, 0)));
        blackout.setEndTime(instantAt(LocalTime.of(23, 0)));
        blackout.setReason("Torneo");
        blackoutRepository.saveAndFlush(blackout);

        assertThat(availabilityService.availabilityFor(club, TODAY).slots())
                .allSatisfy(slot -> assertThat(slot.available()).isEmpty());
    }

    @Test
    @DisplayName("Un horario sin tarifa configurada no se publica")
    void slotsWithoutPriceAreNotOffered() {
        pricingRuleRepository.deleteAll();
        // Solo la tarde tiene precio: la manana queda sin publicar.
        savePricingRule(null, DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(23, 59), "20000");

        assertThat(slotAt(LocalTime.of(9, 30)).available()).isEmpty();
        assertThat(slotAt(LocalTime.of(15, 30)).available()).hasSize(2);
    }

    @Test
    @DisplayName("La tarifa propia de una cancha le gana a la general del club")
    void courtSpecificPriceWins() {
        savePricingRule(court2, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(23, 0), "26000");

        SlotView slot = slotAt(LocalTime.of(18, 30));
        assertThat(priceOf(slot, "Cancha 1")).isEqualByComparingTo("20000");
        assertThat(priceOf(slot, "Cancha 2")).isEqualByComparingTo("26000");
        assertThat(slot.cheapestPrice()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Entre dos tarifas que aplican, gana la franja mas angosta")
    void narrowestPricingWindowWins() {
        // Tarifa de horario pico, mas especifica que la general de todo el dia.
        savePricingRule(null, DayOfWeek.TUESDAY, LocalTime.of(19, 0), LocalTime.of(22, 0), "30000");

        assertThat(priceOf(slotAt(LocalTime.of(20, 0)), "Cancha 1")).isEqualByComparingTo("30000");
        assertThat(priceOf(slotAt(LocalTime.of(15, 30)), "Cancha 1")).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Un club que cierra de madrugada ofrece turnos pasada la medianoche")
    void clubsClosingAfterMidnightKeepTheirLateSlots() {
        club.setCloseTime(LocalTime.of(1, 0));
        club = tenantRepository.saveAndFlush(club);
        savePricingRule(null, DayOfWeek.WEDNESDAY, LocalTime.of(0, 0), LocalTime.of(2, 0), "18000");

        List<SlotView> slots = availabilityService.availabilityFor(club, TODAY).slots();

        // 08:00 del martes a 01:00 del miercoles son 17 horas: entran 11 turnos de 90
        // minutos y sobran 30 de cola. Con bloques encadenados desde la apertura, la
        // hora de cierre funciona como tope y no como ultimo horario: el turno de las
        // 23:00 termina 00:30 y el club se queda sin vender esa media hora.
        assertThat(slots).hasSize(11);
        assertThat(slots.getLast().startTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(slots.getLast().endTime()).isEqualTo(LocalTime.of(0, 30));
    }

    @Test
    @DisplayName("Mas alla del horizonte de reservas la grilla llega vacia")
    void datesBeyondTheHorizonReturnNothing() {
        LocalDate farAway = TODAY.plusDays(club.getBookingHorizonDays() + 1);

        assertThat(availabilityService.availabilityFor(club, farAway).slots()).isEmpty();
    }

    @Test
    @DisplayName("La grilla incluye los datos del club que la app necesita para decidir el checkout")
    void responseCarriesClubConfiguration() {
        AvailabilityResponse response = availabilityService.availabilityFor(club, TODAY);

        assertThat(response.club().slug()).isEqualTo("club-necochea");
        assertThat(response.club().allowUnpaidBooking()).isTrue();
        assertThat(response.club().acceptsOnlinePayments()).isFalse();
        assertThat(response.slotDurationMinutes()).isEqualTo(90);
        assertThat(response.courts()).hasSize(2);
    }

    // ------------------------------------------------------------ utilidades

    private Court saveCourt(String name, int order) {
        Court court = new Court();
        court.setName(name);
        court.setDisplayOrder(order);
        return courtRepository.saveAndFlush(court);
    }

    private void savePricingRule(Court court, DayOfWeek day, LocalTime from, LocalTime to, String price) {
        PricingRule rule = new PricingRule();
        rule.setCourt(court);
        rule.setDay(day);
        rule.setStartTime(from);
        rule.setEndTime(to);
        rule.setPrice(new BigDecimal(price));
        pricingRuleRepository.saveAndFlush(rule);
    }

    private void bookAt(Court court, LocalTime start, BookingStatus status) {
        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setCustomer(customer);
        booking.setStartTime(instantAt(start));
        booking.setEndTime(instantAt(start.plusMinutes(90)));
        booking.setStatus(status);
        booking.setTotalPrice(new BigDecimal("20000"));
        bookingRepository.saveAndFlush(booking);
    }

    private Instant instantAt(LocalTime time) {
        return TODAY.atTime(time).atZone(ZONE).toInstant();
    }

    private SlotView slotAt(LocalTime start) {
        return availabilityService.availabilityFor(club, TODAY).slots().stream()
                .filter(slot -> slot.startTime().equals(start))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay un turno que arranque a las " + start));
    }

    private BigDecimal priceOf(SlotView slot, String courtName) {
        Optional<BigDecimal> price = slot.available().stream()
                .filter(c -> c.courtName().equals(courtName))
                .map(AvailabilityResponse.CourtAvailability::price)
                .findFirst();
        return price.orElseThrow(() -> new AssertionError(courtName + " no figura disponible"));
    }

    @SuppressWarnings("unused")
    private static Duration minutes(long value) {
        return Duration.ofMinutes(value);
    }
}
