package hu.deposoft.webshop.application.invoicing;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.order.Invoice;
import hu.deposoft.webshop.domain.order.InvoiceRepository;
import hu.deposoft.webshop.domain.order.InvoiceState;
import hu.deposoft.webshop.domain.order.InvoiceType;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Issues invoices for a paid order, partitioning the lines by their invoice
 * source (T24): BILLINGO lines → an invoice we issue; KULCS_SOFT lines → pushed
 * to Kulcs-Soft. Idempotent — a source already invoiced successfully is skipped;
 * FAILED sources are retried. Runs after the PAID commit (event) and from a
 * scheduled retry.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvoicingService {

    private final InvoiceRepository invoices;
    private final OrderRepository orders;
    private final InvoiceIssuer issuer;
    private final OrderSink kulcsSink;
    private final CreditNoteIssuer creditNoteIssuer;

    @Transactional
    public void invoice(Long orderId) {
        Order order = orders.findWithItemsById(orderId).orElse(null);
        if (order == null) {
            log.warn("invoice: order {} not found", orderId);
            return;
        }
        Map<InvoiceSource, List<OrderItem>> bySource = order.getItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getInvoiceSource));

        bySource.forEach((source, lines) -> {
            Invoice invoice = invoices.findByOrderAndSourceAndType(order, source, InvoiceType.INVOICE).orElse(null);
            if (invoice != null && invoice.isSuccessful()) {
                return; // already invoiced for this source
            }
            if (invoice == null) {
                invoice = invoices.save(Invoice.of(order, source));
            }
            try {
                switch (source) {
                    case BILLINGO -> {
                        InvoiceIssuer.InvoiceResult r = issuer.issue(order, lines);
                        invoice.recordIssued(r.invoiceNumber(), r.publicUrl(), r.externalId());
                        log.info("Order {} invoiced via Billingo: {}", order.orderNumber(), r.invoiceNumber());
                    }
                    case KULCS_SOFT -> {
                        kulcsSink.push(order, lines);
                        invoice.recordPushed();
                        log.info("Order {} pushed to Kulcs-Soft ({} line(s))", order.orderNumber(), lines.size());
                    }
                    default -> throw new IllegalStateException("Unhandled invoice source: " + source);
                }
            } catch (RuntimeException e) {
                invoice.recordFailed(e.getMessage());
                log.error("Invoicing failed for order {} source {}: {}", order.orderNumber(), source, e.getMessage());
            }
        });
    }

    /** Storno: issue a credit note per source, mirroring {@link #invoice}. Idempotent + retryable. */
    @Transactional
    public void creditNote(Long orderId) {
        Order order = orders.findWithItemsById(orderId).orElse(null);
        if (order == null) {
            log.warn("creditNote: order {} not found", orderId);
            return;
        }
        Map<InvoiceSource, List<OrderItem>> bySource = order.getItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getInvoiceSource));

        bySource.forEach((source, lines) -> {
            Invoice note = invoices.findByOrderAndSourceAndTypeAndOrderItemIsNull(order, source, InvoiceType.CREDIT_NOTE).orElse(null);
            if (note != null && note.isSuccessful()) {
                return;
            }
            if (note == null) {
                note = invoices.save(Invoice.creditNote(order, source));
            }
            try {
                switch (source) {
                    case BILLINGO -> {
                        String originalExternalId = invoices
                                .findByOrderAndSourceAndType(order, source, InvoiceType.INVOICE)
                                .map(Invoice::getExternalId).orElse(null);
                        InvoiceIssuer.InvoiceResult r = creditNoteIssuer.creditNote(order, lines, originalExternalId);
                        note.recordIssued(r.invoiceNumber(), r.publicUrl(), r.externalId());
                        log.info("Order {} credit-noted via Billingo: {}", order.orderNumber(), r.invoiceNumber());
                    }
                    case KULCS_SOFT -> {
                        kulcsSink.pushCreditNote(order, lines);
                        note.recordPushed();
                        log.info("Order {} credit pushed to Kulcs-Soft ({} line(s))", order.orderNumber(), lines.size());
                    }
                    default -> throw new IllegalStateException("Unhandled invoice source: " + source);
                }
            } catch (RuntimeException e) {
                note.recordFailed(e.getMessage());
                log.error("Credit note failed for order {} source {}: {}", order.orderNumber(), source, e.getMessage());
            }
        });
    }

    /** Storno of a single cancelled line (2b-2), scoped to that order_item. Idempotent + retryable. */
    @Transactional
    public void creditNoteForLine(OrderItem line) {
        Order order = line.getOrder();
        InvoiceSource source = line.getInvoiceSource();
        Invoice note = invoices.findByOrderItemAndType(line, InvoiceType.CREDIT_NOTE).orElse(null);
        if (note != null && note.isSuccessful()) {
            return;
        }
        if (note == null) {
            note = invoices.save(Invoice.creditNote(order, source, line));
        }
        try {
            switch (source) {
                case BILLINGO -> {
                    String originalExternalId = invoices
                            .findByOrderAndSourceAndTypeAndOrderItemIsNull(order, source, InvoiceType.INVOICE)
                            .map(Invoice::getExternalId).orElse(null);
                    InvoiceIssuer.InvoiceResult r = creditNoteIssuer.creditNote(order, List.of(line), originalExternalId);
                    note.recordIssued(r.invoiceNumber(), r.publicUrl(), r.externalId());
                    log.info("Order {} line {} credit-noted via Billingo: {}", order.orderNumber(), line.getId(), r.invoiceNumber());
                }
                case KULCS_SOFT -> {
                    kulcsSink.pushCreditNote(order, List.of(line));
                    note.recordPushed();
                    log.info("Order {} line {} credit pushed to Kulcs-Soft", order.orderNumber(), line.getId());
                }
                default -> throw new IllegalStateException("Unhandled invoice source: " + source);
            }
        } catch (RuntimeException e) {
            note.recordFailed(e.getMessage());
            log.error("Line credit note failed for order {} line {}: {}", order.orderNumber(), line.getId(), e.getMessage());
        }
    }

    /**
     * Retry any (order, type) left in FAILED state (scheduled). Line-scoped credit
     * notes retry per line. The returned int is the approximate number of distinct
     * work items attempted (a whole-order retry may cover several lines in one call).
     */
    @Transactional
    public int retryFailed() {
        List<Invoice> failed = invoices.findByState(InvoiceState.FAILED);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.INVOICE)
                .map(i -> i.getOrder().getId()).distinct()
                .forEach(this::invoice);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE && i.getOrderItem() == null)
                .map(i -> i.getOrder().getId()).distinct()
                .forEach(this::creditNote);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE && i.getOrderItem() != null)
                .forEach(i -> creditNoteForLine(i.getOrderItem()));
        return (int) failed.stream()
                .map(i -> i.getOrder().getId() + ":" + i.getType()
                        + ":" + (i.getOrderItem() == null ? "-" : i.getOrderItem().getId()))
                .distinct().count();
    }
}
