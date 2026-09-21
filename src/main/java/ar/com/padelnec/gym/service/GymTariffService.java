package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymTariff;
import ar.com.padelnec.gym.repository.GymTariffRepository;
import ar.com.padelnec.web.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La tarifa por cantidad de dias por semana, la cuota que el mostrador fija antes.
 * Una fila existe solo para los dias que el club ofrece: los otros no tienen precio.
 */
@Service
@RequiredArgsConstructor
public class GymTariffService {

    private final GymTariffRepository tariffRepository;

    public record TariffRow(int daysPerWeek, BigDecimal price) {
    }

    @Transactional(readOnly = true)
    public List<TariffRow> tariffs() {
        return tariffRepository.findAllByOrderByDaysPerWeekAsc().stream()
                .map(t -> new TariffRow(t.getDaysPerWeek(), t.getPrice()))
                .toList();
    }

    /** Cuanto vale el plan de {@code daysPerWeek} dias, o null si el club no lo ofrece. */
    @Transactional(readOnly = true)
    public BigDecimal priceOf(int daysPerWeek) {
        return tariffRepository.findByDaysPerWeek(daysPerWeek)
                .map(GymTariff::getPrice)
                .orElse(null);
    }

    /** Guarda (o actualiza) el precio de un plan de dias por semana. */
    @Transactional
    public void setPrice(int daysPerWeek, BigDecimal price) {
        if (daysPerWeek < 1 || daysPerWeek > 7) {
            throw new BusinessRuleException("Los días por semana van de 1 a 7.");
        }
        tariffRepository.findByDaysPerWeek(daysPerWeek)
                .ifPresentOrElse(t -> t.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP)),
                        () -> {
                            GymTariff tariff = new GymTariff();
                            tariff.setDaysPerWeek(daysPerWeek);
                            tariff.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP));
                            tariffRepository.save(tariff);
                        });
    }

    /** Borra el precio de un plan: ese socio deja de estar ofrecido. */
    @Transactional
    public void remove(int daysPerWeek) {
        tariffRepository.findByDaysPerWeek(daysPerWeek).ifPresent(tariffRepository::delete);
    }
}