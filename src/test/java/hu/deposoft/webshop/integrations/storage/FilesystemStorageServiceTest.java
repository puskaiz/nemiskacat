package hu.deposoft.webshop.integrations.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filesystem storage adapter — content-addressed keys, no Docker required.
 */
class FilesystemStorageServiceTest {

    @Test
    void putIsContentAddressedAndRoundTrips(@TempDir Path dir) throws IOException {
        FilesystemStorageService storage = new FilesystemStorageService(dir.toString());

        byte[] jpeg = "hello-jpeg-bytes".getBytes();

        String key = storage.put(jpeg, "image/jpeg");

        // sha256 hex of the bytes, under up/ with the jpg extension
        assertThat(key).matches("up/[0-9a-f]{64}\\.jpg");

        // file actually written under the storage dir
        Path written = dir.resolve(key);
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readAllBytes(written)).isEqualTo(jpeg);

        // same bytes -> same key (content-addressed, idempotent)
        String key2 = storage.put(jpeg, "image/jpeg");
        assertThat(key2).isEqualTo(key);

        // exists
        assertThat(storage.exists(key)).isTrue();

        // load returns the same bytes
        assertThat(storage.load(key).getContentAsByteArray()).isEqualTo(jpeg);

        // delete removes
        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
        assertThat(Files.exists(written)).isFalse();
    }

    @Test
    void pngContentTypeYieldsPngExtension(@TempDir Path dir) {
        FilesystemStorageService storage = new FilesystemStorageService(dir.toString());

        String key = storage.put("png-bytes".getBytes(), "image/png");

        assertThat(key).matches("up/[0-9a-f]{64}\\.png");
    }
}
