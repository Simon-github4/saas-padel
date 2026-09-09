package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Servicio que el club destaca en la portada: contadores, estacionamiento, alquiler de paletas. */
@Entity
@Table(name = "club_amenity")
@Getter
@Setter
public class ClubAmenity extends TenantScopedEntity {

    /** Nombre de un icono propio de la app, ej. "court", "parking", "racket". */
    @Column(nullable = false, length = 40)
    private String icon;

    /** Titulo corto del servicio, ej. "3 canchas techadas". */
    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 160)
    private String description;

    /** Orden en el que aparece en la portada. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
