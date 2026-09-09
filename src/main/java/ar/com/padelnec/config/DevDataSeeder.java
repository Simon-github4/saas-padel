package ar.com.padelnec.config;

import ar.com.padelnec.domain.ClubAmenity;
import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.HeroVariant;
import ar.com.padelnec.domain.enums.ThemeMode;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubAmenityRepository;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga clubes de ejemplo para poder trabajar apenas arranca la aplicacion.
 *
 * <p>Son tres y no uno porque el producto es multi-tenant: con un solo club no se
 * puede mirar la busqueda global, ni notar que un club se lleve puesta la agenda de
 * otro. Se diferencian entre si a proposito —horarios, duracion del turno, precios y
 * paleta— para que las pantallas que los cruzan muestren algo parecido a la realidad.
 *
 * <p>Solo bajo el perfil {@code dev}: en produccion los clubes se dan de alta desde
 * el panel de plataforma, y una contrasena conocida en el codigo seria un agujero.
 *
 * <p>Se siembra una unica vez: si el club de la documentacion ya existe, no se
 * toca nada. La base embebida ({@link EmbeddedPostgresConfig}) persiste entre
 * arranques, asi que borrar y recargar en cada uno se llevaria puesto cualquier
 * dato cargado a mano. Para volver a ver los clubes de ejemplo desde cero, resetea
 * la base (ver {@link EmbeddedPostgresConfig}) en vez de reiniciar la app.
 */
