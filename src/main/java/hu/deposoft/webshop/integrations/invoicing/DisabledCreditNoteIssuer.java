package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.webshop.application.invoicing.CreditNoteIssuer;
import hu.deposoft.webshop.application.invoicing.InvoiceIssuer;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;

import java.util.List;

/** Active until Billingo is configured; issuing throws so the source is recorded FAILED and retried. */
public class DisabledCreditNoteIssuer implements CreditNoteIssuer {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public InvoiceIssuer.InvoiceResult creditNote(Order order, List<OrderItem> lines, String originalExternalId) {
        throw new IllegalStateException("Billingo credit notes are not configured (webshop.invoicing.billingo-enabled=false)");
    }
}
