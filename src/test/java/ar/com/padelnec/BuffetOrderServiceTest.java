package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.repository.BuffetOrderRepository;
import ar.com.padelnec.service.BuffetOrderService;
import ar.com.padelnec.service.BuffetOrderService.OrderWithItems;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.web.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pedidos de buffet sin turno: se abren con un nombre, los productos se suman
 * después y se cobran igual que un turno.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class})
class BuffetOrderServiceTest {

    @Autowired private BuffetOrderService buffetOrderService;
    @Autowired private BuffetOrderRepository buffetOrderRepository;
    @Autowired private ProductService productService;
    @Autowired private PaymentService paymentService;
    @Autowired private ClubFixture fixture;
    @Autowired private JdbcTemplate jdbc;

    private Tenant club;
    private Product agua;
    private Product cafe;

    @BeforeEach
    void setUp() {
        fixture.reset();
        club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        agua = productService.createProduct("Agua", new BigDecimal("1500"));
        cafe = productService.createProduct("Café", new BigDecimal("2000"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Un pedido se abre a nombre de alguien, vacío; sin nombre no")
    void anOrderOpensEmptyUnderAName() {
        BuffetOrder order = buffetOrderService.open("  Marta  ", null);

        assertThat(order.getCustomerName()).isEqualTo("Marta");
        assertThat(order.getTotalPrice()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> buffetOrderService.open("   ", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("a nombre de quién");
    }

    @Test
    @DisplayName("Los productos se suman al pedido después de abrirlo, y quitar uno lo resta")
    void itemsAddUpAfterOpeningAndRemovingSubtracts() {
        BuffetOrder order = buffetOrderService.open("Marta", null);

        productService.registerSale(order, agua, 2, null);
        ProductSale coffee = productService.registerSale(reload(order), cafe, 1, null);
        assertThat(reload(order).getTotalPrice()).isEqualByComparingTo("5000");

        productService.removeSale(coffee.getId());
        assertThat(reload(order).getTotalPrice()).isEqualByComparingTo("3000");

        assertThat(buffetOrderService.ordersOn(club, LocalDate.now(club.zoneId())))
                .singleElement()
                .satisfies(listed -> assertThat(listed.items())
                        .extracting(ProductSale::getProductName)
                        .containsExactly("Agua"));
    }

    @Test
    @DisplayName("Se cobra igual que un turno, y lo cobrado de más se devuelve")
    void chargesAndRefundsLikeABooking() {
        BuffetOrder order = buffetOrderService.open("Marta", null);
        ProductSale water = productService.registerSale(order, agua, 2, null);

        paymentService.registerManualPayment(reload(order), new BigDecimal("3000"), PaymentMethod.CASH, null);
        assertThat(reload(order).balanceDue()).isEqualByComparingTo("0");

        // Se saca un agua ya cobrada: quedan 1500 a favor, y se devuelven.
        productService.removeSale(water.getId());
        productService.registerSale(reload(order), agua, 1, null);
        assertThat(reload(order).creditBalance()).isEqualByComparingTo("1500");
        paymentService.registerRefund(reload(order), new BigDecimal("1500"), PaymentMethod.CASH, null);

        BuffetOrder settled = reload(order);
        assertThat(settled.getPaidAmount()).isEqualByComparingTo("1500");
        assertThat(settled.balanceDue()).isEqualByComparingTo("0");
        assertThat(settled.creditBalance()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> paymentService.registerRefund(reload(order), new BigDecimal("5000"),
                PaymentMethod.CASH, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Un pedido abierto por error se borra con sus productos, pero no si ya tiene cobros")
    void onlyOrdersWithoutPaymentsCanBeDeleted() {
        BuffetOrder mistake = buffetOrderService.open("Por error", null);
        productService.registerSale(mistake, agua, 1, null);
        buffetOrderService.delete(mistake.getId());
        assertThat(buffetOrderRepository.findById(mistake.getId())).isEmpty();

        BuffetOrder paid = buffetOrderService.open("Marta", null);
        productService.registerSale(paid, agua, 1, null);
        paymentService.registerManualPayment(reload(paid), new BigDecimal("1500"), PaymentMethod.CASH, null);
        assertThatThrownBy(() -> buffetOrderService.delete(paid.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("devolución");
    }

    @Test
    @DisplayName("La base no deja una venta sin turno ni pedido")
    void aSaleMustBelongToABookingOrAnOrder() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product_sale (club_id, product_id, product_name, unit_price, quantity)
                VALUES (?, ?, 'Agua', 1500, 1)
                """, club.getId(), agua.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private BuffetOrder reload(BuffetOrder order) {
        return buffetOrderService.require(order.getId());
    }
}
