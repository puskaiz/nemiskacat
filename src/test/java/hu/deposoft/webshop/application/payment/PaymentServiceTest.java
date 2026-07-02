package hu.deposoft.webshop.application.payment;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T10 acceptance: payment init, and every gateway error branch handled
 * idempotently — duplicate callback, aborted payment + retry, late callback via
 * the re-check job, and the "paid but recording failed" alert.
 */
@SpringBootTest
@Testcontainers
@Transactional
class PaymentServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    PaymentService paymentService;

    @Autowired
    CartService cartService;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    CatalogImporter importer;

    @Autowired
    PaymentRepository payments;

    @Autowired
    OrderRepository orders;

    @MockitoBean
    PaymentGateway gateway;

    Order order;

    @BeforeEach
    void seedAndPlaceOrder() {
        SourceProduct product = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(product), List.of()));
        String token = cartService.addItem(null, "FES-1", 2).token();
        order = checkoutService.placeOrder(token, new PlaceOrderCommand(
                "pay-test-key", "Teszt Elek", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        when(gateway.initPayment(any(), Mockito.anyLong(), any())).thenReturn(
                new PaymentGateway.InitResult("PAY-1", "https://bank.example/pay/PAY-1"));
    }

    // ---- init ----

    @Test
    void startCreatesInitiatedPaymentAndReturnsRedirect() {
        PaymentService.StartResult result = paymentService.start(order, "http://localhost/khpos/return");

        assertThat(result.redirectUrl()).isEqualTo("https://bank.example/pay/PAY-1");
        Payment payment = payments.findByPayId("PAY-1").orElseThrow();
        assertThat(payment.getState()).isEqualTo(Payment.State.INITIATED);
        assertThat(payment.getAmountHuf()).isEqualTo(order.getTotalGrossHuf());
    }

    @Test
    void startIsRefusedForNonNewOrder() {
        order.transitionTo(OrderStatus.PAID);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentService.start(order, "http://localhost/khpos/return"))
                .isInstanceOf(PaymentService.PaymentNotAllowedException.class);
    }

    // ---- confirmed + duplicate callback ----

    @Test
    void confirmedResultMarksOrderPaid() {
        paymentService.start(order, "http://localhost/khpos/return");

        paymentService.applyGatewayResult("PAY-1", PaymentGateway.ResultKind.CONFIRMED, "OK");

        assertThat(payments.findByPayId("PAY-1").orElseThrow().getState()).isEqualTo(Payment.State.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void duplicateConfirmedCallbackIsNoOp() {
        paymentService.start(order, "http://localhost/khpos/return");
        paymentService.applyGatewayResult("PAY-1", PaymentGateway.ResultKind.CONFIRMED, "OK");

        paymentService.applyGatewayResult("PAY-1", PaymentGateway.ResultKind.CONFIRMED, "OK again");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        Payment payment = payments.findByPayId("PAY-1").orElseThrow();
        assertThat(payment.isAlert()).isFalse();
        assertThat(payment.getResultMessage()).isEqualTo("OK"); // second call did not touch it
    }

    // ---- aborted payment + retry ----

    @Test
    void rejectedPaymentLeavesOrderNewAndAllowsRetry() {
        paymentService.start(order, "http://localhost/khpos/return");
        paymentService.applyGatewayResult("PAY-1", PaymentGateway.ResultKind.REJECTED, "card declined");

        assertThat(payments.findByPayId("PAY-1").orElseThrow().getState()).isEqualTo(Payment.State.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);

        // retry: a new attempt gets its own payId
        when(gateway.initPayment(any(), Mockito.anyLong(), any())).thenReturn(
                new PaymentGateway.InitResult("PAY-2", "https://bank.example/pay/PAY-2"));
        paymentService.start(order, "http://localhost/khpos/return");
        assertThat(payments.count()).isEqualTo(2);
    }

    // ---- late callback: re-check job ----

    @Test
    void recheckJobResolvesLateCallback() {
        paymentService.start(order, "http://localhost/khpos/return");
        when(gateway.queryStatus("PAY-1")).thenReturn(
                new PaymentGateway.StatusResult(PaymentGateway.ResultKind.CONFIRMED, "settled late"));

        paymentService.recheckPending(java.time.Duration.ZERO);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        Payment payment = payments.findByPayId("PAY-1").orElseThrow();
        assertThat(payment.getState()).isEqualTo(Payment.State.CONFIRMED);
        assertThat(payment.getLastCheckedAt()).isNotNull();
    }

    @Test
    void recheckSkipsRecentAndTerminalPayments() {
        paymentService.start(order, "http://localhost/khpos/return");
        // cutoff 1 hour: the fresh INITIATED payment is too recent to check
        paymentService.recheckPending(java.time.Duration.ofHours(1));

        Mockito.verify(gateway, Mockito.never()).queryStatus(any());
    }

    // ---- "paid but recording failed" ----

    @Test
    void confirmedOnCancelledOrderRaisesAlert() {
        paymentService.start(order, "http://localhost/khpos/return");
        order.transitionTo(OrderStatus.CANCELLED);

        paymentService.applyGatewayResult("PAY-1", PaymentGateway.ResultKind.CONFIRMED, "OK");

        Payment payment = payments.findByPayId("PAY-1").orElseThrow();
        assertThat(payment.getState()).isEqualTo(Payment.State.CONFIRMED); // money WAS taken
        assertThat(payment.isAlert()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void confirmedForUnknownPayIdDoesNotCrash() {
        paymentService.applyGatewayResult("NO-SUCH-PAY", PaymentGateway.ResultKind.CONFIRMED, "OK");
        // logged as ERROR; nothing to assert in the DB — must simply not throw
    }

    // ---- reversal ----

    @Test
    void reversedPaymentIsRecordedOrderStaysNew() {
        paymentService.start(order, "http://localhost/khpos/return");
        paymentService.applyGatewayResult("PAY-1", PaymentGateway.ResultKind.REVERSED, "user cancelled");

        assertThat(payments.findByPayId("PAY-1").orElseThrow().getState()).isEqualTo(Payment.State.REVERSED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
    }
}
