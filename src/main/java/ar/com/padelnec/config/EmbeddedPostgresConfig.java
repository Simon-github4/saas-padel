package ar.com.padelnec.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
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
 * <p>Los datos persisten entre reinicios en {@code ~/.padel-saas/pgdata}. Este
 * bean solo existe bajo el perfil {@code dev}; en produccion se usa el
 * {@code DataSource} normal apuntado por {@code DB_URL}.
 */
@Configuration
@Profile("dev")
public class EmbeddedPostgresConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfig.class);
    private static final int PORT = 54329;

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        Path dataDirectory = Path.of(System.getProperty("user.home"), ".padel-saas", "pgdata");
        log.info("Iniciando PostgreSQL embebido en el puerto {} (datos en {})", PORT, dataDirectory);

        return EmbeddedPostgres.builder()
                .setPort(PORT)
                .setDataDirectory(dataDirectory)
                .setCleanDataDirectory(false)
                .start();
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
