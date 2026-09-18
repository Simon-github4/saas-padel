package ar.com.padelnec.payment;

import java.time.Instant;

/**
 * Lo que se sabe de quien paga una sena, para mandarselo a MercadoPago.
 *
 * <p>El antifraude de MercadoPago decide con los datos que recibe: una order que
 * solo trae un mail inventado y un monto se parece mucho a un pago sospechoso, y
 * se rechazaba con "Te protegimos de un pago sospechoso" a jugadores reales. Todo
 * es opcional porque cada dato falta en algun camino (el invitado no tiene cuenta,
 * un telefono extranjero no siempre separa el codigo de area).
 *
 * @param email        mail verificado de la cuenta del jugador; null si reservo
 *                     como invitado, y ahi se usa el sintetico
 * @param registeredAt alta de la cuenta del jugador; null si es invitado
 * @param ipAddress    IP desde la que se hizo la reserva
 */
public record DepositPayer(String firstName, String lastName, String email,
                           String phoneAreaCode, String phoneNumber,
                           Instant registeredAt, String ipAddress) {

    /** Sin nada: la order sale solo con el mail sintetico, como antes. */
    public static DepositPayer unknown() {
        return new DepositPayer(null, null, null, null, null, null, null);
    }
}
