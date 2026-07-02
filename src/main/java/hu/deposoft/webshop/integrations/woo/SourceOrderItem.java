package hu.deposoft.webshop.integrations.woo;

/** One WooCommerce order line as exported from the WP DB. */
public record SourceOrderItem(
        Long wooProductId,
        Long wooVariationId,
        String sku,
        String productName,
        String variantLabel,
        int quantity,
        long unitGrossHuf,
        int taxRatePercent,
        long lineGrossHuf) {
}
