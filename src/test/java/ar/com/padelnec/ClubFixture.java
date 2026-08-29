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
import ar.com.padelnec.repository.PricingRuleRepository;
import ar.com.padelnec.repository.RecurringBookingRepository;
import ar.com.padelnec.repository.TenantRepository;
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

    /** Deja la base limpia. Se corre como root porque abarca a todos los clubes. */
    @Transactional
    public void reset() {
        TenantContext.set(TenantContext.ROOT);
        try {
            notificationLogRepository.deleteAllInBatch();
            alertRepository.deleteAllInBatch();
            paymentRepository.deleteAllInBatch();
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
        return tenantRepository.saveAndFlush(club);
    }

    @Transactional
    public Court court(String name, int order) {
        Court court = new Court();
        court.setName(name);
        court.setDisplayOrder(order);
        return courtRepository.saveAndFlush(court);
    }

    /** Tarifa que cubre todo el horario del club para ese dia. */
    @Transactional
    public PricingRule allDayPrice(DayOfWeek day, String price) {
        PricingRule rule = new PricingRule();
        rule.setDay(day);
        rule.setStartTime(LocalTime.of(0, 0));
        rule.setEndTime(LocalTime.of(23, 59));
        rule.setPrice(new BigDecimal(price));
        return pricingRuleRepository.saveAndFlush(rule);
    }

    /** Tarifa acotada a una franja concreta del dia. */
    @Transactional
    public PricingRule priceWindow(DayOfWeek day, LocalTime from, LocalTime to, String price) {
        PricingRule rule = new PricingRule();
        rule.setDay(day);
        rule.setStartTime(from);
        rule.setEndTime(to);
        rule.setPrice(new BigDecimal(price));
        return pricingRuleRepository.saveAndFlush(rule);
    }

    @Transactional
    public Tenant save(Tenant club) {
        return tenantRepository.saveAndFlush(club);
    }
}
