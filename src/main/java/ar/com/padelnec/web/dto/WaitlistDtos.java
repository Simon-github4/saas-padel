package ar.com.padelnec.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Contratos de la lista de espera. */
public final class WaitlistDtos {

    private WaitlistDtos() {
    }

    /** Lo que manda el jugador al anotarse en un horario lleno. */
    public record JoinWaitlistRequest(
            @NotNull(message = "Elegí un horario") Instant startTime,
            @NotBlank(message = "Necesitamos tu nombre")
            @Size(max = 120) String fullName,
            @NotBlank(message = "Necesitamos tu teléfono")
            @Size(max = 25) String phoneNumber) {
    }

    public record JoinWaitlistResponse(String message) {
    }
}
