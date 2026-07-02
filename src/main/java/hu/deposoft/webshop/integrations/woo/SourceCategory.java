package hu.deposoft.webshop.integrations.woo;

public record SourceCategory(
        long wooTermId,
        String slug,
        String name,
        Long parentWooTermId,
        int sortOrder,
        String description,
        String seoTitle,
        String metaDescription) {
}
