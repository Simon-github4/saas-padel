package ar.com.padelnec.service;

import java.util.UUID;

/**
 * Aviso pendiente de mandarle al jugador.
 *
 * <p>Se publica como evento en vez de llamar al proveedor en el momento porque los
 * WhatsApp tienen que salir recien cuando la reserva quedo firme en la base. Si se
 * mandaran dentro de la transaccion, un choque contra la restriccion de
 * solapamiento dejaria al jugador con un mensaje de turno confirmado que no existe.
 */
public record BookingEvent(UUID clubId, UUID bookingId, Kind kind) {

    public enum Kind {
        /** Link de 15 minutos para confirmar una reserva sin sena. */
        CONFIRMATION_REQUEST,
        /** Turno de palabra ya confirmado. */
        CONFIRMED_UNPAID,
        /** Sena acreditada por MercadoPago. */
        CONFIRMED_PAID,
        /** Se vencio el plazo y la cancha volvio a la grilla. */
        CONFIRMATION_EXPIRED,
        /** El club dio de baja el turno. */
        CANCELLED_BY_CLUB,
        /** Recordatorio previo al turno. */
        REMINDER
    }

    public static BookingEvent of(UUID clubId, UUID bookingId, Kind kind) {
        return new BookingEvent(clubId, bookingId, kind);
    }
}
