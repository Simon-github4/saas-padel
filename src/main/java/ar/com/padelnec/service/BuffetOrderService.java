package ar.com.padelnec.service;

import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.repository.BuffetOrderRepository;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.repository.ProductSaleRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de buffet sin turno: lo que consume alguien que no está jugando.
 *
 * <p>Los productos y los cobros de un pedido pasan por los mismos servicios que
 * los de un turno ({@link ProductService}, {@link PaymentService}): este servicio
 * solo abre el pedido, lo busca y lo lista.
 */
@Service
@RequiredArgsConstructor
public class BuffetOrderService {

    private final BuffetOrderRepository buffetOrderRepository;
    private final ProductSaleRepository productSaleRepository;
    private final PaymentRepository paymentRepository;

    /** Un pedido con sus productos, en el orden en que se cargaron. */
    public record OrderWithItems(BuffetOrder order, List<ProductSale> items) {
    }

    /** Abre un pedido vacío a nombre de alguien. Los productos se le suman después. */
    @Transactional
    public BuffetOrder open(String customerName, UUID registeredBy) {
        if (customerName == null || customerName.isBlank()) {
            throw new BusinessRuleException("Poné a nombre de quién es el pedido");
        }
        String name = customerName.trim();
        BuffetOrder order = new BuffetOrder();
        order.setCustomerName(name.length() > 120 ? name.substring(0, 120) : name);
        order.setRegisteredBy(registeredBy);
        return buffetOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public BuffetOrder require(UUID orderId) {
        return buffetOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("El pedido no existe"));
    }

    /**
     * Los pedidos abiertos un día, del más reciente al más viejo, con sus productos.
     *
     * <p>Por el día calendario del club, igual que la caja: un pedido de pasada la
     * medianoche es del día nuevo.
     */
    @Transactional(readOnly = true)
    public List<OrderWithItems> ordersOn(Tenant club, LocalDate date) {
        Instant from = date.atStartOfDay(club.zoneId()).toInstant();
        Instant until = date.plusDays(1).atStartOfDay(club.zoneId()).toInstant();
        List<BuffetOrder> orders = buffetOrderRepository
                .findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(from, until);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ProductSale>> itemsByOrder = productSaleRepository
                .findAllByBuffetOrderIdInOrderByCreatedAtAsc(orders.stream().map(BuffetOrder::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(sale -> sale.getBuffetOrder().getId()));
        return orders.stream()
                .map(order -> new OrderWithItems(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    /**
     * Borra un pedido abierto por error.
     *
     * <p>Solo si nunca se le cobró nada: un pedido con cobros ya es plata que pasó
     * por la caja, y borrarlo haría que la caja de ese día deje de sumar. Si hay
     * que deshacerlo, se sacan los productos y se registra la devolución.
     */
    @Transactional
    public void delete(UUID orderId) {
        BuffetOrder order = require(orderId);
        if (!paymentRepository.findAllByBuffetOrderIdOrderByCreatedAtAsc(orderId).isEmpty()) {
            throw new BusinessRuleException(
                    "Este pedido ya tiene cobros: sacá los productos y registrá la devolución en vez de borrarlo");
        }
        // Las ventas se van con el pedido (ON DELETE CASCADE).
        buffetOrderRepository.delete(order);
    }
}
