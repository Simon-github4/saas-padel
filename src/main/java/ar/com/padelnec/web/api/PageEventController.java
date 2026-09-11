package ar.com.padelnec.web.api;

import ar.com.padelnec.service.PageEventService;
import ar.com.padelnec.web.dto.PageEventDtos.TrackRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe el recorrido que la app del jugador va anotando.
 *
 * <p>Cuelga de {@code /api/public} como el resto de lo que consume la app, pero
 * no lleva slug: la portada y la busqueda global no son de ningun club, y el club
 * de los eventos que si lo tienen viaja adentro de cada uno.
 *
 * <p>Devuelve 204 siempre que el lote este bien formado, incluso si termino
 * descartando todo. Del otro lado hay un {@code sendBeacon} que no mira la
 * respuesta y una app a la que no le sirve enterarse de nada de esto.
 */
@RestController
@RequestMapping("/api/public/events")
@RequiredArgsConstructor
public class PageEventController {

    private final PageEventService pageEventService;
    private final EventRateLimiter rateLimiter;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void track(@Valid @RequestBody TrackRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.allow(httpRequest.getRemoteAddr(), request.events().size())) {
            return;
        }
        pageEventService.record(request, httpRequest.getHeader(HttpHeaders.USER_AGENT));
    }
}
