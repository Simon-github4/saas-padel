package ar.com.padelnec.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Turnos libres de varios clubes para un dia y una franja horaria.
 *
 * <p>Responde la pregunta con la que llega el jugador que todavia no eligio club:
 * "hoy a la noche, donde sea". Por eso {@link #matches()} no viene agrupada por club
 * sino ordenada por horario: primero cuando puede jugar, y el club es un dato mas de
 * cada turno.
 *
 * <p>Es deliberadamente mas flaca que {@link AvailabilityResponse}: aquella trae
 * servicios, colores, medios de pago y el detalle de cada cancha, que multiplicados
 * por todos los clubes serian kilobytes que esta pantalla no usa.
 */
public record CourtSearchResponse(
        LocalDate date,
        List<ClubOption> clubs,
        List<Match> matches) {

    /**
     * Un club para el filtro. Viaja en la misma respuesta que los resultados porque
     * sale de la misma consulta: pedirlo aparte seria un viaje de mas para nada.
     */
    public record ClubOption(String slug, String name, String city, int bookingHorizonDays) {
    }

    /** Un horario libre en un club concreto. */
    public record Match(
            String clubSlug,
            String clubName,
            String city,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Instant startsAt,
            /** El mas barato de las canchas libres: el "desde $X" de la tarjeta. */
            BigDecimal cheapestPrice,
            int playersPerCourt,
            int freeCourts,
            boolean promo) {
    }
}
