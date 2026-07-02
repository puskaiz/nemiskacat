package hu.deposoft.webshop.application.catalog;

import org.springframework.core.io.Resource;

/**
 * Port to blob storage for product images (dependency inversion: the application
 * layer owns the contract, integrations/storage implements it). Keys are
 * content-addressed (sha256 of the bytes), so the same image always maps to the
 * same key and re-uploads are idempotent.
 */
public interface StorageService {

    /**
     * Store the given bytes and return their storage key.
     *
     * @param bytes       the file content
     * @param contentType MIME type (e.g. {@code image/jpeg}); drives the key extension
     * @return content-addressed key, e.g. {@code up/<sha256>.jpg}
     */
    String put(byte[] bytes, String contentType);

    /** Load the content at {@code key} as a Spring {@link Resource}. */
    Resource load(String key);

    /** Delete the content at {@code key} (no-op if it does not exist). */
    void delete(String key);

    /** Whether content exists at {@code key}. */
    boolean exists(String key);
}
