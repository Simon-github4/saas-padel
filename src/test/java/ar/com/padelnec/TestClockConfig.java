package ar.com.padelnec;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * El reloj congelado de los tests que mueven el tiempo a mano.
 *
 * <p>Es uno solo a proposito: con un reloj por clase, cada clase tenia su propio
 * contexto de Spring (ver {@link TestDatabaseConfig}).
 *
 * <p>Como el reloj pasa de una clase a la siguiente, cada una lo pone en su hora en
 * el {@code @BeforeEach}; el instante de aca es solo el de arranque.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    /**
     * Nombre distinto del bean de produccion a proposito: convive con el reloj
     * real y gana por {@code @Primary}, en vez de pisar su definicion.
     */
    @Bean
    @Primary
    public MutableClock testClock() {
        return MutableClock.at("2026-09-01T10:00:00Z");
    }
}
