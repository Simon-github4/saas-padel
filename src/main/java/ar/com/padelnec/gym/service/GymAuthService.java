package ar.com.padelnec.gym.service;

import ar.com.padelnec.gym.GymModule;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymSession;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymSessionRepository;
import ar.com.padelnec.support.TokenHash;
import ar.com.padelnec.support.Tokens;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login por DNI y clave de los socios, con sesiones de token opaco.
 *
 * <p>Sigue el mismo esquema que la sesion del jugador de padel (token de 32
 * bytes al azar, del que solo se guarda la huella), pero con tablas propias: un
 * socio es de un club y entra con su DNI, no con un email global.
 */
@Service
@RequiredArgsConstructor
public class GymAuthService {

    /** Con clave: igual para DNI inexistente, clave incorrecta y socio deshabilitado; no dice cual fallo. */
    static final String BAD_CREDENTIALS = "DNI o clave incorrectos";
    /** Sin clave no hay nada que ocultar: el DNI no es un secreto y decirlo ayuda al socio que se equivoco. */
    static final String UNKNOWN_DNI = "No encontramos un socio con ese DNI. Si ya sos socio, consultá en el mostrador.";
    static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * Un año, y se renueva cada vez que el socio usa la app: quien va al gimnasio no se entera
     * de que existe una sesion. Las que quedan sin uso vencen y las limpia el job diario.
     */
    private static final Duration SESSION_TTL = Duration.ofDays(365);
    /** Renovar en cada pedido seria una escritura por pedido: alcanza con hacerlo una vez por dia. */
    private static final Duration RENEW_AFTER = Duration.ofDays(1);
    private static final String SESSION_EXPIRED = "Tu sesión venció. Volvé a iniciar sesión.";

    private final GymMemberRepository memberRepository;
    private final GymSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final GymModule gymModule;
    private final Clock clock;

    /**
     * Hash contra el que se compara cuando el DNI no existe, para que probar un
     * DNI inexistente tarde lo mismo que probar uno real con la clave mal y no
     * se pueda averiguar quien es socio midiendo la demora.
     */
    private volatile String dummyHash;

    /** Lo que la app necesita saber de la sesion recien abierta. */
    public record IssuedSession(String token, Instant expiresAt, String fullName, boolean mustChangePassword) {
    }

    /**
     * Entra con el DNI y, solo si el club lo pide, la clave. Sin clave, el DNI alcanza: en un club de
     * barrio el mostrador ve quien entra, y la clave no frena el unico fraude real (prestar la
     * cuenta a un conocido). El limite de intentos por DNI y por origen sigue igual.
     */
    @Transactional
    public IssuedSession login(String rawDni, String rawPassword) {
        String dni = GymDni.digitsOrNull(rawDni);
        GymMember member = dni == null ? null : memberRepository.findByDni(dni).orElse(null);
        boolean passwordRequired = gymModule.isPasswordRequired();

        if (!passwordRequired) {
            if (member == null || !member.isEnabled()) {
                throw new BusinessRuleException(UNKNOWN_DNI);
            }
            return createSession(member, false);
        }

        String hash = member != null ? member.getPasswordHash() : dummyHash();
        boolean passwordOk = rawPassword != null && passwordEncoder.matches(rawPassword, hash);
        if (member == null || !member.isEnabled() || !passwordOk) {
            throw new BusinessRuleException(BAD_CREDENTIALS);
        }
        return createSession(member, member.isMustChangePassword());
    }

    /**
     * El socio dueno de una sesion vigente. Sirve para la pantalla de cambio de
     * clave, que es la unica que se puede usar con la clave temporal.
     */
    @Transactional
    public GymMember resolveSession(String token) {
        GymSession session = sessionRepository.findByTokenHashAndRevokedAtIsNull(TokenHash.of(token))
                .orElseThrow(() -> new UnauthorizedSessionException(SESSION_EXPIRED));
        Instant now = clock.instant();
        if (session.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedSessionException(SESSION_EXPIRED);
        }
        GymMember member = session.getMember();
        if (!member.isEnabled()) {
            throw new UnauthorizedSessionException(SESSION_EXPIRED);
        }
        // Sesion deslizante: cada uso la extiende, asi el socio activo no vuelve a entrar nunca.
        if (session.getExpiresAt().isBefore(now.plus(SESSION_TTL).minus(RENEW_AFTER))) {
            session.setExpiresAt(now.plus(SESSION_TTL));
        }
        return member;
    }

    /**
     * Como {@link #resolveSession}, pero exige que ya haya cambiado la clave temporal. Solo aplica
     * si el club pide clave: sin ella no hay clave temporal que cambiar.
     */
    @Transactional
    public GymMember requireMember(String token) {
        GymMember member = resolveSession(token);
        if (gymModule.isPasswordRequired() && member.isMustChangePassword()) {
            throw new PasswordChangeRequiredException();
        }
        return member;
    }

    /**
     * Cambia la clave y cierra TODAS las sesiones del socio, la actual incluida:
     * una sesion abierta con la clave vieja (un celular perdido, la clave
     * temporal que vio el mostrador) no tiene que sobrevivir. Devuelve una
     * sesion nueva para que la app siga adentro sin volver a loguearse.
     */
    @Transactional
    public IssuedSession changePassword(String token, String currentPassword, String newPassword) {
        UUID memberId = resolveSession(token).getId();
        GymMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedSessionException(SESSION_EXPIRED));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new BusinessRuleException("La clave actual no es correcta.");
        }
        validateNewPassword(newPassword, currentPassword);

        member.setPasswordHash(passwordEncoder.encode(newPassword));
        member.setMustChangePassword(false);
        sessionRepository.revokeAllForMember(member.getId(), clock.instant());
        return createSession(member, false);
    }

    @Transactional
    public void logout(String token) {
        sessionRepository.findByTokenHashAndRevokedAtIsNull(TokenHash.of(token))
                .ifPresent(session -> session.setRevokedAt(clock.instant()));
    }

    private void validateNewPassword(String newPassword, String currentPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException(
                    "La clave nueva tiene que tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new BusinessRuleException("La clave nueva tiene que ser distinta de la actual.");
        }
    }

    private IssuedSession createSession(GymMember member, boolean mustChangePassword) {
        String token = Tokens.generate();
        GymSession session = new GymSession();
        session.setMember(member);
        session.setTokenHash(TokenHash.of(token));
        session.setExpiresAt(clock.instant().plus(SESSION_TTL));
        sessionRepository.save(session);
        return new IssuedSession(token, session.getExpiresAt(), member.getFullName(), mustChangePassword);
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode(Tokens.generate());
            dummyHash = hash;
        }
        return hash;
    }
}
