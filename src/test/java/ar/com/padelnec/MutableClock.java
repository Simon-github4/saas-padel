package ar.com.padelnec;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj controlable para los tests.
 *
 * <p>Casi todas las reglas de este sistema son temporales: el DRAFT que vence a los
 * 10 minutos, el link de WhatsApp que caduca a los 15, la ventana de cancelacion,
 * el turno que ya empezo. Poder mover el tiempo a mano es lo que permite probarlas
 * sin esperar.
 */
public class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant), ZoneId.of("UTC"));
    }

    public void set(Instant newInstant) {
        this.instant = newInstant;
    }

    public void advance(Duration amount) {
        this.instant = this.instant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
