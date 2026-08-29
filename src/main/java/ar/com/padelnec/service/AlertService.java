package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.OperationalAlert;
import ar.com.padelnec.domain.enums.AlertType;
import ar.com.padelnec.repository.OperationalAlertRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avisos para el panel sobre lo que el sistema no puede resolver solo.
 *
 * <p>El caso central es la devolucion de una sena: MercadoPago cobro, el jugador
 * cancelo y la plata tiene que volver por fuera del sistema. Si eso no aparece en
 * ningun lado, el club se entera cuando el jugador reclama.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final OperationalAlertRepository alertRepository;
    private final Clock clock;

    @Transactional
    public OperationalAlert raise(AlertType type, Booking booking, String message) {
        OperationalAlert alert = new OperationalAlert();
        alert.setType(type);
        alert.setBooking(booking);
        alert.setMessage(message);
        log.info("Alerta {} para la reserva {}: {}", type,
                booking != null ? booking.getId() : "-", message);
        return alertRepository.save(alert);
    }

    /** El jugador cancelo un turno con sena paga: hay que coordinar la devolucion. */
    @Transactional
    public void refundRequired(Booking booking) {
        raise(AlertType.REFUND_REQUIRED, booking, ("Canceló %s (%s) un turno con $%s ya pagados. "
                + "Coordiná la devolución por WhatsApp.").formatted(
                booking.getCustomer().getFullName(),
                booking.getCustomer().getPhoneNumber(),
                booking.getPaidAmount().toPlainString()));
    }

    /**
     * Acredito un pago sobre una reserva que ya se habia liberado. Es la carrera
     * entre el vencimiento del DRAFT y la demora del webhook: hay plata cobrada sin
     * turno detras y alguien la tiene que devolver.
     */
    @Transactional
    public void orphanPayment(Booking booking, String amount) {
        raise(AlertType.ORPHAN_PAYMENT, booking, ("Entró un pago de $%s para un turno que ya se "
                + "había liberado por falta de pago. Revisá si hay que devolverlo o reubicar "
                + "al jugador.").formatted(amount));
    }

    @Transactional
    public void notificationFailed(Booking booking, String phone, String reason) {
        raise(AlertType.NOTIFICATION_FAILED, booking,
                "No se pudo avisar por WhatsApp a %s: %s".formatted(phone, reason));
    }

    /** Un turno fijo no se pudo materializar porque la franja ya estaba ocupada. */
    @Transactional
    public void recurringConflict(String detail) {
        raise(AlertType.RECURRING_CONFLICT, null, detail);
    }

    @Transactional(readOnly = true)
    public List<OperationalAlert> pending() {
        return alertRepository.findPending();
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return alertRepository.countByResolvedFalse();
    }

    @Transactional
    public void resolve(UUID alertId, UUID userId) {
        alertRepository.findById(alertId)
                .ifPresent(alert -> alert.resolve(userId, clock.instant()));
    }
}
