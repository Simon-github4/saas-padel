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
import ar.com.padelnec.support.BuffetCoverage;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

    /**
     * Cómo se paga lo que se cobra en una pasada.
     *
     * <p>{@link #all} es todo por un solo medio: el F8 o el F9 con los campos
     * vacíos, que cobran lo elegido o, sin nada elegido, todo lo que debe.
     * {@link #split} es lo que dicen los dos campos, efectivo y transferencia; el
     * efectivo ya sin el vuelto, que no entra a la caja.
     */
    public record Charge(PaymentMethod allWith, BigDecimal cash, BigDecimal transfer, BigDecimal expected) {

        public static Charge all(PaymentMethod method) {
            return new Charge(method, null, null, null);
        }

        /**
         * Todo por un medio, siempre que sea lo que la pantalla mostraba. Si otro
         * puesto cobró o sumó algo en el medio, no se cobra otro monto en
         * silencio: el cliente pagó lo que vio.
         */
        public static Charge all(PaymentMethod method, BigDecimal expected) {
            return new Charge(method, null, null, expected);
        }

        public static Charge split(BigDecimal cash, BigDecimal transfer) {
            return new Charge(null, cash == null ? BigDecimal.ZERO : cash,
                    transfer == null ? BigDecimal.ZERO : transfer, null);
        }

        /** Un monto por un medio, como el cobro del detalle del pedido. */
        public static Charge of(PaymentMethod method, BigDecimal amount) {
            return method == PaymentMethod.TRANSFER ? split(BigDecimal.ZERO, amount) : split(amount, BigDecimal.ZERO);
        }
    }

    /** Cómo quedó el pedido después de guardarlo, y cuánto se cobró por cada medio en esa pasada. */
    public record Checkout(BuffetOrder order, BigDecimal cash, BigDecimal transfer) {

        public BigDecimal charged() {
            return cash.add(transfer);
        }
    }

    /** Unidades de una línea que entran en lo que se cobra ahora. */
    private record Pick(ProductSale sale, int units) {
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
     * @param chargeWith cómo se cobra el saldo, o null para dejarlo pendiente
     * @see #checkout(UUID, String, List, Charge, Map, Map, UUID)
     */
    @Transactional
    public Checkout checkout(UUID orderId, String customerName, List<NewItem> items,
                             PaymentMethod chargeWith, UUID registeredBy) {
        return checkout(orderId, customerName, items, chargeWith == null ? null : Charge.all(chargeWith),
                Map.of(), Map.of(), registeredBy);
    }

    /**
     * Guarda de una vez lo que se armó en el mostrador: abre el pedido si es
     * nuevo, le suma los productos y, si viene un cobro, lo registra.
     *
     * <p>Es el camino rápido de la pantalla de buffet, donde el pedido se arma en
     * pantalla y recién se guarda al cobrar. Va en una sola transacción para que
     * un producto dado de baja en el medio o un cobro rechazado no dejen un
     * pedido a medio cargar.
     *
     * <p>Se cobra toda la cuenta o una parte, de dos formas: eligiendo productos,
     * de a unidades (3 de las 5 milanesas), que tienen que pagarse justo; o por
     * monto, sin elegir nada, que puede ser menos de lo que debe y cubre los
     * productos en el orden en que se cargaron ({@link BuffetCoverage}). Cada
     * parte puede ir en efectivo, por transferencia o mitad y mitad.
     *
     * <p>El nombre solo hace falta para dejar algo en la cuenta: una venta que se
     * cobra entera en el momento puede ir sin nombre (venta rápida). Una que deja
     * algo pendiente, no: una cuenta sin nombre después no se encuentra.
     *
     * @param orderId     el pedido al que se suman los productos, o null para abrir uno
     * @param charge      cómo se paga, o null para dejar todo en la cuenta
     * @param pickedSales unidades elegidas de líneas ya guardadas, por id de venta
     * @param pickedItems unidades elegidas de los productos de {@code items}, por id de producto
     */
    @Transactional
    public Checkout checkout(UUID orderId, String customerName, List<NewItem> items, Charge charge,
                             Map<UUID, Integer> pickedSales, Map<UUID, Integer> pickedItems, UUID registeredBy) {
        boolean unnamed = customerName == null || customerName.isBlank();
        if (orderId == null && charge == null && unnamed) {
            throw new BusinessRuleException("Para dejarlo en la cuenta, poné a nombre de quién es");
        }
        BuffetOrder order = orderId == null ? create(customerName, registeredBy) : require(orderId);
        Map<UUID, ProductSale> createdSales = addItems(order, items, registeredBy);
        if (charge == null) {
            return new Checkout(order, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<ProductSale> lines = linesOf(order);
        List<Pick> picks = picks(lines, pickedSales, pickedItems, createdSales);
        BigDecimal target = picks.isEmpty() ? order.balanceDue() : costOf(order, lines, picks);
        if (target.signum() <= 0) {
            throw new BusinessRuleException(picks.isEmpty()
                    ? "Este pedido no tiene nada para cobrar"
                    : "Lo que elegiste no tiene nada para cobrar");
        }

        BigDecimal cash;
        BigDecimal transfer;
        if (charge.allWith() == null) {
            cash = charge.cash();
            transfer = charge.transfer();
        } else if (charge.allWith() == PaymentMethod.CASH || charge.allWith() == PaymentMethod.TRANSFER) {
            if (charge.expected() != null && charge.expected().compareTo(target) != 0) {
                throw new BusinessRuleException(
                        "Lo que hay que cobrar cambió recién: otro puesto cobró o sumó algo, o cambió un precio."
                                + " Revisalo y volvé a cobrar.");
            }
            cash = charge.allWith() == PaymentMethod.CASH ? target : BigDecimal.ZERO;
            transfer = charge.allWith() == PaymentMethod.TRANSFER ? target : BigDecimal.ZERO;
        } else {
            throw new BusinessRuleException("Ese metodo no se puede cargar a mano");
        }
        if (cash.signum() < 0 || transfer.signum() < 0) {
            throw new BusinessRuleException("El monto tiene que ser mayor a cero");
        }
        BigDecimal charged = cash.add(transfer);
        if (charged.signum() == 0) {
            throw new BusinessRuleException("Poné cuánto se cobra");
        }
        if (picks.isEmpty() && charged.compareTo(target) > 0) {
            throw new BusinessRuleException("No podés cobrar más de lo que debe");
        }
        if (!picks.isEmpty() && charged.compareTo(target) != 0) {
            throw new BusinessRuleException("Lo que se cobra tiene que dar justo lo elegido");
        }
        if (orderId == null && unnamed && charged.compareTo(order.balanceDue()) < 0) {
            throw new BusinessRuleException("Para dejar lo que falta en la cuenta, poné a nombre de quién es");
        }

        if (cash.signum() > 0) {
            paymentService.registerManualPayment(order, cash, PaymentMethod.CASH, registeredBy);
        }
        if (transfer.signum() > 0) {
            paymentService.registerManualPayment(order, transfer, PaymentMethod.TRANSFER, registeredBy);
        }
        picks.forEach(this::markPaid);
        coverInOrder(order);
        return new Checkout(order, cash, transfer);
    }

    /** Suma los productos al pedido. El mismo producto dos veces va en una sola línea: así se lee en el detalle. */
    private Map<UUID, ProductSale> addItems(BuffetOrder order, List<NewItem> items, UUID registeredBy) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (NewItem item : items) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        Map<UUID, Product> products = productRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<UUID, ProductSale> createdSales = new LinkedHashMap<>();
        quantities.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("El producto no existe");
            }
            if (!product.isActive()) {
                throw new BusinessRuleException(product.getName() + " ya no está a la venta");
            }
            createdSales.put(productId, productService.registerSale(order, product, quantity, registeredBy));
        });
        return createdSales;
    }

    private List<ProductSale> linesOf(BuffetOrder order) {
        return productSaleRepository.findAllByBuffetOrderIdInOrderByCreatedAtAscIdAsc(List.of(order.getId()));
    }

    /** Lo elegido en pantalla, ya como líneas del pedido: las guardadas por su id, las recién sumadas por producto. */
    private List<Pick> picks(List<ProductSale> lines, Map<UUID, Integer> pickedSales,
                             Map<UUID, Integer> pickedItems, Map<UUID, ProductSale> createdSales) {
        Map<UUID, ProductSale> byId = lines.stream()
                .collect(Collectors.toMap(ProductSale::getId, Function.identity()));
        List<Pick> picks = new ArrayList<>();
        pickedSales.forEach((saleId, units) -> picks.add(new Pick(found(byId.get(saleId)), units)));
        pickedItems.forEach((productId, units) -> picks.add(new Pick(found(createdSales.get(productId)), units)));
        for (Pick pick : picks) {
            if (pick.sale().isPaid()) {
                throw new BusinessRuleException(pick.sale().getProductName() + " ya está cobrado");
            }
            if (pick.units() <= 0 || pick.units() > pick.sale().getQuantity()) {
                throw new BusinessRuleException("No hay tantas unidades de " + pick.sale().getProductName()
                        + " para cobrar");
            }
        }
        return picks;
    }

    private ProductSale found(ProductSale sale) {
        if (sale == null) {
            throw new ResourceNotFoundException("Uno de los productos elegidos ya no está en la cuenta");
        }
        return sale;
    }

    /** Lo que cuesta lo elegido, descontado lo que ya le cubre lo pagado por monto. */
    private BigDecimal costOf(BuffetOrder order, List<ProductSale> lines, List<Pick> picks) {
        List<BigDecimal> covered = BuffetCoverage.covered(coverageLines(lines), order.getPaidAmount());
        BigDecimal total = BigDecimal.ZERO;
        for (Pick pick : picks) {
            BigDecimal coveredOfLine = covered.get(lines.indexOf(pick.sale()));
            total = total.add(BuffetCoverage.cost(coverageLine(pick.sale()), pick.units(), coveredOfLine));
        }
        return total;
    }

    /** Las unidades elegidas quedan cobradas; si no son todas las de la línea, se separan y las otras siguen pendientes. */
    private void markPaid(Pick pick) {
        markPaid(pick.sale(), pick.units());
    }

    private void markPaid(ProductSale sale, int units) {
        if (units < sale.getQuantity()) {
            ProductSale paidPart = sale.splitOff(units);
            paidPart.setPaid(true);
            productSaleRepository.save(paidPart);
        } else {
            sale.setPaid(true);
        }
    }

    /**
     * Marca como cobrado lo que ya cubre lo pagado por monto, en el orden en que
     * se cargó la cuenta: de 2 milanesas a $12.000, $20.000 cubren una entera.
     *
     * <p>Lo que sobra no llega a otra unidad y queda a cuenta de la línea donde
     * se cortó: esa milanesa se lee «quedan $4.000».
     *
     * <p>Lo gratis no espera su turno: con el primer cobro queda marcado, esté
     * donde esté. Si no, una cuenta saldada podía quedar con un «$0» pendiente.
     */
    private void coverInOrder(BuffetOrder order) {
        List<ProductSale> lines = linesOf(order);
        lines.stream()
                .filter(sale -> !sale.isPaid() && sale.getUnitPrice().signum() == 0)
                .forEach(sale -> sale.setPaid(true));
        BigDecimal left = BuffetCoverage.unassigned(coverageLines(lines), order.getPaidAmount());
        for (ProductSale sale : lines) {
            if (sale.isPaid()) {
                continue;
            }
            if (left.signum() <= 0) {
                return;
            }
            int quantity = sale.getQuantity();
            int whole = left.divideToIntegralValue(sale.getUnitPrice())
                    .min(BigDecimal.valueOf(quantity))
                    .intValue();
            if (whole > 0) {
                left = left.subtract(sale.getUnitPrice().multiply(BigDecimal.valueOf(whole)));
                markPaid(sale, whole);
            }
            if (whole < quantity) {
                return;
            }
        }
    }

    private static List<BuffetCoverage.Line> coverageLines(List<ProductSale> lines) {
        return lines.stream().map(BuffetOrderService::coverageLine).toList();
    }

    private static BuffetCoverage.Line coverageLine(ProductSale sale) {
        return new BuffetCoverage.Line(sale.getUnitPrice(), sale.getQuantity(), sale.isPaid());
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
                .findAllByBuffetOrderIdInOrderByCreatedAtAscIdAsc(orders.stream().map(BuffetOrder::getId).toList())
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
