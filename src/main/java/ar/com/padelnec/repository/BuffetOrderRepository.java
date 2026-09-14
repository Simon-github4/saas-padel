package ar.com.padelnec.repository;

import ar.com.padelnec.domain.BuffetOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuffetOrderRepository extends JpaRepository<BuffetOrder, UUID> {

    /** Los pedidos abiertos en una ventana, del más reciente al más viejo. Va por {@code ix_buffet_order_club_created}. */
    List<BuffetOrder> findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Instant from, Instant until);
}
