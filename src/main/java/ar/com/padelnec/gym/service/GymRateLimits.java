package ar.com.padelnec.gym.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
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

    public void checkLogin(String slug, String dni, String origin) {
        loginByOrigin.check(origin);
        loginByDni.check(loginKey(slug, dni));
    }

    /**
     * El socio que se esta probando, no el texto que se tipeo.
     *
     * <p>El login se queda con los digitos del DNI y el club se busca sin distinguir
     * mayusculas: {@code 30.111.222} en {@code Los-Troncos} es el mismo socio que
     * {@code 30111222} en {@code los-troncos}. Contando el texto tal cual, cada
     * forma de escribirlo tenia su propio cupo de intentos. Un DNI sin digitos
     * suficientes no es de nadie y el login lo rechaza igual: queda contado aparte,
     * tal cual llego.
     */
    static String loginKey(String slug, String dni) {
        String club = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        String digits = GymDni.digitsOrNull(dni);
        return club + ":" + (digits != null ? digits : dni);
    }

    public void checkCheckIn(java.util.UUID memberId, String origin) {
        checkInByOrigin.check(origin);
        checkInByMember.check(memberId.toString());
    }
}
