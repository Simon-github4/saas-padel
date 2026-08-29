package ar.com.padelnec.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /**
     * El reloj se inyecta en vez de llamar a {@code Instant.now()} porque casi toda
     * la logica de este sistema depende del tiempo: vencimientos de 10 y 15 minutos,
     * ventana de cancelacion, turnos pasados. Poder fijarlo hace que esas reglas se
     * puedan probar de verdad.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
