package hu.deposoft.webshop.integrations.woo;

/**
 * One slide of a workshop's image slider. Unlike {@link SourceImage} (catalog
 * import, where the storage key already exists), the workshop import only has the
 * source {@code url}; the bytes are fetched and stored during import to obtain a
 * content-addressed storage key. {@code position} preserves slider order.
 */
public record SourceWorkshopImage(
        String url,
        int position) {
}
