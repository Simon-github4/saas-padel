package ar.com.padelnec.service;

import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PricingRule;
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
 * <p>Gana la regla mas especifica: primero la que nombra la cancha, y entre reglas
 * igual de especificas, la de franja mas angosta. Asi un club puede poner un precio
 * general para la tarde y pisarlo solo para la cancha techada de los sabados sin
 * tener que enumerar todas las combinaciones.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final Comparator<PricingRule> MOST_SPECIFIC_FIRST =
            Comparator.comparing(PricingRule::isCourtSpecific).reversed()
                    .thenComparing(PricingService::windowMinutes);

    private final PricingRuleRepository pricingRuleRepository;

    /**
     * Precio del turno completo, o vacio si el club no configuro esa franja.
     *
     * <p>Un horario sin precio no se ofrece online: es preferible que falte un turno
     * en la grilla a venderlo en cero.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> priceFor(Court court, DayOfWeek day, LocalTime slotStart) {
        return resolve(pricingRuleRepository.findAllByDayOfWeek(day.getValue()), court, day, slotStart);
    }

    /**
     * Igual que {@link #priceFor}, pero sobre reglas ya cargadas. El motor de
     * disponibilidad las lee una sola vez y resuelve toda la grilla en memoria.
     */
    public Optional<BigDecimal> resolve(List<PricingRule> rulesOfTheDay, Court court,
                                        DayOfWeek day, LocalTime slotStart) {
        return rulesOfTheDay.stream()
                .filter(rule -> rule.covers(day, slotStart))
                .filter(rule -> !rule.isCourtSpecific()
                        || rule.getCourt().getId().equals(court.getId()))
                .min(MOST_SPECIFIC_FIRST)
                .map(PricingRule::getPrice);
    }

    @Transactional(readOnly = true)
    public List<PricingRule> rulesFor(DayOfWeek day) {
        return pricingRuleRepository.findAllByDayOfWeek(day.getValue());
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

    private static long windowMinutes(PricingRule rule) {
        return ChronoUnit.MINUTES.between(rule.getStartTime(), rule.getEndTime());
    }
}
