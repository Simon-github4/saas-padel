package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.notification.EmailSender;
import ar.com.padelnec.repository.PendingPlayerSignupRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.repository.PlayerSessionRepository;
import ar.com.padelnec.service.PlayerAuthService;
import ar.com.padelnec.service.PlayerAuthService.IssuedSession;
import ar.com.padelnec.support.TokenHash;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ninguna credencial de cuenta queda escrita en claro en la base.
 *
 * <p>El token de sesion, el de reseteo y el del link de alta son credenciales:
 * quien los tenga entra como ese jugador, le cambia la contrasena o le crea la
 * cuenta. Guardados tal cual, una copia de la base -- un backup mal guardado, un
 * dump pedido para depurar algo -- alcanzaba para quedarse con todas las sesiones
 * abiertas.
 *
 * <p>Se verifica contra las columnas reales y no contra los getters: lo que
 * importa es lo que queda escrito en el disco, que es lo que se copia.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, TokenStorageTest.CapturingMail.class})
class TokenStorageTest {

    private static final String EMAIL = "jugadora@test.com";
    private static final String PASSWORD = "unaClaveSegura123";

    @TestConfiguration(proxyBeanMethods = false)
    static class CapturingMail {
        @Bean
        @Primary
        Capturing capturingEmailSender() {
            return new Capturing();
        }
    }

    static class Capturing implements EmailSender {
        String lastBody;

        @Override
        public String providerName() {
            return "capturing";
        }

        @Override
        public SendResult send(String toAddress, String subject, String plainBody) {
            lastBody = plainBody;
            return SendResult.ok();
        }
    }

    @Autowired private PlayerAuthService playerAuthService;
    @Autowired private PlayerSessionRepository playerSessionRepository;
    @Autowired private PlayerAccountRepository playerAccountRepository;
    @Autowired private PendingPlayerSignupRepository pendingPlayerSignupRepository;
    @Autowired private Capturing mail;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        playerSessionRepository.deleteAllInBatch();
        pendingPlayerSignupRepository.deleteAllInBatch();
        playerAccountRepository.deleteAllInBatch();
        mail.lastBody = null;
    }

    @Test
    @DisplayName("El token de sesion no esta en la base, solo su huella")
    void sessionTokensAreStoredHashed() {
        IssuedSession issued = registerAndConfirm();

        String stored = jdbc.queryForObject(
                "SELECT token_hash FROM player_session LIMIT 1", String.class);

        assertThat(stored)
                .as("lo guardado no puede ser el token que tiene el navegador")
                .isNotEqualTo(issued.token())
                .isEqualTo(TokenHash.of(issued.token()))
                .hasSize(64);

        // Y la sesion sigue funcionando con el token de verdad.
        assertThat(playerAuthService.resolveSession(issued.token()).getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("El token del link de alta no esta en la base, solo su huella")
    void signupTokensAreStoredHashed() {
        playerAuthService.register(EMAIL, PASSWORD, "Jugadora", "2262415000");
        String token = tokenFromMail("verify-email?token=");

        String stored = jdbc.queryForObject(
                "SELECT confirm_token_hash FROM player_signup_pending LIMIT 1", String.class);

        assertThat(stored).isNotEqualTo(token).isEqualTo(TokenHash.of(token));
        assertThat(playerAuthService.confirmSignupByToken(token)).isTrue();
    }

    @Test
    @DisplayName("El token de reseteo no esta en la base, solo su huella")
    void resetTokensAreStoredHashed() {
        registerAndConfirm();
        mail.lastBody = null;
        playerAuthService.requestPasswordReset(EMAIL);
        String token = tokenFromMail("/reset-password/");

        String stored = jdbc.queryForObject(
                "SELECT password_reset_token_hash FROM player_account WHERE email = ?",
                String.class, EMAIL);

        assertThat(stored).isNotEqualTo(token).isEqualTo(TokenHash.of(token));

        // Y el link sigue sirviendo para lo que existe.
        playerAuthService.resetPassword(token, "otraClaveDistinta456");
        assertThat(playerAuthService.login(EMAIL, "otraClaveDistinta456")).isNotNull();
    }

    @Test
    @DisplayName("Con una copia de la base no se entra: la huella no sirve como token")
    void aStolenHashIsUseless() {
        IssuedSession issued = registerAndConfirm();
        String stolen = jdbc.queryForObject(
                "SELECT token_hash FROM player_session LIMIT 1", String.class);

        // Es exactamente lo que veria quien consigue un dump, y no abre nada:
        // el servidor vuelve a calcular la huella de lo que le mandan, asi que
        // presentar la huella solo produce la huella de la huella.
        assertThat(playerSessionRepository
                .findByTokenHashAndRevokedAtIsNull(TokenHash.of(stolen)))
                .isEmpty();
        assertThat(TokenHash.of(stolen)).isNotEqualTo(TokenHash.of(issued.token()));
    }

    @Test
    @DisplayName("La huella que calcula la migracion es la misma que calcula la aplicacion")
    void theMigrationComputesTheSameHash() {
        // V5 convierte las filas que ya existian con SQL:
        //   UPDATE player_session SET token = encode(sha256(token::bytea), 'hex')
        // Si ese calculo no coincidiera con el de TokenHash, cada sesion abierta
        // quedaria rota en silencio el dia del despliegue. En una base de test
        // vacia la migracion no convierte nada, asi que la equivalencia hay que
        // comprobarla aparte -- es la unica parte de V5 que ningun otro test toca.
        String token = "un-token-de-ejemplo_con-guiones-y_guion-bajo";

        String comoLoHaceLaMigracion = jdbc.queryForObject(
                "SELECT encode(sha256(?::bytea), 'hex')", String.class, token);

        assertThat(comoLoHaceLaMigracion).isEqualTo(TokenHash.of(token));
    }

    // ----------------------------------------------------------------- helpers

    private IssuedSession registerAndConfirm() {
        playerAuthService.register(EMAIL, PASSWORD, "Jugadora", "2262415000");
        Matcher code = Pattern.compile("\\d{6}").matcher(mail.lastBody);
        assertThat(code.find()).isTrue();
        return playerAuthService.confirmSignup(EMAIL, code.group());
    }

    private String tokenFromMail(String after) {
        int from = mail.lastBody.indexOf(after);
        assertThat(from).as("el mail no traia el link esperado").isNotNegative();
        Matcher matcher = Pattern.compile("^[A-Za-z0-9_-]+")
                .matcher(mail.lastBody.substring(from + after.length()));
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
