package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.security.ClubUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** Login del panel: entra con el nombre de usuario o con el mail, lo que sea mas comodo. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class ClubUserDetailsServiceTest {

    @Autowired private ClubUserDetailsService detailsService;
    @Autowired private ClubUserRepository clubUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClubFixture fixture;

    @BeforeEach
    void setUp() {
        fixture.reset();
    }

    private ClubUser seedOwner(Tenant club, String fullName, String email) {
        ClubUser owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setFullName(fullName);
        owner.setEmail(email);
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash(passwordEncoder.encode("unaClaveLarga123"));
        return clubUserRepository.saveAndFlush(owner);
    }

    @Test
    @DisplayName("Entra tipeando el nombre de usuario")
    void findsUserByFullName() {
        Tenant club = fixture.club("club-necochea");
        seedOwner(club, "Juana Dueña", "juana@clubnecochea.test");

        UserDetails found = detailsService.loadUserByUsername("Juana Dueña");

        assertThat(found.getUsername()).isEqualTo("juana@clubnecochea.test");
    }

    @Test
    @DisplayName("El nombre de usuario no distingue mayusculas")
    void fullNameLookupIsCaseInsensitive() {
        Tenant club = fixture.club("club-necochea");
        seedOwner(club, "Juana Dueña", "juana@clubnecochea.test");

        assertThat(detailsService.loadUserByUsername("JUANA DUEÑA")).isNotNull();
    }

    @Test
    @DisplayName("Entra tipeando el mail, como antes")
    void findsUserByEmail() {
        Tenant club = fixture.club("club-necochea");
        seedOwner(club, "Juana Dueña", "juana@clubnecochea.test");

        UserDetails found = detailsService.loadUserByUsername("juana@clubnecochea.test");

        assertThat(found.getUsername()).isEqualTo("juana@clubnecochea.test");
    }

    @Test
    @DisplayName("Un usuario o mail que no existe no deja entrar")
    void rejectsUnknownIdentifier() {
        assertThatThrownBy(() -> detailsService.loadUserByUsername("no-existe"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
