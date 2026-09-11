package ar.com.padelnec.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Contratos de la bitacora de visitas. */
public final class PageEventDtos {

    private PageEventDtos() {
    }

    /**
     * Un lote de eventos de una misma visita.
     *
     * <p>Van juntos porque la app los acumula y los manda de a tandas: una visita
     * normal genera una decena, y una peticion por cada una desperdicia bateria del
     * telefono y ancho de banda para nada.
     *
     * <p>El origen ({@code referrer}, {@code utmSource}) viaja en el lote y no en
     * cada evento porque es de la visita entera, no de un paso.
     */
    public record TrackRequest(
            @NotNull UUID sessionId,
            @Size(max = 120) String referrer,
            @Size(max = 60) String utmSource,
            @NotEmpty @Size(max = 30) List<@Valid TrackedEvent> events) {
    }

    /**
     * Un paso del recorrido.
     *
     * <p>Solo se validan los campos que hacen al evento identificable. Las
     * propiedades se parsean con tolerancia y quedan nulas si no se entienden: una
     * bitacora no puede tirar una peticion del jugador por un dato accesorio mal
     * formado, y ademas el navegador nunca ve esta respuesta.
     */
    public record TrackedEvent(
            @NotNull @Min(1) Integer seq,
            @NotBlank @Size(max = 30) String name,
            @NotBlank @Size(max = 200) String path,
            @Size(max = 60) String clubSlug,
            // Envueltos y no primitivos: un campo que falta no se puede meter en un
            // primitivo y revienta el parseo entero con un 500, en vez de quedar en
            // su valor por defecto o dar un 400 claro.
            Boolean fromSearch,
            Integer results,
            @Size(max = 10) String date,
            @Size(max = 5) String from,
            @Size(max = 5) String to,
            @Size(max = 200) String clubs,
            @Size(max = 40) String slotAt,
            Integer step,
            @Size(max = 20) String paymentChoice,
            @Size(max = 40) String bookingId,
            @Size(max = 60) String detail) {
    }
}
