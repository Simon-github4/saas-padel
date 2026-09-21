package ar.com.padelnec.gym.service;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.gym.domain.GymCheckin;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymMembership;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.gym.repository.GymSedeRepository;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import ar.com.padelnec.web.UnauthorizedSessionException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el ingreso de un socio y decide si corresponde.
 *
 * <p>El socio SIEMPRE viene de la sesion: quien llama pasa el {@code memberId}
 * que resolvio de la sesion, nunca uno que mando el cliente. Es lo que impide
 * registrar un ingreso "a nombre de otro". Solo el panel puede registrar a un
 * socio por id, con {@link #forceCheckIn}.
 */
@Service
@RequiredArgsConstructor
public class GymCheckinService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GymCheckinRepository checkinRepository;
    private final GymMembershipRepository membershipRepository;
    private final GymMemberRepository memberRepository;
    private final GymSedeRepository sedeRepository;
    private final GymBillingService billingService;
    private final TenantService tenantService;
    private final Clock clock;

    /**
     * Lo que ve el socio despues de escanear. {@code alreadyRegistered} es true
     * cuando ya tenia el ingreso de hoy: escanear dos veces no es un error.
     */
    public record CheckInResult(String sedeName, boolean alreadyRegistered, int weekUsed, int weekLimit,
                                LocalDate validUntil) {
    }

    /**
     * Ingreso del propio socio, escaneando el QR de la sede.
     *
     * @param location donde dice estar el celular, o null si no lo informo. Si la sede tiene
     *                 coordenadas cargadas es obligatoria: es lo que impide registrarse desde
     *                 lejos con una foto del QR
     */
    @Transactional
    public CheckInResult checkIn(UUID memberId, String qrToken, GymLocation.Point location) {
        GymMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedSessionException("Tu sesión venció. Volvé a iniciar sesión."));
        if (!member.isEnabled()) {
            throw new BusinessRuleException("Tu acceso está deshabilitado. Consultá en el mostrador.");
        }

        String token = qrToken == null ? "" : qrToken.trim();
        GymSede sede = sedeRepository.findByQrTokenAndActiveTrue(token)
                .orElseThrow(() -> new BusinessRuleException(
                        "Ese código QR no es válido. Escaneá el cartel de la entrada."));
        return register(member, sede, null, false, location);
    }

    /**
     * Ingreso cargado por el mostrador, siempre para HOY. Puede pasar por encima del
     * tope semanal (queda marcado como excepcion), pero no exige menos que el
     * escaneo en lo demas: sin cuota vigente no hay ingreso, hay que cobrarla.
     *
     * <p>No admite fecha: un ingreso cargado despues de cerrado un periodo cambiaria
     * lo que ya se liquido.
     */
    @Transactional
    public CheckInResult forceCheckIn(UUID memberId, UUID sedeId, UUID staffUserId) {
        GymMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ese socio."));
        if (!member.isEnabled()) {
            throw new BusinessRuleException("Ese socio está deshabilitado.");
        }
        GymSede sede = sedeRepository.findById(sedeId)
                .orElseThrow(() -> new BusinessRuleException("Elegí la sede del ingreso."));
        // El mostrador esta en la sede y ve al socio: no se le pide ubicacion.
        return register(member, sede, staffUserId, true, null);
    }

    private CheckInResult register(GymMember member, GymSede sede, UUID staffUserId, boolean allowOverride,
                                   GymLocation.Point location) {
        ZoneId zone = tenantService.requireCurrent().zoneId();
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();

        GymBillingService.Status status = billingService.status(member, today);
        GymMembership membership = status.plan();
        if (membership == null) {
            throw new BusinessRuleException("No tenés una cuota vigente. Consultá en el mostrador.");
        }
        if (!status.started()) {
            throw new BusinessRuleException("Tu cuota empieza el " + DAY.format(membership.getStartsOn()) + ".");
        }
        if (!status.canEnter()) {
            throw new BusinessRuleException("Adeudás " + status.monthsLate() + " cuotas. Renová en el mostrador.");
        }
        if (!membership.allows(sede)) {
            throw new BusinessRuleException("Tu cuota no incluye " + sede.getName() + ".");
        }

        // La cuota queda valida hasta fin de mes, o mas alla si el socio pago por adelantado.
        LocalDate validUntil = status.paidUntil() != null ? status.paidUntil() : status.periodEnd();

        GymWeek week = GymWeek.of(today);
        Optional<GymCheckin> existing = checkinRepository.findByMemberIdAndLocalDate(member.getId(), today);
        long used = checkinRepository.countByMemberIdAndLocalDateBetween(
                member.getId(), week.monday(), week.sunday());
        if (existing.isPresent()) {
            return result(existing.get().getSede().getName(), true, used, membership, validUntil);
        }

        // Recien aca, cuando el ingreso es nuevo: escanear de nuevo lo que ya estaba registrado
        // no necesita pedirle nada al socio.
        int distance = staffUserId == null && sede.hasLocation()
                ? verifyLocation(sede, location)
                : GymCheckinRepository.NO_DISTANCE;

        boolean overLimit = used >= membership.getDaysPerWeek();
        if (overLimit && !allowOverride) {
            String alreadyUsed = membership.getDaysPerWeek() == 1
                    ? "Ya usaste tu día"
                    : "Ya usaste tus " + membership.getDaysPerWeek() + " días";
            throw new BusinessRuleException(alreadyUsed + " de esta semana. La semana se renueva el lunes; "
                    + "si necesitás entrar hoy, hablá con el mostrador.");
        }

        UUID clubId = TenantContext.require();
        int inserted = staffUserId == null
                ? checkinRepository.insertIfAbsent(clubId, member.getId(), membership.getId(), sede.getId(),
                        now, today, distance, false)
                : checkinRepository.insertIfAbsentByStaff(clubId, member.getId(), membership.getId(),
                        sede.getId(), now, today, distance, overLimit, staffUserId);

        // Si otro pedido se adelanto entre la lectura y el insert, el ingreso ya existe: es un
        // "ya registrado", no un error.
        return result(sede.getName(), inserted == 0, used + inserted, membership, validUntil);
    }

    /**
     * El socio tiene que estar cerca de la sede. El radio es holgado (200 m por defecto) porque el
     * GPS falla adentro de un edificio; la ubicacion la informa el celular, asi que no frena a
     * quien la falsifique a proposito, pero si el "lo hago desde mi casa con la foto del QR".
     *
     * @return a cuantos metros estaba, para dejarlo de constancia
     */
    private static int verifyLocation(GymSede sede, GymLocation.Point location) {
        if (location == null) {
            throw new LocationRequiredException("Necesitamos tu ubicación para registrar el ingreso en "
                    + sede.getName() + ". Activá el permiso de ubicación del navegador y probá de nuevo.");
        }
        double meters = GymLocation.distanceMeters(sede.getLatitude(), sede.getLongitude(),
                location.latitude(), location.longitude());
        if (meters > sede.getRadiusMeters()) {
            throw new BusinessRuleException("Parece que no estás en " + sede.getName() + ": estás a "
                    + describeDistance(meters) + ". Para registrar tu ingreso tenés que estar a menos de "
                    + sede.getRadiusMeters() + " m.");
        }
        return (int) Math.round(meters);
    }

    private static String describeDistance(double meters) {
        return meters >= 1000
                ? "unos %.1f km".formatted(meters / 1000).replace('.', ',')
                : "unos " + Math.round(meters / 10) * 10 + " m";
    }

    private static CheckInResult result(String sedeName, boolean alreadyRegistered, long weekUsed,
                                        GymMembership membership, LocalDate validUntil) {
        return new CheckInResult(sedeName, alreadyRegistered, (int) weekUsed, membership.getDaysPerWeek(),
                validUntil);
    }
}
