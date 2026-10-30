package ar.com.padelnec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.BuffetOrder;
import ar.com.padelnec.domain.Payment;
import ar.com.padelnec.domain.Product;
import ar.com.padelnec.domain.ProductSale;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.gym.GymFixture;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.service.BuffetOrderService;
import ar.com.padelnec.service.BuffetOrderService.Charge;
import ar.com.padelnec.service.BuffetOrderService.Checkout;
import ar.com.padelnec.service.BuffetOrderService.NewItem;
import ar.com.padelnec.service.PaymentService;
import ar.com.padelnec.service.ProductService;
import ar.com.padelnec.support.BuffetCoverage;
import ar.com.padelnec.web.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Los casos raros del cobro de a partes en el buffet: centavos, productos
 * gratis, sacar cosas ya pagadas, precios que cambian en el medio, cuentas
 * viejas con datos del cobro anterior. En todos, la plata tiene que cerrar:
 * lo cobrado es la suma de los pagos, y lo que falta es lo que muestran las líneas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestDatabaseConfig.class, ClubFixture.class, GymFixture.class})
class BuffetPartialPaymentEdgeCasesTest {

    @Autowired private BuffetOrderService buffetOrderService;
    @Autowired private ProductService productService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ClubFixture fixture;
    @Autowired private JdbcTemplate jdbc;

    private Product agua;
    private Product cafe;

    @BeforeEach
    void setUp() {
        fixture.reset();
        Tenant club = fixture.club("club-necochea");
        TenantContext.set(club.getId());
        agua = productService.createProduct("Agua", new BigDecimal("1500"));
        cafe = productService.createProduct("Café", new BigDecimal("2000"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Con centavos: un monto que no llega a una unidad deja centavos a cuenta, y elegir el resto cierra justo")
    void centsAddUpExactly() {
        Product alfajor = productService.createProduct("Alfajor", new BigDecimal("333.33"));
        UUID orderId = tab("Marta", new NewItem(alfajor.getId(), 3));

        buffetOrderService.checkout(orderId, null, List.of(), Charge.split(new BigDecimal("333.34"), BigDecimal.ZERO),
                Map.of(), Map.of(), null);
        assertThat(order(orderId).balanceDue()).isEqualByComparingTo("666.65");
        ProductSale pending = pendingLines(orderId).getFirst();
        assertThat(pending.getQuantity()).isEqualTo(2);
        assertThat(covered(orderId).getFirst()).isEqualByComparingTo("0.01");

        Checkout rest = buffetOrderService.checkout(orderId, null, List.of(), Charge.all(PaymentMethod.TRANSFER),
                Map.of(pending.getId(), 2), Map.of(), null);

        assertThat(rest.transfer()).isEqualByComparingTo("666.65");
        assertMoneyCloses(orderId);
        assertThat(order(orderId).balanceDue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Un monto con más de dos decimales no se cobra: la base lo redondearía distinto en el pago y en la cuenta")
    void moreThanTwoDecimalsIsRejected() {
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 1));

        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.split(new BigDecimal("100.005"), BigDecimal.ZERO), Map.of(), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("decimales");
        assertThat(paymentRepository.findAllByBuffetOrderIdOrderByCreatedAtAsc(orderId)).isEmpty();
    }

    @Test
    @DisplayName("Un producto gratis no traba nada: se cobra lo demás y queda marcado; elegirlo solo no cobra nada")
    void freeProducts() {
        Product cortesia = productService.createProduct("Agua de cortesía", BigDecimal.ZERO);
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 1), new NewItem(cortesia.getId(), 1));
        ProductSale free = productService.salesOfOrder(orderId).stream()
                .filter(sale -> sale.getUnitPrice().signum() == 0).findFirst().orElseThrow();

        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.all(PaymentMethod.CASH), Map.of(free.getId(), 1), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nada para cobrar");

        buffetOrderService.checkout(orderId, null, List.of(), Charge.all(PaymentMethod.CASH), Map.of(), Map.of(), null);
        assertThat(productService.salesOfOrder(orderId)).allMatch(ProductSale::isPaid);
        assertMoneyCloses(orderId);
    }

    @Test
    @DisplayName("Sacar algo ya pagado por monto pasa esa plata a lo que sigue: la cuenta debe menos, no se pierde nada")
    void removingSomethingPaidMovesTheMoneyOn() {
        // Dos aguas pedidas en dos vueltas (dos líneas) y un café: $5.000.
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 1));
        buffetOrderService.checkout(orderId, null, List.of(new NewItem(agua.getId(), 1)), null, null);
        buffetOrderService.checkout(orderId, null, List.of(new NewItem(cafe.getId(), 1)), null, null);
        buffetOrderService.checkout(orderId, null, List.of(), Charge.split(new BigDecimal("3000"), BigDecimal.ZERO),
                Map.of(), Map.of(), null);
        ProductSale paidWater = productService.salesOfOrder(orderId).getFirst();
        assertThat(paidWater.isPaid()).isTrue();

