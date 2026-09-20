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
import ar.com.padelnec.gym.repository.GymSedeRepository;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymMembershipService.Sale;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ningun club ve ni toca lo del gimnasio de otro. Lo prueba desde el contexto
 * de un club sobre datos del otro, que es como fallaria de verdad.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class})
class GymTenantIsolationTest {

    @Autowired private ClubFixture clubFixture;
    @Autowired private GymFixture gym;
    @Autowired private GymMemberRepository memberRepository;
    @Autowired private GymSedeRepository sedeRepository;
    @Autowired private GymMembershipRepository membershipRepository;
    @Autowired private GymMemberService memberService;
    @Autowired private GymMembershipService membershipService;
    @Autowired private GymOverviewService overviewService;

    private Tenant troncos;
    private Tenant necochea;
    private GymSede necocheaSede;
    private CreatedMember necocheaMember;

    @BeforeEach
    void setUp() {
        clubFixture.reset();
        troncos = clubFixture.club("los-troncos");
        necochea = clubFixture.club("club-necochea");
        gym.enable(troncos);
        gym.enable(necochea);

        GymSede troncosSede = gym.sede(troncos, "Troncos");
        CreatedMember troncosMember = gym.member(troncos, "30111222", "Socio de Troncos");
        LocalDate today = LocalDate.now();
        gym.sell(troncos, troncosMember.id(), today, today.plusDays(29), 3, troncosSede);

        necocheaSede = gym.sede(necochea, "Necochea");
        necocheaMember = gym.member(necochea, "30111222", "Socio de Necochea");
        TenantContext.set(troncos.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Desde un club no se ven las sedes, los socios ni las cuotas del otro")
    void listingsOnlyShowTheCurrentClub() {
        assertThat(sedeRepository.findAll()).extracting(GymSede::getName).containsExactly("Troncos");
        assertThat(memberRepository.findAll()).extracting("fullName").containsExactly("Socio de Troncos");
        assertThat(membershipRepository.findAll()).hasSize(1);
        assertThat(overviewService.members()).extracting("fullName").containsExactly("Socio de Troncos");
    }

    @Test
    @DisplayName("El mismo DNI en dos clubes son dos socios distintos: se busca solo en el club actual")
    void theSameDniIsADifferentMemberInEachClub() {
        assertThat(memberRepository.findByDni("30111222")).get()
                .extracting("fullName").isEqualTo("Socio de Troncos");

        TenantContext.set(necochea.getId());

        assertThat(memberRepository.findByDni("30111222")).get()
                .extracting("fullName").isEqualTo("Socio de Necochea");
    }

    @Test
    @DisplayName("Buscar por id un socio o una sede de otro club no encuentra nada")
    void lookupsByIdDoNotCrossClubs() {
        assertThat(memberRepository.findById(necocheaMember.id())).isEmpty();
        assertThat(sedeRepository.findById(necocheaSede.getId())).isEmpty();
        assertThat(sedeRepository.findByQrTokenAndActiveTrue(necocheaSede.getQrToken())).isEmpty();
    }

    @Test
    @DisplayName("No se puede operar sobre un socio de otro club: ni deshabilitarlo ni resetearle la clave")
    void servicesCannotTouchAnotherClubsMember() {
        assertThatThrownBy(() -> memberService.setEnabled(necocheaMember.id(), false))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> memberService.resetPassword(necocheaMember.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("No se le puede cobrar una cuota a un socio de otro club, ni en una sede ajena")
    void cannotSellAcrossClubs() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> membershipService.sell(new Sale(necocheaMember.id(), today, today.plusDays(29), 3,
                new BigDecimal("30000"), PayMethod.CASH, necocheaSede.getId(), Set.of(necocheaSede.getId()), null)))
                .isInstanceOf(ResourceNotFoundException.class);

        // Un socio propio, pero con una sede del otro club.
        GymSede ownSede = sedeRepository.findAll().getFirst();
        CreatedMember ownNewMember = memberService.create("50333444", "Otro socio", null);
        assertThatThrownBy(() -> membershipService.sell(new Sale(ownNewMember.id(), today, today.plusDays(29), 3,
                new BigDecimal("30000"), PayMethod.CASH, ownSede.getId(), Set.of(necocheaSede.getId()), null)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
