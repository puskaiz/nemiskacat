package hu.deposoft.webshop.api;

import hu.deposoft.webshop.application.catalog.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Serves uploaded image originals from storage. Public read (images aren't secret); keys are
 *  content-hashed and immutable, so cache hard. Uploads/mutations stay admin+flag-gated. */
@RestController
@RequiredArgsConstructor
public class MediaController {

    private final StorageService storage;

    @GetMapping("/media/**")
    public ResponseEntity<Resource> media(HttpServletRequest request) {
        String key = request.getRequestURI().substring("/media/".length());
        if (!storage.exists(key)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(contentTypeFor(key))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(storage.load(key));
    }

    private static MediaType contentTypeFor(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (k.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (k.endsWith(".jpg") || k.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
