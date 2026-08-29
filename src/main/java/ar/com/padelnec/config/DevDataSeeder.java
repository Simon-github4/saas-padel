package ar.com.padelnec.config;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga un club de ejemplo para poder trabajar apenas arranca la aplicacion.
 *
 * <p>Solo bajo el perfil {@code dev}: en produccion los clubes se dan de alta desde
 * el panel de plataforma, y una contrasena conocida en el codigo seria un agujero.
 */
@Configuration
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder {

    private static final String SLUG = "club-necochea";

    @Bean
    public ApplicationRunner seedDemoClub(TenantRepository tenantRepository,
                                          CourtRepository courtRepository,
                                          PricingRuleRepository pricingRuleRepository,
                                          ClubUserRepository clubUserRepository,
                                          PasswordEncoder passwordEncoder) {
        return args -> seed(tenantRepository, courtRepository, pricingRuleRepository,
                clubUserRepository, passwordEncoder);
    }

    @Transactional
    void seed(TenantRepository tenantRepository, CourtRepository courtRepository,
              PricingRuleRepository pricingRuleRepository, ClubUserRepository clubUserRepository,
              PasswordEncoder passwordEncoder) {

        if (tenantRepository.existsBySlugIgnoreCase(SLUG)) {
            log.info("El club de ejemplo ya existe, no se vuelve a cargar");
            return;
        }

        Tenant club = new Tenant();
        club.setName("Pádel Necochea");
        club.setSlug(SLUG);
        club.setWhatsappNumber("+542262400000");
        club.setOpenTime(LocalTime.of(8, 0));
        club.setCloseTime(LocalTime.of(23, 30));
        club.setDefaultSlotDuration(90);
        club.setCancellationLimitHours(12);
        club.setDepositPercentage(new BigDecimal("50.00"));
        club.setAllowUnpaidBooking(true);
        club = tenantRepository.saveAndFlush(club);

        // A partir de aca todo lleva club_id: hay que declarar el tenant.
        TenantContext.set(club.getId());
        try {
            Court cancha1 = court(courtRepository, "Cancha 1 (techada)", 1);
            court(courtRepository, "Cancha 2", 2);
            court(courtRepository, "Cancha 3", 3);

            // Tarifa base de lunes a domingo, con recargo en el horario pico de la
            // tarde-noche, que es como cobra la mayoria de los clubes.
            for (DayOfWeek day : DayOfWeek.values()) {
                price(pricingRuleRepository, null, day,
                        LocalTime.of(0, 0), LocalTime.of(18, 0), "18000");
                price(pricingRuleRepository, null, day,
                        LocalTime.of(18, 0), LocalTime.of(23, 59), "24000");
            }
            // La techada vale mas en el horario pico.
            price(pricingRuleRepository, cancha1, DayOfWeek.SATURDAY,
                    LocalTime.of(18, 0), LocalTime.of(23, 59), "28000");

            ClubUser owner = new ClubUser();
            owner.setClubId(club.getId());
            owner.setEmail("dueno@clubnecochea.test");
            owner.setFullName("Dueño del club");
            owner.setRole(UserRole.OWNER);
            owner.setPasswordHash(passwordEncoder.encode("padel1234"));
            clubUserRepository.save(owner);

            log.info("""

                    +--- Club de ejemplo cargado -------------------------------
                    | Grilla:  /api/public/{}/availability?date=YYYY-MM-DD
                    | Panel:   usuario dueno@clubnecochea.test / clave padel1234
                    +-----------------------------------------------------------""", SLUG);
        } finally {
            TenantContext.clear();
        }
    }

    private Court court(CourtRepository repository, String name, int order) {
        Court court = new Court();
        court.setName(name);
        court.setDisplayOrder(order);
        return repository.saveAndFlush(court);
    }

    private void price(PricingRuleRepository repository, Court court, DayOfWeek day,
                       LocalTime from, LocalTime to, String amount) {
        PricingRule rule = new PricingRule();
        rule.setCourt(court);
        rule.setDay(day);
        rule.setStartTime(from);
        rule.setEndTime(to);
        rule.setPrice(new BigDecimal(amount));
        repository.saveAndFlush(rule);
    }
}
