package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Base de toda entidad transaccional.
 *
 * <p>El campo lo completa y lo filtra Hibernate a partir del
 * {@code CurrentTenantIdentifierResolver}: ninguna consulta escrita a mano puede
 * olvidarse el {@code WHERE club_id = ?} y filtrar datos de otro club.
 */
@MappedSuperclass
@Getter
public abstract class TenantScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "club_id", nullable = false, updatable = false)
    private UUID clubId;
}
