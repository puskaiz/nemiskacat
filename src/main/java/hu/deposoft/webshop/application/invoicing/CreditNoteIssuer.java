package hu.deposoft.webshop.application.invoicing;

import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;

import java.util.List;

/** Port: issues a storno (credit note) for a group of order lines (Billingo for workshops). */
public interface CreditNoteIssuer {

    /** @param originalExternalId the issuer's id of the original invoice document (may be null). */
    InvoiceIssuer.InvoiceResult creditNote(Order order, List<OrderItem> lines, String originalExternalId);

    default boolean isEnabled() {
        return true;
    }
}
