package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.CourtRoof;
import ar.com.padelnec.domain.enums.CourtSurface;
import ar.com.padelnec.domain.enums.CourtWall;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "court")
@Getter
@Setter
public class Court extends TenantScopedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    /** Orden en el que aparece la cancha en la grilla del panel y de la app. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Una cancha inactiva desaparece de la grilla pero conserva su historial. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Blindex o pared: el jugador puede filtrar la busqueda por esto. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CourtWall wall = CourtWall.GLASS;

    /** Con alfombra o sin: tambien filtra la busqueda. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CourtSurface surface = CourtSurface.CARPET;

    /** Techada o al aire libre: tambien filtra la busqueda. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CourtRoof roof = CourtRoof.OUTDOOR;
}
