package ar.com.padelnec.domain.enums;

public enum AlertType {
    /** El jugador cancelo un turno que ya tenia sena paga: el club debe devolver a mano. */
    REFUND_REQUIRED,
    /** Llego un pago aprobado para una reserva que ya habia vencido o estaba cancelada. */
    ORPHAN_PAYMENT,
    /** No se pudo entregar un WhatsApp al jugador. */
    NOTIFICATION_FAILED,
    /** Un turno fijo no pudo materializarse porque la franja estaba ocupada. */
    RECURRING_CONFLICT
}
