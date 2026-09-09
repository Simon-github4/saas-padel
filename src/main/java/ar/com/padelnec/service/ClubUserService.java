package ar.com.padelnec.service;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.UserRole;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.web.BusinessRuleException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El usuario de mostrador de un club: un solo rol mas, sin la configuracion
 * ni las estadisticas que si ve el dueno.
 *
 * <p>Un club tiene como mucho un usuario de mostrador. No hace falta una
 * pantalla de alta multiple: si el dia de mañana un club pide varios, ese es
 * el momento de generalizar esto.
 */
@Service
@RequiredArgsConstructor
public class ClubUserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final ClubUserRepository clubUserRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<ClubUser> findStaff(UUID clubId) {
        return clubUserRepository.findFirstByClubIdAndRole(clubId, UserRole.STAFF);
    }

    @Transactional
    public ClubUser createStaff(Tenant club, String fullName, String email, String rawPassword) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessRuleException("Poné un nombre para el usuario");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("Poné un mail para el usuario");
        }
        validatePassword(rawPassword);
        if (findStaff(club.getId()).isPresent()) {
            throw new BusinessRuleException("Este club ya tiene un usuario de mostrador");
        }

        ClubUser user = new ClubUser();
        user.setClubId(club.getId());
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setRole(UserRole.STAFF);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        try {
            // saveAndFlush, no save: la unique constraint del mail se checkea recien
            // al insertar de verdad, y sin forzar el flush aca ese INSERT queda
            // diferido hasta el commit -- demasiado tarde para este catch.
            return clubUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("Ya existe un usuario con ese mail");
        }
    }

    @Transactional
    public void setPassword(Tenant club, UUID userId, String rawPassword) {
        validatePassword(rawPassword);
        ClubUser user = ownedStaff(club, userId);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        clubUserRepository.save(user);
    }

    @Transactional
    public void setEnabled(Tenant club, UUID userId, boolean enabled) {
        ClubUser user = ownedStaff(club, userId);
        user.setEnabled(enabled);
        clubUserRepository.save(user);
    }

    /**
     * {@code ClubUser} no lleva el filtro de tenant de Hibernate a proposito
     * (el login ocurre antes de que exista contexto de club, ver su propio
     * javadoc) -- asi que el chequeo de que el usuario sea del mismo club de
     * quien pide el cambio hay que hacerlo a mano, aca. Mismo mensaje para
     * "no existe" y "es de otro club": no hay que distinguirle a quien pide el
     * cambio cual de los dos paso.
     */
    private ClubUser ownedStaff(Tenant club, UUID userId) {
        ClubUser user = clubUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado"));
        if (!club.getId().equals(user.getClubId())) {
            throw new BusinessRuleException("Usuario no encontrado");
        }
        return user;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException(
                    "La contraseña tiene que tener al menos %d caracteres".formatted(MIN_PASSWORD_LENGTH));
        }
    }
}
