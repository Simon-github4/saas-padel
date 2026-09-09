package ar.com.padelnec.web.api;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.repository.TenantHeroImageRepository;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.ManagedBooking;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.CheckoutService;
import ar.com.padelnec.service.CheckoutService.CheckoutResult;
import ar.com.padelnec.service.CourtSearchService;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.service.WaitlistService;
import ar.com.padelnec.web.dto.AvailabilityResponse;
import ar.com.padelnec.web.dto.BookingDtos.BookingDetailResponse;
import ar.com.padelnec.web.dto.BookingDtos.BookingShareResponse;
import ar.com.padelnec.web.dto.BookingDtos.CancellationResponse;
import ar.com.padelnec.web.dto.BookingDtos.CreateBookingRequest;
import ar.com.padelnec.web.dto.BookingDtos.CreateBookingResponse;
import ar.com.padelnec.web.dto.CourtSearchResponse;
import ar.com.padelnec.web.dto.WaitlistDtos.JoinWaitlistRequest;
import ar.com.padelnec.web.dto.WaitlistDtos.JoinWaitlistResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API que consume la app del jugador.
 *
 * <p>Los endpoints de grilla y checkout se resuelven por el slug del club. Los de
 * gestion, en cambio, se identifican solo con el token, porque el link que le llego
 * al jugador por WhatsApp no lleva el slug.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicBookingController {

    private final TenantService tenantService;
    private final TenantHeroImageRepository tenantHeroImageRepository;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;
    private final CheckoutService checkoutService;
    private final CourtSearchService courtSearchService;
    private final WaitlistService waitlistService;
    private final NotificationService notificationService;
    private final BookingRateLimiter rateLimiter;
    private final Clock clock;

    /**
     * Turnos libres en varios clubes a la vez.
     *
     * <p>No lleva slug: es la busqueda del jugador que todavia no eligio club. Por eso
     * {@code search} esta reservado en {@code TenantContextFilter} y no se resuelve como
     * el nombre de un club.
     */
    @GetMapping("/search")
    public CourtSearchResponse search(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "HH:mm") LocalTime from,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "HH:mm") LocalTime to,
            @RequestParam(required = false) String clubs) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        return courtSearchService.search(
                day,
                from != null ? from : LocalTime.MIN,
                to != null ? to : LocalTime.of(23, 59),
                slugsOf(clubs));
    }

    /** Los clubes llegan como lista separada por comas; vacio significa todos. */
    private Set<String> slugsOf(String clubs) {
        if (clubs == null || clubs.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(clubs.split(","))
                .map(String::trim)
                .filter(slug -> !slug.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Grilla del dia con precios por horario. */
    @GetMapping("/{slug}/availability")
    public AvailabilityResponse availability(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Tenant club = tenantService.activate(slug);
        return availabilityService.availabilityFor(club, date);
    }

    /**
     * Foto de portada que el club subio como archivo desde el panel.
     *
     * <p>Cuando en cambio pego una URL externa, {@code heroImageUrl} apunta ahi
     * directo y este endpoint ni se llama.
     */
    @GetMapping("/{slug}/hero-image")
    public ResponseEntity<byte[]> heroImage(@PathVariable String slug) {
        Tenant club = tenantService.activate(slug);
        return tenantHeroImageRepository.findById(club.getId())
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .body(image.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Anotarse para que avisen si se libera una cancha en un horario lleno. */
    @PostMapping("/{slug}/waitlist")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinWaitlistResponse joinWaitlist(@PathVariable String slug,
                                             @Valid @RequestBody JoinWaitlistRequest request,
                                             HttpServletRequest httpRequest) {
        // El telefono es de quien lo escribe, no de quien lo autentica: sin este
        // limite, un script podia anotar el numero de un tercero en la lista de
        // espera de cada horario, y esa persona terminaba recibiendo el aviso.
        rateLimiter.check(httpRequest.getRemoteAddr());

        Tenant club = tenantService.activate(slug);
        waitlistService.join(club, request.startTime(), request.phoneNumber(), request.fullName());
        return new JoinWaitlistResponse("Listo, te avisamos por WhatsApp si se libera una cancha.");
    }

    /** Alta de la reserva. Devuelve el link de pago o el aviso de confirmacion. */
    @PostMapping("/{slug}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateBookingResponse book(@PathVariable String slug,
                                      @Valid @RequestBody CreateBookingRequest request,
                                      HttpServletRequest httpRequest) {
        // Reservar bloquea la grilla sin haber pagado nada, asi que el endpoint se
        // limita por origen antes de tocar la base.
        rateLimiter.check(httpRequest.getRemoteAddr());

        Tenant club = tenantService.activate(slug);
        CheckoutResult result = checkoutService.checkout(club, new NewBooking(
                request.courtId(),
                request.startTime(),
                request.fullName(),
                request.phoneNumber(),
                PaymentChoice.valueOf(request.paymentChoice().name())));

        return new CreateBookingResponse(
                result.booking().getId(),
                result.booking().getStatus().name(),
                notificationService.managementLink(result.booking()),
                result.booking().getManagementToken(),
                notificationService.shareLink(result.booking()),
                result.checkoutUrl(),
                result.requiresWhatsappConfirmation(),
                result.booking().getTotalPrice(),
                result.booking().getDepositAmount(),
                messageFor(club, result));
    }

    /** Confirmacion del flujo sin pago, desde el link de WhatsApp. */
    @PostMapping("/confirm/{token}")
    public BookingDetailResponse confirm(@PathVariable String token) {
        ManagedBooking managed = bookingService.confirmByToken(token);
        return BookingDetailResponse.of(managed.club(), managed.booking(), clock.instant(),
                notificationService.shareLink(managed.booking()));
    }

    /** Portal de gestion del turno. */
    @GetMapping("/manage/{token}")
    public BookingDetailResponse manage(@PathVariable String token) {
        ManagedBooking managed = bookingService.findByManagementToken(token);
        return BookingDetailResponse.of(managed.club(), managed.booking(), clock.instant(),
                notificationService.shareLink(managed.booking()));
    }

    /** Vista publica de solo lectura, para compartir el turno con otros jugadores. */
    @GetMapping("/share/{token}")
    public BookingShareResponse share(@PathVariable String token) {
        ManagedBooking managed = bookingService.findByShareToken(token);
        return BookingShareResponse.of(managed.club(), managed.booking());
    }

    /** Cancelacion por parte del jugador. */
    @PostMapping("/manage/{token}/cancel")
    public CancellationResponse cancel(@PathVariable String token) {
        BookingService.CancellationResult result = bookingService.cancelByManagementToken(token);

        String message = result.refundNeeded()
                ? ("Turno cancelado. Para coordinar la devolución de tu seña, escribinos por "
                        + "WhatsApp al " + result.clubWhatsapp() + ".")
                : "Turno cancelado. La cancha ya volvió a estar disponible.";

        return new CancellationResponse(
                result.booking().getStatus().name(),
                result.refundNeeded(),
                result.clubWhatsapp(),
                message);
    }

    private String messageFor(Tenant club, CheckoutResult result) {
        if (result.requiresWhatsappConfirmation()) {
            return ("Te mandamos un WhatsApp para confirmar. Tenés %d minutos antes de que la "
                    + "cancha vuelva a quedar libre.").formatted(club.getConfirmationTtlMinutes());
        }
        if (result.isConfirmed()) {
            return "Turno confirmado. Te esperamos, no hace falta que hagas nada más.";
        }
        return ("Te llevamos a MercadoPago para pagar la seña. Tenés %d minutos para completar "
                + "el pago.").formatted(club.getDraftTtlMinutes());
    }
}
