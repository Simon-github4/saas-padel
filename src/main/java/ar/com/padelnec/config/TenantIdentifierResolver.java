package ar.com.padelnec.config;

import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Le dice a Hibernate cual es el club activo.
 *
 * <p>Junto con {@code @TenantId} en {@code TenantScopedEntity}, hace que el
 * {@code WHERE club_id = ?} lo agregue el ORM y no cada consulta escrita a mano.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.get();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Los jobs recorren varios clubes reutilizando la misma sesion de persistencia.
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        // Unico caso en que Hibernate omite el filtro por club.
        return TenantContext.ROOT.equals(tenantId);
    }

    @Override
    public void customize(java.util.Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
