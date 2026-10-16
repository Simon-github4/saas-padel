package ar.com.padelnec.service;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.PanelRememberToken;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.PanelRememberTokenRepository;
import ar.com.padelnec.support.TokenHash;
import ar.com.padelnec.support.Tokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los dispositivos que quedaron recordados en el panel.
 *
 * <p>Quince dias fijos desde el ingreso con clave: usarlo todos los dias no lo
 * estira, asi que cada dos semanas se vuelve a escribir la clave. Un token que
 * se copio del navegador sirve, como mucho, hasta ese dia.
 *
 * <p>El token no rota en cada uso, como hace el {@code PersistentTokenBasedRememberMeServices}
 * de Spring. Esa rotacion detecta una cookie copiada, pero con el panel daria
 * falsos positivos: al volver despues de que la sesion se vencio, el navegador
 * pide la pagina y sus recursos en paralelo, todos con la misma cookie. El primero
 * la rota, los demas llegan con la vieja, y Spring lo toma como un robo y cierra
 * todos los dispositivos del usuario.
 */
@Service
@RequiredArgsConstructor
public class PanelRememberService {

    public static final Duration VALIDITY = Duration.ofDays(15);

    private final PanelRememberTokenRepository repository;
    private final ClubUserRepository clubUserRepository;
    private final Clock clock;

    /** Recuerda un dispositivo. Devuelve el token en claro, que solo viaja en la cookie. */
    @Transactional
    public String remember(UUID userId) {
        Instant now = clock.instant();
        // Limpieza al paso: los vencidos de este usuario ya no le sirven a nadie.
        repository.deleteExpiredForUser(userId, now);

        String token = Tokens.generate();
        PanelRememberToken row = new PanelRememberToken();
        row.setUserId(userId);
        row.setTokenHash(TokenHash.of(token));
        row.setExpiresAt(now.plus(VALIDITY));
        repository.save(row);
        return token;
    }

    /**
     * El usuario de un dispositivo recordado, si el token sigue valido y el
     * usuario habilitado. Uno vencido o de un usuario deshabilitado se borra.
     */
    @Transactional
    public Optional<ClubUser> resolve(String rawToken) {
        String hash = TokenHash.of(rawToken);
        if (hash == null) {
            return Optional.empty();
        }
        Optional<PanelRememberToken> found = repository.findByTokenHash(hash);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PanelRememberToken row = found.get();
        Instant now = clock.instant();
        Optional<ClubUser> user = clubUserRepository.findById(row.getUserId()).filter(ClubUser::isEnabled);
        if (!row.getExpiresAt().isAfter(now) || user.isEmpty()) {
            repository.delete(row);
            return Optional.empty();
        }
        row.setLastUsedAt(now);
        return user;
    }

    /** Olvida este dispositivo: el usuario toco "Salir". */
    @Transactional
    public void forget(String rawToken) {
        String hash = TokenHash.of(rawToken);
        if (hash != null) {
            repository.deleteByTokenHash(hash);
        }
    }

    /**
     * Olvida todos los dispositivos del usuario. Va con cada cambio de clave y al
     * deshabilitarlo: si no, una cookie copiada seguiria entrando hasta vencer.
     */
    @Transactional
    public void forgetAll(UUID userId) {
        repository.deleteAllForUser(userId);
    }
}
