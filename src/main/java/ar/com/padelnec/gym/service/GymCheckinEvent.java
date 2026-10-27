package ar.com.padelnec.gym.service;

import java.util.UUID;

/**
 * Un socio entro escaneando el QR. Se publica como evento para que el panel se entere
 * recien cuando el ingreso quedo escrito en la base (ver {@link GymCheckinFeed}).
 *
 * @param owesTheMonth entro por la gracia, con el mes impago: es lo que el mostrador tiene
 *                     que cobrarle
 */
public record GymCheckinEvent(UUID clubId, String memberName, String sedeName, boolean owesTheMonth) {
}
