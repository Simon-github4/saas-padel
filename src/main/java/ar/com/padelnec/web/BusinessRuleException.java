package ar.com.padelnec.web;

/**
 * Una regla de negocio impide la operacion: la cancha ya se tomo, el plazo para
 * cancelar vencio, el club no acepta reservas sin sena.
 *
 * <p>El mensaje esta escrito para que el jugador lo lea tal cual en pantalla.
 *
 * <p>El {@link Reason} dice cual regla fue, para la analitica del checkout: el
 * mensaje no sirve para eso, porque cambia de redaccion y alguno lleva el
 * telefono del jugador. Solo lo llevan las reglas que pueden frenar una reserva;
 * el resto sale sin motivo.
 */
public class BusinessRuleException extends RuntimeException {

    /** Cual regla freno la reserva. Viaja en la respuesta, nunca con datos del jugador. */
    public enum Reason {
        NAME_MISSING,
        PHONE_MISSING,
        PHONE_INVALID,
        CUSTOMER_BLOCKED,
        SLOT_NOT_IN_GRID,
        COURT_CLOSED,
        SLOT_PAST,
        BEYOND_HORIZON,
        NO_PRICE,
        QUOTA_REACHED,
        DEPOSIT_REQUIRED,
        ONLINE_PAYMENT_UNAVAILABLE,
        /** Numero que nunca reservo, sin codigo de verificacion. */
        VERIFICATION_REQUIRED,
        VERIFICATION_CODE_INVALID,
        /** El codigo vencio o se agotaron los intentos: hay que pedir otro. */
        VERIFICATION_CODE_EXPIRED,
        VERIFICATION_CODE_NOT_SENT,
        VERIFICATION_LIMIT,
        /** Reserva con sesion y un telefono distinto al verificado de la cuenta. */
        ACCOUNT_PHONE_LOCKED
    }

    private final Reason reason;

    public BusinessRuleException(String message) {
        this(null, message);
    }

    public BusinessRuleException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** Nulo en las reglas que no frenan una reserva. */
    public Reason getReason() {
        return reason;
    }
}
