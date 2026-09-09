package ar.com.padelnec.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Clave AES de desarrollo, generada una sola vez y guardada en {@code target/}.
 *
 * <p>Mismo motivo que {@code target/devdb} en {@link EmbeddedPostgresConfig}:
 * al vivir bajo {@code target}, un {@code mvn clean} ya la borra, asi que no
 * hace falta acordarse de limpiarla a mano, y nunca queda una clave real
 * -ni siquiera una "solo de dev"- commiteada en el repo.
 */
final class DevEncryptionKey {

    private static final Path FILE = Path.of("target", "dev-encryption-key.local");

    static String loadOrGenerate() {
        try {
            if (Files.exists(FILE)) {
                return Files.readString(FILE).trim();
            }
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            String key = Base64.getEncoder().encodeToString(raw);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, key);
            return key;
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "No se pudo generar la clave de cifrado de desarrollo", ex);
        }
    }

    private DevEncryptionKey() {
    }
}
