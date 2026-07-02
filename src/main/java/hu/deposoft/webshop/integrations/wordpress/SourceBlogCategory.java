package hu.deposoft.webshop.integrations.wordpress;

/** A WordPress blog category (taxonomy=category): display name + 1:1-preserved slug. */
public record SourceBlogCategory(String name, String slug) {
}
