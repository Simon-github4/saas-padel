package ar.com.padelnec.domain.enums;

public enum UserRole {
    /** Soporte de la plataforma: no pertenece a ningun club. */
    SUPER_ADMIN,
    /** Dueno del club: configuracion, precios y usuarios. */
    OWNER,
    /** Personal de mostrador: agenda y cobros, sin acceso a configuracion. */
    STAFF
}
