package ar.com.padelnec.web.api;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.notification.NotificationService;
import ar.com.padelnec.service.AvailabilityService;
import ar.com.padelnec.service.BookingService;
import ar.com.padelnec.service.BookingService.ManagedBooking;
import ar.com.padelnec.service.BookingService.NewBooking;
import ar.com.padelnec.service.BookingService.PaymentChoice;
import ar.com.padelnec.service.CheckoutService;
import ar.com.padelnec.service.CheckoutService.CheckoutResult;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.dto.AvailabilityResponse;
import ar.com.padelnec.web.dto.BookingDtos.BookingDetailResponse;
import ar.com.padelnec.web.dto.BookingDtos.CancellationResponse;
import ar.com.padelnec.web.dto.BookingDtos.CreateBookingRequest;
import ar.com.padelnec.web.dto.BookingDtos.CreateBookingResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;
    private final CheckoutService checkoutService;
    private final NotificationService notificationService;
    private final BookingRateLimiter rateLimiter;
    private final Clock clock;

    /** Grilla del dia con precios por horario. */
    @GetMapping("/{slug}/availability")
    public AvailabilityResponse availability(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Tenant club = tenantService.activate(slug);
        return availabilityService.availabilityFor(club, date);
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
        return BookingDetailResponse.of(managed.club(), managed.booking(), clock.instant());
    }

    /** Portal de gestion del turno. */
    @GetMapping("/manage/{token}")
    public BookingDetailResponse manage(@PathVariable String token) {
        ManagedBooking managed = bookingService.findByManagementToken(token);
        return BookingDetailResponse.of(managed.club(), managed.booking(), clock.instant());
    }

    /** Cancelacion por parte del jugador. */
    @PostMapping("/manage/{token}/cancel")
    public CancellationResponse cancel(@PathVariable String token) {
        BookingService.CancellationResult result = bookingService.cancelByManagementToken(token);

        String message = result.refundNeeded()
                ? ("Turno cancelado. Para coordinar la devolucion de tu sena, escribinos por "
                        + "WhatsApp al " + result.clubWhatsapp() + ".")
                : "Turno cancelado. La cancha ya volvio a estar disponible.";

        return new CancellationResponse(
                result.booking().getStatus().name(),
                result.refundNeeded(),
                result.clubWhatsapp(),
                message);
    }

    private String messageFor(Tenant club, CheckoutResult result) {
        if (result.requiresWhatsappConfirmation()) {
            return ("Te mandamos un WhatsApp para confirmar. Tenes %d minutos antes de que la "
                    + "cancha vuelva a quedar libre.").formatted(club.getConfirmationTtlMinutes());
        }
        return ("Te llevamos a MercadoPago para pagar la sena. Tenes %d minutos para completar "
                + "el pago.").formatted(club.getDraftTtlMinutes());
    }
}
