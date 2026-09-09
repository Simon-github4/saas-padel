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
import org.springframework.util.FileSystemUtils;

/**
 * Levanta un PostgreSQL real embebido para desarrollo local.
 *
 * <p>Existe porque el esquema depende de cosas que solo tiene Postgres de verdad
 * (la restriccion de exclusion GiST que impide el doble booking, tstzrange,
 * btree_gist): con una base en memoria tipo H2 el proyecto compilaria pero
 * arrancaria sin su garantia mas importante.
 *
 * <p>Los datos viven en {@code target/devdb} y sobreviven entre arranques: al
 * ser parte de {@code target}, un {@code mvn clean} ya los borra sin agregar
 * nada al workflow habitual. Para resetear sin un clean completo, arranca con
 * {@code DEV_DB_RESET=true} (o borra la carpeta a mano) con la app parada:
 * borrarla con el motor todavia corriendo la corrompe.
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
    private static final Path DATA_DIR = Path.of("target", "devdb");

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        if (Boolean.parseBoolean(System.getenv("DEV_DB_RESET"))) {
            log.info("DEV_DB_RESET=true: borrando {} para arrancar con una base vacia",
                    DATA_DIR.toAbsolutePath());
            FileSystemUtils.deleteRecursively(DATA_DIR);
        }
        log.info("Iniciando PostgreSQL embebido en el puerto {} (datos en {})", PORT,
                DATA_DIR.toAbsolutePath());
        // Por defecto el builder limpia el directorio en cada arranque (initdb de
        // nuevo cada vez); hay que pedirle explicitamente que no lo haga para que
        // los datos sobrevivan de una corrida a la siguiente.
        return EmbeddedPostgres.builder().setPort(PORT).setDataDirectory(DATA_DIR)
                .setCleanDataDirectory(false).start();
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
