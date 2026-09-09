package ar.com.padelnec;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.CustomerRepository;
import ar.com.padelnec.repository.NotificationLogRepository;
import ar.com.padelnec.repository.OperationalAlertRepository;
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
            bookingRepository.deleteAllInBatch();
            recurringBookingRepository.deleteAllInBatch();
            blackoutRepository.deleteAllInBatch();
            pricingRuleRepository.deleteAllInBatch();
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
        Court court = new Court();
        court.setName(name);
        court.setDisplayOrder(order);
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

    @Transactional
    public Tenant setGeneralPricePerPerson(Tenant club, String pricePerPerson) {
        club.setGeneralPricePerPerson(new BigDecimal(pricePerPerson));
        return tenantRepository.saveAndFlush(club);
    }

    @Transactional
    public Tenant save(Tenant club) {
        return tenantRepository.saveAndFlush(club);
    }
}
