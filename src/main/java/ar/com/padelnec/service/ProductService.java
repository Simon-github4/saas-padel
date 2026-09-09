package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.ProductRepository;
import ar.com.padelnec.repository.ProductSaleRepository;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogo de productos de kiosco y sus ventas durante un turno. */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSaleRepository productSaleRepository;
    private final BookingRepository bookingRepository;

    /** Venta de un producto durante un turno: se suma directo al total del turno. */
    @Transactional
    public ProductSale registerSale(Booking booking, Product product, int quantity, UUID registeredBy) {
        if (quantity <= 0) {
            throw new BusinessRuleException("La cantidad tiene que ser mayor a cero");
        }
        if (!booking.getStatus().acceptsPayment()) {
            throw new BusinessRuleException("Este turno ya no admite cargos");
        }
        ProductSale sale = new ProductSale();
        sale.setBooking(booking);
        sale.setProduct(product);
        sale.setProductName(product.getName());
        sale.setUnitPrice(product.getUnitPrice());
        sale.setQuantity(quantity);
        sale.setRegisteredBy(registeredBy);
        productSaleRepository.save(sale);

        booking.setTotalPrice(booking.getTotalPrice().add(sale.subtotal()));
        bookingRepository.save(booking);
        return sale;
    }

    /** Saca una linea cargada por error: resta su importe del total del turno. */
    @Transactional
    public void removeSale(UUID saleId) {
        ProductSale sale = productSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("La venta no existe"));
        Booking booking = sale.getBooking();
        booking.setTotalPrice(booking.getTotalPrice().subtract(sale.subtotal()).max(BigDecimal.ZERO));
        bookingRepository.save(booking);
        productSaleRepository.delete(sale);
    }

    @Transactional(readOnly = true)
    public List<ProductSale> salesOf(UUID bookingId) {
        return productSaleRepository.findAllByBookingIdOrderByCreatedAtAsc(bookingId);
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
