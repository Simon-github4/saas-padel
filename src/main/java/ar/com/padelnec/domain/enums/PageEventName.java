package ar.com.padelnec.domain.enums;

/**
 * Catalogo cerrado de lo que se registra del recorrido de un visitante.
 *
 * <p>Cerrado a proposito: el endpoint que los recibe es publico y sin sesion, asi
 * que lo que no este aca se descarta en vez de guardarse. Agregar un evento es
 * agregar un valor y la columna que necesite, no aceptar cualquier cosa que
 * mande el navegador.
 *
 * <p>No hay un evento de "no reservo": eso se deduce por ausencia de
 * {@link #BOOKING_CREATED} en la sesion. Nadie avisa que se va de una pagina.
 */
public enum PageEventName {
    /** Entro a una ruta. Es el unico que emite todas las paginas. */
    VIEW,
    /** Ejecuto una busqueda global, con sus filtros y cuantos turnos le volvieron. */
    SEARCH,
    /** Toco un resultado de la busqueda global y se fue a la ficha de ese club. */
    SEARCH_RESULT_CLICK,
    /** Avanzo (o volvio) dentro del flujo de reserva de un club: dia, hora, datos. */
    CLUB_STEP,
    /** Eligio un horario concreto de la grilla. */
    SLOT_CLICK,
    /** Mando el formulario de reserva, con la forma de pago que eligio. */
    CHECKOUT_SUBMIT,
    /** Reservo. Es el final del embudo y trae el id de la reserva real. */
    BOOKING_CREATED,
    /** El checkout fallo. Distinto de abandonar: quiso y no pudo. */
    BOOKING_FAILED,
    /** Vino de la busqueda global y el turno ya estaba tomado al llegar. */
    LINK_EXPIRED,
    /** Se anoto en la lista de espera de un horario lleno. */
    WAITLIST_JOINED
}
