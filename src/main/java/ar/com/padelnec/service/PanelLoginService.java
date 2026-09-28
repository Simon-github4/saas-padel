package ar.com.padelnec.service;

import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.PanelLogin;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.PanelLoginRepository;
import ar.com.padelnec.support.UserAgents;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Anota cada ingreso al panel: quien, a que club, desde que IP y con que
 * dispositivo.
 *
 * <p>Los intentos fallidos solo se anotan si el usuario existe. Lo que alguien
 * tipea en el campo de usuario sin que coincida con nadie puede ser cualquier
 * cosa, incluida su contraseña escrita en el campo equivocado.
 *
 * <p>La IP es la del usuario aunque la app corra detras del proxy de Render:
 * {@code server.forward-headers-strategy} ya la toma de {@code X-Forwarded-For}.
 */
@Service
@RequiredArgsConstructor
public class PanelLoginService {

    private final PanelLoginRepository panelLoginRepository;
    private final ClubUserRepository clubUserRepository;
    private final Clock clock;

    /** Anota el intento hecho con lo que se tipeo en el campo de usuario. */
    @Transactional
    public void record(String typedName, String result) {
        find(typedName).ifPresent(user -> save(user, result));
    }

    /** Mismo criterio que {@code ClubUserDetailsService}: primero por nombre de usuario, despues por mail. */
    private Optional<ClubUser> find(String typedName) {
        if (typedName == null || typedName.isBlank()) {
            return Optional.empty();
        }
        return clubUserRepository.findByFullNameIgnoreCase(typedName)
                .or(() -> clubUserRepository.findByEmailIgnoreCase(typedName));
    }

    private void save(ClubUser user, String result) {
        HttpServletRequest request = currentRequest();
        String userAgent = request == null ? null : request.getHeader("User-Agent");

        PanelLogin login = new PanelLogin();
        login.setClubId(user.getClubId());
        login.setUserId(user.getId());
        login.setUserName(user.getFullName());
        login.setResult(result);
        login.setIp(request == null ? null : trim(request.getRemoteAddr(), 45));
        login.setDevice(UserAgents.device(userAgent));
        login.setUserAgent(trim(userAgent, 300));
        panelLoginRepository.save(login);

        if (PanelLogin.OK.equals(result)) {
            user.setLastLoginAt(clock.instant());
        }
    }

    /** Nulo fuera de un request web, como en los tests que llaman al login directo. */
    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.strip();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
