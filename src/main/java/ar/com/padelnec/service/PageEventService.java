package ar.com.padelnec.service;

import ar.com.padelnec.config.AppProperties;
import ar.com.padelnec.domain.PageEvent;
import ar.com.padelnec.domain.enums.PageEventName;
import ar.com.padelnec.repository.PageEventRepository;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.web.dto.PageEventDtos.TrackRequest;
import ar.com.padelnec.web.dto.PageEventDtos.TrackedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Guarda el recorrido que hace un visitante por la app del jugador.
 *
 * <p>La regla que gobierna todo lo de aca: medir no puede romper ni ensuciar. Un
 * evento con un dato incomprensible se guarda sin ese dato, uno con un nombre que
 * no esta en el catalogo se descarta, y nada de esto le devuelve un error a la
 * app -- el navegador manda los eventos con {@code sendBeacon} y ni siquiera mira
 * la respuesta.
 *
 * <p>La normalizacion de la ruta no es cosmetica: {@code /manage/:token} y sus
 * hermanos llevan en la URL el token que autoriza el turno. El navegador ya manda
 * la ruta normalizada; que el servidor la vuelva a normalizar es lo que garantiza
 * que ningun token termine escrito en esta tabla.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageEventService {

    /**
     * Rutas de la SPA cuyo segundo segmento es un token secreto. Se guarda la
     * forma, no el valor.
     */
    private static final Set<String> TOKEN_ROUTES =
            Set.of("manage", "confirm", "turno", "reset-password");

    /** Rutas sin parametros: se guardan tal cual. */
    private static final Set<String> PLAIN_ROUTES =
            Set.of("buscar", "login", "account", "forgot-password", "privacidad", "terminos");

    /** Lo que no es ninguna ruta conocida. Que exista ya es informacion. */
    private static final String OTHER = "/otro";

    private static final Pattern BOTS = Pattern.compile(
            "bot|crawl|spider|slurp|facebookexternalhit|headless|lighthouse|preview|scrape",
            Pattern.CASE_INSENSITIVE);

    /**
     * Telefono o computadora, nada mas fino. Las tablets no se distinguen de forma
     * confiable -- el iPad moderno se anuncia como escritorio -- y una categoria
     * que miente es peor que no tenerla.
     */
    private static final Pattern MOBILE = Pattern.compile(
            "mobi|android|iphone|ipod", Pattern.CASE_INSENSITIVE);

    private final PageEventRepository pageEventRepository;
    private final TenantRepository tenantRepository;
    private final AppProperties properties;

    /**
     * Sin {@code @Transactional} a proposito, aunque toque la base dos veces.
     *
     * <p>El {@code saveAll} del repositorio abre y cierra la suya, que es lo que
     * hace que el {@code catch} de mas abajo sirva: dentro de una transaccion
     * propia, la violacion de unicidad no aparece al guardar sino al confirmar, ya
     * fuera de este metodo, y ademas dejaria la transaccion marcada para
     * deshacerse. Que la lectura previa y la escritura no sean atomicas no importa:
     * esa lectura es un atajo para no chocar contra la unicidad, no una garantia.
     */
    public void record(TrackRequest request, String userAgent) {
        if (!properties.getAnalytics().isEnabled()) {
            return;
        }
        if (userAgent != null && BOTS.matcher(userAgent).find()) {
            // Un crawler no es una visita. Google entra a la ficha de cada club
            // todos los dias, y eso solo alcanza para inflar el tope del embudo.
            return;
        }

        List<PageEvent> pending = build(request, device(userAgent));
        if (pending.isEmpty()) {
            return;
        }
        try {
            pageEventRepository.saveAll(pending);
        } catch (DataIntegrityViolationException ex) {
            // Dos envios del mismo lote cruzados en el tiempo: el filtro previo por
            // seq los vio a los dos como nuevos y la unicidad de la base corto el
            // empate. Los eventos ya quedaron guardados por el otro envio; lo que se
            // pierde es a lo sumo algun evento nuevo que viajaba en el mismo lote,
            // que es un precio razonable por no complicar la ingesta.
            log.debug("Lote de eventos repetido para la visita {}", request.sessionId());
        }
    }

    private List<PageEvent> build(TrackRequest request, String device) {
        Set<Integer> alreadyStored = new HashSet<>(pageEventRepository.findStoredSeqs(
                request.sessionId(), request.events().stream().map(TrackedEvent::seq).toList()));
        Set<Integer> seen = new HashSet<>();
        Map<String, UUID> clubIds = new HashMap<>();
        List<PageEvent> events = new ArrayList<>();

        for (TrackedEvent incoming : request.events()) {
            if (alreadyStored.contains(incoming.seq()) || !seen.add(incoming.seq())) {
                continue;
            }
            Optional<PageEventName> name = name(incoming.name());
            if (name.isEmpty()) {
                continue;
            }
            events.add(toEntity(request, incoming, name.get(), device, clubIds));
        }
        return events;
    }

    private PageEvent toEntity(TrackRequest request, TrackedEvent incoming, PageEventName name,
                               String device, Map<String, UUID> clubIds) {
        PageEvent event = new PageEvent();
        event.setSessionId(request.sessionId());
        event.setSeq(incoming.seq());
        event.setName(name);
        event.setPath(normalizePath(incoming.path()));
        event.setClubId(clubId(incoming.clubSlug(), clubIds));
        event.setFromSearch(Boolean.TRUE.equals(incoming.fromSearch()));
        event.setReferrerHost(trim(request.referrer(), 120));
        event.setUtmSource(trim(request.utmSource(), 60));
        event.setDevice(device);

        event.setResults(positive(incoming.results()));
        event.setSearchDate(parse(incoming.date(), LocalDate::parse));
        event.setTimeFrom(parse(incoming.from(), LocalTime::parse));
        event.setTimeTo(parse(incoming.to(), LocalTime::parse));
        event.setClubsFilter(trim(incoming.clubs(), 200));
        event.setSlotAt(parse(incoming.slotAt(), Instant::parse));
        event.setStep(step(incoming.step()));
        event.setPaymentChoice(paymentChoice(incoming.paymentChoice()));
        event.setBookingId(parse(incoming.bookingId(), UUID::fromString));
        event.setDetail(trim(incoming.detail(), 60));
        return event;
    }

    /**
     * La ruta reducida a su forma: {@code /club/necochea-padel} es
     * {@code /club/:slug}, y {@code /manage/8f3a...} es {@code /manage/:token}.
     *
     * <p>Lo que no reconoce cae en {@code /otro} en vez de guardarse tal cual: si
     * manana aparece una ruta nueva sin instrumentar, el peor caso es una fila que
     * no dice de donde, no un secreto escrito en la bitacora.
     */
    private String normalizePath(String raw) {
        String path = raw.split("[?#]", 2)[0];
        String[] segments = path.split("/");
        // split() de "/buscar" da ["", "buscar"]; el de "/" da un arreglo vacio.
        if (segments.length < 2 || segments[1].isBlank()) {
            return "/";
        }
        String first = segments[1].toLowerCase(Locale.ROOT);
        if (PLAIN_ROUTES.contains(first)) {
            return "/" + first;
        }
        if (TOKEN_ROUTES.contains(first)) {
            return "/" + first + "/:token";
        }
        if ("club".equals(first) && segments.length > 2) {
            return "/club/:slug";
        }
        return OTHER;
    }

    /**
     * El club sale del slug y no de lo que diga el navegador: un slug inventado
     * queda como visita sin club, no como una fila apuntando a cualquier lado.
     */
    private UUID clubId(String slug, Map<String, UUID> resolved) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return resolved.computeIfAbsent(slug, key -> tenantRepository
                .findBySlugIgnoreCaseAndActiveTrue(key)
                .map(club -> club.getId())
                .orElse(null));
    }

    private Optional<PageEventName> name(String raw) {
        try {
            return Optional.of(PageEventName.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String paymentChoice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PaymentChoice.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Short step(Integer raw) {
        // Fuera de 1..3 no es un paso del flujo, y el CHECK de la tabla lo
        // rechazaria arrastrando consigo al resto del lote.
        if (raw == null || raw < 1 || raw > 3) {
            return null;
        }
        return raw.shortValue();
    }

    private Integer positive(Integer raw) {
        return raw == null || raw < 0 ? null : raw;
    }

    private String trim(String raw, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Parseo tolerante: un dato accesorio mal formado se pierde, no rompe nada. */
    private <T> T parse(String raw, Function<String, T> parser) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return parser.apply(raw.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return MOBILE.matcher(userAgent).find() ? "mobile" : "desktop";
    }
}
