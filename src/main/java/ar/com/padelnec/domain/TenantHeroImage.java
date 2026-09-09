package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Bytes de la foto de portada subida como archivo, separados de {@link Tenant}
 * a proposito.
 *
 * <p>{@code Tenant} se carga en practicamente cualquier request del sistema
 * (cada vista del panel, la disponibilidad publica, la busqueda entre clubes,
 * el webhook de MercadoPago) y ninguno de esos caminos necesita la imagen,
 * solo el endpoint que la sirve. Mezclada en la misma fila, cada uno de esos
 * caminos pagaba el peso completo de una imagen de hasta 5MB sin usarla
 * nunca. La clave primaria es el propio id del tenant: es una relacion 1 a 1
 * real, no una entidad con vida propia.
 */
@Entity
@Table(name = "tenant_hero_image")
@Getter
@Setter
public class TenantHeroImage {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
}
