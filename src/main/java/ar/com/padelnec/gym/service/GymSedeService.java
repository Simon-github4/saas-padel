package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.repository.GymSedeRepository;
import ar.com.padelnec.support.Tokens;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Los locales del gimnasio y su QR de la puerta. */
@Service
@RequiredArgsConstructor
public class GymSedeService {

    private final GymSedeRepository sedeRepository;

    @Transactional(readOnly = true)
    public List<GymSede> all() {
        return sedeRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<GymSede> active() {
        return sedeRepository.findAllByActiveTrueOrderByNameAsc();
    }

    /** Lo que se carga de una sede. La ubicacion es opcional; el radio, si falta, es el de fabrica. */
    public record SedeData(String name, String address, BigDecimal partnerSharePct, Double latitude,
                           Double longitude, Integer radiusMeters) {

        public static SedeData basic(String name, String address, BigDecimal partnerSharePct) {
            return new SedeData(name, address, partnerSharePct, null, null, null);
        }
    }

    @Transactional
    public GymSede create(String name, String address, BigDecimal partnerSharePct) {
        return create(SedeData.basic(name, address, partnerSharePct));
    }

    @Transactional
    public GymSede create(SedeData data) {
        GymSede sede = new GymSede();
        apply(sede, data);
        sede.setActive(true);
        sede.setQrToken(Tokens.generate());
        return save(sede);
    }

    @Transactional
    public GymSede update(UUID sedeId, SedeData data, boolean active) {
        GymSede sede = require(sedeId);
        apply(sede, data);
        sede.setActive(active);
        return save(sede);
    }

    /** Invalida el QR impreso: el cartel viejo deja de servir y hay que imprimir el nuevo. */
    @Transactional
    public GymSede regenerateQr(UUID sedeId) {
        GymSede sede = require(sedeId);
        sede.setQrToken(Tokens.generate());
        return sede;
    }

    private GymSede require(UUID sedeId) {
        return sedeRepository.findById(sedeId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa sede."));
    }

    private GymSede save(GymSede sede) {
        try {
            return sedeRepository.saveAndFlush(sede);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("Ya hay una sede con ese nombre.");
        }
    }

    private static void apply(GymSede sede, SedeData data) {
        String name = data.name();
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Poné el nombre de la sede.");
        }
        if (name.trim().length() > 120) {
            throw new BusinessRuleException("El nombre de la sede es demasiado largo.");
        }
        BigDecimal pct = data.partnerSharePct() == null ? BigDecimal.ZERO : data.partnerSharePct();
        if (pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessRuleException("El porcentaje del socio tiene que estar entre 0 y 100.");
        }

        Double latitude = data.latitude();
        Double longitude = data.longitude();
        if ((latitude == null) != (longitude == null)) {
            throw new BusinessRuleException("Cargá la latitud y la longitud juntas, o dejá las dos vacías.");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new BusinessRuleException("Esas coordenadas no son válidas. La latitud va de -90 a 90 y la "
                    + "longitud de -180 a 180.");
        }
        int radius = data.radiusMeters() == null ? GymSede.DEFAULT_RADIUS_METERS : data.radiusMeters();
        if (radius < 20 || radius > 5000) {
            throw new BusinessRuleException("El radio tiene que estar entre 20 y 5000 metros.");
        }

        sede.setName(name.trim());
        sede.setAddress(data.address() == null || data.address().isBlank() ? null : data.address().trim());
        sede.setPartnerSharePct(pct.setScale(2, java.math.RoundingMode.HALF_UP));
        sede.setLatitude(latitude);
        sede.setLongitude(longitude);
        sede.setRadiusMeters(radius);
    }
}
