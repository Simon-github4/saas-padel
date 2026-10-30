package ar.com.padelnec.repository;

import ar.com.padelnec.domain.ProductSale;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductSaleRepository extends JpaRepository<ProductSale, UUID> {

    List<ProductSale> findAllByBookingIdOrderByCreatedAtAsc(UUID bookingId);

    /**
     * En el orden en que se cargaron, que es el orden en que un pago por monto
     * cubre la cuenta. El id desempata dos líneas con la misma fecha.
     */
    List<ProductSale> findAllByBuffetOrderIdInOrderByCreatedAtAscIdAsc(Collection<UUID> buffetOrderIds);

    /**
     * Lo vendido en el buffet dentro de una ventana, por fecha de venta: en turnos
     * y en pedidos sueltos.
     *
     * <p>No es plata cobrada: una venta se suma al total del turno o del pedido y
     * se cobra despues, a veces otro dia. Por eso la caja la muestra aparte y no
     * la mezcla con sus movimientos, que son los cobros de verdad.
     *
     * <p>Va por {@code ix_product_sale_club_created}.
     */
    @Query("""
            SELECT new ar.com.padelnec.repository.BuffetSaleRow(ps.productName, ps.quantity, ps.unitPrice)
            FROM ProductSale ps
            WHERE ps.createdAt >= :from AND ps.createdAt < :until
            """)
    List<BuffetSaleRow> findSalesBetween(@Param("from") Instant from, @Param("until") Instant until);

    /**
     * Lo vendido en pedidos de buffet sin turno dentro de una ventana, por fecha
     * de venta.
     *
     * <p>Solo los pedidos: lo que se consume en un turno ya esta en el total del
     * turno, y las estadisticas lo facturan por ahi.
     */
    @Query("""
            SELECT new ar.com.padelnec.repository.SaleAmountRow(ps.createdAt, ps.quantity, ps.unitPrice)
            FROM ProductSale ps
            WHERE ps.buffetOrder IS NOT NULL AND ps.createdAt >= :from AND ps.createdAt < :until
            """)
    List<SaleAmountRow> findOrderSalesBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Lo consumido en cada uno de estos turnos, sumado. Un turno sin consumiciones no aparece. */
    @Query("""
            SELECT new ar.com.padelnec.repository.BookingConsumptionRow(ps.booking.id, SUM(ps.unitPrice * ps.quantity))
            FROM ProductSale ps
            WHERE ps.booking.id IN :bookingIds
            GROUP BY ps.booking.id
            """)
    List<BookingConsumptionRow> sumByBooking(@Param("bookingIds") Collection<UUID> bookingIds);
}
