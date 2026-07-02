package hu.deposoft.webshop.integrations.woo;

import java.util.List;

/**
 * A workshop as exported from the legacy WooCommerce/Elementor site. Mirrors
 * {@link SourceProduct} in style but carries only what a workshop needs: identity
 * ({@code externalId} = the WP page id; {@code slug} taken 1:1 from Woo), the
 * assembled {@code descriptionHtml}, and the ordered slider/gallery images.
 * Sessions/dates/prices are out of scope (entered in the admin).
 */
public record SourceWorkshop(
        long externalId,
        String slug,
        String name,
        String descriptionHtml,
        List<SourceWorkshopImage> images) {
}
