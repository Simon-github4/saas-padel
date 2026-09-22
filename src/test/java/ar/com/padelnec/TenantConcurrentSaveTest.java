package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.payment.MercadoPagoOAuthClient;
import ar.com.padelnec.payment.MercadoPagoOAuthClient.AccountResponse;
import ar.com.padelnec.payment.MercadoPagoOAuthClient.TokenResponse;
import ar.com.padelnec.payment.MercadoPagoOAuthService;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.TenantService;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Configuracion carga el club al abrir la pantalla y lo guarda por pestanas.
 * Guardar esa copia entera pisaba todo lo que habia cambiado mientras tanto:
 * lo de otra pestana, lo del otro dueno, y los tokens que el job de
 * renovacion de MercadoPago cambia a la madrugada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class TenantConcurrentSaveTest {

    @Autowired private ClubFixture fixture;
    @Autowired private TenantService tenantService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private MercadoPagoOAuthService oauthService;
    @MockitoBean private MercadoPagoOAuthClient client;

    private Tenant club;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-version");
    }

    @Test
    @DisplayName("Una pantalla abierta desde antes de la renovacion no vuelve a escribir los tokens viejos")
    void savingSettingsKeepsTokensRenewedMeanwhile() {
        connect("APP_USR-viejo", "TG-viejo");
        // Asi queda Configuracion: abierta con los tokens de ese momento.
        Tenant enPantalla = reload();

        // A la madrugada, el job los renueva.
        when(client.refresh("TG-viejo"))
                .thenReturn(new TokenResponse("APP_USR-nuevo", "TG-nuevo", 123L, 15_552_000L));
        oauthService.refresh(reload());

        // A la manana el dueno cambia el horario desde esa misma pantalla.
        tenantService.update(enPantalla.getId(), c -> c.setMaxActiveBookings(5));

        Tenant saved = reload();
        assertThat(saved.getMaxActiveBookings()).isEqualTo(5);
        assertThat(saved.getMpAccessToken()).isEqualTo("APP_USR-nuevo");
        assertThat(saved.getMpRefreshToken()).isEqualTo("TG-nuevo");
    }

    @Test
    @DisplayName("Guardar una pestana no deshace lo que otra guardo mientras tanto")
    void savingOneTabKeepsWhatAnotherTabSaved() {
        tenantService.update(club.getId(), c -> c.setTagline("Jugá al lado del mar"));
        tenantService.update(club.getId(), c -> c.setMaxActiveBookings(5));

        Tenant saved = reload();
        assertThat(saved.getTagline()).isEqualTo("Jugá al lado del mar");
        assertThat(saved.getMaxActiveBookings()).isEqualTo(5);
    }

    @Test
    @DisplayName("Guardar entera una copia vieja del club falla en vez de pisar")
    void savingAStaleCopyFails() {
        Tenant vieja = reload();
        tenantService.update(club.getId(), c -> c.setTagline("guardado despues"));

        vieja.setMaxActiveBookings(5);
        assertThatThrownBy(() -> tenantRepository.saveAndFlush(vieja))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(reload().getTagline()).isEqualTo("guardado despues");
    }

    @Test
    @DisplayName("Si otro guardado entra entre la lectura y la escritura, update falla en vez de pisarlo")
    void updateDetectsASaveThatSneaksIn() {
        assertThatThrownBy(() -> tenantService.update(club.getId(), c -> {
            // Otro guardado, en otra transaccion, justo despues de que update leyo.
            CompletableFuture.runAsync(() ->
                    tenantService.update(club.getId(), other -> other.setTagline("el otro"))).join();
            c.setMaxActiveBookings(5);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        Tenant saved = reload();
        assertThat(saved.getTagline()).isEqualTo("el otro");
        assertThat(saved.getMaxActiveBookings()).isNotEqualTo(5);
    }

    // ------------------------------------------------------------ utilidades

    private void connect(String accessToken, String refreshToken) {
        when(client.exchangeCode(anyString(), anyString(), anyString()))
                .thenReturn(new TokenResponse(accessToken, refreshToken, 123L, 15_552_000L));
        when(client.fetchAccount(anyString()))
                .thenReturn(new AccountResponse(null, "Juan", "Pérez", "juan@example.com"));
        String url = oauthService.startAuthorization(club);
        Matcher state = Pattern.compile("[?&]state=([^&]+)").matcher(url);
        assertThat(state.find()).isTrue();
        assertThat(oauthService.completeAuthorization("code", state.group(1))).isTrue();
    }

    private Tenant reload() {
        return tenantRepository.findById(club.getId()).orElseThrow();
    }
}
