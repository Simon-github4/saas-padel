package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.domain.PendingPlayerSignup;
import ar.com.padelnec.domain.PlayerSession;
import ar.com.padelnec.notification.EmailSender;
import ar.com.padelnec.support.TokenHash;
import ar.com.padelnec.repository.PendingPlayerSignupRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.repository.PlayerSessionRepository;
import ar.com.padelnec.security.GoogleIdTokenVerifier;
import ar.com.padelnec.service.PlayerAuthService;
import ar.com.padelnec.service.PlayerAuthService.IssuedSession;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** Login del jugador por email/contrasena y Google: alta con confirmacion, login, reset. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class,
        PlayerAuthServiceTest.FixedClockConfig.class, PlayerAuthServiceTest.FakesConfig.class})
class PlayerAuthServiceTest {

    private static final String NOW = "2026-09-01T10:00:00Z";
    private static final String EMAIL = "juana@example.com";
    private static final String PASSWORD = "unaClaveLarga123";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return new MutableClock(Instant.parse(NOW), ZoneId.of("UTC"));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakesConfig {
        @Bean
        @Primary
        EmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }

        @Bean
        @Primary
        GoogleIdTokenVerifier fakeGoogleIdTokenVerifier() {
            return new FakeGoogleIdTokenVerifier();
        }
    }

    /** Captura el ultimo email mandado. Los tests sacan el codigo/token del cuerpo, como haria el jugador. */
    static class CapturingEmailSender implements EmailSender {
        String lastTo;
        String lastSubject;
        String lastBody;

        @Override
        public String providerName() {
            return "capturing";
        }

        @Override
        public SendResult send(String toAddress, String subject, String plainBody) {
            lastTo = toAddress;
            lastSubject = subject;
            lastBody = plainBody;
            return SendResult.ok();
        }
    }

    /** Identidad de Google que el test setea antes de llamar a loginWithGoogle. */
    static class FakeGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
        Optional<GoogleIdentity> nextResult = Optional.empty();

        @Override
        public Optional<GoogleIdentity> verify(String idToken) {
            return nextResult;
        }
    }

    @Autowired private PlayerAuthService playerAuthService;
    @Autowired private PlayerAccountRepository playerAccountRepository;
    @Autowired private PendingPlayerSignupRepository pendingPlayerSignupRepository;
    @Autowired private PlayerSessionRepository playerSessionRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private Clock clock;
    @Autowired private CapturingEmailSender emailSender;
    @Autowired private FakeGoogleIdTokenVerifier googleVerifier;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).set(Instant.parse(NOW));
        fixture.reset();
        googleVerifier.nextResult = Optional.empty();
        emailSender.lastTo = null;
        emailSender.lastSubject = null;
        emailSender.lastBody = null;
    }

    // --------------------------------------------------------------- alta

    @Test
    @DisplayName("Registrarse no crea la cuenta todavia; confirmar el codigo si")
    void registerDoesNotCreateTheAccountUntilConfirmed() {
        playerAuthService.register(EMAIL, PASSWORD, "Juana Pérez", null);

        assertThat(playerAccountRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(emailSender.lastTo).isEqualTo(EMAIL);

        IssuedSession session = playerAuthService.confirmSignup(EMAIL, extractCode());

        assertThat(session.token()).isNotBlank();
        assertThat(session.session().getPlayer().getEmail()).isEqualTo(EMAIL);
        assertThat(session.session().getPlayer().isEmailVerified()).isTrue();
        assertThat(session.session().getPlayer().getDisplayName()).isEqualTo("Juana Pérez");
        assertThat(pendingPlayerSignupRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("El link de confirmacion crea la cuenta igual que el codigo, pero sin abrir sesion")
    void confirmSignupByTokenAlsoCreatesTheAccount() {
        playerAuthService.register(EMAIL, PASSWORD, null, null);
        PendingPlayerSignup pending = pendingPlayerSignupRepository.findByEmail(EMAIL).orElseThrow();

        boolean confirmed = playerAuthService.confirmSignupByToken(tokenFromLastEmail("verify-email?token="));

        assertThat(confirmed).isTrue();
        assertThat(playerAccountRepository.findByEmail(EMAIL).orElseThrow().isEmailVerified()).isTrue();
        assertThat(pendingPlayerSignupRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("Un codigo incorrecto no crea la cuenta; despues de 5 intentos hay que registrarse de nuevo")
    void wrongCodeDoesNotCreateTheAccountAndLocksOutAfterFiveAttempts() {
        playerAuthService.register(EMAIL, PASSWORD, null, null);
        String realCode = extractCode();
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> playerAuthService.confirmSignup(EMAIL, wrongCode))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no es correcto");
        }
        assertThatThrownBy(() -> playerAuthService.confirmSignup(EMAIL, wrongCode))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Registrate de nuevo");

        assertThat(playerAccountRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(pendingPlayerSignupRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("Un segundo registro con el mismo email reemplaza el pedido anterior")
    void aSecondRegisterReplacesThePendingSignup() {
        playerAuthService.register(EMAIL, PASSWORD, null, null);
        String staleCode = extractCode();

        playerAuthService.register(EMAIL, "otraClave456", null, null);

        assertThatThrownBy(() -> playerAuthService.confirmSignup(EMAIL, staleCode))
                .isInstanceOf(BusinessRuleException.class);

        IssuedSession session = playerAuthService.confirmSignup(EMAIL, extractCode());
        assertThat(session).isNotNull();
    }

    @Test
    @DisplayName("Vencido el plazo, ni el codigo ni el link confirman")
    void expiredSignupCannotBeConfirmed() {
        playerAuthService.register(EMAIL, PASSWORD, null, null);
        String code = extractCode();
        PendingPlayerSignup pending = pendingPlayerSignupRepository.findByEmail(EMAIL).orElseThrow();

        ((MutableClock) clock).advance(Duration.ofMinutes(11));

        assertThatThrownBy(() -> playerAuthService.confirmSignup(EMAIL, code))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("venció");
        assertThat(playerAuthService.confirmSignupByToken(tokenFromLastEmail("verify-email?token="))).isFalse();
    }

    @Test
    @DisplayName("No se puede registrar con un email que ya tiene una cuenta confirmada")
    void duplicateEmailIsRejected() {
        registerAndConfirm(EMAIL, PASSWORD, null, null);

        assertThatThrownBy(() -> playerAuthService.register(EMAIL, "otraClave123", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene una cuenta");
    }

    // -------------------------------------------------------------- login

    @Test
    @DisplayName("Login con las credenciales correctas actualiza el ultimo acceso")
    void loginSucceedsAndUpdatesLastLoginAt() {
        registerAndConfirm(EMAIL, PASSWORD, null, null);
        ((MutableClock) clock).advance(Duration.ofMinutes(5));

        IssuedSession session = playerAuthService.login(EMAIL, PASSWORD);

        assertThat(session.token()).isNotBlank();
        assertThat(session.session().getPlayer().getLastLoginAt()).isEqualTo(Instant.parse(NOW).plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Contrasena incorrecta y email inexistente devuelven el mismo mensaje: no delatan cual de los dos")
    void wrongPasswordAndUnknownEmailGiveTheSameMessage() {
        registerAndConfirm(EMAIL, PASSWORD, null, null);

        Throwable wrongPassword = catchException(() -> playerAuthService.login(EMAIL, "otraClave"));
        Throwable unknownEmail = catchException(() -> playerAuthService.login("nadie@example.com", PASSWORD));

        assertThat(wrongPassword).isInstanceOf(BusinessRuleException.class);
        assertThat(unknownEmail).isInstanceOf(BusinessRuleException.class);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
    }

    @Test
    @DisplayName("Login con contrasena contra una cuenta solo-Google da un mensaje especifico")
    void loginAgainstGoogleOnlyAccountHintsGoogle() {
        googleVerifier.nextResult = Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("sub-1", EMAIL, true, "Juana"));
        playerAuthService.loginWithGoogle("token");

        assertThatThrownBy(() -> playerAuthService.login(EMAIL, PASSWORD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Google");
    }

    // ------------------------------------------------------------- google

    @Test
    @DisplayName("Login con Google sin cuenta previa crea una nueva")
    void googleLoginCreatesANewAccount() {
        googleVerifier.nextResult = Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("sub-1", EMAIL, true, "Juana"));

        IssuedSession session = playerAuthService.loginWithGoogle("token");

        assertThat(session.session().getPlayer().getEmail()).isEqualTo(EMAIL);
        assertThat(session.session().getPlayer().isEmailVerified()).isTrue();
        assertThat(session.session().getPlayer().getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("Google linkea por email solo si Google lo marca como verificado")
    void googleLinksToExistingAccountOnlyWhenEmailVerified() {
        registerAndConfirm(EMAIL, PASSWORD, null, null);

        googleVerifier.nextResult = Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("sub-1", EMAIL, true, "Juana"));
        playerAuthService.loginWithGoogle("token");

        assertThat(playerAccountRepository.count()).isEqualTo(1);
        assertThat(playerAccountRepository.findByEmail(EMAIL).orElseThrow().getGoogleSubject())
                .isEqualTo("sub-1");
    }

    @Test
    @DisplayName("Si Google no marca el email como verificado, no linkea: crea una cuenta aparte")
    void googleDoesNotLinkWhenEmailNotVerified() {
        registerAndConfirm(EMAIL, PASSWORD, null, null);

        googleVerifier.nextResult = Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("sub-1", EMAIL, false, "Juana"));

        assertThatThrownBy(() -> playerAuthService.loginWithGoogle("token"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene una cuenta");
    }

    @Test
    @DisplayName("Loguearse dos veces con Google con el mismo sub siempre resuelve la misma cuenta")
    void sameGoogleSubjectAlwaysResolvesTheSameAccount() {
        googleVerifier.nextResult = Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("sub-1", EMAIL, true, "Juana"));
        IssuedSession first = playerAuthService.loginWithGoogle("token");

        IssuedSession second = playerAuthService.loginWithGoogle("token");

        assertThat(second.session().getPlayer().getId()).isEqualTo(first.session().getPlayer().getId());
        assertThat(playerAccountRepository.count()).isEqualTo(1);
    }

    // --------------------------------------------------------- reset de contrasena

    @Test
    @DisplayName("Reset de contrasena de punta a punta: la vieja deja de servir, la nueva funciona")
    void passwordResetEndToEnd() {
        registerAndConfirm(EMAIL, PASSWORD, null, null);

        playerAuthService.requestPasswordReset(EMAIL);
        String token = tokenFromLastEmail("/reset-password/");
        assertThat(token).isNotBlank();

        playerAuthService.resetPassword(token, "unaClaveNueva456");

        assertThatThrownBy(() -> playerAuthService.login(EMAIL, PASSWORD))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(playerAuthService.login(EMAIL, "unaClaveNueva456")).isNotNull();
    }

    @Test
    @DisplayName("Pedir un reset para un email que no existe no revela nada: no lanza")
    void requestingResetForUnknownEmailDoesNothingSilently() {
        playerAuthService.requestPasswordReset("nadie@example.com");
        assertThat(emailSender.lastTo).isNull();
    }

    @Test
    @DisplayName("Un token de reset vencido se rechaza aunque la contrasena nueva sea valida")
    void expiredResetTokenIsRejected() {
        registerAndConfirm(EMAIL, PASSWORD, null, null);
        playerAuthService.requestPasswordReset(EMAIL);
        String token = tokenFromLastEmail("/reset-password/");

        ((MutableClock) clock).advance(Duration.ofHours(2));

        assertThatThrownBy(() -> playerAuthService.resetPassword(token, "unaClaveNueva456"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("venció");
    }

    @Test
    @DisplayName("Resetear la contrasena revoca las demas sesiones activas de la cuenta")
    void passwordResetRevokesOtherSessions() {
        IssuedSession session = registerAndConfirm(EMAIL, PASSWORD, null, null);

        playerAuthService.requestPasswordReset(EMAIL);
        String token = tokenFromLastEmail("/reset-password/");
        playerAuthService.resetPassword(token, "unaClaveNueva456");

        assertThatThrownBy(() -> playerAuthService.resolveSession(session.token()))
                .isInstanceOf(UnauthorizedSessionException.class);
    }

    // ----------------------------------------------------------- historial

    @Test
    @DisplayName("Sin telefono cargado, el historial devuelve vacio en vez de fallar")
    void historyIsEmptyWithoutAPhone() {
        IssuedSession session = registerAndConfirm(EMAIL, PASSWORD, null, null);

        assertThat(playerAuthService.history(session.token())).isEmpty();
    }

    @Test
    @DisplayName("Una cuenta de Google con el email sin verificar del lado de Google tampoco ve el historial")
    void googleAccountWithUnverifiedEmailCannotSeeHistory() {
        googleVerifier.nextResult = Optional.of(
                new GoogleIdTokenVerifier.GoogleIdentity("sub-1", EMAIL, false, "Juana"));
        IssuedSession session = playerAuthService.loginWithGoogle("token");

        assertThatThrownBy(() -> playerAuthService.history(session.token()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Confirmá tu email");
    }

    // --------------------------------------------------------------- sesion

    @Test
    @DisplayName("Una sesion vencida no resuelve, y el logout la corta antes de tiempo")
    void sessionExpiryAndLogout() {
        IssuedSession session = registerAndConfirm(EMAIL, PASSWORD, null, null);

        assertThat(playerAuthService.resolveSession(session.token())).isNotNull();

        ((MutableClock) clock).advance(Duration.ofDays(91));
        assertThatThrownBy(() -> playerAuthService.resolveSession(session.token()))
                .isInstanceOf(UnauthorizedSessionException.class);

        // Logout, con una sesion nueva y vigente esta vez.
        ((MutableClock) clock).set(Instant.parse(NOW));
        IssuedSession fresh = playerAuthService.login(EMAIL, PASSWORD);
        playerAuthService.logout(fresh.token());

        assertThatThrownBy(() -> playerAuthService.resolveSession(fresh.token()))
                .isInstanceOf(UnauthorizedSessionException.class);
        assertThat(playerSessionRepository.findByTokenHashAndRevokedAtIsNull(TokenHash.of(fresh.token()))).isEmpty();
    }

    // ------------------------------------------------------------ ayudantes

    /** Registra y confirma de una, para los tests a los que solo les importa tener una cuenta usable. */
    private IssuedSession registerAndConfirm(String email, String password, String displayName, String phoneNumber) {
        playerAuthService.register(email, password, displayName, phoneNumber);
        return playerAuthService.confirmSignup(email, extractCode());
    }

    /** Saca el codigo de 6 digitos del ultimo mail mandado, tal como lo leeria el jugador. */
    /** El token tal como le llega al jugador. La fila guarda su huella, no el valor. */
    private String tokenFromLastEmail(String after) {
        String body = emailSender.lastBody;
        assertThat(body).as("no salio ningun mail").isNotNull();
        int from = body.indexOf(after);
        assertThat(from).as("el mail no traia el link esperado").isNotNegative();
        Matcher matcher = Pattern.compile("^[A-Za-z0-9_-]+")
                .matcher(body.substring(from + after.length()));
        assertThat(matcher.find()).as("el link no traia token").isTrue();
        return matcher.group();
    }

    private String extractCode() {
        Matcher matcher = Pattern.compile("\\d{6}").matcher(emailSender.lastBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private Throwable catchException(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable ex) {
            return ex;
        }
    }
}
