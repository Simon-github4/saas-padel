package ar.com.padelnec.gym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.ClubFixture;
import ar.com.padelnec.TestDatabaseConfig;
import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.gym.service.GymAuthService;
import ar.com.padelnec.gym.service.GymAuthService.IssuedSession;
import ar.com.padelnec.gym.service.GymCheckinService;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymMemberService.DeletionImpact;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Alta, edicion y baja de socios: el DNI es opcional al darlo de alta (los socios
 * anotados sin DNI se cargan igual) y se completa o corrige despues; el socio cargado
 * por error se elimina, salvo que ya tenga ingresos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class})
class GymMemberServiceTest {

    @Autowired private GymMemberService memberService;
    @Autowired private GymMemberRepository memberRepository;
    @Autowired private GymAuthService authService;
    @Autowired private GymMembershipRepository membershipRepository;
    @Autowired private GymCheckinService checkinService;
    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;

    private Tenant club;

    @BeforeEach
    void setUp() {
        clubFixture.reset();
        club = clubFixture.club("los-troncos");
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

    @Test
    @DisplayName("Un socio repetido, sin ingresos, se elimina con sus cuotas y sus sesiones")
    void aMemberWithoutCheckinsCanBeDeleted() {
        GymSede sede = gym.sede(club, "Los Troncos Gym");
        CreatedMember ana = memberService.create("30111222", "Ana Gómez", null);
        LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        gym.sell(club, ana.id(), today.minusDays(1), today.plusDays(29), 3, sede);
        IssuedSession session = authService.login("30111222", null);

        memberService.delete(ana.id());

        assertThat(memberRepository.findById(ana.id())).isEmpty();
        assertThat(membershipRepository.findAllByMemberId(ana.id())).isEmpty();
        assertThatThrownBy(() -> authService.requireMember(session.token()))
                .isInstanceOf(UnauthorizedSessionException.class);
    }

    @Test
    @DisplayName("Un socio que ya registro ingresos no se elimina: se deshabilita")
    void aMemberWithCheckinsCannotBeDeleted() {
        GymSede sede = gym.sede(club, "Los Troncos Gym");
        CreatedMember ana = memberService.create("30111222", "Ana Gómez", null);
        LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        gym.sell(club, ana.id(), today.minusDays(1), today.plusDays(29), 3, sede);
        checkinService.forceCheckIn(ana.id(), sede.getId(), null);

        assertThatThrownBy(() -> memberService.delete(ana.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Deshabilitalo");
        assertThat(memberRepository.findById(ana.id())).isPresent();
    }

    @Test
    @DisplayName("Antes de eliminar se ve lo que sale de la caja: cuotas vigentes, total y dias")
    void theDeletionImpactShowsWhatLeavesTheCash() {
        GymSede sede = gym.sede(club, "Los Troncos Gym");
        CreatedMember ana = memberService.create(null, "Ana Gómez", null);
        LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        gym.sell(club, ana.id(), today.minusDays(1), today.plusDays(29), 3, sede);
        gym.sell(club, ana.id(), today.plusDays(30), today.plusDays(59), 3, sede);

        DeletionImpact impact = memberService.deletionImpact(ana.id());

        assertThat(impact.hasCheckins()).isFalse();
        assertThat(impact.fees()).isEqualTo(2);
        assertThat(impact.total()).isEqualByComparingTo("60000");
        assertThat(impact.paidDays()).containsExactly(today);
    }
}
