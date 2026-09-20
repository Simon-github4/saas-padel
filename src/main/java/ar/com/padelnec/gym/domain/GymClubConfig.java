package ar.com.padelnec.gym.domain;

import ar.com.padelnec.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Prende el modulo de gimnasio para un club. Existir con {@code enabled = true}
 * es lo que lo activa; no se agrega una columna a {@code tenant} para que el
 * modulo no toque el modelo de padel.
 */
@Entity
@Table(name = "gym_club_config")
@Getter
@Setter
public class GymClubConfig extends TenantScopedEntity {

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Como entran los socios. False (lo normal): con solo el DNI. True: con DNI y la
     * clave temporal que da el mostrador, que el socio cambia la primera vez. El
     * codigo de la clave sigue entero para poder prenderlo cuando el club lo quiera.
     */
    @Column(name = "password_required", nullable = false)
    private boolean passwordRequired = false;
}
