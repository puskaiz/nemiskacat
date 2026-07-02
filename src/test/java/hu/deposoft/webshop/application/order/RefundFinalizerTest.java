package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.invoicing.InvoicingService;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.Payment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RefundFinalizerTest {

    private final AuditService audit = mock(AuditService.class);
    private final InvoicingService invoicing = mock(InvoicingService.class);
    private final RefundFinalizer finalizer = new RefundFinalizer(audit, invoicing);

    @Test
    void leavesPaymentUntouchedWhenOrderCannotTransition() {
        // A COMPLETED order cannot transition to REFUNDED (OrderStatus state machine).
        Order order = Order.place("client-1", "Teszt", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup", "Személyes átvétel", 0L);
        order.transitionTo(OrderStatus.PAID);
        order.transitionTo(OrderStatus.PACKING);
        order.transitionTo(OrderStatus.SHIPPED);
        order.transitionTo(OrderStatus.COMPLETED);
        Payment payment = Payment.initiate(order, "PAY-x", 1000L);
        payment.markState(Payment.State.CONFIRMED, "ok");

        assertThatThrownBy(() -> finalizer.finalizeRefund(order, payment, "msg", true))
                .isInstanceOf(IllegalStateException.class);

        // transitionTo threw before any other mutation: payment still CONFIRMED, no side effects.
        assertThat(payment.getState()).isEqualTo(Payment.State.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verifyNoInteractions(audit, invoicing);
    }
}
