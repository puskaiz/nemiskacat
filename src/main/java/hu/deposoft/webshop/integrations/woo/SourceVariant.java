package hu.deposoft.webshop.integrations.woo;

import java.time.OffsetDateTime;
import java.util.Map;

/** A variation of a variable product. {@code attributes} maps attribute slug to value slug. */
public record SourceVariant(
        long wooId,
        String sku,
        Long regularPriceHuf,
        Long salePriceHuf,
        OffsetDateTime saleFrom,
        OffsetDateTime saleTo,
        Boolean manageStock,
        Integer stockQty,
        Integer weightGrams,
        int position,
        Map<String, String> attributes) {
}
