package ar.com.padelnec.notification.whatsapp;

/**
 * Mensajes que el sistema le manda al jugador.
 *
 * <p>Existen como enum y no como strings sueltos por una restriccion real de
 * WhatsApp: los mensajes que inicia el negocio (todos estos, porque el jugador no
 * viene escribiendo) solo se pueden mandar con una plantilla aprobada previamente
 * por Meta. Cada valor de aca corresponde a una plantilla cargada en el proveedor,
 * y el orden de las variables tiene que coincidir con el que se aprobo.
 *
 * <p>Todas terminan en un link a la pagina (el turno, o el club para elegir otro):
 * al cargarlas en el proveedor, cada plantilla lleva su link como ultima variable,
 * salvo la baja del club, donde va antes del telefono.
 */
public enum NotificationTemplate {

    /** Link de confirmacion del flujo sin pago. Variables: nombre, club, cancha, fecha, hora, minutos, link. */
    CONFIRMATION_REQUEST(7),

    /** Turno confirmado de palabra. Variables: nombre, club, cancha, fecha, hora, total, link. */
    BOOKING_CONFIRMED_UNPAID(7),

    /** Sena acreditada. Variables: nombre, club, cancha, fecha, hora, saldo, link. */
    BOOKING_CONFIRMED_PAID(7),

    /**
     * Vencio el plazo de confirmacion y se libero la cancha. Variables: nombre, club,
     * cancha, fecha, hora, link para reservarla de nuevo.
     */
    CONFIRMATION_EXPIRED(6),

    /**
     * El club dio de baja el turno. Variables: nombre, club, cancha, fecha, hora, link
     * al club en ese dia, telefono del club.
     */
    BOOKING_CANCELLED_BY_CLUB(7),

    /** Recordatorio previo al turno. Variables: nombre, club, cancha, fecha, hora, saldo, link. */
    BOOKING_REMINDER(7),

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
