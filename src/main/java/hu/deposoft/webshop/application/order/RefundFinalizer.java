package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.invoicing.InvoicingService;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The single definition of "finalize a refund in the DB" — shared by the manual refund
 * ({@link RefundService}) and the reconciliation sweep ({@code RefundReconciliationService}):
 * move the order to REFUNDED, mark the payment reversed, audit it, and issue the per-source
 * storno invoice. {@code transitionTo} runs FIRST so an illegal transition throws before any
 * other state changes — a caller that catches it then sees a clean, unmutated aggregate.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefundFinalizer {

    private final AuditService audit;
    private final InvoicingService invoicing;

    /** @param reconciled true when a background reconciliation healed it (audit-trail marker). */
    public void finalizeRefund(Order order, Payment payment, String reversalMessage, boolean reconciled) {
        OrderStatus previous = order.getStatus();
        order.transitionTo(OrderStatus.REFUNDED);
        payment.markState(Payment.State.REVERSED, reversalMessage);
        String suffix = reconciled ? " (reconciled)" : "";
        audit.record("ORDER_REFUNDED", "order", String.valueOf(order.getId()), previous + "→REFUNDED" + suffix);
        log.info("Order {} refunded ({} HUF){}", order.orderNumber(), order.getTotalGrossHuf(),
                reconciled ? " [reconciled]" : "");
        invoicing.creditNote(order.getId());
    }
}
