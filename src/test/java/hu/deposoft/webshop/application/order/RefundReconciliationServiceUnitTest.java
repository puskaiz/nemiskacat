package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-unit coverage of the {@code reconcileOne} heal-failure branch with mocked collaborators
 * (the Testcontainers {@code RefundReconciliationServiceTest} covers the happy/heal/alert paths,
 * but a real {@code RefundFinalizer} cannot be made to throw on a genuinely PAID order). Locks
 * the escalation guarantee: a gateway-confirmed refund that the DB cannot record raises the
 * alert flag and reports ALERTED rather than silently dropping it.
 */
class RefundReconciliationServiceUnitTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final RefundFinalizer finalizer = mock(RefundFinalizer.class);
    private final RefundReconciliationService service =
            new RefundReconciliationService(orders, payments, gateway, finalizer);

    @Test
    void alertsWhenHealFails() {
        Payment payment = mock(Payment.class);
        Order order = mock(Order.class);
        when(payments.findById(1L)).thenReturn(Optional.of(payment));
        when(payment.getState()).thenReturn(Payment.State.CONFIRMED);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getPayId()).thenReturn("PAY-1");
        when(order.getId()).thenReturn(10L);
        when(order.orderNumber()).thenReturn("NK-1");
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        when(gateway.refundability("PAY-1")).thenReturn(PaymentGateway.Refundability.ALREADY_REFUNDED);
        doThrow(new IllegalStateException("boom"))
                .when(finalizer).finalizeRefund(order, payment, "reconciled: already refunded at gateway", true);

        RefundReconciliationService.Outcome outcome = service.reconcileOne(1L);

        assertThat(outcome).isEqualTo(RefundReconciliationService.Outcome.ALERTED);
        verify(payment).raiseAlert(contains("could not be finalized"));
        verify(gateway, never()).refund(anyString(), anyLong());
    }
}
