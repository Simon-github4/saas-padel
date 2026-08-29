package ar.com.padelnec.web.dto;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contratos de la app del jugador. */
public final class BookingDtos {

    private BookingDtos() {
    }

    /** Lo que manda el checkout. */
    public record CreateBookingRequest(
            @NotNull(message = "Elegí una cancha") UUID courtId,
            @NotNull(message = "Elegí un horario") Instant startTime,
            @NotBlank(message = "Necesitamos tu nombre")
            @Size(max = 120) String fullName,
            @NotBlank(message = "Necesitamos tu teléfono")
            @Size(max = 25) String phoneNumber,
            @NotNull(message = "Elegí cómo querés pagar") PaymentChoiceDto paymentChoice) {
    }

    public enum PaymentChoiceDto {
        /** Sena online por MercadoPago. */
        DEPOSIT_ONLINE,
        /** De palabra, confirmando por WhatsApp. */
        PAY_AT_CLUB
    }

    /**
     * Respuesta del checkout.
     *
     * <p>El link de gestion viaja aca ademas de por WhatsApp: si el mensaje no
     * llega, el jugador no puede quedarse sin forma de ver ni cancelar su turno.
     */
    public record CreateBookingResponse(
            UUID bookingId,
            String status,
            String managementUrl,
            String checkoutUrl,
            boolean awaitingWhatsappConfirmation,
            BigDecimal totalPrice,
            BigDecimal depositAmount,
            String message) {
    }

    /** Detalle que ve el jugador en su portal de gestion. */
    public record BookingDetailResponse(
            UUID bookingId,
            String status,
            String clubName,
            String clubWhatsapp,
            String courtName,
            Instant startTime,
            Instant endTime,
            BigDecimal totalPrice,
            BigDecimal paidAmount,
            BigDecimal balanceDue,
            boolean cancellableOnline,
            int cancellationLimitHours,
            String cancellationHint) {

        public static BookingDetailResponse of(Tenant club, Booking booking, Instant now) {
            boolean cancellable = booking.getStatus().isCancellable()
                    && booking.isWithinCancellationWindow(club.getCancellationLimitHours(), now);

            String hint = cancellable ? null
                    : ("Faltan menos de %d horas para tu turno. Para cancelar, escribinos por "
                            + "WhatsApp al %s.").formatted(
                            club.getCancellationLimitHours(), club.getWhatsappNumber());

            return new BookingDetailResponse(
                    booking.getId(),
                    booking.getStatus().name(),
                    club.getName(),
                    club.getWhatsappNumber(),
                    booking.getCourt().getName(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    booking.getTotalPrice(),
                    booking.getPaidAmount(),
                    booking.balanceDue(),
                    cancellable,
                    club.getCancellationLimitHours(),
                    hint);
        }
    }

    /** Resultado de cancelar desde el portal. */
    public record CancellationResponse(
            String status,
            boolean refundNeeded,
            String clubWhatsapp,
            String message) {
    }
}
