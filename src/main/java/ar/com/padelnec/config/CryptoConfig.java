package ar.com.padelnec.config;

import ar.com.padelnec.support.EncryptedStringConverter;
import ar.com.padelnec.support.TextCipher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    /**
     * Se registra en el converter apenas existe el bean, porque Hibernate crea sus
     * converters por reflexion y no puede recibirlo por inyeccion.
     */
    @Bean
    public TextCipher textCipher(AppProperties properties) {
        TextCipher cipher = new TextCipher(properties.getSecurity().getEncryptionKey());
        EncryptedStringConverter.configure(cipher);
        return cipher;
    }
}
