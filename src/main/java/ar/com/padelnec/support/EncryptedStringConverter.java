package ar.com.padelnec.support;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra y descifra transparentemente los campos anotados con
 * {@code @Convert(converter = EncryptedStringConverter.class)}.
 *
 * <p>Hibernate instancia los converters por reflexion, fuera del contenedor de
 * Spring, asi que el cifrador se inyecta una unica vez al arrancar la aplicacion
 * en lugar de por constructor.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile TextCipher cipher;

    /** La invoca {@code CryptoConfig} al levantar el contexto de Spring. */
    public static void configure(TextCipher textCipher) {
        cipher = textCipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return requireCipher().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return requireCipher().decrypt(dbData);
    }

    private static TextCipher requireCipher() {
        TextCipher current = cipher;
        if (current == null) {
            throw new IllegalStateException(
                    "EncryptedStringConverter se uso antes de configurar el cifrador. "
                            + "Falta app.security.encryption-key?");
        }
        return current;
    }
}
