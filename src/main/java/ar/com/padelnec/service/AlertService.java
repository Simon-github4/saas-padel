package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.OperationalAlert;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.AlertType;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.repository.OperationalAlertRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d/MM", ES_AR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", ES_AR);

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
                booking.displayName(),
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

    /**
     * Se cancelo un turno con gente esperando ese horario, por la web o desde el panel.
     *
     * <p>El barrido automatico les avisa por WhatsApp o por mail, pero el club
     * no se entera, y con el WhatsApp del club en stand by el unico aviso que
     * sale es el mail. Asi el mostrador revisa la lista de espera y les escribe
     * a mano, con el link para reservar ese turno.
     *
     * <p>Quien cancelo sale del motivo que ya quedo en el turno, no de un
     * parametro aparte: {@code markCancelled} ya lo dejo escrito.
     */
    @Transactional
    public void waitlistSlotFreed(Tenant club, Booking booking, long waiting) {
        ZonedDateTime start = booking.getStartTime().atZone(club.zoneId());
        String who = booking.getCancellationReason() == CancellationReason.CLUB
                ? "El club dio de baja"
                : "Se canceló por la web";
        raise(AlertType.WAITLIST_SLOT_FREED, booking, ("%s el turno de %s del %s a las %s hs. "
                + "%s en la lista de espera: les llega un aviso automático por mail, "
                + "pero conviene escribirles también por WhatsApp.").formatted(
                who, booking.getCourt().getName(), start.format(DAY), start.format(TIME),
                waiting == 1 ? "Hay 1 anotado" : "Hay " + waiting + " anotados"));
    }

    @Transactional(readOnly = true)
    public List<OperationalAlert> pending() {
        return alertRepository.findPending();
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return alertRepository.countByResolvedFalse();
    }

    /** Las que siguen sin resolver y aparecieron despues de un momento dado, las mas nuevas primero. */
    @Transactional(readOnly = true)
    public List<OperationalAlert> pendingSince(Instant since) {
        return alertRepository.findByResolvedFalseAndCreatedAtAfterOrderByCreatedAtDesc(since);
    }

    @Transactional
    public void resolve(UUID alertId, UUID userId) {
        alertRepository.findById(alertId)
                .ifPresent(alert -> alert.resolve(userId, clock.instant()));
    }
}
