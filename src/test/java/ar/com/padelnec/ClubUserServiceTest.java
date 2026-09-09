package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.service.ClubUserService;
import ar.com.padelnec.web.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** Alta y manejo del usuario de mostrador: un solo rol mas, sin configuracion ni estadisticas. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class ClubUserServiceTest {

    @Autowired private ClubUserService clubUserService;
    @Autowired private ClubUserRepository clubUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubFixture fixture;

    private Tenant club;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Crea el usuario de mostrador con la contraseña cifrada")
    void createsStaffUser() {
        ClubUser staff = clubUserService.createStaff(
                club, "Ana Mostrador", "ana@clubnecochea.test", "unaClaveLarga123");

        assertThat(staff.getRole()).isEqualTo(UserRole.STAFF);
        assertThat(staff.getClubId()).isEqualTo(club.getId());
        assertThat(staff.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("unaClaveLarga123", staff.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Un club no puede tener dos usuarios de mostrador")
    void rejectsASecondStaffUser() {
        clubUserService.createStaff(club, "Ana", "ana@clubnecochea.test", "unaClaveLarga123");

        assertThatThrownBy(() ->
                clubUserService.createStaff(club, "Beto", "beto@clubnecochea.test", "otraClaveLarga1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("El mail de mostrador no puede repetir uno ya usado en otro club")
    void rejectsADuplicateEmail() {
        clubUserService.createStaff(club, "Ana", "ana@clubnecochea.test", "unaClaveLarga123");
        Tenant otherClub = fixture.club("otro-club");

        assertThatThrownBy(() -> clubUserService.createStaff(
                otherClub, "Ana en otro club", "ana@clubnecochea.test", "otraClaveLarga1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("La contraseña tiene que tener un largo mínimo")
    void rejectsAShortPassword() {
        assertThatThrownBy(() -> clubUserService.createStaff(club, "Ana", "ana@clubnecochea.test", "corta"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Deshabilitar el acceso queda guardado")
    void disablesAccess() {
        ClubUser staff = clubUserService.createStaff(
                club, "Ana", "ana@clubnecochea.test", "unaClaveLarga123");

        clubUserService.setEnabled(club, staff.getId(), false);

        assertThat(clubUserRepository.findById(staff.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Cambiar la contraseña la vuelve a cifrar")
    void changesPassword() {
        ClubUser staff = clubUserService.createStaff(
                club, "Ana", "ana@clubnecochea.test", "unaClaveLarga123");

        clubUserService.setPassword(club, staff.getId(), "otraClaveLarga1");

        ClubUser reloaded = clubUserRepository.findById(staff.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("otraClaveLarga1", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("unaClaveLarga123", reloaded.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("Sin usuario de mostrador, findStaff no encuentra nada")
    void findStaffIsEmptyWhenNoneExists() {
        assertThat(clubUserService.findStaff(club.getId())).isEmpty();
    }

    @Test
    @DisplayName("setPassword sobre un id inexistente falla con mensaje entendible")
    void setPasswordFailsForUnknownUser() {
        assertThatThrownBy(() -> clubUserService.setPassword(club, UUID.randomUUID(), "unaClaveLarga123"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Un club no puede tocar el usuario de mostrador de otro club")
    void cannotMutateAnotherClubsStaff() {
        ClubUser staff = clubUserService.createStaff(
                club, "Ana", "ana@clubnecochea.test", "unaClaveLarga123");
        Tenant otherClub = fixture.club("otro-club");

        assertThatThrownBy(() -> clubUserService.setPassword(otherClub, staff.getId(), "otraClaveLarga1"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> clubUserService.setEnabled(otherClub, staff.getId(), false))
                .isInstanceOf(BusinessRuleException.class);

        // Ni la contraseña ni el estado deberian haber cambiado.
        ClubUser reloaded = clubUserRepository.findById(staff.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("unaClaveLarga123", reloaded.getPasswordHash())).isTrue();
    }
}
