package hu.deposoft.webshop.integrations.woo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A product as exported from WooCommerce. For a simple product the price/stock
 * fields below apply and {@code variants} is empty; for a variable product the
 * sellable data lives in {@code variants}.
 */
public record SourceProduct(
        long wooId,
        String slug,
        String name,
        String type,
        String status,
        String shortDescription,
        String description,
        String taxClass,
        String seoTitle,
        String metaDescription,
        List<Long> categoryWooTermIds,
        List<String> attributeSlugs,
        String sku,
        Long regularPriceHuf,
        Long salePriceHuf,
        OffsetDateTime saleFrom,
        OffsetDateTime saleTo,
        Boolean manageStock,
        Integer stockQty,
        Integer weightGrams,
        List<SourceVariant> variants,
        List<SourceImage> images,
        List<Long> tagWooTermIds) {
}
