package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.MutableClock;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymCheckin;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymCheckinService.CheckInResult;
import ar.com.padelnec.gym.service.GymLocation.Point;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.service.ClubUserService;
import ar.com.padelnec.web.BusinessRuleException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
 * Las reglas del ingreso al gimnasio: vigencia, tope semanal de lunes a domingo,
 * un ingreso por dia y sedes permitidas.
 *
 * <p>El reloj es controlable: casi todo esto depende de que dia y a que hora se
 * escanea, y el club vive en Buenos Aires (UTC-3), no en UTC.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class, GymCheckinServiceTest.FixedClockConfig.class})
class GymCheckinServiceTest {

    /** Lunes 14 de septiembre de 2026, 10:00 en Buenos Aires. */
    private static final Instant MONDAY_10AM = Instant.parse("2026-09-14T13:00:00Z");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return MutableClock.at("2026-09-14T13:00:00Z");
        }
    }

    @Autowired private GymCheckinService checkinService;
    @Autowired private GymCheckinRepository checkinRepository;
    @Autowired private GymMemberService memberService;
    @Autowired private ClubUserService clubUserService;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;
    @Autowired private Clock clock;

    private Tenant club;
    private GymSede necochea;
    private GymSede quequen;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        clock().set(MONDAY_10AM);
        clubFixture.reset();
        club = clubFixture.club("los-troncos");
        gym.enable(club);
        necochea = gym.sede(club, "Necochea");
        quequen = gym.sede(club, "Quequén");
        memberId = gym.member(club, "30111222", "Juan Pérez").id();
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    private CheckInResult scan(GymSede sede) {
        return checkinService.checkIn(memberId, sede.getQrToken(), null);
    }

    @Test
    @DisplayName("Con la cuota vigente, escanear el QR registra el ingreso y cuenta el dia de la semana")
    void validMembershipChecksIn() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);

        CheckInResult result = scan(necochea);

        assertThat(result.alreadyRegistered()).isFalse();
        assertThat(result.sedeName()).isEqualTo("Necochea");
        assertThat(result.weekUsed()).isEqualTo(1);
        assertThat(result.weekLimit()).isEqualTo(3);
        assertThat(result.validUntil()).isEqualTo(MONDAY.plusDays(29));
    }

    @Test
    @DisplayName("Escanear dos veces el mismo dia no es un error ni gasta otro dia")
    void scanningTwiceTheSameDayIsIdempotent() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);

        scan(necochea);
        CheckInResult second = scan(necochea);

        assertThat(second.alreadyRegistered()).isTrue();
        assertThat(second.weekUsed()).isEqualTo(1);
        assertThat(checkinRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin ninguna cuota, no entra")
    void noMembershipIsRejected() {
        assertThatThrownBy(() -> scan(necochea))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No tenés una cuota vigente");
    }

    @Test
    @DisplayName("Con un mes impago entra: la deuda de un solo mes es gracia, no bloqueo")
    void oneUnpaidMonthIsStillAllowed() {
        gym.sell(club, memberId, MONDAY.minusDays(40), MONDAY.minusDays(10), 3, necochea);

        CheckInResult result = scan(necochea);

        assertThat(result.alreadyRegistered()).isFalse();
        // El plan (dias por semana) sigue siendo el de la ultima cuota paga.
        assertThat(result.weekLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("Con dos cuotas impagas no entra, y el mensaje dice cuántas adeuda")
    void twoUnpaidMonthsAreBlocked() {
        // Una cuota paga (julio, del 10 al 9) deja agosto y septiembre impagos.
        LocalDate july = LocalDate.of(2026, 7, 10);
        gym.sell(club, memberId, july, july.plusMonths(1).minusDays(1), 3, necochea);

        assertThatThrownBy(() -> scan(necochea))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Adeudás 2 cuotas");
    }

    @Test
    @DisplayName("Con la cuota que empieza mas adelante, todavia no entra")
    void futureMembershipIsNotYetValid() {
        gym.sell(club, memberId, MONDAY.plusDays(3), MONDAY.plusDays(33), 3, necochea);

        assertThatThrownBy(() -> scan(necochea))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tu cuota empieza el 17/09/2026");
    }

    @Test
    @DisplayName("El ultimo dia de la cuota todavia vale")
    void theLastDayOfThePeriodIsStillValid() {
        gym.sell(club, memberId, MONDAY.minusDays(29), MONDAY, 3, necochea);

        assertThat(scan(necochea).alreadyRegistered()).isFalse();
    }

    @Test
    @DisplayName("Al pasarse de los dias de la semana se rechaza, y el lunes se renueva el cupo")
    void weeklyLimitBlocksUntilNextMonday() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 2, necochea);

        scan(necochea);                                    // lunes
        clock().set(MONDAY_10AM.plusSeconds(86_400));
        scan(necochea);                                    // martes
        clock().set(MONDAY_10AM.plusSeconds(2 * 86_400));  // miercoles

        assertThatThrownBy(() -> scan(necochea))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya usaste tus 2 días de esta semana");

        clock().set(MONDAY_10AM.plusSeconds(7 * 86_400));  // lunes siguiente
        CheckInResult nextWeek = scan(necochea);
        assertThat(nextWeek.weekUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("La semana va de lunes a domingo en la hora del club: el domingo a la noche no cuenta para el lunes")
    void weekBoundaryUsesTheClubTimeZone() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 1, necochea);

        // Domingo 20/09 a las 23:00 en Buenos Aires = lunes 21/09 a las 02:00 UTC.
        clock().set(Instant.parse("2026-09-21T02:00:00Z"));
        scan(necochea);

        // Lunes 21/09 a las 10:00 en Buenos Aires: semana nueva, el cupo de 1 dia esta libre.
        // Si el sistema contara en UTC, el domingo a la noche ya seria lunes y esto se rechazaria.
        clock().set(Instant.parse("2026-09-21T13:00:00Z"));
        CheckInResult monday = scan(necochea);

        assertThat(monday.alreadyRegistered()).isFalse();
        assertThat(monday.weekUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Los dias de la semana se suman entre todas las sedes")
    void weeklyLimitCountsAllSedes() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 1, necochea, quequen);

        scan(necochea);
        clock().set(MONDAY_10AM.plusSeconds(86_400));

        assertThatThrownBy(() -> scan(quequen))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya usaste tu día de esta semana");
    }

    @Test
    @DisplayName("Una cuota que no incluye la sede no deja entrar ahi")
    void aSedeOutsideThePlanIsRejected() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);

        assertThatThrownBy(() -> scan(quequen))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tu cuota no incluye Quequén");
    }

    @Test
    @DisplayName("Un QR inventado o de una sede inactiva no sirve")
    void anInvalidQrIsRejected() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);

        assertThatThrownBy(() -> checkinService.checkIn(memberId, "token-inventado", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("código QR no es válido");
    }

    @Test
    @DisplayName("El QR de otro club no sirve aunque el token exista")
    void anotherClubsQrIsRejected() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);
        Tenant other = clubFixture.club("otro-club");
        GymSede foreign = gym.sede(other, "Sede ajena");
        TenantContext.set(club.getId());

        assertThatThrownBy(() -> checkinService.checkIn(memberId, foreign.getQrToken(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("código QR no es válido");
    }

    @Test
    @DisplayName("Un socio deshabilitado no entra")
    void aDisabledMemberIsRejected() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);
        memberService.setEnabled(memberId, false);

        assertThatThrownBy(() -> scan(necochea))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("deshabilitado");
    }

    @Test
    @DisplayName("El mostrador puede dejar pasar por encima del tope semanal, y queda como excepcion")
    void staffCanOverrideTheWeeklyLimit() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 1, necochea);
        scan(necochea);
        LocalDate tuesday = MONDAY.plusDays(1);
        clock().set(MONDAY_10AM.plusSeconds(86_400));
        UUID staff = clubUserService.createStaff(club, "Mostrador", "mostrador@troncos.test", "clave-larga-1").getId();

        assertThatThrownBy(() -> scan(necochea)).isInstanceOf(BusinessRuleException.class);
        CheckInResult forced = checkinService.forceCheckIn(memberId, necochea.getId(), staff);

        assertThat(forced.weekUsed()).isEqualTo(2);
        GymCheckin saved = checkinRepository.findByMemberIdAndLocalDate(memberId, tuesday).orElseThrow();
        assertThat(saved.isOverride()).isTrue();
        assertThat(saved.getRegisteredBy()).isEqualTo(staff);
        // Siempre es el ingreso de HOY: el servicio no admite otra fecha.
        assertThat(saved.getLocalDate()).isEqualTo(tuesday);
    }

    @Test
    @DisplayName("El ingreso manual dentro del cupo no queda marcado como excepcion")
    void manualCheckInWithinTheLimitIsNotAnOverride() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);
        UUID staff = clubUserService.createStaff(club, "Mostrador", "mostrador@troncos.test", "clave-larga-1").getId();

        checkinService.forceCheckIn(memberId, necochea.getId(), staff);

        GymCheckin saved = checkinRepository.findByMemberIdAndLocalDate(memberId, MONDAY).orElseThrow();
        assertThat(saved.isOverride()).isFalse();
        assertThat(saved.getRegisteredBy()).isEqualTo(staff);
    }

    @Test
    @DisplayName("El ingreso manual sin cuota vigente se rechaza: hay que cobrarla primero")
    void manualCheckInStillNeedsAMembership() {
        assertThatThrownBy(() -> checkinService.forceCheckIn(memberId, necochea.getId(), UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No tenés una cuota vigente");
    }

    // ---------------------------------------------------------------- ubicacion

    private static final double LAT = -38.5545;
    private static final double LON = -58.7396;

    /** Una sede con ubicacion (radio de 200 m) donde el socio tiene cuota vigente. */
    private GymSede locatedSede() {
        GymSede located = gym.sedeAt(club, "Gimnasio con GPS", LAT, LON, 200);
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, located);
        return located;
    }

    private CheckInResult scanFrom(GymSede sede, double latitude, double longitude) {
        return checkinService.checkIn(memberId, sede.getQrToken(), new Point(latitude, longitude));
    }

    @Test
    @DisplayName("Una sede con ubicacion no registra sin ella: pide activar el permiso")
    void aLocatedSedeRequiresALocation() {
        GymSede located = locatedSede();

        assertThatThrownBy(() -> scan(located))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Necesitamos tu ubicación")
                .hasMessageContaining("Gimnasio con GPS");
        assertThat(checkinRepository.count()).isZero();
    }

    @Test
    @DisplayName("Estando en la sede registra el ingreso y guarda a cuantos metros estaba")
    void insideTheRadiusChecksIn() {
        GymSede located = locatedSede();

        scanFrom(located, LAT + 0.001, LON);   // unos 111 m

        GymCheckin saved = checkinRepository.findByMemberIdAndLocalDate(memberId, MONDAY).orElseThrow();
        assertThat(saved.getDistanceMeters()).isBetween(105, 118);
    }

    @Test
    @DisplayName("Un GPS que se equivoca por 150 m todavia entra: el margen es de 200")
    void aSloppyGpsFixIsTolerated() {
        GymSede located = locatedSede();

        assertThat(scanFrom(located, LAT + 0.00135, LON).alreadyRegistered()).isFalse();
    }

    @Test
    @DisplayName("Pasado el margen no registra, y el mensaje dice a que distancia esta")
    void outsideTheRadiusIsRejected() {
        GymSede located = locatedSede();

        assertThatThrownBy(() -> scanFrom(located, LAT + 0.0025, LON))   // unos 278 m
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Parece que no estás en Gimnasio con GPS")
                .hasMessageContaining("unos 280 m")
                .hasMessageContaining("menos de 200 m");
        assertThat(checkinRepository.count()).isZero();
    }

    @Test
    @DisplayName("Registrarse desde otra ciudad con la foto del QR no funciona")
    void aPhotoOfTheQrFromAnotherCityFails() {
        GymSede located = locatedSede();

        // Buenos Aires, a unos 500 km.
        assertThatThrownBy(() -> scanFrom(located, -34.6037, -58.3816))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("km");
    }

    @Test
    @DisplayName("El radio es de cada sede: con uno mas chico, la misma distancia ya no alcanza")
    void eachSedeHasItsOwnRadius() {
        GymSede tight = gym.sedeAt(club, "Sede chica", LAT, LON, 50);
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, tight);

        assertThatThrownBy(() -> scanFrom(tight, LAT + 0.001, LON))   // unos 111 m
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("menos de 50 m");
    }

    @Test
    @DisplayName("Escanear de nuevo lo que ya estaba registrado no pide la ubicacion otra vez")
    void anAlreadyRegisteredCheckInDoesNotNeedTheLocation() {
        GymSede located = locatedSede();
        scanFrom(located, LAT, LON);

        CheckInResult again = scan(located);

        assertThat(again.alreadyRegistered()).isTrue();
    }

    @Test
    @DisplayName("El mostrador registra sin ubicacion: esta en la sede y ve al socio")
    void staffDoesNotNeedALocation() {
        GymSede located = locatedSede();
        UUID staff = clubUserService.createStaff(club, "Mostrador", "mostrador@troncos.test", "clave-larga-1").getId();

        checkinService.forceCheckIn(memberId, located.getId(), staff);

        GymCheckin saved = checkinRepository.findByMemberIdAndLocalDate(memberId, MONDAY).orElseThrow();
        assertThat(saved.getDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("Una sede sin ubicacion cargada no la verifica: es opcional")
    void aSedeWithoutLocationIsNotVerified() {
        gym.sell(club, memberId, MONDAY, MONDAY.plusDays(29), 3, necochea);

        scan(necochea);

        assertThat(checkinRepository.findByMemberIdAndLocalDate(memberId, MONDAY).orElseThrow()
                .getDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("Se cumple lo que el test da por hecho: el lunes de referencia es lunes")
    void referenceDayIsAMonday() {
        assertThat(MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }
}
