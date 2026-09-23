package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.service.GymAuthService;
import ar.com.padelnec.gym.service.GymAuthService.IssuedSession;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.UnauthorizedSessionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Alta y edicion de socios: el DNI es opcional al darlo de alta (los socios anotados
 * sin DNI se cargan igual) y se completa o corrige despues.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class})
class GymMemberServiceTest {

    @Autowired private GymMemberService memberService;
    @Autowired private GymMemberRepository memberRepository;
    @Autowired private GymAuthService authService;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;

    @BeforeEach
    void setUp() {
        clubFixture.reset();
        Tenant club = clubFixture.club("los-troncos");
        gym.enable(club);
        TenantContext.set(club.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Un socio se puede dar de alta sin DNI, y varios en el mismo club")
    void membersCanBeCreatedWithoutDni() {
        CreatedMember ana = memberService.create("  ", "Ana Gómez", null);
        CreatedMember beto = memberService.create(null, "Beto Ruiz", null);

        assertThat(ana.dni()).isNull();
        assertThat(memberRepository.findById(ana.id()).orElseThrow().getDni()).isNull();
        assertThat(memberRepository.findById(beto.id()).orElseThrow().getDni()).isNull();
    }

    @Test
    @DisplayName("El DNI se carga despues, y recien ahi el socio entra a la app con el")
    void theDniCanBeAddedLater() {
        CreatedMember ana = memberService.create(null, "Ana Gómez", null);

        memberService.updateDetails(ana.id(), "30.111.222", "Ana Gómez", "2262 123456");

        assertThat(memberRepository.findById(ana.id()).orElseThrow().getDni()).isEqualTo("30111222");
        assertThat(authService.login("30111222", null).fullName()).isEqualTo("Ana Gómez");
    }

    @Test
    @DisplayName("El DNI de otro socio del club no se puede repetir, ni en el alta ni al editar")
    void theDniIsStillUnique() {
        memberService.create("30111222", "Ana Gómez", null);
        CreatedMember beto = memberService.create(null, "Beto Ruiz", null);

        assertThatThrownBy(() -> memberService.create("30111222", "Otra Ana", null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> memberService.updateDetails(beto.id(), "30111222", "Beto Ruiz", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya hay un socio con ese DNI");
    }

    @Test
    @DisplayName("Un DNI mal cargado se rechaza; guardar el mismo DNI de nuevo no es un duplicado")
    void aBadDniIsRejectedAndTheSameDniIsNotADuplicate() {
        CreatedMember ana = memberService.create("30111222", "Ana Gómez", null);

        assertThatThrownBy(() -> memberService.updateDetails(ana.id(), "123", "Ana Gómez", null))
                .isInstanceOf(BusinessRuleException.class);

        memberService.updateDetails(ana.id(), "30111222", "Ana María Gómez", null);
        assertThat(memberRepository.findById(ana.id()).orElseThrow().getFullName()).isEqualTo("Ana María Gómez");
    }

    @Test
    @DisplayName("Si cambia el DNI se cierran las sesiones abiertas; si no cambia, siguen")
    void changingTheDniClosesOpenSessions() {
        CreatedMember ana = memberService.create("30111222", "Ana Gómez", null);
        IssuedSession session = authService.login("30111222", null);

        memberService.updateDetails(ana.id(), "30111222", "Ana María Gómez", null);
        assertThat(authService.requireMember(session.token()).getId()).isEqualTo(ana.id());

        memberService.updateDetails(ana.id(), "30111223", "Ana María Gómez", null);
        assertThatThrownBy(() -> authService.requireMember(session.token()))
                .isInstanceOf(UnauthorizedSessionException.class);
    }
}
