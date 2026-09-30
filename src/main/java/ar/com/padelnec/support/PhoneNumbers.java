package ar.com.padelnec.support;

import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.BusinessRuleException.Reason;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Optional;
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

    /**
     * Dice como corregirlo, no solo que esta mal: casi todos los rechazos son
     * celulares escritos sin codigo de area, como se los dicta uno en su ciudad
     * ({@code 15 415000}).
     */
    private static final String INVALID_PHONE =
            "Revisá el teléfono: escribilo con el código de área, así: 2262 15-415000 o 11 5555-1234.";

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    /**
     * @return el numero en E.164, ej. {@code +5492262415000}
     * @throws BusinessRuleException si no es un telefono valido, con un mensaje
     *                               pensado para mostrarle al jugador
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException(Reason.PHONE_MISSING, "Necesitamos tu teléfono para confirmarte el turno");
        }
        PhoneNumber parsed;
        try {
            parsed = util.parse(raw.trim(), DEFAULT_REGION);
        } catch (NumberParseException ex) {
            throw new BusinessRuleException(Reason.PHONE_INVALID, INVALID_PHONE);
        }
        if (!util.isValidNumber(parsed)) {
            throw new BusinessRuleException(Reason.PHONE_INVALID, INVALID_PHONE);
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

    /**
     * Como lo escribe un argentino, para mostrarselo al jugador: {@code 2262 21-2345},
     * {@code 351 412-3456}, {@code 11 5555-1234}. Codigo de area, espacio, y el
     * abonado con guion antes de los ultimos cuatro; sin el 15 ni el 9, que en
     * pantalla no aportan nada.
     *
     * <p>El largo del codigo de area (2, 3 o 4 digitos segun la ciudad) sale de
     * libphonenumber, no de una tabla propia: las caracteristicas cambian (Bariloche
     * paso de 2944 a 294) y la libreria es la que se mantiene al dia. Un numero del
     * exterior, o uno cuyo codigo de area no se reconoce, va en formato
     * internacional: no hay una forma "local" que le sirva a todos.
     */
    public String forLocalDisplay(String e164) {
        if (!e164.startsWith("+54")) {
            return forDisplay(e164);
        }
        return splitAreaCode(e164)
                .filter(split -> split.areaCode() != null && split.number().length() > 4)
                .map(split -> {
                    String number = split.number();
                    int cut = number.length() - 4;
                    return split.areaCode() + " " + number.substring(0, cut) + "-" + number.substring(cut);
                })
                .orElseGet(() -> forDisplay(e164));
    }

    /**
     * Numero para el canal de WhatsApp, sin el 9 movil que sí lleva el E.164 normal.
     *
     * <p>Solo se usa con {@code app.whatsapp.argentina-without-nine=true}. El sandbox
     * viejo de Twilio matcheaba al destinatario sin el 9; el de prueba actual ("join
     * twilio-trial") y la documentacion de Twilio para Argentina lo quieren con el 9,
     * que es como ya se guarda. Por defecto se manda el E.164 tal cual.
     */
    public String forWhatsAppChannel(String e164) {
        return e164.startsWith(MOBILE_PREFIX) ? "+54" + e164.substring(MOBILE_PREFIX.length()) : e164;
    }

    /** Telefono partido en codigo de area y abonado, ej. {@code 2262} y {@code 415000}. */
    public record AreaCodeAndNumber(String areaCode, String number) {
    }

    /**
     * Separa el codigo de area del resto, como lo piden los formularios que no
     * aceptan E.164 (MercadoPago, por ejemplo).
     *
     * <p>En Argentina el codigo de area va de 2 a 4 digitos segun la ciudad, asi que
     * no se puede cortar a ojo: lo decide libphonenumber con las reglas de linea
     * fija, previa quita del 9 de celular, que no es parte del codigo de area.
     *
     * @return vacio si el numero no se puede leer; con codigo de area nulo si se
     *         lee pero no tiene uno reconocible
     */
    public Optional<AreaCodeAndNumber> splitAreaCode(String e164) {
        PhoneNumber parsed;
        try {
            parsed = util.parse(e164, DEFAULT_REGION);
        } catch (NumberParseException ex) {
            return Optional.empty();
        }
        String national = String.valueOf(parsed.getNationalNumber());
        if (parsed.getCountryCode() == ARGENTINA && national.length() == 11 && national.startsWith("9")) {
            national = national.substring(1);
            parsed = new PhoneNumber().setCountryCode(ARGENTINA).setNationalNumber(Long.parseLong(national));
        }
        int length = util.getLengthOfGeographicalAreaCode(parsed);
        if (length <= 0 || length >= national.length()) {
            return Optional.of(new AreaCodeAndNumber(null, national));
        }
        return Optional.of(new AreaCodeAndNumber(national.substring(0, length), national.substring(length)));
    }

    /**
     * Link de WhatsApp para que el jugador escriba al club con un toque.
     *
     * <p>Directo a {@code api.whatsapp.com/send} y no por {@code wa.me}: el
     * redirect de {@code wa.me} re-decoda el texto y rompe los caracteres de 4
     * bytes, asi que los emojis llegaban como "?".
     */
    public String whatsappLink(String e164, String presetMessage) {
        String digits = e164.replaceAll("[^0-9]", "");
        if (presetMessage == null || presetMessage.isBlank()) {
            return "https://api.whatsapp.com/send?phone=" + digits;
        }
        // URLEncoder es para formularios y deja los espacios como "+": algunas
        // versiones de WhatsApp los muestran tal cual ("Hola+Juan"). %20 lo lee
        // bien cualquiera, y un "+" de verdad en el mensaje ya viene como %2B.
        return "https://api.whatsapp.com/send?phone=" + digits + "&text="
                + java.net.URLEncoder.encode(presetMessage, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20");
    }
}