@Configuration
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder {

    /** El club de la documentacion: el que aparece en el README. */
    private static final String SLUG = "club-necochea";

    private static final DayOfWeek[] LABORABLES = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
    private static final DayOfWeek[] FINDE = {DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};

    /** Las dependencias juntas, para no arrastrar siete parametros por cada club. */
    private record Repos(TenantRepository tenants, CourtRepository courts,
                         PricingRuleRepository prices, ClubUserRepository users,
                         ClubAmenityRepository amenities, ProductRepository products,
                         PasswordEncoder passwordEncoder) {
    }

    @Bean
    public ApplicationRunner seedDemoClub(TenantRepository tenantRepository,
                                          CourtRepository courtRepository,
                                          PricingRuleRepository pricingRuleRepository,
                                          ClubUserRepository clubUserRepository,
                                          ClubAmenityRepository amenityRepository,
                                          ProductRepository productRepository,
                                          PasswordEncoder passwordEncoder) {
        Repos repos = new Repos(tenantRepository, courtRepository, pricingRuleRepository,
                clubUserRepository, amenityRepository, productRepository, passwordEncoder);
        return args -> seed(repos);
    }

    @Transactional
    void seed(Repos repos) {
        if (repos.tenants().findBySlugIgnoreCase(SLUG).isPresent()) {
            log.info("Los clubes de ejemplo ya existen, no se vuelven a cargar");
            return;
        }

        seedNecochea(repos);
        seedCostaVerde(repos);
        seedElMuelle(repos);

        log.info("""

                +--- Clubes de ejemplo cargados ----------------------------
                | Busqueda global: /buscar
                | Grilla:          /club/{}
                | Panel (dueño):     dueno@clubnecochea.test / padel1234
                | Panel (mostrador): mostrador@clubnecochea.test / padel1234
                +-----------------------------------------------------------""", SLUG);
    }

    /** El club grande: turnos de 90 minutos, tres canchas y una techada mas cara. */
    private void seedNecochea(Repos repos) {
        Tenant club = new Tenant();
        club.setName("Pádel Necochea");
        club.setSlug(SLUG);
        club.setWhatsappNumber("+542262400000");
        club.setTagline("Reservá tu cancha y jugá al lado del mar");
        club.setAddress("Av. 59 nº 440");
        club.setCity("Necochea, Buenos Aires");
        club.setLatitude(new BigDecimal("-38.554069"));
        club.setLongitude(new BigDecimal("-58.748572"));
        club.setHeroImageUrl("https://images.unsplash.com/photo-1622279457486-62dcc4a431d6?auto=format&fit=crop&w=1600&q=80");
        club.setHeroCtaLabel("Ver horarios");
        club.setHeroOverlay(55);
        club.setOpenTime(LocalTime.of(8, 0));
        club.setCloseTime(LocalTime.of(23, 30));
        club.setDefaultSlotDuration(90);
        club.setCancellationLimitHours(12);
        club.setDepositPercentage(new BigDecimal("50.00"));
        club.setAllowUnpaidBooking(true);
        // Tarifa general por persona: cae aca el turno que ninguna regla cubre.
        club.setGeneralPricePerPerson(new BigDecimal("6000"));

        seedClub(repos, club, "dueno@clubnecochea.test", saved -> {
            Court cancha1 = court(repos, "Cancha 1 (techada)", 1);
            court(repos, "Cancha 2", 2);
            court(repos, "Cancha 3", 3);

            amenity(repos, "court", "4 canchas", "3 descubiertas y 1 techada", 1);
            amenity(repos, "timer", "Turnos de 90 minutos", "Horarios fijos, sin esperas", 2);
            amenity(repos, "shower", "Duchas y vestuarios", "¿Y un café después de jugar?", 3);
            amenity(repos, "parking", "Estacionamiento propio", "Fácil de llegar con auto", 4);

            product(repos, "Gatorade", "2500");
            product(repos, "Agua", "1500");
            product(repos, "Alquiler de paleta", "3000");

            // Franjas multi-dia, cada una con su precio POR PERSONA y su promo.
            // La regla que gana es la mas especifica: general < franja < cancha.
            // Dia de semana: dia normal barato (sin promo) y noche normal.
            price(repos, null, LocalTime.of(0, 0), LocalTime.of(18, 0), "4500", false, LABORABLES);
            price(repos, null, LocalTime.of(18, 0), LocalTime.of(23, 59), "6000", false, LABORABLES);
            // Fin de semana: promo explicita que cobra el precio menor de la fecha.
            price(repos, null, LocalTime.of(0, 0), LocalTime.of(18, 0), "6500", true, FINDE);
            price(repos, null, LocalTime.of(18, 0), LocalTime.of(23, 59), "7500", true, FINDE);
            // La techada vale mas en el horario pico, todos los dias.
            price(repos, cancha1, LocalTime.of(18, 0), LocalTime.of(23, 59), "7000", false,
                    DayOfWeek.values());

            // Solo este club trae ademas un usuario de mostrador, para poder
            // probar el rol STAFF sin tener que darlo de alta a mano.
            staff(repos, saved, "mostrador@clubnecochea.test");
        });
    }

    /**
     * El club chico del otro lado del rio: turnos de 60 minutos y paleta clara.
     *
     * <p>La duracion distinta no es decorativa. Es lo que hace que en la busqueda
     * global los horarios de los clubes no caigan todos en la misma grilla, que es
     * exactamente lo que pasa en la calle.
     */
    private void seedCostaVerde(Repos repos) {
        Tenant club = new Tenant();
        club.setName("Costa Verde Pádel");
        club.setSlug("costa-verde");
        club.setWhatsappNumber("+542262400001");
        club.setTagline("Dos canchas, cero vueltas");
        club.setAddress("Calle 502 nº 1250");
        club.setCity("Quequén, Buenos Aires");
        club.setLatitude(new BigDecimal("-38.560278"));
        club.setLongitude(new BigDecimal("-58.700556"));
        club.setHeroImageUrl("https://images.unsplash.com/photo-1554068865-24cecd4e34b8?auto=format&fit=crop&w=1600&q=80");
        club.setHeroOverlay(45);
        club.setThemeMode(ThemeMode.LIGHT);
        club.setHeroVariant(HeroVariant.COURT_SPLIT);
        club.setOpenTime(LocalTime.of(9, 0));
        club.setCloseTime(LocalTime.of(23, 0));
        club.setDefaultSlotDuration(60);
        club.setCancellationLimitHours(6);
        club.setAllowUnpaidBooking(true);
        club.setGeneralPricePerPerson(new BigDecimal("4000"));

        seedClub(repos, club, "dueno@costaverde.test", saved -> {
            court(repos, "Cancha Roja", 1);
            court(repos, "Cancha Azul", 2);

            amenity(repos, "court", "2 canchas de blindex", "Piso sintético nuevo", 1);
            amenity(repos, "timer", "Turnos de 60 minutos", "Entrás y salís en hora", 2);
            amenity(repos, "racket", "Alquiler de paletas", "Vení sin nada y jugá igual", 3);

            price(repos, null, LocalTime.of(0, 0), LocalTime.of(17, 0), "3500", false, LABORABLES);
            price(repos, null, LocalTime.of(17, 0), LocalTime.of(23, 59), "4800", false, LABORABLES);
            price(repos, null, LocalTime.of(0, 0), LocalTime.of(23, 59), "5200", true, FINDE);
        });
    }

    /** El club caro del centro: abre temprano y cierra antes que los otros dos. */
    private void seedElMuelle(Repos repos) {
        Tenant club = new Tenant();
        club.setName("El Muelle Pádel");
        club.setSlug("el-muelle");
        club.setWhatsappNumber("+542262400002");
        club.setTagline("Cuatro canchas techadas en el centro");
        club.setAddress("Calle 83 nº 2100");
        club.setCity("Necochea, Buenos Aires");
        club.setLatitude(new BigDecimal("-38.547500"));
        club.setLongitude(new BigDecimal("-58.739167"));
        club.setHeroImageUrl("https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=1600&q=80");
        club.setHeroCtaLabel("Ver disponibilidad");
        club.setHeroOverlay(60);
        club.setHeroVariant(HeroVariant.SCOREBOARD);
        club.setOpenTime(LocalTime.of(7, 0));
        club.setCloseTime(LocalTime.of(22, 0));
        club.setDefaultSlotDuration(90);
        club.setCancellationLimitHours(24);
        club.setAllowUnpaidBooking(true);
        club.setGeneralPricePerPerson(new BigDecimal("7000"));

        seedClub(repos, club, "dueno@elmuelle.test", saved -> {
            court(repos, "Cancha 1", 1);
            court(repos, "Cancha 2", 2);
            court(repos, "Cancha 3", 3);
            Court central = court(repos, "Cancha Central", 4);

            amenity(repos, "court", "4 canchas techadas", "Se juega llueva o no", 1);
            amenity(repos, "shower", "Vestuarios con duchas", "Toallas incluidas", 2);
            amenity(repos, "parking", "Cochera cubierta", "A media cuadra", 3);

            price(repos, null, LocalTime.of(0, 0), LocalTime.of(16, 0), "5500", false,
                    DayOfWeek.values());
            price(repos, null, LocalTime.of(16, 0), LocalTime.of(23, 59), "8000", false,
                    DayOfWeek.values());
            // La central tiene tribuna: es la que se pelea el horario pico.
            price(repos, central, LocalTime.of(18, 0), LocalTime.of(23, 59), "9500", false,
                    DayOfWeek.values());
        });
    }

    /**
     * Guarda el club y carga sus datos con el tenant puesto.
     *
     * <p>Canchas, tarifas y servicios llevan {@code club_id}: sin el contexto, el
     * insert falla contra la clave foranea. Y se limpia al salir, porque el club
     * siguiente no puede heredar el de este.
     */
    private void seedClub(Repos repos, Tenant club, String ownerEmail, Consumer<Tenant> contents) {
        Tenant saved = repos.tenants().saveAndFlush(club);
        TenantContext.set(saved.getId());
        try {
            contents.accept(saved);
            owner(repos, saved, ownerEmail);
        } finally {
            TenantContext.clear();
        }
    }

    private void owner(Repos repos, Tenant club, String email) {
        ClubUser owner = new ClubUser();
        owner.setClubId(club.getId());
        owner.setEmail(email);
        owner.setFullName("Dueño de " + club.getName());
        owner.setRole(UserRole.OWNER);
        owner.setPasswordHash(repos.passwordEncoder().encode("padel1234"));
        repos.users().save(owner);
    }

    private void staff(Repos repos, Tenant club, String email) {
        ClubUser staff = new ClubUser();
        staff.setClubId(club.getId());
        staff.setEmail(email);
        staff.setFullName("Mostrador de " + club.getName());
        staff.setRole(UserRole.STAFF);
        staff.setPasswordHash(repos.passwordEncoder().encode("padel1234"));
        repos.users().save(staff);
    }

    private Court court(Repos repos, String name, int order) {
        Court court = new Court();
        court.setName(name);
        court.setDisplayOrder(order);
        return repos.courts().saveAndFlush(court);
    }

    private void price(Repos repos, Court court, LocalTime from, LocalTime to,
                       String pricePerPerson, boolean promo, DayOfWeek... days) {
        PricingRule rule = new PricingRule();
        rule.setCourt(court);
        rule.setStartTime(from);
        rule.setEndTime(to);
        rule.setPrice(new BigDecimal(pricePerPerson).multiply(BigDecimal.valueOf(4)));
        rule.setPromo(promo);
        for (DayOfWeek day : days) {
            rule.addDay(day);
        }
        repos.prices().saveAndFlush(rule);
    }

    private void amenity(Repos repos, String icon, String title, String description, int order) {
        ClubAmenity amenity = new ClubAmenity();
        amenity.setIcon(icon);
        amenity.setTitle(title);
        amenity.setDescription(description);
        amenity.setDisplayOrder(order);
        repos.amenities().saveAndFlush(amenity);
    }

    private void product(Repos repos, String name, String unitPrice) {
        Product product = new Product();
        product.setName(name);
        product.setUnitPrice(new BigDecimal(unitPrice));
        repos.products().saveAndFlush(product);
    }
}
