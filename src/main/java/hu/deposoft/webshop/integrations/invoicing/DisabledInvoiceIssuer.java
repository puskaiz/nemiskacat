package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.webshop.application.invoicing.InvoiceIssuer;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;

import java.util.List;

/** Active until Billingo is configured (webshop.invoicing.billingo-enabled=false).
 *  Issuing throws → the source is recorded FAILED and retried once enabled. */
public class DisabledInvoiceIssuer implements InvoiceIssuer {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public InvoiceResult issue(Order order, List<OrderItem> lines) {
        throw new IllegalStateException("Billingo invoicing is not configured (webshop.invoicing.billingo-enabled=false)");
    }
}
