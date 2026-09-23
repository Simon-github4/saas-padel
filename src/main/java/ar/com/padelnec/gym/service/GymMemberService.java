package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymSessionRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta y mantenimiento de socios desde el mostrador.
 *
 * <p>La clave la genera el sistema y se le muestra al staff una sola vez, para
 * que se la dicte al socio; en la base solo queda su hash. El socio esta
 * obligado a cambiarla en su primer ingreso, asi la clave que vio el mostrador
 * no sigue valiendo.
 */
@Service
@RequiredArgsConstructor
public class GymMemberService {

    /** Sin I, O, 0 y 1: la clave se dicta o se lee de un papel. */
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TEMP_PASSWORD_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GymMemberRepository memberRepository;
    private final GymSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * El socio recien dado de alta, con la clave temporal en claro (unica vez que existe asi).
     * {@code dni} es null si se lo dio de alta sin DNI.
     */
    public record CreatedMember(UUID id, String fullName, String dni, String temporaryPassword) {
    }

    @Transactional(readOnly = true)
    public List<GymMember> all() {
        return memberRepository.findAllByOrderByFullNameAsc();
    }

    /**
     * Da de alta a un socio. El DNI es opcional: los socios anotados sin DNI se cargan
     * con el nombre y el DNI se completa despues con {@link #updateDetails}.
     */
    @Transactional
    public CreatedMember create(String rawDni, String fullName, String phone) {
        String dni = GymDni.optional(rawDni);
        String name = requireName(fullName);
        requireFreeDni(dni, null);

        String temporary = temporaryPassword();
        GymMember member = new GymMember();
        member.setDni(dni);
        member.setFullName(name);
        member.setPhone(blankToNull(phone));
        member.setPasswordHash(passwordEncoder.encode(temporary));
        member.setMustChangePassword(true);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            // Dos altas simultaneas con el mismo DNI: la constraint de la base es la que decide.
            throw new BusinessRuleException("Ya hay un socio con ese DNI.");
        }
        return new CreatedMember(member.getId(), member.getFullName(), dni, temporary);
    }

    /**
     * Corrige DNI, nombre y telefono. El DNI se puede cargar despues del alta o
     * corregir; si cambia, se cierran las sesiones abiertas del socio: quien entro
     * con el DNI anterior no sigue adentro con esta cuenta.
     */
    @Transactional
    public void updateDetails(UUID memberId, String rawDni, String fullName, String phone) {
        GymMember member = require(memberId);
        String dni = GymDni.optional(rawDni);
        String name = requireName(fullName);
        requireFreeDni(dni, member.getId());

        boolean dniChanged = !Objects.equals(dni, member.getDni());
        member.setDni(dni);
        member.setFullName(name);
        member.setPhone(blankToNull(phone));
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("Ya hay un socio con ese DNI.");
        }
        if (dniChanged) {
            sessionRepository.revokeAllForMember(member.getId(), clock.instant());
        }
    }

    /**
     * Le pone una clave temporal nueva y cierra todas sus sesiones abiertas: si
     * la clave se perdio o la conocia otra persona, el celular que ya estaba
     * adentro no puede seguir usandola.
     */
    @Transactional
    public String resetPassword(UUID memberId) {
        GymMember member = require(memberId);
        String temporary = temporaryPassword();
        member.setPasswordHash(passwordEncoder.encode(temporary));
        member.setMustChangePassword(true);
        sessionRepository.revokeAllForMember(member.getId(), clock.instant());
        return temporary;
    }

    /** Deshabilitar corta el acceso al instante: sus sesiones se cierran. */
    @Transactional
    public void setEnabled(UUID memberId, boolean enabled) {
        GymMember member = require(memberId);
        member.setEnabled(enabled);
        if (!enabled) {
            sessionRepository.revokeAllForMember(member.getId(), clock.instant());
        }
    }

    private GymMember require(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ese socio."));
    }

    /** Que el DNI no sea de otro socio del club. {@code self} es el socio que se edita, o null en el alta. */
    private void requireFreeDni(String dni, UUID self) {
        if (dni != null && memberRepository.findByDni(dni)
                .filter(other -> !other.getId().equals(self)).isPresent()) {
            throw new BusinessRuleException("Ya hay un socio con ese DNI.");
        }
    }

    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("Poné el nombre del socio.");
        }
        String name = fullName.trim();
        if (name.length() > 120) {
            throw new BusinessRuleException("El nombre es demasiado largo.");
        }
        return name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }
}
