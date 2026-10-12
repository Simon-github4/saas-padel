package ar.com.padelnec.ui;

/**
 * Pantalla que muestra turnos y se tiene que redibujar cuando entra una reserva
 * por la web mientras esta abierta.
 *
 * <p>El que se entera es {@link MainLayout}, en su poll: sin esto el aviso
 * sonaba y aparecia arriba, pero la agenda de abajo seguia con el hueco libre
 * hasta que alguien cambiaba de dia o recargaba.
 */
interface RefreshesOnNewBookings {

    void newBookingsArrived();
}
