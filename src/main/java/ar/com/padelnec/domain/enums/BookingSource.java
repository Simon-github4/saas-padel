package ar.com.padelnec.domain.enums;

public enum BookingSource {
    /** Cargada por el jugador desde la app. */
    WEB,
    /** Cargada a mano por el club (telefono, WhatsApp, mostrador). */
    ADMIN,
    /** Generada automaticamente a partir de un turno fijo. */
    RECURRING
}
