package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.BuffetOrderRepository;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.repository.ProductSaleRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogo de productos del buffet y sus ventas, durante un turno o en un pedido suelto. */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSaleRepository productSaleRepository;
    private final BookingRepository bookingRepository;
    private final BuffetOrderRepository buffetOrderRepository;

    /** Venta de un producto durante un turno: se suma directo al total del turno. */
    @Transactional
    public ProductSale registerSale(Booking booking, Product product, int quantity, UUID registeredBy) {
        if (!booking.getStatus().acceptsPayment()) {
            throw new BusinessRuleException("Este turno ya no admite cargos");
        }
        ProductSale sale = newSale(product, quantity, registeredBy);
        sale.setBooking(booking);
        productSaleRepository.save(sale);

        booking.setTotalPrice(booking.getTotalPrice().add(sale.subtotal()));
        bookingRepository.save(booking);
        return sale;
    }

    /** Venta de un producto en un pedido de buffet sin turno: se suma al total del pedido. */
    @Transactional
    public ProductSale registerSale(BuffetOrder order, Product product, int quantity, UUID registeredBy) {
        ProductSale sale = newSale(product, quantity, registeredBy);
        sale.setBuffetOrder(order);
        productSaleRepository.save(sale);

        order.setTotalPrice(order.getTotalPrice().add(sale.subtotal()));
        buffetOrderRepository.save(order);
        return sale;
    }

    /** Saca una linea cargada por error: resta su importe del total del turno o del pedido. */
    @Transactional
    public void removeSale(UUID saleId) {
        ProductSale sale = productSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("La venta no existe"));
        if (sale.getBooking() != null) {
            Booking booking = sale.getBooking();
            booking.setTotalPrice(booking.getTotalPrice().subtract(sale.subtotal()).max(BigDecimal.ZERO));
            bookingRepository.save(booking);
        } else {
            BuffetOrder order = sale.getBuffetOrder();
            order.setTotalPrice(order.getTotalPrice().subtract(sale.subtotal()).max(BigDecimal.ZERO));
            buffetOrderRepository.save(order);
        }
        productSaleRepository.delete(sale);
    }

    @Transactional(readOnly = true)
    public List<ProductSale> salesOf(UUID bookingId) {
        return productSaleRepository.findAllByBookingIdOrderByCreatedAtAsc(bookingId);
    }

    @Transactional(readOnly = true)
    public List<ProductSale> salesOfOrder(UUID orderId) {
        return productSaleRepository.findAllByBuffetOrderIdInOrderByCreatedAtAsc(List.of(orderId));
    }

    /** Las ventas de varios pedidos de una sola vez, para listarlos sin una consulta por pedido. */
    @Transactional(readOnly = true)
    public List<ProductSale> salesOfOrders(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return productSaleRepository.findAllByBuffetOrderIdInOrderByCreatedAtAsc(orderIds);
    }

    private ProductSale newSale(Product product, int quantity, UUID registeredBy) {
        if (quantity <= 0) {
            throw new BusinessRuleException("La cantidad tiene que ser mayor a cero");
        }
        ProductSale sale = new ProductSale();
        sale.setProduct(product);
        sale.setProductName(product.getName());
        sale.setUnitPrice(product.getUnitPrice());
        sale.setQuantity(quantity);
        sale.setRegisteredBy(registeredBy);
        return sale;
    }

    // ------------------------------------------------------------ catalogo

    @Transactional
    public Product createProduct(String name, BigDecimal unitPrice) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Poné un nombre para el producto");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("El precio tiene que ser mayor o igual a cero");
        }
        Product product = new Product();
        product.setName(name);
        product.setUnitPrice(unitPrice);
        return productRepository.save(product);
    }

    @Transactional
    public void setActive(UUID productId, boolean active) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe"));
        product.setActive(active);
        productRepository.save(product);
    }
}
