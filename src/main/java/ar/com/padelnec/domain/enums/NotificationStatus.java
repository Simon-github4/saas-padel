package ar.com.padelnec.domain.enums;

public enum NotificationStatus {
    SENT,
    FAILED,
    /** No se intento enviar: el club no tiene canal configurado. */
    SKIPPED
}
