package hu.deposoft.webshop.application.catalog;

/**
 * Downloads an image by URL for import pipelines (workshop slider, blog cover +
 * inline images). The bytes are then stored via {@link StorageService#put} to get
 * a content-addressed key.
 */
public interface ImageFetcher {

    /** A fetched image: raw bytes and the MIME type the storage uses to derive a key. */
    record FetchedImage(byte[] bytes, String contentType) {
    }

    /**
     * Download the image at {@code url}.
     *
     * @throws RuntimeException if the fetch fails (callers log + record the error and continue).
     */
    FetchedImage fetch(String url);
}
