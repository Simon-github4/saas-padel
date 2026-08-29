package ar.com.padelnec.domain.enums;

public enum CancellationReason {
    /** El jugador cancelo desde el portal de gestion. */
    CUSTOMER,
    /** El club cancelo desde el panel. */
    CLUB,
    /** Vencio el plazo para pagar la sena en MercadoPago. */
    PAYMENT_TIMEOUT,
    /** Vencio el plazo para confirmar por WhatsApp. */
    CONFIRMATION_TIMEOUT
}
