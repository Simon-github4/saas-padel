package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.payment.MercadoPagoOAuthClient;
import ar.com.padelnec.payment.MercadoPagoOAuthClient.AccountResponse;
import ar.com.padelnec.payment.MercadoPagoOAuthClient.TokenResponse;
import ar.com.padelnec.payment.MercadoPagoOAuthService;
import ar.com.padelnec.payment.PaymentGatewayException;
import ar.com.padelnec.repository.TenantRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Configuracion muestra de quien es la cuenta de MercadoPago conectada con el
 * nombre y el email, no con el user_id: el dueno no reconoce ese numero.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class MercadoPagoAccountNameTest {

    private static final TokenResponse TOKEN =
            new TokenResponse("APP_USR-access", "TG-refresh", 123456789L, 15_552_000L);

    @Autowired private ClubFixture fixture;
    @Autowired private MercadoPagoOAuthService oauthService;
    @Autowired private TenantRepository tenantRepository;
    @MockitoBean private MercadoPagoOAuthClient client;

    private Tenant club;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-mp");
        when(client.exchangeCode(anyString(), anyString(), anyString())).thenReturn(TOKEN);
    }

    @Test
    @DisplayName("Al conectar se guardan el nombre y el email de la cuenta")
    void connectingStoresTheAccountNameAndEmail() {
        when(client.fetchAccount("APP_USR-access"))
                .thenReturn(new AccountResponse("JUANPEREZ", "Juan", "Pérez", "juan@example.com"));

        assertThat(connect()).isTrue();

        Tenant saved = reload();
        assertThat(saved.getMpAccountName()).isEqualTo("Juan Pérez");
        assertThat(saved.getMpAccountEmail()).isEqualTo("juan@example.com");
        assertThat(saved.getMpUserId()).isEqualTo("123456789");
    }

    @Test
    @DisplayName("Sin nombre y apellido cargados, se usa el apodo de MercadoPago")
    void fallsBackToTheNickname() {
        when(client.fetchAccount("APP_USR-access"))
                .thenReturn(new AccountResponse("CLUBSIMON", " ", null, null));

        connect();

        assertThat(reload().getMpAccountName()).isEqualTo("CLUBSIMON");
        assertThat(reload().getMpAccountEmail()).isNull();
    }

    @Test
    @DisplayName("Si MercadoPago no devuelve la cuenta, la conexion se completa igual")
    void aFailedLookupDoesNotBreakTheConnection() {
        when(client.fetchAccount(anyString()))
                .thenThrow(new PaymentGatewayException("MercadoPago no devolvio los datos de la cuenta"));

        assertThat(connect()).isTrue();

        Tenant saved = reload();
        assertThat(saved.acceptsOnlinePayments()).isTrue();
        assertThat(saved.getMpAccountName()).isNull();
    }

    @Test
    @DisplayName("Una conexion vieja completa la cuenta sin guardar cambios a medio editar")
    void loadMissingAccountOnlyTouchesTheAccountColumns() {
        when(client.fetchAccount(anyString()))
                .thenThrow(new PaymentGatewayException("caida"));
        connect();
        // doReturn y no when(...): when() llamaria al stub de arriba, que tira.
        doReturn(new AccountResponse(null, "Juan", "Pérez", "juan@example.com"))
                .when(client).fetchAccount(anyString());

        // Asi la tiene Configuracion: cargada al abrir la pantalla, con un cambio
        // en otra pestana que el dueno todavia no guardo.
        Tenant enPantalla = reload();
        enPantalla.setTagline("sin guardar");

        oauthService.loadMissingAccount(enPantalla);

        assertThat(enPantalla.getMpAccountName()).isEqualTo("Juan Pérez");
        Tenant saved = reload();
        assertThat(saved.getMpAccountName()).isEqualTo("Juan Pérez");
        assertThat(saved.getMpAccountEmail()).isEqualTo("juan@example.com");
        assertThat(saved.getTagline()).isNull();
    }

    @Test
    @DisplayName("Si la cuenta ya tiene nombre, no se le vuelve a preguntar a MercadoPago")
    void loadMissingAccountSkipsWhenAlreadyKnown() {
        when(client.fetchAccount(anyString()))
                .thenReturn(new AccountResponse(null, "Juan", "Pérez", "juan@example.com"));
        connect();
        Tenant saved = reload();

        clearInvocations(client);
        oauthService.loadMissingAccount(saved);

        verify(client, never()).fetchAccount(any());
    }

    @Test
    @DisplayName("Desconectar borra tambien el nombre y el email")
    void disconnectingClearsTheAccount() {
        when(client.fetchAccount(anyString()))
                .thenReturn(new AccountResponse(null, "Juan", "Pérez", "juan@example.com"));
        connect();

        oauthService.disconnect(reload());

        Tenant saved = reload();
        assertThat(saved.getMpAccountName()).isNull();
        assertThat(saved.getMpAccountEmail()).isNull();
    }

    // ------------------------------------------------------------ utilidades

    /** Recorre el flujo real: arranca la autorizacion y vuelve con el state de la URL. */
    private boolean connect() {
        String url = oauthService.startAuthorization(club);
        Matcher state = Pattern.compile("[?&]state=([^&]+)").matcher(url);
        assertThat(state.find()).isTrue();
        return oauthService.completeAuthorization("code-de-prueba", state.group(1));
    }

    private Tenant reload() {
        return tenantRepository.findById(club.getId()).orElseThrow();
    }
}
