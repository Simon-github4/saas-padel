package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * La cuota mensual segun la cantidad de dias por semana. El mostrador la fija
 * antes (auto-completa el monto al cobrar) y la puede ajustar en cada venta.
 * Que un club no tenga filas para algunos dias significa que no los ofrece.
 */
@Entity
@Table(name = "gym_tariff")
@Getter
@Setter
public class GymTariff extends TenantScopedEntity {

    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}