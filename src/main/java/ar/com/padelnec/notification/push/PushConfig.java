package ar.com.padelnec.notification.push;

import ar.com.padelnec.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Las claves VAPID de los avisos al celular, si las hay.
 *
 * <p>Sin claves no se registra el bean, y quien lo usa lo recibe como
 * {@code Optional} vacio: la opcion no aparece en el panel y no se manda nada.
 */
@Configuration
public class PushConfig {

    @Bean
    @Profile("!dev")
    @Conditional(KeysConfigured.class)
    public VapidKeys vapidKeys(AppProperties properties) {
        AppProperties.Push push = properties.getPush();
        return VapidKeys.of(push.getVapidPublicKey(), push.getVapidPrivateKey(), subject(properties));
    }

    /**
     * En dev no hace falta generarlas a mano: se crean una vez y se guardan en
     * {@code target/}, igual que la clave de cifrado de desarrollo. Que sobrevivan
     * al reinicio importa: con claves nuevas, el celular que ya activo los avisos
     * deja de recibirlos.
     */
    @Bean
    @Profile("dev")
    public VapidKeys devVapidKeys(AppProperties properties) {
        AppProperties.Push push = properties.getPush();
        if (StringUtils.hasText(push.getVapidPublicKey()) && StringUtils.hasText(push.getVapidPrivateKey())) {
            return VapidKeys.of(push.getVapidPublicKey(), push.getVapidPrivateKey(), subject(properties));
        }
        return DevVapidKeys.loadOrGenerate(subject(properties));
    }

    private static String subject(AppProperties properties) {
        String subject = properties.getPush().getVapidSubject();
        if (!StringUtils.hasText(subject)) {
            return "mailto:" + properties.getMail().getFrom();
        }
        if (!subject.startsWith("mailto:") && !subject.startsWith("https://")) {
            throw new IllegalArgumentException("PUSH_VAPID_SUBJECT tiene que empezar con mailto: o https://");
        }
        return subject.trim();
    }

    static class KeysConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("app.push.vapid-public-key"))
                    && StringUtils.hasText(context.getEnvironment().getProperty("app.push.vapid-private-key"));
        }
    }

    /** Mismo criterio que {@code DevEncryptionKey}: bajo {@code target/}, un {@code mvn clean} las borra. */
    static final class DevVapidKeys {

        private static final Path FILE = Path.of("target", "dev-vapid-keys.local");

        static VapidKeys loadOrGenerate(String subject) {
            try {
                if (Files.exists(FILE)) {
                    List<String> lines = Files.readAllLines(FILE);
                    return VapidKeys.of(lines.get(0), lines.get(1), subject);
                }
                VapidKeys keys = VapidKeys.generate(subject);
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, keys.publicKey() + "\n" + keys.privateKey() + "\n");
                return keys;
            } catch (IOException ex) {
                throw new UncheckedIOException("No se pudieron generar las claves VAPID de desarrollo", ex);
            }
        }

        private DevVapidKeys() {
        }
    }
}
