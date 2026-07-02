package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.billingo.client.DocumentClient;
import hu.deposoft.billingo.model.document.Document;
import hu.deposoft.webshop.application.invoicing.CreditNoteIssuer;
import hu.deposoft.webshop.application.invoicing.InvoiceIssuer;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Issues a Billingo credit note by cancelling the original document (storno).
 * Active only when {@code webshop.invoicing.billingo-enabled=true}; otherwise
 * {@link DisabledCreditNoteIssuer} stands in. Needs the original invoice's
 * Billingo document id (stored as the forward invoice's externalId).
 */
@Slf4j
public class BillingoCreditNoteIssuer implements CreditNoteIssuer {

    private final DocumentClient documents;

    public BillingoCreditNoteIssuer(DocumentClient documents) {
        this.documents = documents;
    }

    @Override
    public InvoiceIssuer.InvoiceResult creditNote(Order order, List<OrderItem> lines, String originalExternalId) {
        if (originalExternalId == null || originalExternalId.isBlank()) {
            throw new IllegalStateException("No original Billingo document id for order " + order.orderNumber());
        }
        Document cancelled = documents.cancel(Long.parseLong(originalExternalId.trim()));
        log.info("Billingo credit note {} for order {}", cancelled.invoiceNumber(), order.orderNumber());
        return new InvoiceIssuer.InvoiceResult(
                cancelled.invoiceNumber(), cancelled.publicUrl(), String.valueOf(cancelled.id()));
    }
}
