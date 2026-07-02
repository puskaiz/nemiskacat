package hu.deposoft.webshop.integrations.wordpress;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A WordPress blog post (post_type=post) as exported to JSON. {@code externalId} is
 * the WP post id; {@code slug} is the WP post_name (preserved 1:1). {@code contentHtml}
 * is the raw post_content (converted to Markdown at import). {@code status} is the WP
 * status ("publish"/"draft"). {@code publishedAt} is post_date_gmt (UTC), null for drafts
 * without a date. {@code coverImageUrl} is the featured image's fetchable URL (or null).
 */
public record SourceBlogPost(
        long externalId,
        String slug,
        String title,
        String excerpt,
        String contentHtml,
        String status,
        OffsetDateTime publishedAt,
        String coverImageUrl,
        String seoTitle,
        String seoDescription,
        List<String> categorySlugs,
        List<String> tagSlugs) {
}
