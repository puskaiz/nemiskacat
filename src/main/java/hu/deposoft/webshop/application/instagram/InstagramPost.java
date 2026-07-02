package hu.deposoft.webshop.application.instagram;

import java.time.Instant;

/**
 * Read-only view of a cached Instagram media item, used on the storefront.
 * No raw token or IG API details leak through this type.
 */
public record InstagramPost(
        String id,
        String caption,        // nullable
        MediaType mediaType,   // IMAGE, VIDEO, CAROUSEL_ALBUM
        String displayUrl,     // image URL; for VIDEO the video thumbnail
        String permalink,
        Instant timestamp) {

    public enum MediaType { IMAGE, VIDEO, CAROUSEL_ALBUM }
}
