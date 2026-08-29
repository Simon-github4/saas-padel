package ar.com.padelnec.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Levanta un PostgreSQL real embebido para desarrollo local.
 *
 * <p>Existe porque el esquema depende de cosas que solo tiene Postgres de verdad
 * (la restriccion de exclusion GiST que impide el doble booking, tstzrange,
 * btree_gist): con una base en memoria tipo H2 el proyecto compilaria pero
 * arrancaria sin su garantia mas importante.
 *
 * <p>La base se crea vacia en cada arranque y Flyway la reconstruye entera. Se
 * eligio asi antes que persistirla: un directorio de datos que sobrevive entre
 * corridas se corrompe apenas se lo borra con el motor todavia levantado, y
 * despues el arranque falla por algo que no tiene nada que ver con el codigo. El
 * club de ejemplo lo vuelve a cargar {@code DevDataSeeder} cada vez.
 *
 * <p>Si preferis tu PostgreSQL local (hay uno escuchando en el 5432 de esta
 * maquina), corre sin el perfil {@code dev} y defini {@code DB_URL},
 * {@code DB_USER} y {@code DB_PASSWORD}.
 *
 * <p>Este bean solo existe bajo el perfil {@code dev}; en produccion se usa el
 * {@code DataSource} normal.
 */
@Configuration
@Profile("dev")
public class EmbeddedPostgresConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfig.class);
    private static final int PORT = 54329;

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        log.info("Iniciando PostgreSQL embebido en el puerto {}", PORT);
        return EmbeddedPostgres.builder().setPort(PORT).start();
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
