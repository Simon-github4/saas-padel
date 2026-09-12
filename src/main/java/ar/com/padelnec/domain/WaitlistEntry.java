package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Jugador anotado para que le avisen si se libera una cancha en un horario lleno.
 *
 * <p>No apunta a una cancha en particular: el horario es la unidad ("avisame si se
 * libera alguna cancha a las 20:00"), igual que la grilla publica ofrece el
 * horario con la lista de canchas libres adentro, no cancha por cancha.
 */
@Entity
@Table(name = "waitlist_entry")
@Getter
@Setter
public class WaitlistEntry extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean notified;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    /**
     * El mail de la cuenta del jugador al momento de anotarse, para poder
     * avisarle igual cuando el WhatsApp del club esta apagado o el envio real
     * fallo. Congelado, no una referencia viva a la cuenta -mismo criterio que
     * {@code ProductSale} con el nombre y el precio del producto-: si el
     * jugador cambia de mail despues, este aviso puntual ya estaba armado con
     * el que tenia cuando se anoto.
     */
    @Column(length = 255)
    private String email;

    public void markNotified(Instant now) {
        this.notified = true;
        this.notifiedAt = now;
    }
}
