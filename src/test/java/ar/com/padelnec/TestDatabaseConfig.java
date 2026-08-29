package ar.com.padelnec;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * PostgreSQL efimero para los tests: puerto libre al azar y directorio temporal
 * que se descarta al cerrar. Es un Postgres real, no una base en memoria, porque
 * lo que hay que probar son restricciones que solo existen en Postgres.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabaseConfig {

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.builder().start();
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
