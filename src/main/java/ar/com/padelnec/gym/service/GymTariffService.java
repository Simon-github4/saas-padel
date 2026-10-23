package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymTariff;
import ar.com.padelnec.gym.domain.PlanLength;
import ar.com.padelnec.gym.repository.GymTariffRepository;
import ar.com.padelnec.web.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La tarifa por tipo de cuota (mensual o pase de 15 dias) y cantidad de dias por semana,
 * la cuota que el mostrador fija antes. Una fila existe solo para lo que el club ofrece:
 * lo otro no tiene precio.
 */
@Service
@RequiredArgsConstructor
public class GymTariffService {

    private final GymTariffRepository tariffRepository;

    /** Los precios de un plan de dias por semana; null donde el club no lo ofrece. */
    public record TariffRow(int daysPerWeek, BigDecimal monthPrice, BigDecimal fifteenDaysPrice) {
    }

    /** Un plan por cantidad de dias por semana, con sus dos precios, de menos a mas dias. */
    @Transactional(readOnly = true)
    public List<TariffRow> tariffs() {
        Map<Integer, BigDecimal[]> byDays = new TreeMap<>();
        tariffRepository.findAllByOrderByDaysPerWeekAsc().forEach(t -> {
            BigDecimal[] prices = byDays.computeIfAbsent(t.getDaysPerWeek(), d -> new BigDecimal[2]);
            prices[t.getPlanLength() == PlanLength.MONTH ? 0 : 1] = t.getPrice();
        });
        return byDays.entrySet().stream()
                .map(e -> new TariffRow(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    /** La cuota mensual de {@code daysPerWeek} dias, o null si el club no la ofrece. */
    @Transactional(readOnly = true)
    public BigDecimal priceOf(int daysPerWeek) {
        return priceOf(PlanLength.MONTH, daysPerWeek);
    }

    /** Guarda (o actualiza) la cuota mensual de {@code daysPerWeek} dias. */
    @Transactional
    public void setPrice(int daysPerWeek, BigDecimal price) {
        setPrice(PlanLength.MONTH, daysPerWeek, price);
    }

    /** Cuanto vale ese tipo de cuota de {@code daysPerWeek} dias, o null si el club no lo ofrece. */
    @Transactional(readOnly = true)
    public BigDecimal priceOf(PlanLength length, int daysPerWeek) {
        return tariffRepository.findByPlanLengthAndDaysPerWeek(length, daysPerWeek)
                .map(GymTariff::getPrice)
                .orElse(null);
    }

    /** Guarda (o actualiza) el precio de un tipo de cuota y dias por semana. */
    @Transactional
    public void setPrice(PlanLength length, int daysPerWeek, BigDecimal price) {
        if (daysPerWeek < 1 || daysPerWeek > 7) {
            throw new BusinessRuleException("Los días por semana van de 1 a 7.");
        }
        if (price == null || price.signum() < 0) {
            throw new BusinessRuleException("Poné un monto válido.");
        }
        tariffRepository.findByPlanLengthAndDaysPerWeek(length, daysPerWeek)
                .ifPresentOrElse(t -> t.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP)),
                        () -> {
                            GymTariff tariff = new GymTariff();
                            tariff.setPlanLength(length);
                            tariff.setDaysPerWeek(daysPerWeek);
                            tariff.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP));
                            tariffRepository.save(tariff);
                        });
    }

    /** Borra el precio de ese tipo de cuota: deja de estar ofrecido. */
    @Transactional
    public void remove(PlanLength length, int daysPerWeek) {
        tariffRepository.findByPlanLengthAndDaysPerWeek(length, daysPerWeek).ifPresent(tariffRepository::delete);
    }
}
