package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un periodo pago de un socio: vale desde {@code startsOn} hasta {@code endsOn}
 * inclusive, con un tope de dias por semana, en las sedes indicadas. Renovar es
 * una fila nueva. El cobro va en la misma fila porque se hace en el mostrador,
 * de una sola vez, pero su fecha ({@code paidOn}) es aparte del periodo: se puede
 * cargar hoy una cuota que empezo antes o que se cobro otro dia.
 */
@Entity
@Table(name = "gym_membership")
@Getter
@Setter
public class GymMembership extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private GymMember member;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** El dia en que se cobro: va a la caja de ese dia. No define el periodo. */
    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", nullable = false, length = 20)
    private PayMethod payMethod;

    /** Donde se cobro; no necesariamente a donde se atribuye el ingreso. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collected_sede_id", nullable = false)
    private GymSede collectedSede;

    /** Usuario del panel que lo registro. */
    @Column(name = "registered_by")
    private UUID registeredBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "gym_membership_sede",
            joinColumns = @JoinColumn(name = "membership_id"),
            inverseJoinColumns = @JoinColumn(name = "sede_id"))
    private Set<GymSede> sedes = new HashSet<>();

    /** Vale ese dia: dentro del periodo y sin anular. */
    public boolean covers(LocalDate day) {
        return voidedAt == null && !day.isBefore(startsOn) && !day.isAfter(endsOn);
    }

    public boolean allows(GymSede sede) {
        return sedes.stream().anyMatch(allowed -> allowed.getId().equals(sede.getId()));
    }
}
