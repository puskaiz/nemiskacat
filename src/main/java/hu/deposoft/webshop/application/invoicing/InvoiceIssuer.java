package hu.deposoft.webshop.application.invoicing;

import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;

import java.util.List;

/** Port: issues a real invoice for a group of order lines (Billingo for workshops). */
public interface InvoiceIssuer {

    record InvoiceResult(String invoiceNumber, String publicUrl, String externalId) {
    }

    /** True when the issuer is configured (has key material); false → don't call. */
    default boolean isEnabled() {
        return true;
    }

    InvoiceResult issue(Order order, List<OrderItem> lines);
}
