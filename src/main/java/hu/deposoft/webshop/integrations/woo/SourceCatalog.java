package hu.deposoft.webshop.integrations.woo;

import java.util.List;

/** Neutral import model: one full catalog snapshot, source-agnostic (JSON export now, REST later). */
public record SourceCatalog(
        List<SourceCategory> categories,
        List<SourceAttribute> attributes,
        List<SourceProduct> products,
        List<SourceTag> tags) {
}
