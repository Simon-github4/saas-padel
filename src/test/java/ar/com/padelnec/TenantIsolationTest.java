package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ningun club puede ver los datos de otro.
 *
 * <p>Es la garantia que sostiene todo el modelo multi-tenant, y hasta esta clase
 * no la probaba nadie: descansaba entera en que {@code @TenantId} estuviera bien
 * puesto en cada entidad y en que nadie escribiera una consulta que lo esquive.
 * El doble booking -- el otro fallo que termina con el producto -- tiene una
 * restriccion en la base y un test de concurrencia; esto tenia cero.
 *
 * <p>Dos consultas nativas esquivan el filtro a proposito y estan documentadas
 * ({@code findClubIdByAnyToken}, {@code findHistoryByAccount}). Este test existe
 * para que la tercera, escrita apurado, no pase inadvertida.
 *
 * <p>Se prueba desde el contexto de un club sobre datos del otro, que es la forma
 * en que esto fallaria de verdad: no con una consulta hecha a mano, sino con las
 * mismas llamadas de siempre corriendo con el club equivocado instalado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class TenantIsolationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired private ClubFixture fixture;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BlackoutRepository blackoutRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;

    private Tenant necochea;
    private Tenant muelle;
    private Court courtDeMuelle;
    private Booking bookingDeMuelle;
    private Customer customerDeMuelle;
    private LocalDate matchDay;

    @BeforeEach
    void setUp() {
        fixture.reset();
        matchDay = LocalDate.now(ZONE).plusDays(2);

        necochea = fixture.club("club-necochea");
        TenantContext.runAs(necochea.getId(), () -> {
            fixture.court("Cancha 1", 1);
            fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");
        });

        muelle = fixture.club("el-muelle");
        TenantContext.runAs(muelle.getId(), () -> {
            courtDeMuelle = fixture.court("Cancha unica", 1);
            fixture.allDayPrice(matchDay.getDayOfWeek(), "31000");
            blackout(courtDeMuelle);
            bookingDeMuelle = bookingService.create(muelle, new NewBooking(
                    courtDeMuelle.getId(),
                    ZonedDateTime.of(matchDay, LocalTime.of(20, 0), ZONE).toInstant(),
                    "Jugador del Muelle",
                    "+5492262415900",
                    PaymentChoice.PAY_AT_CLUB));
            customerDeMuelle = bookingDeMuelle.getCustomer();
        });
    }

    @Test
    @DisplayName("Parado en un club, listar no devuelve una sola fila del otro")
    void listingNeverCrossesClubs() {
        TenantContext.runAs(necochea.getId(), () -> {
            assertThat(bookingRepository.findAll())
                    .as("turnos")
                    .noneMatch(booking -> booking.getId().equals(bookingDeMuelle.getId()));
            assertThat(courtRepository.findAll())
                    .as("canchas")
                    .noneMatch(court -> court.getId().equals(courtDeMuelle.getId()));
            assertThat(customerRepository.findAll())
                    .as("clientes")
                    .noneMatch(customer -> customer.getId().equals(customerDeMuelle.getId()));
            assertThat(blackoutRepository.findAll()).as("bloqueos").isEmpty();
            assertThat(pricingRuleRepository.findAll())
                    .as("tarifas")
                    .allSatisfy(rule -> assertThat(rule.getPrice()).hasToString("20000.00"));
        });
    }

    @Test
    @DisplayName("Con el id exacto de una fila ajena en la mano, tampoco se la puede leer")
    void knowingTheIdIsNotEnough() {
        // El caso peligroso de verdad: el id no es un secreto -- viaja por URLs y
        // por respuestas de la API -- asi que lo unico que separa a un club de los
        // datos del otro es el filtro, no que el identificador sea dificil de saber.
        TenantContext.runAs(necochea.getId(), () -> {
            assertThat(bookingRepository.findById(bookingDeMuelle.getId())).isEmpty();
            assertThat(courtRepository.findById(courtDeMuelle.getId())).isEmpty();
            assertThat(customerRepository.findById(customerDeMuelle.getId())).isEmpty();
        });
    }

    @Test
    @DisplayName("Un club no puede reservar sobre una cancha del otro")
    void bookingOnAnotherClubsCourtFails() {
        TenantContext.runAs(necochea.getId(), () ->
                assertThatThrownBy(() -> bookingService.create(necochea, new NewBooking(
                        courtDeMuelle.getId(),
                        ZonedDateTime.of(matchDay, LocalTime.of(18, 30), ZONE).toInstant(),
                        "Jugador de Necochea",
                        "+5492262415901",
                        PaymentChoice.PAY_AT_CLUB)))
                        .hasMessageContaining("La cancha no existe"));
    }

    @Test
    @DisplayName("Sin club en contexto no se ve nada: el sistema falla cerrado")
    void withoutContextNothingIsVisible() {
        TenantContext.clear();
        assertThat(bookingRepository.findAll()).isEmpty();
        assertThat(courtRepository.findAll()).isEmpty();
        assertThat(customerRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("El turno del otro club sigue existiendo: lo que cambia es quien pregunta")
    void theRowIsThereForItsOwner() {
        // Sin esto, los tests de arriba pasarian igual con una base vacia.
        TenantContext.runAs(muelle.getId(), () -> {
            assertThat(bookingRepository.findById(bookingDeMuelle.getId())).isPresent();
            assertThat(courtRepository.findById(courtDeMuelle.getId())).isPresent();
            assertThat(blackoutRepository.findAll()).hasSize(1);
        });
    }

    private void blackout(Court court) {
        Blackout blackout = new Blackout();
        blackout.setCourt(court);
        blackout.setStartTime(ZonedDateTime.of(matchDay, LocalTime.of(8, 0), ZONE).toInstant());
        blackout.setEndTime(ZonedDateTime.of(matchDay, LocalTime.of(9, 30), ZONE).toInstant());
        blackout.setReason("Mantenimiento");
        blackoutRepository.saveAndFlush(blackout);
    }
}
