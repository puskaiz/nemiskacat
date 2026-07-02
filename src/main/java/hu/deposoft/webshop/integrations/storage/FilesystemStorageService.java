package hu.deposoft.webshop.integrations.storage;

import hu.deposoft.webshop.application.catalog.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Filesystem-backed {@link StorageService}. Content-addressed: the key is the
 * sha256 hex of the bytes plus a MIME-derived extension, namespaced under {@code up/}.
 * Re-uploading identical bytes is a no-op (same key, file already present), which
 * keeps the Woo image import idempotent (CLAUDE.md rule #4) and storage de-duplicated.
 *
 * <p>This is the interim local-disk adapter; object storage replaces it later without
 * touching the application layer (the port stays the same).
 */
@Component
public class FilesystemStorageService implements StorageService {

    private final Path root;

    public FilesystemStorageService(@Value("${webshop.storage-dir:./data/uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String put(byte[] bytes, String contentType) {
        String key = "up/" + sha256Hex(bytes) + "." + extensionFor(contentType);
        Path target = resolve(key);
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store " + key, e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        return new FileSystemResource(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    /** Resolve a key under the storage root, rejecting path traversal (../, absolute keys). */
    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Illegal storage key: " + key);
        }
        return resolved;
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            return "bin";
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; this never happens.
            throw new IllegalStateException(e);
        }
    }
}
