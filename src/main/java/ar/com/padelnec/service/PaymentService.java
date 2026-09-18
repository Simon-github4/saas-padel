package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Payment;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.domain.enums.PaymentStatus;
import ar.com.padelnec.payment.MercadoPagoGateway;
import ar.com.padelnec.payment.MercadoPagoGateway.ApprovedPayment;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.BuffetOrderRepository;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.web.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acreditacion de senas y cobros de mostrador, de turnos y de pedidos de buffet. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BuffetOrderRepository buffetOrderRepository;
    private final MercadoPagoGateway gateway;
    private final DepositPayerResolver payerResolver;
    private final AlertService alertService;
    private final ApplicationEventPublisher events;

    /**
     * Genera el link de pago de la sena y deja el pago asentado como pendiente.
     *
     * <p>Se llama despues de que la reserva quedo grabada, no dentro de esa misma
     * transaccion: mantener una transaccion abierta contra la base mientras se
     * espera a un servicio externo es la forma mas facil de quedarse sin conexiones
     * un sabado a la tarde.
     */
    @Transactional
    public String startDepositCheckout(Tenant club, Booking booking) {
        MercadoPagoGateway.Checkout checkout = gateway.createDepositCheckout(
                club, booking, payerResolver.resolve(booking));

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getDepositAmount());
        payment.setMethod(PaymentMethod.MERCADOPAGO);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMpOrderId(checkout.orderId());
        paymentRepository.save(payment);

        return checkout.checkoutUrl();
    }

    /**
     * Procesa una notificacion de MercadoPago para una order.
     *
     * <p>El estado se consulta contra la API y no se toma del cuerpo del webhook:
     * la notificacion avisa que algo paso, no prueba que el dinero exista. Una
     * order puede acumular mas de un intento de pago (un rechazo y despues uno
     * aprobado, por ejemplo), asi que se procesan todos los que trajo -la
     * idempotencia es por id de pago, no por order, asi que reprocesar los ya
     * vistos no hace nada.
     */
    @Transactional
    public void applyWebhook(Tenant club, String mercadoPagoOrderId) {
        List<ApprovedPayment> payments = gateway.fetchOrderPayments(club, mercadoPagoOrderId);
        for (ApprovedPayment remote : payments) {
            applyPayment(club, remote);
        }
    }

    private void applyPayment(Tenant club, ApprovedPayment remote) {
        // Idempotencia: MercadoPago reintenta sus webhooks, y sin esto una sena se
        // acreditaria dos veces sobre la misma reserva.
        if (paymentRepository.findByMpPaymentId(remote.paymentId()).isPresent()) {
            log.debug("El pago {} ya estaba procesado", remote.paymentId());
            return;
        }

        Optional<Booking> found = resolveBooking(remote);
        if (found.isEmpty()) {
            log.error("Llego el pago {} del club {} sin una reserva que lo respalde (ref {})",
                    remote.paymentId(), club.getSlug(), remote.externalReference());
            return;
        }
        Booking booking = found.get();

        if (remote.isRejected()) {
            recordRejection(booking, remote);
            return;
        }
        if (!remote.isApproved()) {
            // En proceso, esperando accion, etc: MercadoPago vuelve a avisar cuando se defina.
            return;
        }
        credit(club, booking, remote);
    }

    /** Cobro en el mostrador cargado por el club: efectivo o transferencia. */
    @Transactional
    public Payment registerManualPayment(Booking booking, BigDecimal amount, PaymentMethod method,
                                         UUID registeredBy) {
        Payment payment = manualMovement(amount, method, registeredBy);
        payment.setBooking(booking);
        paymentRepository.save(payment);

        booking.setPaidAmount(booking.getPaidAmount().add(amount));
        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.isPaidInFull()) {
            // Cobrar el total es la señal de que el turno se jugó: ahorra el paso
            // manual de marcarlo aparte, que en el mostrador era un click de mas
            // en la misma visita del cliente.
            booking.setStatus(BookingStatus.COMPLETED);
        }
        bookingRepository.save(booking);
        return payment;
    }

    /**
     * Devolucion de mostrador: plata que vuelve al jugador en efectivo o transferencia,
     * ej. porque se saco un producto que ya se habia cobrado.
     */
    @Transactional
    public Payment registerRefund(Booking booking, BigDecimal amount, PaymentMethod method,
                                  UUID registeredBy) {
        Payment payment = manualMovement(amount, method, registeredBy);
        if (amount.compareTo(booking.getPaidAmount()) > 0) {
            throw new BusinessRuleException("No podés devolver más de lo que se cobró");
        }
        payment.setBooking(booking);
        payment.setAmount(amount.negate());
        paymentRepository.save(payment);

        booking.setPaidAmount(booking.getPaidAmount().subtract(amount));
        bookingRepository.save(booking);
        return payment;
    }

    /** Cobro en el mostrador de un pedido de buffet sin turno. Mismo circuito que el de un turno. */
    @Transactional
    public Payment registerManualPayment(BuffetOrder order, BigDecimal amount, PaymentMethod method,
                                         UUID registeredBy) {
        Payment payment = manualMovement(amount, method, registeredBy);
        payment.setBuffetOrder(order);
        paymentRepository.save(payment);

        order.setPaidAmount(order.getPaidAmount().add(amount));
        buffetOrderRepository.save(order);
        return payment;
    }

    /** Devolución de un pedido de buffet, ej. porque se sacó un producto que ya se había cobrado. */
    @Transactional
    public Payment registerRefund(BuffetOrder order, BigDecimal amount, PaymentMethod method,
                                  UUID registeredBy) {
        Payment payment = manualMovement(amount, method, registeredBy);
        if (amount.compareTo(order.getPaidAmount()) > 0) {
            throw new BusinessRuleException("No podés devolver más de lo que se cobró");
        }
        payment.setBuffetOrder(order);
        payment.setAmount(amount.negate());
        paymentRepository.save(payment);

        order.setPaidAmount(order.getPaidAmount().subtract(amount));
        buffetOrderRepository.save(order);
        return payment;
    }

    /** Un movimiento de mostrador ya validado, todavía sin turno ni pedido. */
    private Payment manualMovement(BigDecimal amount, PaymentMethod method, UUID registeredBy) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("El monto tiene que ser mayor a cero");
        }
        if (method != PaymentMethod.CASH && method != PaymentMethod.TRANSFER) {
            throw new BusinessRuleException("Ese metodo no se puede cargar a mano");
        }
        Payment payment = new Payment();
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setRegisteredBy(registeredBy);
        return payment;
    }

    @Transactional(readOnly = true)
    public java.util.List<Payment> paymentsOf(UUID bookingId) {
        return paymentRepository.findAllByBookingIdOrderByCreatedAtAsc(bookingId);
    }

    // ------------------------------------------------------------ internos

    private void credit(Tenant club, Booking booking, ApprovedPayment remote) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(remote.amount());
        payment.setMethod(PaymentMethod.MERCADOPAGO);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setMpPaymentId(remote.paymentId());
        payment.setRawPayload(remote.toString());

        // Si dos webhooks del mismo pago entran a la vez, el segundo choca contra la
        // unicidad de mp_payment_id y esta transaccion se revierte entera. Se deja
        // propagar a proposito: atraparlo aca no serviria, porque una transaccion ya
        // marcada para rollback no puede seguir escribiendo, y no se pierde nada
        // porque el otro hilo ya acredito el pago.
        paymentRepository.saveAndFlush(payment);

        booking.setPaidAmount(booking.getPaidAmount().add(remote.amount()));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            // Esta es la carrera que importa: el DRAFT vencio a los 10 minutos, la
            // cancha se libero y el pago acredito despues. Hay plata cobrada sin
            // turno, y solo una persona del club puede resolverlo.
            bookingRepository.save(booking);
            alertService.orphanPayment(booking, remote.amount().toPlainString());
            return;
        }

        if (booking.getStatus() == BookingStatus.DRAFT) {
            booking.markConfirmed();
            bookingRepository.save(booking);
            events.publishEvent(BookingEvent.of(club.getId(), booking.getId(),
                    BookingEvent.Kind.CONFIRMED_PAID));
            return;
        }

        // Ya estaba confirmado: es un pago adicional, se suma y nada mas.
        bookingRepository.save(booking);
    }

    private void recordRejection(Booking booking, ApprovedPayment remote) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(remote.amount() != null ? remote.amount() : BigDecimal.ZERO);
        payment.setMethod(PaymentMethod.MERCADOPAGO);
        payment.setStatus(PaymentStatus.REJECTED);
        payment.setMpPaymentId(remote.paymentId());
        paymentRepository.save(payment);
        // No se cancela la reserva: el jugador puede reintentar mientras el DRAFT
        // siga vivo. Si no lo hace, el job de vencimiento libera la cancha.
    }

    private Optional<Booking> resolveBooking(ApprovedPayment remote) {
        if (remote.externalReference() == null) {
            return Optional.empty();
        }
        try {
            return bookingRepository.findById(UUID.fromString(remote.externalReference()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
