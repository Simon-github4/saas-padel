package ar.com.padelnec.config;

import ar.com.padelnec.support.EncryptedStringConverter;
import ar.com.padelnec.support.TextCipher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class CryptoConfig {

    /**
     * Se registra en el converter apenas existe el bean, porque Hibernate crea sus
     * converters por reflexion y no puede recibirlo por inyeccion.
     */
    @Bean
    @Profile("!dev")
    public TextCipher textCipher(AppProperties properties) {
        return register(new TextCipher(properties.getSecurity().getEncryptionKey()));
    }

    /**
     * En dev no hace falta exportar una clave a mano: se genera una vez y se
     * guarda en {@code target/} ({@link DevEncryptionKey}), para no tener una
     * clave real -ni siquiera una "solo de dev"- commiteada en el repo.
     */
    @Bean
    @Profile("dev")
    public TextCipher devTextCipher() {
        return register(new TextCipher(DevEncryptionKey.loadOrGenerate()));
    }

    private TextCipher register(TextCipher cipher) {
        EncryptedStringConverter.configure(cipher);
        return cipher;
    }
}
