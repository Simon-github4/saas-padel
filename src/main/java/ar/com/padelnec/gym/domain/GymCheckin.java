package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un ingreso al gimnasio. Se inserta por SQL nativo con {@code ON CONFLICT DO
 * NOTHING} (ver {@code GymCheckinRepository#insertIfAbsent}) para que dos
 * escaneos seguidos no choquen contra el unico de un ingreso por dia; esta
 * entidad es para leerlos.
 */
@Entity
@Table(name = "gym_checkin")
@Getter
@Setter
public class GymCheckin extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private GymMember member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private GymMembership membership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sede_id", nullable = false)
    private GymSede sede;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    /** Dia calendario en la zona horaria del club. */
    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    /** A cuantos metros de la sede estaba el celular; nulo si no se verifico la ubicacion. */
    @Column(name = "distance_m")
    private Integer distanceMeters;

    /** El mostrador lo dejo pasar por encima del tope semanal. */
    @Column(name = "is_override", nullable = false)
    private boolean override;

    /** Usuario del panel que lo cargo a mano; nulo si el socio escaneo el QR. */
    @Column(name = "registered_by")
    private UUID registeredBy;
}
