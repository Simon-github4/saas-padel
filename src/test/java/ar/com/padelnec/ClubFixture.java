package ar.com.padelnec;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.CourtSchedule;
import ar.com.padelnec.domain.enums.CourtRoof;
import ar.com.padelnec.domain.enums.CourtSurface;
import ar.com.padelnec.domain.enums.CourtWall;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CourtScheduleRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.repository.NotificationLogRepository;
import ar.com.padelnec.repository.OperationalAlertRepository;
import ar.com.padelnec.repository.PageEventRepository;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.repository.PendingPlayerSignupRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.repository.PlayerSessionRepository;
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.RecurringBookingRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.repository.WaitlistEntryRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma un club de prueba parecido a los de Necochea: dos canchas, turnos de 90
 * minutos y tarifa unica. Cada test lo ajusta desde ahi.
 */
@TestComponent
@RequiredArgsConstructor
public class ClubFixture {

    private final TenantRepository tenantRepository;
    private final CourtRepository courtRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final CourtScheduleRepository courtScheduleRepository;
    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final BlackoutRepository blackoutRepository;
    private final PaymentRepository paymentRepository;
    private final OperationalAlertRepository alertRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final RecurringBookingRepository recurringBookingRepository;
    private final PlayerSessionRepository playerSessionRepository;
    private final PlayerAccountRepository playerAccountRepository;
    private final PendingPlayerSignupRepository pendingPlayerSignupRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final PageEventRepository pageEventRepository;

    /** Deja la base limpia. Se corre como root porque abarca a todos los clubes. */
    @Transactional
    public void reset() {
        TenantContext.set(TenantContext.ROOT);
        try {
            // No son por club, pero un telefono de prueba fijo entre tests
            // choca contra el unique global si no se limpian aca tambien.
            playerSessionRepository.deleteAllInBatch();
            playerAccountRepository.deleteAllInBatch();
            pendingPlayerSignupRepository.deleteAllInBatch();
            notificationLogRepository.deleteAllInBatch();
            alertRepository.deleteAllInBatch();
            paymentRepository.deleteAllInBatch();
            waitlistEntryRepository.deleteAllInBatch();
            // La bitacora de visitas no esta filtrada por club y sus filas de la
            // portada no cuelgan de ninguno: borrar los clubes no se las lleva.
            pageEventRepository.deleteAllInBatch();
            bookingRepository.deleteAllInBatch();
            recurringBookingRepository.deleteAllInBatch();
            blackoutRepository.deleteAllInBatch();
            pricingRuleRepository.deleteAllInBatch();
            courtScheduleRepository.deleteAllInBatch();
            customerRepository.deleteAllInBatch();
            courtRepository.deleteAllInBatch();
            tenantRepository.deleteAllInBatch();
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public Tenant club(String slug) {
        Tenant club = new Tenant();
        club.setName("Club " + slug);
        club.setSlug(slug);
        // Un club de prueba ya esta configurado: se ve en la busqueda y el sitemap.
        club.setListedInSearch(true);
        club.setWhatsappNumber("+542262400000");
        club.setOpenTime(LocalTime.of(8, 0));
        club.setCloseTime(LocalTime.of(23, 0));
        club.setDefaultSlotDuration(90);
        club.setCancellationLimitHours(12);
        club.setDepositPercentage(new BigDecimal("50.00"));
        club.setAllowUnpaidBooking(true);
        // El default de la entidad paso a false porque WhatsApp esta en stand
        // by (ver Tenant.requiresBookingConfirmation), pero el flujo de
        // confirmacion por token sigue siendo real y varios tests lo
        // ejercitan: el club de prueba lo pide, igual que antes.
        club.setRequiresBookingConfirmation(true);
        return tenantRepository.saveAndFlush(club);
    }

    @Transactional
    public Court court(String name, int order) {
        return court(name, order, CourtWall.GLASS, CourtSurface.CARPET);
    }

    @Transactional
    public Court court(String name, int order, CourtWall wall, CourtSurface surface) {
        return court(name, order, wall, surface, CourtRoof.OUTDOOR);
    }

    @Transactional
    public Court court(String name, int order, CourtWall wall, CourtSurface surface, CourtRoof roof) {
        Court court = new Court();
        court.setName(name);
        court.setDisplayOrder(order);
        court.setWall(wall);
        court.setSurface(surface);
        court.setRoof(roof);
        return courtRepository.saveAndFlush(court);
    }

    /** Tarifa que cubre todo el horario del club para ese dia. El precio es del TURNO. */
    @Transactional
    public PricingRule allDayPrice(DayOfWeek day, String price) {
        PricingRule rule = new PricingRule();
        rule.addDay(day);
        rule.setStartTime(LocalTime.of(0, 0));
        rule.setEndTime(LocalTime.of(23, 59));
        rule.setPrice(new BigDecimal(price));
        return pricingRuleRepository.saveAndFlush(rule);
    }

    /** Tarifa acotada a una franja concreta del dia. El precio es del TURNO. */
    @Transactional
    public PricingRule priceWindow(DayOfWeek day, LocalTime from, LocalTime to, String price) {
        PricingRule rule = new PricingRule();
        rule.addDay(day);
        rule.setStartTime(from);
        rule.setEndTime(to);
        rule.setPrice(new BigDecimal(price));
        return pricingRuleRepository.saveAndFlush(rule);
    }

    /** Marca una regla como promo para la prueba del badge. */
    @Transactional
    public PricingRule markPromo(PricingRule rule) {
        rule.setPromo(true);
        return pricingRuleRepository.saveAndFlush(rule);
    }

    /** Horario propio de la cancha ese dia; un cierre menor al inicio es de madrugada. */
    @Transactional
    public CourtSchedule courtHours(Court court, DayOfWeek day, LocalTime from, LocalTime to) {
        CourtSchedule schedule = new CourtSchedule();
        schedule.setCourt(court);
        schedule.setDays(Set.of(day));
        schedule.setStartTime(from);
        schedule.setEndTime(to);
        return courtScheduleRepository.saveAndFlush(schedule);
    }

    @Transactional
    public CourtSchedule courtClosed(Court court, DayOfWeek day) {
        CourtSchedule schedule = new CourtSchedule();
        schedule.setCourt(court);
        schedule.setDays(Set.of(day));
        schedule.setClosed(true);
        return courtScheduleRepository.saveAndFlush(schedule);
    }

    @Transactional
    public Tenant setGeneralPricePerPerson(Tenant club, String pricePerPerson) {
        club.setGeneralPricePerPerson(new BigDecimal(pricePerPerson));
        return tenantRepository.saveAndFlush(club);
    }

    @Transactional
    public Tenant save(Tenant club) {
        return tenantRepository.saveAndFlush(club);
    }

    /**
     * El club como esta en la base. Despues de guardarlo, la copia de antes ya no
     * sirve para volver a guardar: quedo con una version vieja (Tenant.version).
     */
    @Transactional(readOnly = true)
    public Tenant reload(Tenant club) {
        return tenantRepository.findById(club.getId()).orElseThrow();
    }
}
