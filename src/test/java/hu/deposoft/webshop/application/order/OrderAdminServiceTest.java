package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class OrderAdminServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    OrderAdminService service;

    @Autowired
    OrderRepository orders;

    @Autowired
    AuditEntryRepository audit;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    CatalogImporter importer;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
    }

    private Order newOrder(String key) {
        String token = cart.addItem(null, "FES-1", 1).token();
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt Elek", "t@example.com",
                null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
    }

    /** Drive an order to PAID without going through the gateway. */
    private Order paidOrder(String key) {
        Order o = newOrder(key);
        o.transitionTo(OrderStatus.PAID);
        return orders.save(o);
    }

    @Test
    void advancesPaidOrderThroughFulfilment() {
        Long id = paidOrder("t-chain").getId();

        service.transition(id, OrderStatus.PACKING);
        service.transition(id, OrderStatus.SHIPPED);
        service.transition(id, OrderStatus.COMPLETED);

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order", String.valueOf(id)))
                .extracting(e -> e.getAction()).contains("ORDER_STATUS_CHANGE");
    }

    @Test
    void rejectsSettingPaidFromAdmin() {
        Long id = newOrder("t-paid").getId();
        assertThatThrownBy(() -> service.transition(id, OrderStatus.PAID))
                .isInstanceOf(OrderAdminService.TransitionNotAllowedException.class);
    }

    @Test
    void rejectsIllegalHop() {
        Long id = paidOrder("t-hop").getId(); // PAID -> COMPLETED is not allowed by the domain gate
        assertThatThrownBy(() -> service.transition(id, OrderStatus.COMPLETED))
                .isInstanceOf(OrderAdminService.TransitionNotAllowedException.class);
    }

    @Test
    void cancelsUnpaidOrder() {
        Long id = newOrder("t-cancel").getId();
        service.transition(id, OrderStatus.CANCELLED);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void rejectsCancellingPaidOrder() {
        Long id = paidOrder("t-paid-cancel").getId();
        assertThatThrownBy(() -> service.transition(id, OrderStatus.CANCELLED))
                .isInstanceOf(OrderAdminService.TransitionNotAllowedException.class);
    }

    @Test
    void unknownOrderIsNotFound() {
        assertThatThrownBy(() -> service.transition(999999L, OrderStatus.PACKING))
                .isInstanceOf(OrderAdminQueryService.NotFoundException.class);
    }
}
