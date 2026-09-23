package ar.com.padelnec.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.CourtRoof;
import ar.com.padelnec.domain.enums.CourtSurface;
import ar.com.padelnec.domain.enums.CourtWall;
import ar.com.padelnec.repository.TenantRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.dto.AvailabilityResponse.CourtAvailability;
import ar.com.padelnec.web.dto.AvailabilityResponse.SlotView;
import ar.com.padelnec.web.dto.CourtSearchResponse;
import ar.com.padelnec.web.dto.CourtSearchResponse.ClubOption;
import ar.com.padelnec.web.dto.CourtSearchResponse.Match;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Busca turnos libres en varios clubes a la vez.
 *
 * <p>Existe para el jugador que todavia no eligio club: quiere jugar hoy a la noche
 * y le da igual la cancha. La grilla por club no responde eso, porque exige saber de
 * antemano a cual entrar.
 *
 * <p><b>Esta clase no lleva {@code @Transactional} y no es un descuido.</b> Hibernate
 * fija el club al crear la sesion, asi que cambiar el {@link TenantContext} con una
 * transaccion ya abierta no cambia el filtro y las consultas saldrian vacias. El
 * recorrido por clubes tiene que quedar afuera y delegar cada club en un bean que si
 * sea transaccional; aca ese bean es {@link AvailabilityService}. Es el mismo reparto
 * que hacen {@code BookingExpiryJob} y {@code BookingExpiryWorker}.
 *
 * <p>Reusar el motor de disponibilidad cuesta unas cuatro consultas por club (via
 * {@link AvailabilityService#freeSlotsFor}, que no arma el resumen del club — nombre,
 * portada, colores — que esta busqueda ni mira), y podria resolverse en menos con un
 * SQL a medida. No se hace: ahi viven el horario del club, los bloqueos, los turnos
 * fijos, el horizonte de reserva y la resolucion de precios, y una segunda
 * implementacion de todo eso se desincroniza el dia que cambie una regla. Si algun dia
 * son cientos de clubes, este javadoc es el lugar donde empezar a mirar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourtSearchService {

    /**
     * El club de ejemplo al que lleva "Ver un club real" desde la landing comercial.
     * Tiene datos de prueba, asi que no se ofrece a quien busca donde jugar: solo se
     * llega por ese link.
     */
    public static final String DEMO_CLUB_SLUG = "simon";

    private final TenantRepository tenantRepository;
    private final AvailabilityService availabilityService;

    /** Si el club se muestra en los listados generales (busqueda, sitemap). */
    public static boolean listedPublicly(Tenant club) {
        return !DEMO_CLUB_SLUG.equalsIgnoreCase(club.getSlug());
    }

    /**
     * Que tiene que tener la cancha para que el turno cuente. Un campo nulo es "me da
     * igual": el jugador que no eligio paredes ve blindex y pared.
     */
    public record CourtFilter(CourtWall wall, CourtSurface surface, CourtRoof roof) {

        public static final CourtFilter ANY = new CourtFilter(null, null, null);

        boolean accepts(CourtAvailability court) {
            return (wall == null || wall == court.wall())
                    && (surface == null || surface == court.surface())
                    && (roof == null || roof == court.roof());
        }
    }

    /** Turnos libres del dia entre {@code from} y {@code to}, sin mirar como es la cancha. */
    public CourtSearchResponse search(LocalDate date, LocalTime from, LocalTime to,
                                      Set<String> slugs) {
        return search(date, from, to, slugs, CourtFilter.ANY);
    }

    /**
     * Turnos libres del dia entre {@code from} y {@code to}, en los clubes indicados y
     * solo en las canchas que pasan el filtro.
     *
     * @param slugs clubes a mirar; vacio significa todos.
     */
    public CourtSearchResponse search(LocalDate date, LocalTime from, LocalTime to,
                                      Set<String> slugs, CourtFilter filter) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La hora de inicio no puede ser posterior a la de fin.");
        }

        List<Tenant> active = tenantRepository.findAllByActiveTrue().stream()
                .filter(CourtSearchService::listedPublicly)
                .toList();
        Set<String> wanted = slugs.stream()
                .map(slug -> slug.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<Match> matches = new ArrayList<>();
        for (Tenant club : active) {
            if (!wanted.isEmpty() && !wanted.contains(club.getSlug().toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                matches.addAll(matchesOf(club, date, from, to, filter));
            } catch (RuntimeException ex) {
                // Un club con un problema no puede dejar sin resultados a los demas:
                // el jugador que busca "donde sea" prefiere ver los otros cuatro.
                log.error("Fallo la busqueda de turnos del club {}", club.getSlug(), ex);
            }
        }

        // Por horario y no por club: quien busca sin preferencia de lugar lee la lista
        // como una agenda, de lo mas temprano a lo mas tarde.
        matches.sort(Comparator.comparing(Match::startsAt).thenComparing(Match::clubName));

        return new CourtSearchResponse(date, options(active), matches);
    }

    private List<Match> matchesOf(Tenant club, LocalDate date, LocalTime from, LocalTime to,
                                  CourtFilter filter) {
        List<SlotView> slots = TenantContext.callAs(club.getId(),
                () -> availabilityService.freeSlotsFor(club, date));

        List<Match> found = new ArrayList<>();
        for (SlotView slot : slots) {
            if (!withinRange(slot.startTime(), from, to)) {
                continue;
            }
            // Se filtra por cancha y no por horario: si de tres canchas libres solo una
            // es de blindex, el turno sigue apareciendo, con una cancha y a su precio.
            List<CourtAvailability> courts = slot.available().stream()
                    .filter(filter::accepts)
                    .toList();
            if (courts.isEmpty()) {
                continue;
            }
            found.add(new Match(
                    club.getSlug(),
                    club.getName(),
                    club.getCity(),
                    slot.startTime(),
                    slot.endTime(),
                    slot.startsAt(),
                    courts.stream().map(CourtAvailability::price).min(BigDecimal::compareTo).orElseThrow(),
                    club.getPlayersPerCourt(),
                    courts.size(),
                    slot.promo(),
                    courts.stream().map(CourtAvailability::wall)
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(CourtWall.class))),
                    courts.stream().map(CourtAvailability::surface)
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(CourtSurface.class))),
                    courts.stream().map(CourtAvailability::roof)
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(CourtRoof.class)))));
        }
        return found;
    }

    /**
     * El rango se mide contra la hora de <b>inicio</b> del turno: quien pide de 18:00 a
     * 21:30 quiere empezar a jugar en esa franja, no que el turno ya haya terminado a
     * las 21:30.
     */
    private boolean withinRange(LocalTime startTime, LocalTime from, LocalTime to) {
        return !startTime.isBefore(from) && !startTime.isAfter(to);
    }

    private List<ClubOption> options(List<Tenant> clubs) {
        return clubs.stream()
                .sorted(Comparator.comparing(Tenant::getName))
                .map(club -> {
                    // Las dos o ninguna: media coordenada no ubica a nadie.
                    boolean located = club.getLatitude() != null && club.getLongitude() != null;
                    return new ClubOption(club.getSlug(), club.getName(), club.getCity(),
                            club.getBookingHorizonDays(), heroImageOf(club),
                            located ? club.getLatitude() : null,
                            located ? club.getLongitude() : null);
                })
                .toList();
    }

    /** Una URL en blanco cuenta como sin foto: la tarjeta pone las iniciales en vez de una imagen rota. */
    private String heroImageOf(Tenant club) {
        String url = club.getHeroImageUrl();
        return url == null || url.isBlank() ? null : url;
    }
}
