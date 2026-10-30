package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Plantilla de fondo de la historia de turnos libres, separada de {@link Tenant}
 * por lo mismo que {@link TenantHeroImage}: solo la lee el panel al armar la
 * historia, y nadie mas tiene que cargar sus bytes.
 */
@Entity
@Table(name = "tenant_story_template")
@Getter
@Setter
public class TenantStoryTemplate {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** Cuando se subio: el panel reusa la imagen que ya bajo mientras no cambie. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
