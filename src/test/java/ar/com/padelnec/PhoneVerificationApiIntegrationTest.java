package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PhoneVerification;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingSource;
import ar.com.padelnec.payment.MercadoPagoGateway;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.PhoneVerificationRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.CustomerService;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.web.api.BookingRateLimiter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/**
 * La primera reserva sin sena de un numero que nunca se verifico pide que el
 * jugador nos mande un WhatsApp desde ese numero.
 *
 * <p>Sobre HTTP real, como {@link PublicApiIntegrationTest}: el control esta en el
 * endpoint de reserva y en el webhook, y un test de servicio no notaria si alguien
 * los saca de ahi. Lo que Twilio nos reenviaria se postea a mano, firmado con el
 * mismo algoritmo que usa Twilio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Como va a estar en produccion al lanzarla: WhatsApp prendido solo para verificar.
        properties = {"app.whatsapp.provider=log", "app.whatsapp.verify-phones=true",
                "app.whatsapp.notifications=false", "app.whatsapp.from-number=+17017145042",
                "app.whatsapp.auth-token=" + PhoneVerificationApiIntegrationTest.AUTH_TOKEN})
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PhoneVerificationApiIntegrationTest {

    static final String AUTH_TOKEN = "token-de-twilio-de-prueba";
    /** La URL con la que firma Twilio: base-url de los tests mas la ruta del webhook. */
    private static final String WEBHOOK_URL = "http://localhost:8080/api/webhooks/twilio/whatsapp";

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String NEW_PHONE = "2262415000";
    private static final String NEW_PHONE_WHATSAPP = "whatsapp:+5492262415000";
    private static final Pattern CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookings;
    @Autowired private CustomerService customerService;
    @Autowired private PhoneVerificationRepository verifications;
    @Autowired private PlayerAccountRepository playerAccounts;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PhoneNumbers phoneNumbers;

    // Aca se prueban decenas de pedidos seguidos desde el mismo origen; el cupo por
    // origen tiene sus propios tests y no es lo que se mide.
    @MockitoBean private BookingRateLimiter rateLimiter;
    @MockitoBean private MercadoPagoGateway gateway;

    private RestTestClient client;
    private LocalDate matchDay;
    private Tenant club;
    private Court court;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        fixture.reset();

        club = fixture.club("club-necochea");
        // Para poder reservar con sena: con eso no se pide verificar.
        club.setMpAccessToken("APP_USR-token-de-prueba");
        club = fixture.save(club);
        TenantContext.set(club.getId());
        court = fixture.court("Cancha 1", 1);
        fixture.court("Cancha 2", 2);
        matchDay = LocalDate.now(ZONE).plusDays(2);
        fixture.allDayPrice(matchDay.getDayOfWeek(), "20000");
        TenantContext.clear();

        when(gateway.createDepositCheckout(any(), any(), any()))
                .thenReturn(new MercadoPagoGateway.Checkout("ORDER-1", "https://mp.test/checkout"));
    }

    @Test
    @DisplayName("Un numero nuevo no reserva sin sena hasta que manda el WhatsApp; despues reserva y no se le pide mas")
    void newNumberBooksOnlyAfterSendingTheMessage() {
        JsonNode challenge = requestVerification(NEW_PHONE);
        assertThat(challenge.get("verificationRequired").asBoolean()).isTrue();
        String link = challenge.get("whatsappLink").asText();
        assertThat(link).startsWith("https://api.whatsapp.com/send?phone=17017145042&text=");
        String message = messageIn(link);
        assertThat(message).contains(club.getName()).contains(codeIn(message));
        // El mismo link, para escanearlo desde la compu.
        assertThat(challenge.get("whatsappQr").asText()).startsWith("data:image/svg+xml;base64,");

        assertThat(bookError(slot(0), NEW_PHONE, "PAY_AT_CLUB").get("reason").asText())
                .isEqualTo("VERIFICATION_REQUIRED");
        assertThat(status(challenge)).isEqualTo("PENDING");

        // El jugador manda el mensaje tal cual se lo armo la pagina.
        assertThat(inbound(NEW_PHONE_WHATSAPP, message)).contains("¡Listo!");

        assertThat(status(challenge)).isEqualTo("VERIFIED");
        book(slot(0), NEW_PHONE, "PAY_AT_CLUB").expectStatus().isCreated();
        assertThat(requestVerification(NEW_PHONE).get("verificationRequired").asBoolean()).isFalse();
        book(slot(1), NEW_PHONE, "PAY_AT_CLUB").expectStatus().isCreated();
    }

    @Test
    @DisplayName("Con sena se reserva sin verificar el numero")
    void depositBookingSkipsVerification() {
        book(slot(0), NEW_PHONE, "DEPOSIT_ONLINE").expectStatus().isCreated();
    }

    @Test
    @DisplayName("Una reserva web no prueba el numero: despues, sin sena, igual hay que verificarlo")
    void webBookingDoesNotCountAsProof() {
        book(slot(0), NEW_PHONE, "DEPOSIT_ONLINE").expectStatus().isCreated();

        assertThat(bookError(slot(1), NEW_PHONE, "PAY_AT_CLUB").get("reason").asText())
                .isEqualTo("VERIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("El codigo mandado desde otro numero no verifica nada")
    void codeFromAnotherNumberIsRejected() {
        JsonNode challenge = requestVerification(NEW_PHONE);
        String message = messageIn(challenge.get("whatsappLink").asText());

        assertThat(inbound("whatsapp:+5492262438817", message)).contains("otro número");

        assertThat(status(challenge)).isEqualTo("PENDING");
        assertThat(bookError(slot(0), NEW_PHONE, "PAY_AT_CLUB").get("reason").asText())
                .isEqualTo("VERIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("Un mensaje sin la firma de Twilio se rechaza y no verifica nada")
    void unsignedMessageIsRejected() {
        JsonNode challenge = requestVerification(NEW_PHONE);
        String message = messageIn(challenge.get("whatsappLink").asText());

        client.post().uri("/api/webhooks/twilio/whatsapp")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Twilio-Signature", "firma-inventada")
                .body(form(NEW_PHONE_WHATSAPP, message))
                .exchange()
                .expectStatus().isForbidden();

        assertThat(status(challenge)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Alcanza con el codigo: el jugador puede cambiarle el resto del mensaje")
    void onlyTheCodeMatters() {
        JsonNode challenge = requestVerification(NEW_PHONE);
        String code = codeIn(messageIn(challenge.get("whatsappLink").asText()));

        assertThat(inbound(NEW_PHONE_WHATSAPP, "hola, el código es " + code + " gracias")).contains("¡Listo!");

        assertThat(status(challenge)).isEqualTo("VERIFIED");
    }

    @Test
    @DisplayName("Mientras el pedido esta vigente, pedirlo de nuevo devuelve el mismo mensaje")
    void askingAgainReturnsTheSameMessage() {
        JsonNode first = requestVerification(NEW_PHONE);
        JsonNode second = requestVerification(NEW_PHONE);

        assertThat(second.get("verificationId").asText()).isEqualTo(first.get("verificationId").asText());
        assertThat(second.get("whatsappLink").asText()).isEqualTo(first.get("whatsappLink").asText());
        assertThat(verifications.count()).isOne();
    }

    @Test
    @DisplayName("Un pedido vencido no sirve: el mensaje llega tarde y hay que pedir otro")
    void expiredRequestIsRejected() {
        JsonNode challenge = requestVerification(NEW_PHONE);
        String message = messageIn(challenge.get("whatsappLink").asText());
        moveLatestBack(Duration.ofMinutes(11));

        assertThat(status(challenge)).isEqualTo("EXPIRED");
        assertThat(inbound(NEW_PHONE_WHATSAPP, message)).contains("venció");
        assertThat(requestVerification(NEW_PHONE).get("verificationId").asText())
                .isNotEqualTo(challenge.get("verificationId").asText());
    }

    @Test
    @DisplayName("Mas de cinco pedidos por dia para el mismo numero se frenan")
    void requestsPerDayAreCapped() {
        for (int sent = 0; sent < 5; sent++) {
            assertThat(requestVerification(NEW_PHONE).get("verificationRequired").asBoolean()).isTrue();
            // Vencido el anterior, el siguiente pedido crea uno nuevo.
            moveLatestBack(Duration.ofMinutes(11));
        }

        JsonNode error = client.post().uri("/api/public/club-necochea/phone-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("phoneNumber", NEW_PHONE))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
        assertThat(error.get("reason").asText()).isEqualTo("VERIFICATION_LIMIT");
    }

    @Test
    @DisplayName("Un mensaje sin codigo recibe una respuesta que explica para que es el numero")
    void messageWithoutCodeGetsAnExplanation() {
        assertThat(inbound(NEW_PHONE_WHATSAPP, "Hola, ¿tienen cancha mañana?")).contains("tu club");
    }

    @Test
    @DisplayName("Mandar el mensaje otra vez, ya verificado, no rompe nada")
    void sendingTheMessageTwiceIsHarmless() {
        String message = messageIn(requestVerification(NEW_PHONE).get("whatsappLink").asText());
        inbound(NEW_PHONE_WHATSAPP, message);

        assertThat(inbound(NEW_PHONE_WHATSAPP, message)).contains("ya está confirmado");
    }

    @Test
    @DisplayName("Un numero con un turno cargado por el club no se verifica")
    void numberLoadedByTheClubSkipsVerification() {
        TenantContext.set(club.getId());
        bookingService.createManual(club, court.getId(), Instant.parse(slot(0).get("startsAt").asText()),
                "Grupo del martes", NEW_PHONE, null, null);
        TenantContext.clear();

        assertThat(requestVerification(NEW_PHONE).get("verificationRequired").asBoolean()).isFalse();
        book(slot(1), NEW_PHONE, "PAY_AT_CLUB").expectStatus().isCreated();
    }

    @Test
    @DisplayName("Un turno cargado por el club que despues paso a ser web no cuenta")
    void onlyClubLoadedBookingsCount() {
        TenantContext.set(club.getId());
        Booking booking = bookingService.createManual(club, court.getId(),
                Instant.parse(slot(0).get("startsAt").asText()), "Simon Diaz", NEW_PHONE, null, null);
        booking.setSource(BookingSource.WEB);
        bookings.save(booking);
        TenantContext.clear();

        assertThat(requestVerification(NEW_PHONE).get("verificationRequired").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Las reservas de un jugador bloqueado no le ahorran la verificacion")
    void blockedPlayerHistoryDoesNotCount() {
        TenantContext.set(club.getId());
        Booking booking = bookingService.createManual(club, court.getId(),
                Instant.parse(slot(0).get("startsAt").asText()), "Numero falso", NEW_PHONE, null, null);
        customerService.setBlocked(booking.getCustomer(), true);
        TenantContext.clear();

        assertThat(requestVerification(NEW_PHONE).get("verificationRequired").asBoolean()).isTrue();
    }

    // ------------------------------------------------ telefono de la cuenta

    @Test
    @DisplayName("Una cuenta con el numero mal cargado lo corrige en la reserva y, verificado, queda fijo")
    void accountWithWrongPhoneFixesItAndLocksIt() {
        String token = account("simon@example.com", "2262555123");
        assertThat(me(token).get("phoneLocked").asBoolean()).isFalse();

        // Reserva con su numero de verdad, y lo confirma desde ese WhatsApp.
        verify(NEW_PHONE, NEW_PHONE_WHATSAPP);
        book(slot(0), NEW_PHONE, "PAY_AT_CLUB", token).expectStatus().isCreated();

        JsonNode me = me(token);
        assertThat(me.get("phoneNumber").asText()).isEqualTo("+5492262415000");
        assertThat(me.get("phoneLocked").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Con sena, el numero de la reserva no pasa a ser el de la cuenta")
    void depositBookingDoesNotAdoptThePhone() {
        String token = account("simon@example.com", "2262555123");

        book(slot(0), NEW_PHONE, "DEPOSIT_ONLINE", token).expectStatus().isCreated();

        assertThat(me(token).get("phoneNumber").asText()).isEqualTo("+5492262555123");
    }

    @Test
    @DisplayName("Con el telefono de la cuenta verificado, no se reserva con otro numero")
    void lockedAccountPhoneCannotBeSwapped() {
        String token = account("simon@example.com", NEW_PHONE);
        verify(NEW_PHONE, NEW_PHONE_WHATSAPP);
        book(slot(0), NEW_PHONE, "PAY_AT_CLUB", token).expectStatus().isCreated();

        JsonNode error = book(slot(1), "2262555123", "PAY_AT_CLUB", token)
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

        verify(NEW_PHONE, NEW_PHONE_WHATSAPP);
        book(slot(0), NEW_PHONE, "PAY_AT_CLUB", token).expectStatus().isCreated();

        assertThat(me(token).get("phoneNumber").isNull()).isTrue();
    }

    // ------------------------------------------------------------ helpers

    /** Pide la verificacion y la confirma mandando el mensaje desde {@code fromWhatsapp}. */
    private void verify(String phone, String fromWhatsapp) {
        String message = messageIn(requestVerification(phone).get("whatsappLink").asText());
        assertThat(inbound(fromWhatsapp, message)).contains("¡Listo!");
    }

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

    private JsonNode requestVerification(String phone) {
        return client.post().uri("/api/public/club-necochea/phone-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("phoneNumber", phone))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();
    }

    private String status(JsonNode challenge) {
        return client.get()
                .uri("/api/public/club-necochea/phone-verification/" + challenge.get("verificationId").asText())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody()
                .get("status").asText();
    }

    /** Lo que haria Twilio al llegar un WhatsApp: postea el mensaje firmado. Devuelve el TwiML. */
    private String inbound(String from, String body) {
        MultiValueMap<String, String> form = form(from, body);
        return client.post().uri("/api/webhooks/twilio/whatsapp")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Twilio-Signature", twilioSignature(form))
                .body(form)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
    }

    private static MultiValueMap<String, String> form(String from, String body) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To", "whatsapp:+17017145042");
        form.add("Body", body);
        form.add("MessageSid", "SM" + "0".repeat(32));
        return form;
    }

    /**
     * La firma de Twilio, escrita aca y no con su SDK: si el webhook la validara
     * mal, usar el mismo codigo de los dos lados no lo notaria. HMAC-SHA1, con el
     * auth token, de la URL seguida de cada parametro (nombre y valor) en orden.
     */
    private static String twilioSignature(MultiValueMap<String, String> form) {
        StringBuilder data = new StringBuilder(WEBHOOK_URL);
        new TreeMap<>(form.toSingleValueMap()).forEach((name, value) -> data.append(name).append(value));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(AUTH_TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** El texto que el link deja escrito en WhatsApp. */
    private static String messageIn(String link) {
        String text = link.substring(link.indexOf("&text=") + "&text=".length());
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static String codeIn(String message) {
        Matcher matcher = CODE.matcher(message);
        assertThat(matcher.find()).as("el mensaje trae un codigo de 6 digitos").isTrue();
        return matcher.group();
    }

    /** Hace de cuenta que el ultimo pedido se hizo antes: el vencimiento es su reloj. */
    private void moveLatestBack(Duration duration) {
        PhoneVerification latest = new ArrayList<>(verifications.findAll()).stream()
                .max(Comparator.comparing(PhoneVerification::getExpiresAt))
                .orElseThrow();
        latest.setExpiresAt(latest.getExpiresAt().minus(duration));
        verifications.save(latest);
    }

    private RestTestClient.ResponseSpec book(JsonNode slot, String phone, String paymentChoice) {
        return book(slot, phone, paymentChoice, null);
    }

    private RestTestClient.ResponseSpec book(JsonNode slot, String phone, String paymentChoice, String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("courtId", slot.get("available").get(0).get("courtId").asText());
        body.put("startTime", Instant.parse(slot.get("startsAt").asText()).toString());
        body.put("fullName", "Simon Diaz");
        body.put("phoneNumber", phone);
        body.put("paymentChoice", paymentChoice);
        var request = client.post().uri("/api/public/club-necochea/bookings")
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.body(body).exchange();
    }

    private JsonNode bookError(JsonNode slot, String phone, String paymentChoice) {
        return book(slot, phone, paymentChoice)
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
