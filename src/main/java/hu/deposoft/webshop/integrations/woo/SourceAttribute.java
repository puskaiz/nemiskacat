package hu.deposoft.webshop.integrations.woo;

import java.util.List;

public record SourceAttribute(
        long wooAttributeId,
        String slug,
        String label,
        String type,
        List<SourceAttributeValue> values) {
}
