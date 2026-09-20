package ar.com.padelnec.gym.service;

import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Los topes de la app del gimnasio.
 *
 * <p>El DNI no es un secreto: cualquiera sabe el de otra persona, asi que el
 * login se limita por DNI (para que nadie pruebe claves contra un socio) y por
 * origen (para que nadie barra DNIs). El check-in se limita por socio y por
 * origen: un socio escanea una vez por dia y a veces reintenta, no decenas de
 * veces; el tope frena el abuso sin molestar a nadie legitimo.
 */
@Component
public class GymRateLimits {

    private final SlidingWindowLimiter loginByDni;
    private final SlidingWindowLimiter loginByOrigin;
    private final SlidingWindowLimiter checkInByMember;
    private final SlidingWindowLimiter checkInByOrigin;

    public GymRateLimits(Clock clock) {
        String tooMany = "Hiciste muchos intentos seguidos. Esperá unos minutos y probá de nuevo.";
        this.loginByDni = new SlidingWindowLimiter(clock, 5, Duration.ofMinutes(15), tooMany);
        this.loginByOrigin = new SlidingWindowLimiter(clock, 30, Duration.ofMinutes(15), tooMany);
        this.checkInByMember = new SlidingWindowLimiter(clock, 12, Duration.ofMinutes(10), tooMany);
        this.checkInByOrigin = new SlidingWindowLimiter(clock, 60, Duration.ofMinutes(10), tooMany);
    }

    /** El DNI que llega sin normalizar se usa tal cual: es solo una clave de conteo. */
    public void checkLogin(String slug, String dni, String origin) {
        loginByOrigin.check(origin);
        loginByDni.check(slug + ":" + dni);
    }

    public void checkCheckIn(java.util.UUID memberId, String origin) {
        checkInByOrigin.check(origin);
        checkInByMember.check(memberId.toString());
    }
}
