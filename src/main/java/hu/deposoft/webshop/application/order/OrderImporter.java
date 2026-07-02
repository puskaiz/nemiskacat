package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceOrder;
import hu.deposoft.webshop.integrations.woo.SourceOrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Idempotent historical-order migration (cutover). Upserts by woo_order_id;
 * sets the final status directly; creates a single confirmed Payment only when
 * Woo recorded payment. Lines whose product/variant no longer exists are kept
 * with a null variant and their snapshot. No events, no stock, no invoicing.
 *
 * <p>Each order is imported in its OWN transaction, so a single bad record fails
 * in isolation (recorded in the report's failures list) without rolling back the
 * rest, and the persistence context stays bounded across a 22k-order run.
 * Progress is logged every {@value #PROGRESS_EVERY} orders. Re-running is safe
 * (idempotent by woo_order_id) and retries only the previously failed orders.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderImporter {

    /** Emit a progress log line every this many processed orders. */
    private static final int PROGRESS_EVERY = 500;

    private final VariantRepository variants;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PlatformTransactionManager txManager;

    /** 1:1 WooCommerce status mapping (lossless). Keys are the wc-* values without the wc- prefix. */
    private static final Map<String, OrderStatus> STATUS = Map.of(
            "completed", OrderStatus.COMPLETED,
            "processing", OrderStatus.PROCESSING,
            "on-hold", OrderStatus.ON_HOLD,
            "cancelled", OrderStatus.CANCELLED,
            "failed", OrderStatus.FAILED,
            "refunded", OrderStatus.REFUNDED,
            "awaiting-shipment", OrderStatus.AWAITING_SHIPMENT);

    public OrderImportReport run(List<SourceOrder> sources) {
        OrderImportReport report = new OrderImportReport();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        int total = sources.size();
        int processed = 0;
        log.info("Importing {} orders (one transaction per order)…", total);
        for (SourceOrder src : sources) {
            Outcome out = new Outcome();
            try {
                tx.executeWithoutResult(status -> importOne(src, out));
                applyOutcome(src, out, report);
            } catch (RuntimeException e) {
                // The order's own transaction rolled back; record it and carry on.
                report.failed(src.wooOrderId(), e.toString());
                log.warn("Failed to import order {}: {}", src.wooOrderId(), e.toString());
            }
            if (++processed % PROGRESS_EVERY == 0 || processed == total) {
                log.info("Order import progress: {}/{} ({}%) — imported={}, skipped={}, failed={}",
                        processed, total, total == 0 ? 100 : processed * 100 / total,
                        report.imported(), report.skipped(), report.failedCount());
            }
        }
        log.info("Order import finished: {}", report);
        return report;
    }

    /** All DB work for one order, inside the caller's transaction; records the outcome in {@code out}. */
    private void importOne(SourceOrder src, Outcome out) {
        if (orders.findByWooOrderId(src.wooOrderId()).isPresent()) {
            out.skippedExisting = true;
            return;
        }
        OrderStatus status = mapStatus(src.wooStatus());
        if (status == null) {
            out.unknownStatus = src.wooStatus();
            return;
        }
        out.nonHuf = src.currency() != null && !"HUF".equalsIgnoreCase(src.currency());

        Order order = Order.imported(src.wooOrderId(), status, src.customerName(), src.email(),
                src.phone(), src.postcode(), src.city(), src.addressLine(), src.note(),
                src.shipMethodName(), src.shipGrossHuf(), src.itemsGrossHuf(), src.totalGrossHuf(),
                OffsetDateTime.parse(src.createdAt()));

        for (SourceOrderItem line : src.items()) {
            Variant variant = resolveVariant(line);
            if (variant == null) {
                out.orphanSkus.add(line.sku());
            }
            order.addImportedItem(OrderItem.create(order, variant, line.productName(),
                    line.variantLabel(), line.sku(), line.unitGrossHuf(), line.taxRatePercent(),
                    line.quantity(), InvoiceSource.KULCS_SOFT));
        }
        orders.save(order);
        out.imported = true;

        if (src.paid()) {
            Payment payment = Payment.initiate(order, "woo-pay-" + src.wooOrderId(), src.totalGrossHuf());
            payment.markState(Payment.State.CONFIRMED, "Imported from WooCommerce");
            payments.save(payment);
            out.paid = true;
        }
    }

    /** Apply a committed order's outcome to the report — only after its transaction succeeded. */
    private void applyOutcome(SourceOrder src, Outcome out, OrderImportReport report) {
        if (out.skippedExisting) {
            report.skippedOrder();
        } else if (out.unknownStatus != null) {
            log.warn("Unknown Woo status '{}' on order {} — skipped", out.unknownStatus, src.wooOrderId());
            report.unknownStatus(out.unknownStatus);
            report.skippedOrder();
        } else if (out.imported) {
            report.importedOrder();
            out.orphanSkus.forEach(report::orphanLine);
            if (out.nonHuf) report.nonHufOrder(src.wooOrderId());
            if (out.paid) report.payment();
        }
    }

    OrderStatus mapStatus(String wooStatus) {
        if (wooStatus == null) return null;
        String key = wooStatus.startsWith("wc-") ? wooStatus.substring(3) : wooStatus;
        return STATUS.get(key);
    }

    Variant resolveVariant(SourceOrderItem line) {
        if (line.wooVariationId() != null && line.wooVariationId() != 0) {
            Variant v = variants.findByExternalId(line.wooVariationId()).orElse(null);
            if (v != null) return v;
        }
        if (line.wooProductId() != null) {
            Variant v = variants.findByExternalId(line.wooProductId()).orElse(null);
            if (v != null) return v;
        }
        if (line.sku() != null && !line.sku().isBlank()) {
            return variants.findBySku(line.sku()).orElse(null);
        }
        return null;
    }

    /** Per-order outcome captured inside the transaction, applied to the report only on commit. */
    private static final class Outcome {
        boolean skippedExisting;
        String unknownStatus;
        boolean nonHuf;
        boolean imported;
        boolean paid;
        final List<String> orphanSkus = new ArrayList<>();
    }
}
