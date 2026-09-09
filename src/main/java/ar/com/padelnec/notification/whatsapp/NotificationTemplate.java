package ar.com.padelnec.notification.whatsapp;

/**
 * Mensajes que el sistema le manda al jugador.
 *
 * <p>Existen como enum y no como strings sueltos por una restriccion real de
 * WhatsApp: los mensajes que inicia el negocio (todos estos, porque el jugador no
 * viene escribiendo) solo se pueden mandar con una plantilla aprobada previamente
 * por Meta. Cada valor de aca corresponde a una plantilla cargada en el proveedor,
 * y el orden de las variables tiene que coincidir con el que se aprobo.
 */
public enum NotificationTemplate {

    /** Link de confirmacion del flujo sin pago. Variables: nombre, club, cancha, fecha, hora, minutos, link. */
    CONFIRMATION_REQUEST(7),

    /** Turno confirmado de palabra. Variables: nombre, cancha, fecha, hora, total, link. */
    BOOKING_CONFIRMED_UNPAID(6),

    /** Sena acreditada. Variables: nombre, cancha, fecha, hora, saldo, link. */
    BOOKING_CONFIRMED_PAID(6),

    /** Vencio el plazo de confirmacion y se libero la cancha. Variables: nombre, cancha, fecha, hora. */
    CONFIRMATION_EXPIRED(4),

    /** El club dio de baja el turno. Variables: nombre, cancha, fecha, hora, telefono del club. */
    BOOKING_CANCELLED_BY_CLUB(5),

    /** Recordatorio previo al turno. Variables: nombre, cancha, fecha, hora, saldo. */
    BOOKING_REMINDER(5),

    /** Se libero un horario que alguien esperaba. Variables: nombre, club, fecha, hora, link. */
    WAITLIST_SLOT_FREED(5);

    private final int variableCount;

    NotificationTemplate(int variableCount) {
        this.variableCount = variableCount;
    }

    public int variableCount() {
        return variableCount;
    }
}