        productService.removeSale(paidWater.getId());

        // Quedan 1 agua y 1 café ($3.500); se pagaron $3.000: el agua que queda paga y el café con $1.500 a cuenta.
        assertThat(order(orderId).balanceDue()).isEqualByComparingTo("500");
        assertThat(covered(orderId)).extracting(BigDecimal::intValue).containsExactly(1500);
        Checkout rest = buffetOrderService.checkout(orderId, null, List.of(), Charge.all(PaymentMethod.TRANSFER),
                Map.of(), Map.of(), null);
        assertThat(rest.transfer()).isEqualByComparingTo("500");
        assertThat(productService.salesOfOrder(orderId)).allMatch(ProductSale::isPaid);
        assertMoneyCloses(orderId);
    }

    @Test
    @DisplayName("Sacar el producto a medio pagar deja esa plata a favor, y se devuelve justa")
    void removingTheHalfPaidLineLeavesCredit() {
        Product milanesa = productService.createProduct("Milanesa", new BigDecimal("12000"));
        UUID orderId = tab("Mesa 4", new NewItem(milanesa.getId(), 2));
        buffetOrderService.checkout(orderId, null, List.of(), Charge.split(new BigDecimal("20000"), BigDecimal.ZERO),
                Map.of(), Map.of(), null);
        ProductSale halfPaid = pendingLines(orderId).getFirst();

        productService.removeSale(halfPaid.getId());

        assertThat(order(orderId).creditBalance()).isEqualByComparingTo("8000");
        paymentService.registerRefund(order(orderId), new BigDecimal("8000"), PaymentMethod.CASH, null);
        assertThat(order(orderId).balanceDue()).isEqualByComparingTo("0");
        assertThat(order(orderId).creditBalance()).isEqualByComparingTo("0");
        assertMoneyCloses(orderId);
    }

    @Test
    @DisplayName("Si el precio cambió mientras la pantalla tenía el viejo, lo elegido no se cobra con el monto viejo")
    void priceChangedWhileTheScreenWasOpen() {
        UUID orderId = tab("Marta", new NewItem(cafe.getId(), 1));
        productService.updateProduct(agua.getId(), "Agua", new BigDecimal("2000"));

        // La pantalla sumó un agua cuando valía $1.500 y la eligió para cobrarla con eso.
        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(new NewItem(agua.getId(), 1)),
                Charge.split(new BigDecimal("1500"), BigDecimal.ZERO), Map.of(), Map.of(agua.getId(), 1), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("justo lo elegido");
        assertThat(productService.salesOfOrder(orderId)).hasSize(1);
        assertThat(order(orderId).getPaidAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Elegir de lo guardado y de lo recién sumado, y pagarlo mitad y mitad, cierra justo")
    void pickingSavedAndNewItemsWithMixedPayment() {
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 3));
        ProductSale water = productService.salesOfOrder(orderId).getFirst();

        // 2 aguas guardadas ($3.000) + 1 café que se suma ahora ($2.000) = $5.000.
        Checkout part = buffetOrderService.checkout(orderId, null, List.of(new NewItem(cafe.getId(), 2)),
                Charge.split(new BigDecimal("1000"), new BigDecimal("4000")),
                Map.of(water.getId(), 2), Map.of(cafe.getId(), 1), null);

        assertThat(part.charged()).isEqualByComparingTo("5000");
        assertThat(order(orderId).balanceDue()).isEqualByComparingTo("3500");
        assertThat(pendingLines(orderId))
                .extracting(ProductSale::getProductName, ProductSale::getQuantity)
                .containsExactlyInAnyOrder(tuple("Agua", 1), tuple("Café", 1));
        assertMoneyCloses(orderId);
    }

    @Test
    @DisplayName("Muchos pagos chicos por monto suman exacto, sin que un centavo quede colgado")
    void manySmallPaymentsAddUp() {
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 1), new NewItem(cafe.getId(), 1));
        // $3.500 en seis pagos, con centavos sueltos.
        for (String amount : List.of("0.01", "999.99", "500", "1000", "999.99", "0.01")) {
            buffetOrderService.checkout(orderId, null, List.of(), Charge.split(new BigDecimal(amount), BigDecimal.ZERO),
                    Map.of(), Map.of(), null);
            assertMoneyCloses(orderId);
        }
        assertThat(order(orderId).balanceDue()).isEqualByComparingTo("0");
        assertThat(productService.salesOfOrder(orderId)).allMatch(ProductSale::isPaid);
        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.split(new BigDecimal("0.01"), BigDecimal.ZERO), Map.of(), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nada para cobrar");
    }

    @Test
    @DisplayName("Montos negativos, cero o un medio que no es de mostrador no registran nada")
    void nonsenseAmountsAreRejected() {
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 2));

        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.split(new BigDecimal("-500"), new BigDecimal("3500")), Map.of(), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.split(BigDecimal.ZERO, BigDecimal.ZERO), Map.of(), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.all(PaymentMethod.MERCADOPAGO), Map.of(), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.split(new BigDecimal("999999999999"), BigDecimal.ZERO), Map.of(), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(paymentRepository.findAllByBuffetOrderIdOrderByCreatedAtAsc(orderId)).isEmpty();
        assertThat(order(orderId).getPaidAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Una cuenta vieja con todo marcado como pagado aunque debía (el cobro de antes) se sigue pudiendo cobrar justo")
    void legacyAccountMarkedPaidButOwing() {
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 2), new NewItem(cafe.getId(), 1));
        paymentService.registerManualPayment(order(orderId), new BigDecimal("1000"), PaymentMethod.CASH, null);
        // El detalle de antes marcaba todo como cobrado con cualquier pago.
        jdbc.update("UPDATE product_sale SET paid = true WHERE buffet_order_id = ?", orderId);

        assertThat(covered(orderId)).allMatch(amount -> amount.signum() == 0);
        Checkout rest = buffetOrderService.checkout(orderId, null, List.of(), Charge.all(PaymentMethod.CASH),
                Map.of(), Map.of(), null);

        assertThat(rest.cash()).isEqualByComparingTo("4000");
        assertThat(order(orderId).balanceDue()).isEqualByComparingTo("0");
        assertMoneyCloses(orderId);
    }

    @Test
    @DisplayName("Si otro puesto cobró una parte mientras la pantalla mostraba el total viejo, F8 no cobra otro monto en silencio")
    void chargeAllWithAStaleAmountIsRejected() {
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 2), new NewItem(cafe.getId(), 1));
        // Esta pantalla ve $5.000; el otro puesto cobra $2.000.
        buffetOrderService.checkout(orderId, null, List.of(), Charge.split(new BigDecimal("2000"), BigDecimal.ZERO),
                Map.of(), Map.of(), null);

        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(),
                Charge.all(PaymentMethod.CASH, new BigDecimal("5000")), Map.of(), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cambió recién");
        assertThat(order(orderId).getPaidAmount()).isEqualByComparingTo("2000");

        Checkout rest = buffetOrderService.checkout(orderId, null, List.of(),
                Charge.all(PaymentMethod.CASH, new BigDecimal("3000")), Map.of(), Map.of(), null);
        assertThat(rest.cash()).isEqualByComparingTo("3000");
        assertMoneyCloses(orderId);
    }

    @Test
    @DisplayName("Lo elegido ya pagado por otro puesto no se vuelve a cobrar")
    void somethingAnotherStationAlreadyCharged() {
        UUID orderId = tab("Marta", new NewItem(agua.getId(), 2));
        ProductSale water = productService.salesOfOrder(orderId).getFirst();
        buffetOrderService.checkout(orderId, null, List.of(), Charge.all(PaymentMethod.CASH),
                Map.of(water.getId(), 2), Map.of(), null);

        // El otro puesto todavía tenía las dos aguas elegidas.
        assertThatThrownBy(() -> buffetOrderService.checkout(orderId, null, List.of(), Charge.all(PaymentMethod.CASH),
                Map.of(water.getId(), 2), Map.of(), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(paymentRepository.findAllByBuffetOrderIdOrderByCreatedAtAsc(orderId)).hasSize(1);
        assertMoneyCloses(orderId);
    }

    // ------------------------------------------------------------------ ayudas

    private UUID tab(String name, NewItem... items) {
        return buffetOrderService.checkout(null, name, List.of(items), null, null).order().getId();
    }

    private BuffetOrder order(UUID orderId) {
        return buffetOrderService.require(orderId);
    }

    private List<ProductSale> pendingLines(UUID orderId) {
        return productService.salesOfOrder(orderId).stream().filter(sale -> !sale.isPaid()).toList();
    }

    /** Lo que le cubre lo pagado por monto a cada línea pendiente, en orden. */
    private List<BigDecimal> covered(UUID orderId) {
        List<ProductSale> lines = productService.salesOfOrder(orderId);
        List<BigDecimal> all = BuffetCoverage.covered(lines.stream()
                .map(sale -> new BuffetCoverage.Line(sale.getUnitPrice(), sale.getQuantity(), sale.isPaid()))
                .toList(), order(orderId).getPaidAmount());
        return java.util.stream.IntStream.range(0, lines.size())
                .filter(i -> !lines.get(i).isPaid())
                .mapToObj(all::get)
                .toList();
    }

    /**
     * La plata cierra: lo cobrado de la cuenta es la suma de sus pagos, el
     * total es la suma de sus líneas, y lo que debe es lo que les falta a las
     * líneas pendientes (salvo una cuenta vieja marcada de más, donde lo que
     * manda es el saldo).
     */
    private void assertMoneyCloses(UUID orderId) {
        BuffetOrder order = order(orderId);
        BigDecimal payments = paymentRepository.findAllByBuffetOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(order.getPaidAmount()).isEqualByComparingTo(payments);
        List<ProductSale> lines = productService.salesOfOrder(orderId);
        BigDecimal total = lines.stream().map(ProductSale::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(order.getTotalPrice()).isEqualByComparingTo(total);
        BigDecimal marked = lines.stream().filter(ProductSale::isPaid).map(ProductSale::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (marked.compareTo(order.getPaidAmount()) <= 0) {
            BigDecimal pendingLeft = lines.stream().filter(sale -> !sale.isPaid()).map(ProductSale::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .subtract(order.getPaidAmount().subtract(marked))
                    .max(BigDecimal.ZERO);
            assertThat(order.balanceDue()).isEqualByComparingTo(pendingLeft);
        }
    }
}
