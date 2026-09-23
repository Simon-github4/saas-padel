package ar.com.padelnec.gym;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymClubConfig;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymClubConfigRepository;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymMembershipService.Sale;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymSedeService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Prende el gimnasio en el club de ejemplo de desarrollo y le carga dos sedes y tres
 * socios, para poder probar la app y el panel apenas arranca.
 *
 * <p>Solo bajo el perfil {@code dev}, y dentro de este paquete (no en el
 * {@code DevDataSeeder} de padel) para que el modulo siga sin tocar nada de afuera.
 * Es idempotente: si el club ya tiene el modulo prendido, no toca nada.
 *
 * <p>El club entra solo con el DNI (lo normal). Los tres socios tienen ademas la clave
 * {@value #DEV_PASSWORD}, que solo cuenta si se prende el modo "DNI y clave": temporal como
 * la de un alta real, la primera vez la app pide cambiarla.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
class GymDevSeeder {

    /** El club de la documentacion, el mismo que siembra DevDataSeeder. */
    private static final String CLUB_SLUG = "club-necochea";
    static final String DEV_PASSWORD = "gimnasio1234";

    private final TenantService tenantService;
    private final GymModule gymModule;
    private final GymClubConfigRepository configRepository;
    private final GymSedeService sedeService;
    private final GymMemberRepository memberRepository;
    private final GymMembershipService membershipService;
    private final GymOverviewService overviewService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Cuando la aplicacion ya termino de arrancar, no como un {@code ApplicationRunner}: el club de
     * ejemplo lo crea el {@code DevDataSeeder} de padel, que es otro runner, y el orden entre dos
     * runners sin {@code @Order} no esta garantizado. Este evento llega despues de todos.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedWhenReady() {
        Tenant club;
        try {
            club = tenantService.activate(CLUB_SLUG);
        } catch (ResourceNotFoundException ex) {
            log.info("Gimnasio de ejemplo: no esta el club {}, no se siembra nada", CLUB_SLUG);
            return;
        }
        try {
            TenantContext.runAs(club.getId(), this::seed);
        } finally {
            TenantContext.clear();
        }
    }

    private void seed() {
        if (gymModule.isEnabled()) {
            return;
        }
        configRepository.save(new GymClubConfig());

        GymSede necochea = sedeService.create("Necochea", "Calle 62 Nº 2500", BigDecimal.ZERO);
        GymSede quequen = sedeService.create("Quequén", "Av. Costanera 800", new BigDecimal("50"));
        LocalDate today = overviewService.today();

        GymMember ana = member("30111222", "Ana Gómez");
        GymMember beto = member("40222333", "Beto Ruiz");
        member("50333444", "Carla Díaz");

        // Ana: cuota vigente, 3 dias por semana, en las dos sedes.
        sell(ana, today.minusDays(5), today.plusDays(24), 3, necochea, quequen);
        // Beto: la cuota le vencio hace una semana.
        sell(beto, today.minusDays(37), today.minusDays(7), 2, necochea);
        // Carla no tiene ninguna cuota.

        log.info("Gimnasio de ejemplo listo en {}: socios 30111222 (vigente), 40222333 (vencida) y "
                + "50333444 (sin cuota); entran con el DNI (con clave, seria {})", CLUB_SLUG, DEV_PASSWORD);
    }

    private GymMember member(String dni, String name) {
        GymMember member = new GymMember();
        member.setDni(dni);
        member.setFullName(name);
        member.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
        member.setMustChangePassword(true);
        return memberRepository.save(member);
    }

    private void sell(GymMember member, LocalDate from, LocalDate to, int daysPerWeek, GymSede... sedes) {
        Set<java.util.UUID> sedeIds = new java.util.HashSet<>();
        for (GymSede sede : sedes) {
            sedeIds.add(sede.getId());
        }
        membershipService.sell(new Sale(member.getId(), from, to, daysPerWeek, new BigDecimal("30000"),
                PayMethod.CASH, sedes[0].getId(), sedeIds, null, null));
    }
}
