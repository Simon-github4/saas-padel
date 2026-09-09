package ar.com.padelnec.support;

/**
 * Oculta la mayor parte de un dato de contacto antes de que llegue a un log.
 *
 * <p>Los logs de fallo de envio (WhatsApp, email) necesitan algo para identificar
 * de que intento se trata, pero no el telefono o el email completo: son datos
 * personales que un dia pueden terminar en un agregador externo.
 */
public final class Masking {

    private static final int VISIBLE_DIGITS = 4;

    /** {@code +5492262415111} queda {@code *********5111}. */
    public static String phone(String e164) {
        if (e164 == null || e164.length() <= VISIBLE_DIGITS) {
            return e164;
        }
        int hidden = e164.length() - VISIBLE_DIGITS;
        return "*".repeat(hidden) + e164.substring(hidden);
    }

    /** {@code simon.diaz@ufasta.edu.ar} queda {@code s***@ufasta.edu.ar}. */
    public static String email(String address) {
        if (address == null) {
            return null;
        }
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }

    private Masking() {
    }
}
