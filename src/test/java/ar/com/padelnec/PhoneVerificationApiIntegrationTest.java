package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PhoneVerification;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.whatsapp.NotificationTemplate;
import ar.com.padelnec.notification.whatsapp.WhatsAppSender;
import ar.com.padelnec.repository.PhoneVerificationRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.api.BookingRateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;

/**
 * La primera reserva de un numero que nunca reservo pide el codigo de WhatsApp.
 *
 * <p>Sobre HTTP real, como {@link PublicApiIntegrationTest}: el control esta en el
 * endpoint de reserva, y un test de servicio no notaria si alguien lo saca de ahi.
 * El envio se reemplaza por un mock para leer el codigo que "llego".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Como va a estar en produccion al lanzarla: WhatsApp prendido solo para el codigo.
        properties = {"app.whatsapp.provider=log", "app.whatsapp.verify-phones=true",
                "app.whatsapp.notifications=false"})
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PhoneVerificationApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String NEW_PHONE = "2262415000";

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private BookingService bookingService;
    @Autowired private CustomerService customerService;
    @Autowired private PhoneVerificationRepository verifications;
    @Autowired private PlayerAccountRepository playerAccounts;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PhoneNumbers phoneNumbers;

    @MockitoBean private WhatsAppSender sender;
    // Aca se prueban decenas de reservas seguidas desde el mismo origen; el cupo por
    // origen tiene sus propios tests y no es lo que se mide.
    @MockitoBean private BookingRateLimiter rateLimiter;

    private RestTestClient client;
    private LocalDate matchDay;
    private Tenant club;
    private Court court;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        fixture.reset();

        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.court("Cancha 2", 2);
        matchDay = LocalDate.now(ZONE).plusDays(2);
        fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");
        TenantContext.clear();

        when(sender.send(any(), any(), any(), any())).thenReturn(WhatsAppSender.SendResult.ok("test"));
    }

    @Test
    @DisplayName("Un numero nuevo recibe el codigo y sin el no reserva; con el codigo reserva y queda en la lista")
    void newNumberBooksOnlyWithTheCode() {
        assertThat(requestCode(NEW_PHONE)).isTrue();
        String code = lastCodeSent();

        assertThat(bookError(slot(0), NEW_PHONE, null).get("reason").asText()).isEqualTo("VERIFICATION_REQUIRED");
        assertThat(bookError(slot(0), NEW_PHONE, wrong(code)).get("reason").asText())
                .isEqualTo("VERIFICATION_CODE_INVALID");

        book(slot(0), NEW_PHONE, code).expectStatus().isCreated();

        // Ya esta en la lista: la proxima no pide nada, ni manda otro WhatsApp.
        assertThat(requestCode(NEW_PHONE)).isFalse();
        book(slot(1), NEW_PHONE, null).expectStatus().isCreated();
        verify(sender, times(1)).send(any(), eq(NotificationTemplate.PHONE_VERIFICATION_CODE), any(), any());
        // Con los avisos apagados, las dos reservas no mandaron ningun otro WhatsApp.
        verify(sender, never()).send(any(),
                argThat(template -> template != NotificationTemplate.PHONE_VERIFICATION_CODE), any(), any());
    }

    @Test
    @DisplayName("Un numero que ya reservo alguna vez, aunque haya sido cargado por el club, no se verifica")
    void numberThatAlreadyBookedSkipsTheCode() {
        TenantContext.set(club.getId());
        bookingService.createManual(club, court.getId(), Instant.parse(slot(0).get("startsAt").asText()),
                "Grupo del martes", NEW_PHONE, null, null);
        TenantContext.clear();

        assertThat(requestCode(NEW_PHONE)).isFalse();
        book(slot(1), NEW_PHONE, null).expectStatus().isCreated();
        verify(sender, never()).send(any(), eq(NotificationTemplate.PHONE_VERIFICATION_CODE), any(), any());
    }

    @Test
    @DisplayName("Las reservas de un jugador bloqueado no le ahorran el codigo")
    void blockedPlayerHistoryDoesNotCount() {
        TenantContext.set(club.getId());
        Booking booking = bookingService.createManual(club, court.getId(),
                Instant.parse(slot(0).get("startsAt").asText()), "Numero falso", NEW_PHONE, null, null);
        customerService.setBlocked(booking.getCustomer(), true);
        TenantContext.clear();

        assertThat(requestCode(NEW_PHONE)).isTrue();
    }

    @Test
    @DisplayName("Pedir el codigo otra vez enseguida no manda un segundo WhatsApp")
    void askingAgainRightAwayDoesNotResend() {
        assertThat(requestCode(NEW_PHONE)).isTrue();
        assertThat(requestCode(NEW_PHONE)).isTrue();

        verify(sender, times(1)).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cinco codigos mal escritos vencen el codigo, aunque despues llegue el correcto")
    void tooManyWrongCodesExpireIt() {
        requestCode(NEW_PHONE);
        String code = lastCodeSent();

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(bookError(slot(0), NEW_PHONE, wrong(code)).get("reason").asText())
                    .isEqualTo("VERIFICATION_CODE_INVALID");
        }
        assertThat(bookError(slot(0), NEW_PHONE, wrong(code)).get("reason").asText())
                .isEqualTo("VERIFICATION_CODE_EXPIRED");
        assertThat(bookError(slot(0), NEW_PHONE, code).get("reason").asText())
                .isEqualTo("VERIFICATION_CODE_EXPIRED");
    }

    @Test
    @DisplayName("Un codigo vencido no sirve y hay que pedir otro")
    void expiredCodeIsRejected() {
        requestCode(NEW_PHONE);
        String code = lastCodeSent();
        moveLatestCodeBack(Duration.ofMinutes(11));

        assertThat(bookError(slot(0), NEW_PHONE, code).get("reason").asText())
                .isEqualTo("VERIFICATION_CODE_EXPIRED");
    }

    @Test
    @DisplayName("Mas de cinco codigos por dia para el mismo numero se frenan")
    void codesPerDayAreCapped() {
        for (int sent = 0; sent < 5; sent++) {
            assertThat(requestCode(NEW_PHONE)).isTrue();
            // Pasado el minuto de espera, el siguiente pedido manda uno nuevo.
            moveLatestCodeBack(Duration.ofMinutes(2));
        }

        JsonNode error = requestCodeError(NEW_PHONE);
        assertThat(error.get("reason").asText()).isEqualTo("VERIFICATION_LIMIT");
        verify(sender, times(5)).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Si el WhatsApp no sale, se avisa y no cuenta como codigo mandado")
    void failedSendIsReported() {
        when(sender.send(any(), any(), any(), any())).thenReturn(WhatsAppSender.SendResult.failed("Twilio 63024"));

        JsonNode error = requestCodeError(NEW_PHONE);

        assertThat(error.get("reason").asText()).isEqualTo("VERIFICATION_CODE_NOT_SENT");
        assertThat(verifications.count()).isZero();
    }

    // ------------------------------------------------ telefono de la cuenta

    @Test
    @DisplayName("Una cuenta con el numero mal cargado lo corrige en la reserva y, verificado, queda fijo")
    void accountWithWrongPhoneFixesItAndLocksIt() {
        String token = account("simon@example.com", "2262555123");
        assertThat(me(token).get("phoneLocked").asBoolean()).isFalse();

        // Reserva con su numero de verdad: le llega el codigo ahi, no al de la cuenta.
        assertThat(requestCode(NEW_PHONE)).isTrue();
        book(slot(0), NEW_PHONE, lastCodeSent(), token).expectStatus().isCreated();

        JsonNode me = me(token);
        assertThat(me.get("phoneNumber").asText()).isEqualTo("+5492262415000");
        assertThat(me.get("phoneLocked").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Con el telefono de la cuenta verificado, no se reserva con otro numero")
    void lockedAccountPhoneCannotBeSwapped() {
        String token = account("simon@example.com", NEW_PHONE);
        requestCode(NEW_PHONE);
        book(slot(0), NEW_PHONE, lastCodeSent(), token).expectStatus().isCreated();

        JsonNode error = book(slot(1), "2262555123", null, token)
                .expectStatus().isEqualTo(422)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(error.get("reason").asText()).isEqualTo("ACCOUNT_PHONE_LOCKED");
    }

    @Test
    @DisplayName("Un numero que ya es de otra cuenta no se mueve: la reserva sale y la cuenta queda como estaba")
    void phoneOfAnotherAccountIsNotTaken() {
        account("otra@example.com", NEW_PHONE);
        String token = account("simon@example.com", null);

        requestCode(NEW_PHONE);
        book(slot(0), NEW_PHONE, lastCodeSent(), token).expectStatus().isCreated();

        assertThat(me(token).get("phoneNumber").isNull()).isTrue();
    }

    // ------------------------------------------------------------ helpers

    /** Cuenta con email verificado y el telefono tal cual se registro; devuelve el token de sesion. */
    private String account(String email, String phone) {
        PlayerAccount account = new PlayerAccount();
        account.setEmail(email);
        account.setEmailVerified(true);
        account.setPasswordHash(passwordEncoder.encode("clave-segura-123"));
        account.setPhoneNumber(phone == null ? null : phoneNumbers.normalize(phone));
        playerAccounts.save(account);
        return client.post().uri("/api/public/player/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "clave-segura-123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody()
                .get("token").asText();
    }

    private JsonNode me(String token) {
        return client.get().uri("/api/public/player/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    private boolean requestCode(String phone) {
        return client.post().uri("/api/public/club-necochea/phone-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("phoneNumber", phone))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody()
                .get("verificationRequired").asBoolean();
    }

    private JsonNode requestCodeError(String phone) {
        return client.post().uri("/api/public/club-necochea/phone-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("phoneNumber", phone))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    /** El codigo que viajo en el ultimo WhatsApp: la unica variable de la plantilla. */
    @SuppressWarnings("unchecked")
    private String lastCodeSent() {
        ArgumentCaptor<List<String>> variables = ArgumentCaptor.forClass(List.class);
        verify(sender, org.mockito.Mockito.atLeastOnce())
                .send(any(), eq(NotificationTemplate.PHONE_VERIFICATION_CODE), variables.capture(), any());
        return variables.getValue().getFirst();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    /** Hace de cuenta que el ultimo codigo se mando antes: el vencimiento es el reloj del codigo. */
    private void moveLatestCodeBack(Duration duration) {
        PhoneVerification latest = new ArrayList<>(verifications.findAll()).stream()
                .max(java.util.Comparator.comparing(PhoneVerification::getExpiresAt))
                .orElseThrow();
        latest.setExpiresAt(latest.getExpiresAt().minus(duration));
        verifications.save(latest);
    }

    private RestTestClient.ResponseSpec book(JsonNode slot, String phone, String code) {
        return book(slot, phone, code, null);
    }

    private RestTestClient.ResponseSpec book(JsonNode slot, String phone, String code, String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("courtId", slot.get("available").get(0).get("courtId").asText());
        body.put("startTime", Instant.parse(slot.get("startsAt").asText()).toString());
        body.put("fullName", "Simon Diaz");
        body.put("phoneNumber", phone);
        body.put("paymentChoice", "PAY_AT_CLUB");
        if (code != null) {
            body.put("verificationCode", code);
        }
        var request = client.post().uri("/api/public/club-necochea/bookings")
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.body(body).exchange();
    }

    private JsonNode bookError(JsonNode slot, String phone, String code) {
        return book(slot, phone, code)
                .expectStatus().isEqualTo(422)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    /** El turno numero {@code index} del dia con las dos canchas libres. */
    private JsonNode slot(int index) {
        JsonNode grid = client.get().uri("/api/public/club-necochea/availability?date=" + matchDay)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
        List<JsonNode> free = new ArrayList<>();
        for (JsonNode slot : grid.get("slots")) {
            if (slot.get("available").size() == 2) {
                free.add(slot);
            }
        }
        return free.get(index);
    }
}
