package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Jugador, identificado por su telefono dentro de un club.
 *
 * <p>El mismo telefono en dos clubes son dos filas distintas: cada club maneja
 * su propia confianza y su propio historial.
 */
@Entity
@Table(name = "customer")
@Getter
@Setter
public class Customer extends TenantScopedEntity {

    /** Normalizado a E.164, ej. +5492262415000. */
    @Column(name = "phone_number", nullable = false, length = 25)
    private String phoneNumber;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** Habilitado a reservar sin sena aunque el club exija pago anticipado. */
    @Column(name = "is_trusted", nullable = false)
    private boolean trusted;

    /** Bloqueado por el club: no puede reservar desde la web. */
    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Column(name = "no_show_count", nullable = false)
    private int noShowCount;

    @Column(columnDefinition = "text")
    private String notes;
}
