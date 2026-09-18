package ar.com.padelnec.payment;

/**
 * Lo que se sabe de quien paga una sena, para mandarselo a MercadoPago.
 *
 * <p>El antifraude de MercadoPago decide con los datos que recibe: una order que
 * solo trae un mail inventado y un monto se parece mucho a un pago sospechoso, y
 * se rechazaba con "Te protegimos de un pago sospechoso" a jugadores reales. Todo
 * es opcional porque cada dato falta en algun camino (el invitado no tiene cuenta,
 * un telefono extranjero no siempre separa el codigo de area).
 *
 * <p>No lleva la IP ni el alta de la cuenta del jugador: la API de Orders no tiene
 * campo para la IP, y el alta va en {@code additional_info} con claves planas
 * ({@code "payer.registration_date"}) que el SDK 3.7.0 no sabe armar -las anida,
 * y MercadoPago rechaza la order entera con 400-.
 *
 * @param email mail verificado de la cuenta del jugador; null si reservo como
 *              invitado, y ahi se usa el sintetico
 */
public record DepositPayer(String firstName, String lastName, String email,
                           String phoneAreaCode, String phoneNumber) {
}
