package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.InvoiceRepository;
import hu.deposoft.webshop.domain.order.InvoiceState;
import hu.deposoft.webshop.domain.order.InvoiceType;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class RefundServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired RefundService refunds;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;
    @Autowired WorkshopService workshops;
    @Autowired InvoiceRepository invoices;

    @MockitoBean
    PaymentGateway gateway;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
    }

    /** A NEW order with a CONFIRMED payment, driven to a given status. */
    private Order orderInStatus(String key, OrderStatus status) {
        String token = cart.addItem(null, "FES-1", 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt", "t@example.com",
                null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        if (status != OrderStatus.PAID) {
            order.transitionTo(OrderStatus.PAID);
            if (status == OrderStatus.PACKING) order.transitionTo(OrderStatus.PACKING);
        } else {
            order.transitionTo(OrderStatus.PAID);
        }
        return orders.save(order);
    }

    @Test
    void refundsPaidOrder() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
        Long id = orderInStatus("r-ok", OrderStatus.PAID).getId();

        refunds.refund(id);

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payments.findFirstByOrderOrderByIdDesc(orders.findById(id).orElseThrow()).orElseThrow().getState())
                .isEqualTo(Payment.State.REVERSED);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order", String.valueOf(id)))
                .anySatisfy(e -> assertThat(e.getAction()).isEqualTo("ORDER_REFUNDED"));
    }

    @Test
    void rejectsRefundOfShippedOrder() {
        Long id = orderInStatus("r-ship", OrderStatus.PACKING).getId();
        Order o = orders.findById(id).orElseThrow();
        o.transitionTo(OrderStatus.SHIPPED);
        orders.save(o);

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundNotAllowedException.class);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void gatewayFailureLeavesOrderUnchanged() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(false, "bank declined"));
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
        Long id = orderInStatus("r-fail", OrderStatus.PAID).getId();

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundFailedException.class);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void rejectsWholeOrderRefundWhenALineIsCancelled() {
        Order order = orderInStatus("r-partial", OrderStatus.PAID);
        order.getItems().get(0).cancelWholeLine();
        orders.save(order);
        Long id = order.getId();

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundNotAllowedException.class);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void refundIsIdempotent() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
        Long id = orderInStatus("r-idem", OrderStatus.PAID).getId();

        refunds.refund(id);
        refunds.refund(id); // already REFUNDED → no-op

        verify(gateway, org.mockito.Mockito.times(1)).refund(anyString(), anyLong());
    }

    @Test
    void refundsWorkshopOrderEvenWhenBillingoCreditNoteFails() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS", "ws-ref", "leiras", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-REF");
        String token = cart.addItem(null, "WS-REF", 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand("ws-refund", "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        payments.save(Payment.initiate(order, "PAY-WSREF", order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        Long id = orders.save(order).getId();

        refunds.refund(id);

        Order refunded = orders.findById(id).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payments.findFirstByOrderOrderByIdDesc(refunded).orElseThrow().getState())
                .isEqualTo(Payment.State.REVERSED);
        var note = invoices.findByOrderAndSourceAndTypeAndOrderItemIsNull(refunded, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE)
                .orElseThrow();
        assertThat(note.getState()).isEqualTo(InvoiceState.FAILED);
    }

    @Test
    void refundsAlreadyRefundedAtGatewayWithoutRecharging() {
        when(gateway.refundability(anyString()))
                .thenReturn(PaymentGateway.Refundability.ALREADY_REFUNDED);
        Long id = orderInStatus("r-already", OrderStatus.PAID).getId();

        refunds.refund(id); // a prior refund landed but the commit rolled back; this is the retry

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payments.findFirstByOrderOrderByIdDesc(orders.findById(id).orElseThrow()).orElseThrow().getState())
                .isEqualTo(Payment.State.REVERSED);
        verify(gateway, never()).refund(anyString(), anyLong()); // no second charge
    }

    @Test
    void rejectsRefundWhenGatewayReportsRefundInProgress() {
        when(gateway.refundability(anyString()))
                .thenReturn(PaymentGateway.Refundability.REFUND_IN_PROGRESS);
        Long id = orderInStatus("r-inprog", OrderStatus.PAID).getId();

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundFailedException.class);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void rejectsRefundWhenGatewayReportsNotRefundable() {
        when(gateway.refundability(anyString()))
                .thenReturn(PaymentGateway.Refundability.NOT_REFUNDABLE);
        Long id = orderInStatus("r-notref", OrderStatus.PAID).getId();

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundFailedException.class);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(gateway, never()).refund(anyString(), anyLong());
    }
}
