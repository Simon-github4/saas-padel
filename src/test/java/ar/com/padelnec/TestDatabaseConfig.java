package ar.com.padelnec;

import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * PostgreSQL para los tests. Es un Postgres real, no una base en memoria, porque
 * lo que hay que probar son restricciones que solo existen en Postgres.
 *
 * <p>El servidor es uno por corrida (por JVM): levantarlo -initdb y arranque- era
 * lo mas caro de cada contexto de Spring. Lo apaga, y borra su directorio, el
 * shutdown hook de zonky al terminar la JVM.
 *
 * <p>El aislamiento sigue siendo por contexto: cada uno crea su propia base adentro
 * del servidor y Flyway la migra de cero. Spring deja vivos en su cache los
 * contextos ya usados, y con una base compartida uno veria lo que dejo otro.
 *
 * <p>Con pool, como en produccion: sin pool cada transaccion abria una conexion
 * nueva, y en Postgres cada conexion es un proceso nuevo, lento en Windows.
 *
 * <p>Cada contexto distinto sigue costando un arranque de Spring y una base nueva.
 * Dos clases comparten contexto solo si coinciden en todo: el {@code @Import}, los
 * {@code @MockitoBean}, las propiedades y el tipo de servidor. Por eso las clases
 * sin nada propio importan el mismo juego -esta, {@link ClubFixture} y
 * {@code GymFixture}, mas {@link TestClockConfig} si congelan el tiempo-, aunque
 * no usen todo.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabaseConfig {

    private static final AtomicInteger databases = new AtomicInteger();
    private static EmbeddedPostgres server;

    @Bean(destroyMethod = "close")
    @Primary
    public HikariDataSource dataSource() throws IOException, SQLException {
        EmbeddedPostgres postgres = server();
        String database = "test_" + databases.incrementAndGet();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl("postgres", database));
        // 10 para la carrera de BookingConcurrencyTest, que manda 10 reservas a la
        // vez; un contexto ocioso en la cache retiene una sola conexion.
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(1);
        return dataSource;
    }

    private static synchronized EmbeddedPostgres server() throws IOException {
        if (server == null) {
            server = EmbeddedPostgres.builder().start();
        }
        return server;
    }
}
