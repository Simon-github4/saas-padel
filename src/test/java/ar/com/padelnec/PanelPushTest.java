package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PushSubscription;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.notification.push.PanelPushService;
import ar.com.padelnec.notification.push.TestBrowser;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.PushSubscriptionRepository;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.ClubUserService;
import ar.com.padelnec.web.BusinessRuleException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/**
 * Avisos al celular del club (Web Push): una reserva web le llega a los
 * dispositivos de ese club y de ningun otro, cifrada para cada navegador.
 *
 * <p>Los servicios de push (FCM, Apple...) los hace un servidor HTTP local que
 * guarda lo que recibe; los navegadores, {@link TestBrowser}, que lo descifra.
 * Mismo contexto que {@link BookingServiceTest}: martes 01/09/2026 a las 7.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, BookingServiceTest.FixedClockConfig.class})
class PanelPushTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String PASSWORD = "unaClaveLarga123";

    @Autowired private ClubFixture fixture;
    @Autowired private BookingService bookingService;
    @Autowired private ClubUserService clubUserService;
    @Autowired private ClubUserRepository clubUserRepository;
    @Autowired private PanelPushService panelPushService;
    @Autowired private PushSubscriptionRepository subscriptionRepository;
    @Autowired private ObjectMapper objectMapper;

    private record Received(String path, Map<String, String> headers, byte[] body) {
    }

    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private HttpServer pushService;

    private Tenant clubA;
    private Tenant clubB;
    private Court courtA;
    private Court courtB;
    private ClubUser staffA;
    private ClubUser staffB;

    @BeforeEach
    void setUp() throws IOException {
        fixture.reset();
        pushService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pushService.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.getFirst()));
            received.add(new Received(path, headers, exchange.getRequestBody().readAllBytes()));
            // Como FCM: 201 si lo tomo, 410 si el navegador ya dio de baja la suscripcion.
            exchange.sendResponseHeaders(path.startsWith("/gone/") ? 410 : 201, -1);
            exchange.close();
        });
        pushService.start();

        clubA = fixture.club("club-a");
        clubB = fixture.club("club-b");
        courtA = TenantContext.callAs(clubA.getId(), () -> {
            fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
            return fixture.court("Cancha 1", 1);
        });
        courtB = TenantContext.callAs(clubB.getId(), () -> {
            fixture.allDayPrice(DayOfWeek.TUESDAY, "20000");
            return fixture.court("Cancha 1", 1);
        });
        staffA = staff(clubA);
        staffB = staff(clubB);
    }

    @AfterEach
    void tearDown() {
        pushService.stop(0);
        TenantContext.clear();
    }

    @Test
    @DisplayName("Una reserva web le llega cifrada a los celulares de ese club, y no a los de otro")
    void webBookingReachesOnlyThatClub() throws Exception {
        TestBrowser browserA = TestBrowser.create();
        TestBrowser browserB = TestBrowser.create();
        subscribe(clubA, staffA, "/push/a", browserA);
        subscribe(clubB, staffB, "/push/b", browserB);

        Booking bookingA = webBooking(clubA, courtA, LocalTime.of(18, 30));
        Booking bookingB = webBooking(clubB, courtB, LocalTime.of(20, 0));

        // Un solo hilo de envio: lo de cada reserva sale en orden. Si el aviso del
        // club A hubiera ido tambien al celular de B, llegaria antes que este.
        Received first = next();
        assertThat(first.path()).isEqualTo("/push/a");
        assertThat(first.headers())
                .containsEntry("content-encoding", "aes128gcm")
                .containsEntry("ttl", "43200")
                .containsEntry("urgency", "high");
        assertThat(first.headers().get("authorization"))
                .startsWith("vapid t=")
                .endsWith(", k=" + panelPushService.applicationServerKey().orElseThrow());

        Map<?, ?> payload = objectMapper.readValue(browserA.decrypt(first.body()), Map.class);
        assertThat(payload.get("title")).isEqualTo("Nueva reserva");
        assertThat(payload.get("tag")).isEqualTo(bookingA.getId().toString());
        assertThat(payload.get("url")).isEqualTo("/admin/");
        assertThat((String) payload.get("body"))
                .contains("Cancha 1").contains("18:30").contains("Simon Diaz").endsWith("Paga en el club");

        Received second = next();
        assertThat(second.path()).isEqualTo("/push/b");
        assertThat(objectMapper.readValue(browserB.decrypt(second.body()), Map.class).get("tag"))
                .isEqualTo(bookingB.getId().toString());
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("Una reserva avisa una sola vez, aunque después se confirme")
    void confirmingLaterDoesNotNotifyAgain() throws Exception {
        TestBrowser browser = TestBrowser.create();
        subscribe(clubA, staffA, "/push/a", browser);

        Booking booking = webBooking(clubA, courtA, LocalTime.of(18, 30));
        TenantContext.runAs(clubA.getId(), () -> bookingService.confirmByClub(clubA, booking.getId()));
        Booking another = webBooking(clubA, courtA, LocalTime.of(20, 0));

        assertThat(tagOf(next(), browser)).isEqualTo(booking.getId().toString());
        assertThat(tagOf(next(), browser)).isEqualTo(another.getId().toString());
    }

    @Test
    @DisplayName("Un turno que carga el club a mano no avisa al celular")
    void manualBookingDoesNotNotify() throws Exception {
        TestBrowser browser = TestBrowser.create();
        subscribe(clubA, staffA, "/push/a", browser);

        TenantContext.runAs(clubA.getId(), () -> bookingService.createManual(clubA, courtA.getId(),
                slotAt(LocalTime.of(20, 0)), "Grupo del martes", "2262415000", null, null));
        Booking web = webBooking(clubA, courtA, LocalTime.of(18, 30));

        // Si el manual hubiera avisado, ese aviso habria llegado primero.
        assertThat(tagOf(next(), browser)).isEqualTo(web.getId().toString());
    }

    @Test
    @DisplayName("Si el servicio de push contesta 410, la suscripción se borra y las demás siguen")
    void goneSubscriptionIsDeleted() throws Exception {
        UUID gone = subscribe(clubA, staffA, "/gone/viejo", TestBrowser.create());
        UUID alive = subscribe(clubA, owner(clubA), "/push/a", TestBrowser.create());

        webBooking(clubA, courtA, LocalTime.of(18, 30));

        assertThat(next().path()).isIn("/gone/viejo", "/push/a");
        assertThat(next().path()).isIn("/gone/viejo", "/push/a");
        long deadline = System.currentTimeMillis() + 5000;
        while (exists(gone) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(exists(gone)).isFalse();
        assertThat(exists(alive)).isTrue();
    }

    @Test
    @DisplayName("Cambiarle la clave o deshabilitar al usuario apaga sus avisos en todos sus dispositivos")
    void passwordChangeAndDisableDropSubscriptions() {
        UUID first = subscribe(clubA, staffA, "/push/1", TestBrowser.create());
        UUID second = subscribe(clubA, staffA, "/push/2", TestBrowser.create());
        UUID ownerDevice = subscribe(clubA, owner(clubA), "/push/3", TestBrowser.create());

        TenantContext.runAs(clubA.getId(), () -> clubUserService.setPassword(clubA, staffA.getId(), "otraClaveLarga456"));

        assertThat(exists(first)).isFalse();
        assertThat(exists(second)).isFalse();
        assertThat(exists(ownerDevice)).isTrue();

        UUID again = subscribe(clubA, staffA, "/push/4", TestBrowser.create());
        TenantContext.runAs(clubA.getId(), () -> clubUserService.setEnabled(clubA, staffA.getId(), false));
        assertThat(exists(again)).isFalse();
    }

    @Test
    @DisplayName("El navegador se suscribe desde el panel; si el celular cambia de usuario, la suscripción pasa al nuevo")
    void subscribeFromTheBrowser() {
        TestBrowser browser = TestBrowser.create();
        String endpoint = "https://fcm.googleapis.com/fcm/send/dispositivo-" + UUID.randomUUID();
        ClubUserPrincipal staff = ClubUserPrincipal.of(staffA);
        ClubUserPrincipal owner = ClubUserPrincipal.of(owner(clubA));

        TenantContext.runAs(clubA.getId(), () -> {
            panelPushService.subscribe(staff, browser.subscriptionJson(endpoint), "Mozilla/5.0 (Linux; Android 14)");
            assertThat(panelPushService.isSubscribed(staff.userId(), endpoint)).isTrue();

            panelPushService.subscribe(owner, browser.subscriptionJson(endpoint), "Mozilla/5.0 (Linux; Android 14)");
            assertThat(panelPushService.isSubscribed(owner.userId(), endpoint)).isTrue();
            assertThat(panelPushService.isSubscribed(staff.userId(), endpoint)).isFalse();

            panelPushService.unsubscribe(owner.userId(), endpoint);
            assertThat(panelPushService.isSubscribed(owner.userId(), endpoint)).isFalse();
        });
    }

    @Test
    @DisplayName("No se acepta una suscripción que apunte a otro lado que un servicio de push, ni claves rotas")
    void rejectsSuspiciousSubscriptions() {
        TestBrowser browser = TestBrowser.create();
        ClubUserPrincipal staff = ClubUserPrincipal.of(staffA);

        TenantContext.runAs(clubA.getId(), () -> {
            assertThatThrownBy(() -> panelPushService.subscribe(staff,
                    browser.subscriptionJson("http://127.0.0.1:8080/actuator"), null))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> panelPushService.subscribe(staff,
                    browser.subscriptionJson("https://fcm.googleapis.com/fcm/send/x").replace(browser.p256dh(), "AAAA"),
                    null))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> panelPushService.subscribe(staff, "no es json", null))
                    .isInstanceOf(BusinessRuleException.class);
        });
        long saved = TenantContext.callAs(clubA.getId(), subscriptionRepository::count);
        assertThat(saved).isZero();
    }

    // ------------------------------------------------------------ ayudas

    private Received next() throws InterruptedException {
        Received next = received.poll(10, TimeUnit.SECONDS);
        assertThat(next).as("el aviso tendria que haber llegado al servicio de push").isNotNull();
        return next;
    }

    private String tagOf(Received request, TestBrowser browser) {
        return (String) objectMapper.readValue(browser.decrypt(request.body()), Map.class).get("tag");
    }

    /** La suscripcion sigue en la base. Las del club A: la tabla esta filtrada por club. */
    private boolean exists(UUID subscriptionId) {
        return TenantContext.callAs(clubA.getId(), () -> subscriptionRepository.existsById(subscriptionId));
    }

    private Booking webBooking(Tenant club, Court court, LocalTime time) {
        return TenantContext.callAs(club.getId(), () -> bookingService.create(club,
                new NewBooking(court.getId(), slotAt(time), "Simon Diaz", "2262415000", PaymentChoice.PAY_AT_CLUB)));
    }

    /** Directo a la base: el servicio de push de este test es local, y desde el panel no se aceptaria. */
    private UUID subscribe(Tenant club, ClubUser user, String path, TestBrowser browser) {
        return TenantContext.callAs(club.getId(), () -> {
            PushSubscription subscription = new PushSubscription();
            subscription.setClubUserId(user.getId());
            subscription.setEndpoint("http://127.0.0.1:" + pushService.getAddress().getPort() + path);
            subscription.setP256dh(browser.p256dh());
            subscription.setAuth(browser.auth());
            return subscriptionRepository.save(subscription).getId();
        });
    }

    private ClubUser staff(Tenant club) {
        String suffix = UUID.randomUUID().toString();
        return clubUserService.createStaff(club, "Ana " + suffix, "ana-%s@test.com".formatted(suffix), PASSWORD);
    }

    private ClubUser owner(Tenant club) {
        String suffix = UUID.randomUUID().toString();
        ClubUser owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setFullName("Dueño " + suffix);
        owner.setEmail("dueno-%s@test.com".formatted(suffix));
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash("no-se-usa");
        return clubUserRepository.saveAndFlush(owner);
    }

    private static Instant slotAt(LocalTime time) {
        return TODAY.atTime(time).atZone(ZONE).toInstant();
    }
}
