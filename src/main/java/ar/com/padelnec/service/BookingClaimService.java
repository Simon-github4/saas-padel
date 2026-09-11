package ar.com.padelnec.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.repository.BookingRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ata a una cuenta recien abierta los turnos que este navegador reservo sin ella.
 *
 * <p>Existe por una promesa que la app hacia y no cumplia: despues de reservar
 * como invitado ofrece "guardar este turno en una cuenta", y hasta ahora crear la
 * cuenta ahi mismo dejaba el turno donde estaba.
 *
 * <p>Lo que habilita el reclamo es tener el token de gestion. Ese token es la
 * credencial con la que se ve y se cancela el turno, asi que quien lo tiene ya
 * manda sobre esa reserva: atarla a su cuenta no le suma ningun poder. Es
 * exactamente lo contrario del emparejamiento por telefono que habia antes, donde
 * el dato que se presentaba -- un numero que cualquiera puede escribir -- no
 * probaba nada.
 *
 * <p>Sin {@code @Transactional} a proposito, y por el mismo motivo que
 * {@code CourtSearchService}: cada turno puede ser de un club distinto, Hibernate
 * fija el club al abrir la sesion de persistencia, y con una transaccion ya
 * abierta cambiar el contexto no cambia el filtro. Por eso se entra a cada club
 * de a uno con {@link TenantContext#callAs}, y la escritura vive en
 * {@link BookingService#linkToAccount}, que es otro bean y por lo tanto si pasa
 * por el proxy transaccional.
 */
@Service
@RequiredArgsConstructor
public class BookingClaimService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    /**
     * @return cuantos turnos quedaron atados a la cuenta en esta llamada
     */
    public int claim(UUID accountId, List<String> managementTokens) {
        int claimed = 0;
        // Sin repetidos y en orden: el navegador puede mandar el mismo token dos
        // veces si el jugador reservo, volvio y reservo de nuevo.
        for (String token : new LinkedHashSet<>(managementTokens)) {
            if (token == null || token.isBlank()) {
                continue;
            }
            Optional<UUID> clubId = bookingRepository.findClubIdByAnyToken(token.trim());
            if (clubId.isEmpty()) {
                // Un token que no existe no es un error que valga la pena contarle a
                // nadie: puede ser de un turno que el club ya borro, o basura.
                continue;
            }
            boolean linked = TenantContext.callAs(clubId.get(),
                    () -> bookingService.linkToAccount(token.trim(), accountId));
            if (linked) {
                claimed++;
            }
        }
        return claimed;
    }
}
