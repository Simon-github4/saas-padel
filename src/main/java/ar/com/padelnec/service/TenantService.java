package ar.com.padelnec.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto de entrada al multi-tenant: resuelve el club y deja el contexto puesto
 * para que Hibernate filtre todo lo que venga despues.
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    /**
     * Resuelve el club por su slug publico y lo instala en el contexto del hilo.
     * Es lo primero que hace cualquier endpoint de la app del jugador.
     */
    @Transactional(readOnly = true)
    public Tenant activate(String slug) {
        Tenant club = tenantRepository.findBySlugIgnoreCaseAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el club " + slug));
        TenantContext.set(club.getId());
        return club;
    }

    @Transactional(readOnly = true)
    public Tenant activate(UUID clubId) {
        TenantContext.set(clubId);
        return tenantRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el club " + clubId));
    }

    /** Ejecuta un trabajo en el contexto de un club y restaura el anterior al salir. */
    public <T> T within(UUID clubId, Supplier<T> work) {
        return TenantContext.callAs(clubId, work);
    }

    @Transactional(readOnly = true)
    public Tenant requireCurrent() {
        return activate(TenantContext.require());
    }
}
