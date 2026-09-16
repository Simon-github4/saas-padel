package ar.com.padelnec.service;

import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.repository.BuffetOrderRepository;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.repository.ProductSaleRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de buffet sin turno: lo que consume alguien que no está jugando.
 *
 * <p>Los productos y los cobros de un pedido pasan por los mismos servicios que
 * los de un turno ({@link ProductService}, {@link PaymentService}): este servicio
 * abre el pedido, lo busca, lo lista y arma el cobro de mostrador de una vez.
 */
@Service
@RequiredArgsConstructor
public class BuffetOrderService {

    private final BuffetOrderRepository buffetOrderRepository;
    private final ProductSaleRepository productSaleRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final PaymentService paymentService;

    /** Un pedido con sus productos, en el orden en que se cargaron. */
    public record OrderWithItems(BuffetOrder order, List<ProductSale> items) {
    }

    /** Lo que el mostrador suma a un pedido: qué producto y cuántos. */
    public record NewItem(UUID productId, int quantity) {
    }

    /** Cómo quedó el pedido después de guardarlo, y cuánto se cobró en esa pasada. */
    public record Checkout(BuffetOrder order, BigDecimal charged) {
    }

    /** Abre un pedido vacío a nombre de alguien. Los productos se le suman después. */
    @Transactional
    public BuffetOrder open(String customerName, UUID registeredBy) {
        if (customerName == null || customerName.isBlank()) {
            throw new BusinessRuleException("Poné a nombre de quién es el pedido");
        }
        return create(customerName, registeredBy);
    }

    private BuffetOrder create(String customerName, UUID registeredBy) {
        String name = customerName == null || customerName.isBlank() ? null : customerName.trim();
        BuffetOrder order = new BuffetOrder();
        order.setCustomerName(name != null && name.length() > 120 ? name.substring(0, 120) : name);
        order.setRegisteredBy(registeredBy);
        return buffetOrderRepository.save(order);
    }

    /**
     * Guarda de una vez lo que se armó en el mostrador: abre el pedido si es
     * nuevo, le suma los productos y, si se eligió cómo, cobra todo lo que debe.
     *
     * <p>Es el camino rápido de la pantalla de buffet, donde el pedido se arma en
     * pantalla y recién se guarda al cobrar. Va en una sola transacción para que
     * un producto dado de baja en el medio o un cobro rechazado no dejen un
     * pedido a medio cargar.
     *
     * <p>El nombre solo hace falta para dejar algo en la cuenta: una venta que se
     * cobra en el momento puede ir sin nombre (venta rápida).
     *
     * @param orderId    el pedido al que se suman los productos, o null para abrir uno
     * @param chargeWith cómo se cobra el saldo, o null para dejarlo pendiente
     */
    @Transactional
    public Checkout checkout(UUID orderId, String customerName, List<NewItem> items,
                             PaymentMethod chargeWith, UUID registeredBy) {
        boolean unnamed = customerName == null || customerName.isBlank();
        if (orderId == null && chargeWith == null && unnamed) {
            throw new BusinessRuleException("Para dejarlo en la cuenta, poné a nombre de quién es");
        }
        BuffetOrder order = orderId == null ? create(customerName, registeredBy) : require(orderId);

        // El mismo producto dos veces va en una sola línea: así se lee en el detalle.
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (NewItem item : items) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        Map<UUID, Product> products = productRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        quantities.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("El producto no existe");
            }
            if (!product.isActive()) {
                throw new BusinessRuleException(product.getName() + " ya no está a la venta");
            }
            productService.registerSale(order, product, quantity, registeredBy);
        });

        BigDecimal charged = BigDecimal.ZERO;
        if (chargeWith != null) {
            charged = order.balanceDue();
            if (charged.signum() <= 0) {
                throw new BusinessRuleException("Este pedido no tiene nada para cobrar");
            }
            paymentService.registerManualPayment(order, charged, chargeWith, registeredBy);
        }
        return new Checkout(order, charged);
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
        return withItems(buffetOrderRepository
                .findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(from, until));
    }

    /**
     * Las cuentas que el mostrador tiene que tener a mano, por nombre: las que
     * deben o tienen plata a favor, sean del día que sean, y las abiertas hoy que
     * todavía no tienen productos.
     */
    @Transactional(readOnly = true)
    public List<OrderWithItems> unsettledOrders(Tenant club, LocalDate today) {
        return withItems(buffetOrderRepository.findUnsettled(today.atStartOfDay(club.zoneId()).toInstant()));
    }

    private List<OrderWithItems> withItems(List<BuffetOrder> orders) {
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
