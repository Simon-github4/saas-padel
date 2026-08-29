package ar.com.padelnec.domain.enums;

/** Ciclo de vida de una reserva. */
public enum BookingStatus {

    /** Reserva creada, esperando que acredite el pago en MercadoPago. Bloquea la grilla. */
    DRAFT,

    /** Reserva sin pago anticipado, esperando que el jugador toque el link de WhatsApp. Bloquea la grilla. */
    AWAITING_CONFIRMATION,

    /** Turno firme. */
    CONFIRMED,

    /** El turno se jugo. */
    COMPLETED,

    /** Anulada por el jugador, por el club o por vencimiento del plazo. Libera la grilla. */
    CANCELLED,

    /** El jugador no se presento. Libera la grilla para que el club pueda revenderla. */
    NO_SHOW;

    /** Estados que ocupan la cancha y por lo tanto participan de la restriccion de solapamiento. */
    public boolean occupiesSlot() {
        return this == DRAFT || this == AWAITING_CONFIRMATION || this == CONFIRMED || this == COMPLETED;
    }

    public boolean isCancellable() {
        return this == DRAFT || this == AWAITING_CONFIRMATION || this == CONFIRMED;
    }
}
