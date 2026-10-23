package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * La cuota segun su tipo (mensual o pase de 15 dias) y la cantidad de dias por semana.
 * El mostrador la fija antes (auto-completa el monto al cobrar) y la puede ajustar en
 * cada venta. Que un club no tenga fila para un tipo y unos dias significa que no lo ofrece.
 */
@Entity
@Table(name = "gym_tariff")
@Getter
@Setter
public class GymTariff extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_length", nullable = false, length = 20)
    private PlanLength planLength = PlanLength.MONTH;

    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}