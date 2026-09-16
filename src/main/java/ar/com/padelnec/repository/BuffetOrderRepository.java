package ar.com.padelnec.repository;

import ar.com.padelnec.domain.BuffetOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuffetOrderRepository extends JpaRepository<BuffetOrder, UUID> {

    /** Los pedidos abiertos en una ventana, del más reciente al más viejo. Va por {@code ix_buffet_order_club_created}. */
    List<BuffetOrder> findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Instant from, Instant until);

    /**
     * Las cuentas sin saldar de cualquier día (deben, o tienen plata a favor), más
     * las abiertas desde {@code since} que todavía están en cero: un pedido vacío
     * de hace una semana ya no es una cuenta abierta, es un olvido.
     */
    @Query("""
            SELECT o FROM BuffetOrder o
            WHERE o.totalPrice <> o.paidAmount
               OR (o.totalPrice = 0 AND o.paidAmount = 0 AND o.createdAt >= :since)
            ORDER BY o.customerName ASC, o.createdAt ASC
            """)
    List<BuffetOrder> findUnsettled(@Param("since") Instant since);
}
