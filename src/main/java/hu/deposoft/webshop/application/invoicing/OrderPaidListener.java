package hu.deposoft.webshop.application.invoicing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Triggers invoicing only after the PAID transition has committed. */
@Component
@RequiredArgsConstructor
public class OrderPaidListener {

    private final InvoicingService invoicing;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        invoicing.invoice(event.orderId());
    }
}
