package ar.com.padelnec.gym.service;

import ar.com.padelnec.web.BusinessRuleException;

/** El DNI como lo guarda el sistema: solo digitos, sin puntos ni espacios. */
public final class GymDni {

    private GymDni() {
    }

    /** Solo digitos, o null si no tiene el largo de un DNI. Para el login, que no debe decir por que falla. */
    public static String digitsOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.length() >= 6 && digits.length() <= 10 ? digits : null;
    }

    /** Para el alta: si no sirve, dice por que. */
    public static String require(String raw) {
        String digits = digitsOrNull(raw);
        if (digits == null) {
            throw new BusinessRuleException("Revisá el DNI: tiene que tener entre 6 y 10 números.");
        }
        return digits;
    }
}
