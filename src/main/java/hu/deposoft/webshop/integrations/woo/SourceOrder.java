package hu.deposoft.webshop.integrations.woo;

import java.util.List;

/** A WooCommerce order (legacy shop_order) as exported from the WP DB. wooStatus is the raw wc-* key. */
public record SourceOrder(
        long wooOrderId,
        String orderKey,
        String wooStatus,
        String currency,
        String createdAt,
        String paidAt,
        Long wpUserId,
        String customerName,
        String email,
        String phone,
        String postcode,
        String city,
        String addressLine,
        String note,
        String shipMethodName,
        long shipGrossHuf,
        long itemsGrossHuf,
        long totalGrossHuf,
        boolean paid,
        String transactionId,
        List<SourceOrderItem> items) {
}
