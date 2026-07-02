package hu.deposoft.webshop.integrations.wordpress;

import java.time.OffsetDateTime;

public record SourcePage(long externalId, String slug, String title, String bodyHtml,
                         String status, OffsetDateTime publishedAt,
                         String seoTitle, String seoDescription) {
}
