package ar.com.padelnec.support;

import ar.com.padelnec.web.BusinessRuleException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Component;

/**
 * Lleva a E.164 lo que el jugador escribio en el celular.
 *
 * <p>El telefono identifica al jugador dentro del club, asi que normalizarlo mal
 * tiene consecuencias concretas: el mismo jugador aparece dos veces, pierde su
 * marca de confianza y su historial de turnos queda partido. En Argentina la
 * ambiguedad es real, porque el mismo numero se escribe {@code 2262 15-415000},
 * {@code 02262 415000} o {@code +54 9 2262 415000}.
 */
@Component
public class PhoneNumbers {

    private static final String DEFAULT_REGION = "AR";
    private static final int ARGENTINA = 54;
    private static final String MOBILE_PREFIX = "+549";

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    /**
     * @return el numero en E.164, ej. {@code +5492262415000}
     * @throws BusinessRuleException si no es un telefono valido, con un mensaje
     *                               pensado para mostrarle al jugador
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("Necesitamos tu teléfono para confirmarte el turno");
        }
        PhoneNumber parsed;
        try {
            parsed = util.parse(raw.trim(), DEFAULT_REGION);
        } catch (NumberParseException ex) {
            throw new BusinessRuleException("El teléfono " + raw + " no parece válido");
        }
        if (!util.isValidNumber(parsed)) {
            throw new BusinessRuleException("El telefono " + raw + " no parece valido");
        }
        return forceArgentineMobile(parsed);
    }

    /**
     * Agrega el 9 de celular a los numeros argentinos que llegan sin el.
     *
     * <p>Escrito sin el 15 ni el 9, {@code 2262 415000} es indistinguible de una linea
     * fija, y sin esta correccion el mismo jugador quedaria como dos clientes segun
     * como haya tipeado, ademas de que el numero no serviria para WhatsApp. Como este
     * telefono existe justamente para mandarle un WhatsApp, asumir celular es lo
     * correcto: quien de verdad cargue una linea fija no iba a recibir el mensaje de
     * todos modos.
     */
    private String forceArgentineMobile(PhoneNumber parsed) {
        String e164 = util.format(parsed, PhoneNumberFormat.E164);
        if (parsed.getCountryCode() != ARGENTINA) {
            return e164;
        }
        String national = String.valueOf(parsed.getNationalNumber());
        // 10 digitos son codigo de area mas abonado, sin el 9 de celular delante.
        // Ningun codigo de area argentino arranca con 9, asi que no hay ambiguedad.
        return national.length() == 10 ? MOBILE_PREFIX + national : e164;
    }

    /** Formato legible para las pantallas del panel, ej. {@code +54 9 2262 41-5000}. */
    public String forDisplay(String e164) {
        try {
            return util.format(util.parse(e164, DEFAULT_REGION), PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException ex) {
            return e164;
        }
    }

    /** Link de WhatsApp para que el jugador escriba al club con un toque. */
    public String whatsappLink(String e164, String presetMessage) {
        String digits = e164.replaceAll("[^0-9]", "");
        if (presetMessage == null || presetMessage.isBlank()) {
            return "https://wa.me/" + digits;
        }
        return "https://wa.me/" + digits + "?text="
                + java.net.URLEncoder.encode(presetMessage, java.nio.charset.StandardCharsets.UTF_8);
    }
}
