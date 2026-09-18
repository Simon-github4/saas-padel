package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.payment.PaymentGatewayException;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.service.BookingService.NewBooking;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Une la reserva con el cobro.
 *
 * <p>Son dos pasos separados y no una sola transaccion porque el segundo llama a
 * MercadoPago: sostener una transaccion de base abierta mientras se espera a un
 * servicio externo es la forma mas rapida de quedarse sin conexiones justo el
 * sabado a la tarde, que es cuando el sistema mas se usa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    /**
     * @param checkoutUrl link de MercadoPago, o null cuando el turno se paga en el club
     */
    public record CheckoutResult(Booking booking, String checkoutUrl) {

        public boolean requiresWhatsappConfirmation() {
            return booking.getStatus() == BookingStatus.AWAITING_CONFIRMATION;
        }

        public boolean isConfirmed() {
            return booking.getStatus() == BookingStatus.CONFIRMED;
        }
    }

    public CheckoutResult checkout(Tenant club, NewBooking request) {
        Booking booking = bookingService.create(club, request);

        if (booking.getStatus() != BookingStatus.DRAFT) {
            // Camino sin pago: el turno ya quedo tomado esperando el link de WhatsApp.
            return new CheckoutResult(booking, null);
        }

        try {
            return new CheckoutResult(booking, paymentService.startDepositCheckout(club, booking));
        } catch (PaymentGatewayException ex) {
            // Sin link de pago la reserva no tiene futuro. Se libera la cancha ahora
            // en vez de dejarla bloqueada diez minutos esperando algo que no va a pasar.
            releaseFailedDraft(booking);
            throw ex;
        }
    }

    @Transactional
    public void releaseFailedDraft(Booking booking) {
        bookingRepository.findById(booking.getId()).ifPresent(draft -> {
            if (draft.getStatus() == BookingStatus.DRAFT) {
                draft.markCancelled(CancellationReason.PAYMENT_TIMEOUT, clock.instant());
                bookingRepository.save(draft);
                log.info("Se libero la cancha {} porque no se pudo generar el link de pago",
                        draft.getId());
            }
        });
    }
}
