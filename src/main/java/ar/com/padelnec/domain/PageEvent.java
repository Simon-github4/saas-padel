package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.PageEventName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Un paso del recorrido de un visitante por la app del jugador.
 *
 * <p>Es una bitacora, no una entidad de negocio: se escribe una vez y no se
 * modifica nunca. Su valor aparece agregada -- el embudo de una pagina, la
 * conversion de un club -- no fila por fila.
 *
 * <p>No extiende {@link TenantScopedEntity} a proposito. Ese padre exige un club
 * en toda fila y deja que Hibernate filtre por el, y aca la mitad del recorrido
 * (la portada, la busqueda global) ocurre antes de que el jugador elija uno. El
 * club queda como una columna nula-permitida y las consultas del panel filtran a
 * mano.
 *
 * <p>{@code createdAt} es el instante en que el servidor recibio el evento. El
 * orden real dentro de una visita lo da {@link #seq}, que lo pone el navegador:
 * los eventos viajan en lotes y varios comparten el mismo instante de recepcion.
 */
@Entity
@Table(name = "page_event")
@Getter
@Setter
public class PageEvent extends BaseEntity {

    /** Sesion anonima del navegador. Muere con la pestaña; no identifica a nadie. */
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PageEventName name;

    /** Ruta normalizada ({@code /club/:slug}), nunca la URL con sus tokens. */
    @Column(nullable = false, length = 120)
    private String path;

    /** Nulo fuera de la ficha de un club. */
    @Column(name = "club_id")
    private UUID clubId;

    /** Entro a la ficha del club con el {@code ?fecha=&hora=} de la busqueda global. */
    @Column(name = "from_search", nullable = false)
    private boolean fromSearch;

    @Column(name = "referrer_host", length = 120)
    private String referrerHost;

    @Column(name = "utm_source", length = 60)
    private String utmSource;

    /** {@code mobile} o {@code desktop}, deducido del User-Agent. */
    @Column(length = 10)
    private String device;

    /** Cuantos turnos devolvio una busqueda. Cero es el caso interesante. */
    @Column(name = "results")
    private Integer results;

    @Column(name = "search_date")
    private LocalDate searchDate;

    @Column(name = "time_from")
    private LocalTime timeFrom;

    @Column(name = "time_to")
    private LocalTime timeTo;

    /** Slugs de los clubes filtrados, separados por coma. Vacio es "todos". */
    @Column(name = "clubs_filter", length = 200)
    private String clubsFilter;

    /** Horario del turno involucrado, cuando el evento habla de uno. */
    @Column(name = "slot_at")
    private Instant slotAt;

    /** Paso del flujo de reserva: 1 dia, 2 hora, 3 datos. */
    @Column(name = "step")
    private Short step;

    @Column(name = "payment_choice", length = 20)
    private String paymentChoice;

    /**
     * La reserva que cerro el recorrido. Es la unica columna que sale de la
     * bitacora hacia el dominio, y la que permite preguntar si el que entro por
     * la busqueda global termino pagando.
     *
     * <p>Un UUID pelado y no una relacion: el id lo manda el navegador, y una
     * foranea dejaria que un id inventado voltee el lote entero de eventos.
     */
    @Column(name = "booking_id")
    private UUID bookingId;

    /** Dato corto del evento: codigo de error, posicion en la lista de resultados. */
    @Column(length = 60)
    private String detail;
}
