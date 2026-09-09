package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Payment;
import ar.com.padelnec.domain.enums.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByMpPaymentId(String mpPaymentId);

    Optional<Payment> findByMpPreferenceId(String mpPreferenceId);

    List<Payment> findAllByBookingIdOrderByCreatedAtAsc(UUID bookingId);

    /**
     * Lo cobrado dentro de una ventana, por fecha del cobro.
     *
     * <p>Antes esto se preguntaba al reves: se buscaban los turnos del rango y se
     * sumaban los pagos colgados de ellos. Eso ubicaba la plata en el dia del
     * partido y no en el dia en que entro -una sena cobrada el lunes por un turno
     * del sabado caia en el sabado- y ademas perdia lo cobrado sobre un turno que
     * despues se cancelo, porque esa consulta filtraba los turnos que ocupan la
     * grilla. Para el dinero las dos cosas son errores: pertenece al dia en que
     * se movio, lo haya jugado alguien o no.
     *
     * <p>Va por {@code ix_payment_club_created}, que el esquema ya tenia desde el
     * principio y hasta ahora ninguna consulta usaba.
     */
    @Query("""
            SELECT new ar.com.padelnec.repository.PaymentCashRow(p.createdAt, p.method, p.amount)
            FROM Payment p
            WHERE p.status = :status AND p.createdAt >= :from AND p.createdAt < :until
            """)
    List<PaymentCashRow> findCashBetween(@Param("from") Instant from,
                                         @Param("until") Instant until,
                                         @Param("status") PaymentStatus status);

    /**
     * Lo mismo que {@link #findCashBetween}, pero con el turno que explica cada
     * movimiento: es lo que la caja del dia lista renglon por renglon.
     */
    @Query("""
            SELECT new ar.com.padelnec.repository.CashMovementRow(
                p.createdAt, p.method, p.amount, p.registeredBy, p.booking.id,
                p.booking.customer.fullName, p.booking.court.name, p.booking.startTime)
            FROM Payment p
            WHERE p.status = :status AND p.createdAt >= :from AND p.createdAt < :until
            ORDER BY p.createdAt ASC
            """)
    List<CashMovementRow> findMovementsBetween(@Param("from") Instant from,
                                               @Param("until") Instant until,
                                               @Param("status") PaymentStatus status);
}
