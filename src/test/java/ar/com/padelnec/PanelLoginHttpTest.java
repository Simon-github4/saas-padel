package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.PanelLogin;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.PanelLoginRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * El formulario de login del panel, por HTTP: que el ingreso quede anotado con
 * la IP del usuario que llega en {@code X-Forwarded-For} (la del proxy de Render
 * no le dice nada a nadie).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class PanelLoginHttpTest {

    private static final String PASSWORD = "unaClaveLarga123";

    @LocalServerPort private int port;
    @Autowired private ClubFixture fixture;
    @Autowired private ClubUserRepository clubUserRepository;
    @Autowired private PanelLoginRepository panelLoginRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Entrar por el formulario anota el ingreso con la IP real del usuario")
    void formLoginIsRecorded() {
        fixture.reset();
        Tenant club = fixture.club("club-necochea");
        ClubUser owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setEmail("dueno-%s@test.com".formatted(UUID.randomUUID()));
        owner.setFullName("Dueño " + UUID.randomUUID());
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash(passwordEncoder.encode(PASSWORD));
        owner = clubUserRepository.saveAndFlush(owner);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", owner.getEmail());
        form.add("password", PASSWORD);
        RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .post().uri("/admin/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140.0")
                .header("X-Forwarded-For", "181.45.10.20")
                .body(form)
                .exchange();

        UUID ownerId = owner.getId();
        assertThat(panelLoginRepository.findAll())
                .filteredOn(login -> ownerId.equals(login.getUserId()))
                .singleElement()
                .satisfies(login -> {
                    assertThat(login.getResult()).isEqualTo(PanelLogin.OK);
                    assertThat(login.getClubId()).isEqualTo(club.getId());
                    assertThat(login.getIp()).isEqualTo("181.45.10.20");
                    assertThat(login.getDevice()).isEqualTo("desktop");
                });
    }
}
