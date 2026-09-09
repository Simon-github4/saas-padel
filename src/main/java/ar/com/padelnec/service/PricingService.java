package ar.com.padelnec.service;

import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.PricingRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve cuanto sale un turno.
 *
 * <p>El club carga los precios POR PERSONA en el panel, pero internamente el
 * sistema trabaja con el precio del TURNO ({@code por persona x playersPerCourt})
 * porque es lo que se cobra y lo que guardan las reservas.
 *
 * <p>Gana la regla mas especifica: primero la que nombra la cancha, y entre reglas
 * igual de especificas, la de franja mas angosta. Si ninguna regla cubre ese
 * dia/franja, cae a la tarifa general del club; sin general, el turno no se oferta.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final Comparator<PricingRule> MOST_SPECIFIC_FIRST =
            Comparator.comparing(PricingRule::isCourtSpecific).reversed()
                    .thenComparing(PricingService::windowMinutes);

    /** Precio efectivo del turno junto con su flag de promocion. */
    public record ResolvedPrice(BigDecimal totalPrice, boolean promo) {
    }

    private final PricingRuleRepository pricingRuleRepository;

    /**
     * Precio del turno completo para una cancha concreta, o vacio si el club no
     * cubre ese dia/franja ni con una regla ni con la tarifa general.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedPrice> priceFor(Tenant club, Court court, DayOfWeek day, LocalTime slotStart) {
        return resolve(club, rulesFor(club, day), court, day, slotStart);
    }

    /**
     * Igual que {@link #priceFor}, pero sobre reglas ya cargadas. El motor de
     * disponibilidad las lee una sola vez y resuelve toda la grilla en memoria.
     */
    public Optional<ResolvedPrice> resolve(Tenant club, List<PricingRule> rulesOfTheDay,
                                           Court court, DayOfWeek day, LocalTime slotStart) {
        Optional<PricingRule> rule = rulesOfTheDay.stream()
                .filter(r -> r.covers(day, slotStart))
                .filter(r -> !r.isCourtSpecific()
                        || r.getCourt().getId().equals(court.getId()))
                .min(MOST_SPECIFIC_FIRST);
        if (rule.isPresent()) {
            return rule.map(r -> new ResolvedPrice(r.getPrice(), r.isPromo()));
        }
        return generalPrice(club);
    }

    @Transactional(readOnly = true)
    public List<PricingRule> rulesFor(Tenant club, DayOfWeek day) {
        return pricingRuleRepository.findRulesForDay(day.getValue());
    }

    /** Promocion del horario, sin importar la cancha: la marca la regla mas especifica de la franja. */
    public boolean isSlotPromo(Tenant club, List<PricingRule> rulesOfTheDay, DayOfWeek day,
                               LocalTime slotStart) {
        if (rulesOfTheDay == null) {
            return false;
        }
        Optional<PricingRule> franja = rulesOfTheDay.stream()
                .filter(r -> r.covers(day, slotStart))
                .min(MOST_SPECIFIC_FIRST);
        return franja.map(PricingRule::isPromo).orElse(false);
    }

    /** Sena exigida para un turno, redondeada a peso entero para no incomodar al cajero. */
    public BigDecimal depositFor(BigDecimal totalPrice, BigDecimal depositPercentage) {
        if (totalPrice == null || depositPercentage == null) {
            return BigDecimal.ZERO;
        }
        return totalPrice
                .multiply(depositPercentage)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    /** Convierte el precio por persona del club al precio del turno. */
    public BigDecimal perPersonToTurn(BigDecimal perPerson, int playersPerCourt) {
        if (perPerson == null) {
            return null;
        }
        return perPerson.multiply(BigDecimal.valueOf(playersPerCourt));
    }

    private Optional<ResolvedPrice> generalPrice(Tenant club) {
        BigDecimal turn = perPersonToTurn(club.getGeneralPricePerPerson(), club.getPlayersPerCourt());
        if (turn == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedPrice(turn, false));
    }

    private static long windowMinutes(PricingRule rule) {
        return ChronoUnit.MINUTES.between(rule.getStartTime(), rule.getEndTime());
    }
}
