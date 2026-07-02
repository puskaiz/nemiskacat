package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class RefundReconciliationServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired RefundReconciliationService reconciliation;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;

    @MockitoBean PaymentGateway gateway;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
    }

    private Long paidOrder(String key) {
        return paidOrder(key, OrderStatus.PAID);
    }

    private Long paidOrder(String key, OrderStatus status) {
        String token = cart.addItem(null, "FES-1", 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt", "t@example.com",
                null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        if (status == OrderStatus.PACKING) order.transitionTo(OrderStatus.PACKING);
        if (status == OrderStatus.SHIPPED) { order.transitionTo(OrderStatus.PACKING); order.transitionTo(OrderStatus.SHIPPED); }
        orders.save(order);
        return p.getId();
    }

    @Test
    void healsWhenGatewayShowsAlreadyRefunded() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.ALREADY_REFUNDED);
        Long payId = paidOrder("rec-heal");

        RefundReconciliationService.Outcome outcome = reconciliation.reconcileOne(payId);

        assertThat(outcome).isEqualTo(RefundReconciliationService.Outcome.HEALED);
        Payment p = payments.findById(payId).orElseThrow();
        assertThat(p.getState()).isEqualTo(Payment.State.REVERSED);
        assertThat(orders.findById(p.getOrder().getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order", String.valueOf(p.getOrder().getId())))
                .anySatisfy(e -> {
                    assertThat(e.getAction()).isEqualTo("ORDER_REFUNDED");
                    assertThat(e.getSummary()).contains("(reconciled)");
                });
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void leavesOrderWhenRefundInProgress() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUND_IN_PROGRESS);
        Long payId = paidOrder("rec-inprog");

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.NONE);
        assertThat(payments.findById(payId).orElseThrow().getState()).isEqualTo(Payment.State.CONFIRMED);
        assertThat(orders.findById(payments.findById(payId).orElseThrow().getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void doesNothingWhenStillRefundable() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
        Long payId = paidOrder("rec-normal");

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.NONE);
        assertThat(orders.findById(payments.findById(payId).orElseThrow().getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void alertsWhenGatewaySaysNotRefundable() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.NOT_REFUNDABLE);
        Long payId = paidOrder("rec-notref");

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.ALERTED);
        Payment p = payments.findById(payId).orElseThrow();
        assertThat(p.isAlert()).isTrue();
        assertThat(orders.findById(p.getOrder().getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void skipsOrderThatMovedPastPacking() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.ALREADY_REFUNDED);
        Long payId = paidOrder("rec-shipped", OrderStatus.SHIPPED);

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.NONE);
        assertThat(orders.findById(payments.findById(payId).orElseThrow().getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
        verify(gateway, never()).refundability(anyString());
    }

    @Test
    void candidateFinderSelectsOnlyOpenConfirmedNotRecentlyChecked() {
        Long open = paidOrder("rec-cand-open");
        Long shipped = paidOrder("rec-cand-shipped", OrderStatus.SHIPPED);
        Long checked = paidOrder("rec-cand-checked");
        payments.findById(checked).orElseThrow().touchChecked(OffsetDateTime.now(ZoneOffset.UTC));

        List<Long> ids = reconciliation.findCandidatePaymentIds(Duration.ofHours(24));

        assertThat(ids).contains(open).doesNotContain(shipped, checked);
    }
}
