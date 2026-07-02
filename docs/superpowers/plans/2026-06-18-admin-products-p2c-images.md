# Admin Products P2c — image upload + gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin upload and manage product images (upload, set-cover, reorder, delete) behind the product-editor flag — originals stored via a Storage port on a Railway Volume, served by the app; TransformImgs deferred.

**Architecture:** A `StorageService` port (`application/catalog`) with a filesystem impl (`integrations/storage`) writing content-hashed immutable keys under `webshop.storage-dir` (a Railway Volume in prod). `WebshopProperties.imageUrl` routes uploaded `up/…` keys to an app-served `/media/{key}` endpoint (legacy `wp/…` keys still hit `images-base-url`); TransformImgs slots in later behind `imageUrl`. A flag-gated `ProductImageService` + `/api/admin/products/{id}/images` endpoints do the gallery ops; the editable product detail gains a gallery editor. The app does no image processing (CLAUDE.md #8).

**Tech Stack:** Java 21, Spring Boot 3 (multipart), Spring Data JPA, JUnit + Testcontainers + `@TempDir`; admin SPA Refine/react. Design: this plan (brainstormed 2026-06-18); ADR 0006 (updated in Task 5). Builds on P2a (`AdminProperties` flag, `ProductAdminEditService` patterns) + P1 (`ProductAdminQueryService.detail`/`ProductDetailView`).

**Test philosophy:** Storage impl tested with `@TempDir` (no Docker). Image service/endpoints + media serving tested with Testcontainers + MockMvc. Flag-off (403) and non-admin (403) covered. `mvn verify` + admin-ui build/test gate.

---

## File Structure

- **Create** `src/main/java/hu/deposoft/webshop/application/catalog/StorageService.java` — the port.
- **Create** `src/main/java/hu/deposoft/webshop/integrations/storage/FilesystemStorageService.java` — filesystem impl.
- **Modify** `src/main/java/hu/deposoft/webshop/config/WebshopProperties.java` — `imageUrl` routes `up/` keys to `/media/`.
- **Create** `src/main/java/hu/deposoft/webshop/api/MediaController.java` — `GET /media/**`.
- **Modify** `src/main/resources/application.yml` — multipart limits + `webshop.storage-dir`.
- **Modify** `src/main/java/hu/deposoft/webshop/config/AdminSpaConfig.java` — ensure `/media/**` isn't forwarded to the SPA.
- **Modify** `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java` — `ImageView` gains `id`.
- **Create** `src/main/java/hu/deposoft/webshop/application/catalog/ProductImageService.java` — upload/delete/cover/reorder (flag-gated).
- **Create** `src/main/java/hu/deposoft/webshop/api/admin/ProductImageController.java` — the endpoints.
- **Modify** `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java` — `MaxUploadSizeExceededException` → 413.
- **Modify** `src/main/java/hu/deposoft/webshop/domain/catalog/ProductImageRepository.java` — `existsByStorageKey`.
- **Modify** admin-ui: `src/types.ts` (`ProductImageView.id`), `src/pages/products/show.tsx` (gallery editor), `src/api/http.ts` if a multipart helper is needed.
- **Modify** `docs/adr/0006-image-storage-and-cdn.md` (Task 5).

---

## Task 1: StorageService port + filesystem implementation

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/StorageService.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/storage/FilesystemStorageService.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/hu/deposoft/webshop/integrations/storage/FilesystemStorageServiceTest.java`

- [ ] **Step 1: Write the failing test (@TempDir, no Docker)**

```java
package hu.deposoft.webshop.integrations.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FilesystemStorageServiceTest {

    @Test
    void putReturnsContentHashedKeyAndWritesFile(@TempDir Path dir) {
        var storage = new FilesystemStorageService(dir.toString());
        byte[] bytes = "hello-image".getBytes();
        String key = storage.put(bytes, "image/jpeg");
        assertThat(key).startsWith("up/").endsWith(".jpg");
        assertThat(storage.exists(key)).isTrue();
        assertThat(Files.exists(dir.resolve(key))).isTrue();
        // content-addressed: same bytes → same key
        assertThat(storage.put(bytes, "image/jpeg")).isEqualTo(key);
    }

    @Test
    void loadReturnsBytesAndDeleteRemoves(@TempDir Path dir) throws Exception {
        var storage = new FilesystemStorageService(dir.toString());
        String key = storage.put("png-bytes".getBytes(), "image/png");
        assertThat(key).endsWith(".png");
        assertThat(storage.load(key).getContentAsByteArray()).isEqualTo("png-bytes".getBytes());
        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
    }
}
```

- [ ] **Step 2: Run → fail** — `mvn -q -Dtest=FilesystemStorageServiceTest test` → compile failure.

- [ ] **Step 3: Create the port**

`StorageService.java`:
```java
package hu.deposoft.webshop.application.catalog;

import org.springframework.core.io.Resource;

/**
 * Port for binary object storage of product image originals (CLAUDE.md #8: hashed,
 * immutable keys; the app stores bytes but does no image processing). Filesystem-backed
 * today (a Railway Volume in prod); an S3/R2 impl can replace it behind this port.
 */
public interface StorageService {
    /** Store bytes under a content-hashed, immutable key (e.g. {@code up/<sha256>.jpg}). Idempotent. */
    String put(byte[] content, String contentType);
    Resource load(String key);
    void delete(String key);
    boolean exists(String key);
}
```

- [ ] **Step 4: Create the filesystem impl**

`FilesystemStorageService.java`:
```java
package hu.deposoft.webshop.integrations.storage;

import hu.deposoft.webshop.application.catalog.StorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Content-addressed filesystem storage under {@code webshop.storage-dir}. */
@Component
public class FilesystemStorageService implements StorageService {

    private final Path root;

    public FilesystemStorageService(@Value("${webshop.storage-dir:./data/uploads}") String dir) {
        this.root = Path.of(dir);
    }

    @Override
    public String put(byte[] content, String contentType) {
        String key = "up/" + sha256(content) + "." + ext(contentType);
        Path dest = resolve(key);
        try {
            Files.createDirectories(dest.getParent());
            if (!Files.exists(dest)) {
                Files.write(dest, content);
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

    /** Resolve a key under root, rejecting path traversal. */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root.normalize())) {
            throw new IllegalArgumentException("Illegal storage key: " + key);
        }
        return p;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String ext(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }
}
```

- [ ] **Step 5: Add config to `application.yml`**

Under `webshop:` add `storage-dir: ${STORAGE_DIR:./data/uploads}`. Under `spring:` add:
```yaml
  servlet:
    multipart:
      max-file-size: 8MB
      max-request-size: 10MB
```
Also add `data/` to the repo `.gitignore` (the local upload dir must not be committed).

- [ ] **Step 6: Run → pass**

`mvn -q -Dtest=FilesystemStorageServiceTest test` → PASS (2 tests).

- [ ] **Step 7: Commit**
```bash
git add src/main/java/hu/deposoft/webshop/application/catalog/StorageService.java \
        src/main/java/hu/deposoft/webshop/integrations/storage/FilesystemStorageService.java \
        src/main/resources/application.yml .gitignore \
        src/test/java/hu/deposoft/webshop/integrations/storage/FilesystemStorageServiceTest.java
git commit -m "feat(admin): StorageService port + filesystem impl (content-hashed keys) + multipart limits

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Serve originals (`/media`) + route `imageUrl`

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/config/WebshopProperties.java`
- Create: `src/main/java/hu/deposoft/webshop/api/MediaController.java`
- Modify: `src/main/java/hu/deposoft/webshop/config/AdminSpaConfig.java` (only if it would swallow `/media`)
- Test: `src/test/java/hu/deposoft/webshop/config/WebshopPropertiesTest.java`, `src/test/java/hu/deposoft/webshop/api/MediaControllerTest.java`

- [ ] **Step 1: Write the failing tests**

`WebshopPropertiesTest.java` (pure unit):
```java
package hu.deposoft.webshop.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WebshopPropertiesTest {
    private final WebshopProperties props =
            new WebshopProperties("http://cdn.example/up", "http://shop.example", "Nemiskacat");

    @Test void uploadedKeyRoutesToMedia() {
        assertThat(props.imageUrl("up/abc.jpg")).isEqualTo("/media/up/abc.jpg");
    }
    @Test void legacyWpKeyRoutesToImagesBase() {
        assertThat(props.imageUrl("wp/2023/05/x.jpg")).isEqualTo("http://cdn.example/up/2023/05/x.jpg");
    }
}
```
`MediaControllerTest.java` (`@SpringBootTest` + Testcontainers + MockMvc, mirror the harness from `OrderAdminControllerTest`; inject a real `FilesystemStorageService` pointed at a temp dir via `@DynamicPropertySource` setting `webshop.storage-dir`):
```java
    @Test
    void servesStoredImageBytesPublicly() throws Exception {
        String key = storage.put("img-bytes".getBytes(), "image/png"); // @Autowired StorageService storage
        mvc.perform(get("/media/" + key)) // no auth — public
                .andExpect(status().isOk())
                .andExpect(content().bytes("img-bytes".getBytes()));
    }
    @Test
    void unknownKeyReturns404() throws Exception {
        mvc.perform(get("/media/up/doesnotexist.png")).andExpect(status().isNotFound());
    }
```

- [ ] **Step 2: Run → fail.**

- [ ] **Step 3: Route `imageUrl` in `WebshopProperties`**

Replace the `imageUrl` method body:
```java
    /** Resolves a stored image key to a servable URL. Uploaded originals ("up/…") are served by
     *  this app at /media; legacy imported keys ("wp/…") come from the configured images base. */
    public String imageUrl(String storageKey) {
        if (storageKey.startsWith("up/")) {
            return "/media/" + storageKey;
        }
        String path = storageKey.startsWith("wp/") ? storageKey.substring(3) : storageKey;
        return imagesBaseUrl + "/" + path;
    }
```

- [ ] **Step 4: Create `MediaController`**

```java
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
```

- [ ] **Step 5: Make sure the SPA forwarder doesn't swallow `/media`**

Read `AdminSpaConfig.java`. If it forwards unmatched paths to the SPA index (a catch-all), confirm `@RestController` `/media/**` mappings take precedence (they do — controller handlers match before a `ViewControllerRegistry`/forward fallback). If `AdminSpaConfig` explicitly forwards a broad pattern that includes `/media`, exclude `/media/**` from it. (Security already permits `/media` via `.anyRequest().permitAll()`; no SecurityConfig change needed.)

- [ ] **Step 6: Run → pass; commit**
`mvn -q -Dtest=WebshopPropertiesTest,MediaControllerTest test` → PASS.
```bash
git add src/main/java/hu/deposoft/webshop/config/WebshopProperties.java \
        src/main/java/hu/deposoft/webshop/api/MediaController.java \
        src/test/java/hu/deposoft/webshop/config/WebshopPropertiesTest.java \
        src/test/java/hu/deposoft/webshop/api/MediaControllerTest.java
git commit -m "feat(admin): serve uploaded image originals at /media; route imageUrl for up/ keys

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
(Include `AdminSpaConfig.java` if you had to change it.)

---

## Task 3: ProductImageService + endpoints (flag-gated)

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java` (`ImageView` gains `id`)
- Modify: `src/main/java/hu/deposoft/webshop/domain/catalog/ProductImageRepository.java` (`existsByStorageKey`)
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/ProductImageService.java`
- Create: `src/main/java/hu/deposoft/webshop/api/admin/ProductImageController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/ProductImageServiceTest.java`, `src/test/java/hu/deposoft/webshop/api/admin/ProductImageControllerTest.java`

- [ ] **Step 1: `ImageView` gains an id** (the gallery needs ids for cover/delete/reorder)

In `ProductAdminQueryService.java`: change `public record ImageView(String url, String alt) {}` → `public record ImageView(Long id, String url, String alt) {}` and update the `detail()` mapping `.map(i -> new ImageView(i.getId(), properties.imageUrl(i.getStorageKey()), i.getAlt()))`. (`ProductImage` has `getId()` via Lombok.)

- [ ] **Step 2: Add the repo finder**

In `ProductImageRepository.java` add: `boolean existsByStorageKey(String storageKey);`

- [ ] **Step 3: Write the failing service test** (Testcontainers; seed catalog via importer like `ProductAdminQueryServiceTest`; flag ON)

```java
@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
@Testcontainers @Transactional
class ProductImageServiceTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @Autowired ProductImageService imageService;
    @Autowired ProductAdminQueryService query;
    @Autowired hu.deposoft.webshop.application.catalog.CatalogImporter importer;
    // @BeforeEach seed(): copy ProductAdminQueryServiceTest's seed (paint + "kat")
    private Long festekId() { return query.list(null, "fest", 0, 20).items().get(0).id(); }
    private static final byte[] JPG = "fake-jpeg".getBytes();

    @Test void uploadAppendsImageAndFirstBecomesCover() {
        Long id = festekId();
        var view = imageService.upload(id, JPG, "image/jpeg", "a.jpg");
        assertThat(view.images()).hasSize(1);
        assertThat(view.images().get(0).url()).startsWith("/media/up/");
    }
    @Test void rejectsNonImageType() {
        assertThatThrownBy(() -> imageService.upload(festekId(), JPG, "application/pdf", "x.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void deleteRemovesImage() {
        Long id = festekId();
        var v1 = imageService.upload(id, JPG, "image/jpeg", "a.jpg");
        var v2 = imageService.delete(id, v1.images().get(0).id());
        assertThat(v2.images()).isEmpty();
    }
    @Test void reorderAndCover() {
        Long id = festekId();
        imageService.upload(id, "one".getBytes(), "image/jpeg", "1.jpg");
        var two = imageService.upload(id, "two".getBytes(), "image/png", "2.png");
        Long secondId = two.images().get(1).id();
        var covered = imageService.setCover(id, secondId);
        assertThat(covered.images().get(0).id()).isEqualTo(secondId); // cover moved to front
    }
}
```
Plus `ProductImageDisabledTest` (`properties=...=false`) asserting `upload` throws `ProductAdminEditService.EditorDisabledException`.

- [ ] **Step 4: Implement `ProductImageService`**

```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated product image gallery ops (P2c): upload/delete/set-cover/reorder. Stores originals
 *  via StorageService; writes ProductImage rows in our DB. Never touches Woo. */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final StorageService storage;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.ProductDetailView upload(Long productId, byte[] bytes, String contentType, String filename) {
        guard();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported image type: " + contentType);
        }
        Product p = product(productId);
        String key = storage.put(bytes, contentType);
        List<ProductImage> existing = images.findByProductOrderByPositionAsc(p);
        int position = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPosition() + 1;
        images.save(ProductImage.create(p, key, filename == null ? "" : filename, position, existing.isEmpty()));
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView delete(Long productId, Long imageId) {
        guard();
        Product p = product(productId);
        ProductImage img = imageOf(p, imageId);
        String key = img.getStorageKey();
        images.delete(img);
        if (!images.existsByStorageKey(key)) {
            storage.delete(key); // no other row references this original
        }
        reindex(p);
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView setCover(Long productId, Long imageId) {
        guard();
        Product p = product(productId);
        ProductImage cover = imageOf(p, imageId);
        List<ProductImage> ordered = images.findByProductOrderByPositionAsc(p);
        ordered.remove(cover);
        ordered.add(0, cover);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
            ordered.get(i).setFeatured(ordered.get(i) == cover);
        }
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView reorder(Long productId, List<Long> imageIds) {
        guard();
        Product p = product(productId);
        List<ProductImage> current = images.findByProductOrderByPositionAsc(p);
        if (imageIds.size() != current.size()
                || !imageIds.stream().sorted().toList().equals(current.stream().map(ProductImage::getId).sorted().toList())) {
            throw new IllegalArgumentException("Reorder list must contain exactly the product's image ids");
        }
        for (int i = 0; i < imageIds.size(); i++) {
            Long id = imageIds.get(i);
            ProductImage img = current.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            img.setPosition(i);
        }
        return query.detail(productId);
    }

    private void guard() {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
    }

    private ProductImage imageOf(Product p, Long imageId) {
        return images.findByProductOrderByPositionAsc(p).stream()
                .filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No image " + imageId + " on product " + p.getId()));
    }

    private void reindex(Product p) {
        List<ProductImage> ordered = images.findByProductOrderByPositionAsc(p);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
    }
}
```

- [ ] **Step 5: Endpoints + exception mapping**

`ProductImageController.java`:
```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductImageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products/{id}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService imageService;

    public record ReorderRequest(List<Long> imageIds) {}

    @PostMapping
    public ProductDetailView upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return imageService.upload(id, file.getBytes(), file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DeleteMapping("/{imageId}")
    public ProductDetailView delete(@PathVariable Long id, @PathVariable Long imageId) {
        return imageService.delete(id, imageId);
    }

    @PostMapping("/{imageId}/cover")
    public ProductDetailView cover(@PathVariable Long id, @PathVariable Long imageId) {
        return imageService.setCover(id, imageId);
    }

    @PostMapping("/reorder")
    public ProductDetailView reorder(@PathVariable Long id, @RequestBody ReorderRequest req) {
        return imageService.reorder(id, req.imageIds());
    }
}
```
In `AdminExceptionHandler.java` add (oversize upload → 413):
```java
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE)
    public String tooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return "A kép túl nagy (max 8 MB).";
    }
```

- [ ] **Step 6: Controller test** (`@SpringBootTest(properties="webshop.admin.product-editor-enabled=true")`, MockMvc + Testcontainers; mirror `OrderAdminControllerTest` harness + seed):
```java
    @Test void uploadsViaMultipart() throws Exception {
        Long id = /* resolve from GET /api/admin/products */;
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "bytes".getBytes());
        mvc.perform(multipart("/api/admin/products/" + id + "/images").file(file)
                .with(user("a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].url").value(org.hamcrest.Matchers.startsWith("/media/up/")));
    }
    @Test void rejectsNonImage() throws Exception { /* contentType application/pdf → 400 */ }
    @Test void uploadForbiddenForNonAdmin() throws Exception { /* no ADMIN role → 403 */ }
```
Plus a flag-off sibling (`properties=...=false`) → upload returns 403.

- [ ] **Step 7: Run → pass; commit**
`mvn -q -Dtest=ProductImageServiceTest,ProductImageDisabledTest,ProductImageControllerTest,ProductImageEndpointDisabledTest test` → PASS.
```bash
git add src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java \
        src/main/java/hu/deposoft/webshop/domain/catalog/ProductImageRepository.java \
        src/main/java/hu/deposoft/webshop/application/catalog/ProductImageService.java \
        src/main/java/hu/deposoft/webshop/api/admin/ProductImageController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductImageServiceTest.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductImageDisabledTest.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductImageControllerTest.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductImageEndpointDisabledTest.java
git commit -m "feat(admin): flag-gated product image gallery ops (upload/delete/cover/reorder)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
**Note:** adding `id` to `ImageView` means any existing test asserting the old 2-arg shape must be updated — the P1 tests read `images` via accessors (not the constructor), so they keep compiling; verify `ProductAdminQueryServiceTest` stays green.

---

## Task 4: Frontend gallery editor

**Files:**
- Modify: `admin-ui/src/types.ts` (`ProductImageView` gains `id`)
- Modify: `admin-ui/src/pages/products/show.tsx` (gallery editor in the editable branch)
- Modify: `admin-ui/src/api/http.ts` (a multipart POST helper, if `apiFetch` hardcodes JSON content-type)

- [ ] **Step 1: Type**

`types.ts` `ProductImageView` → add `id: number;` (alongside `url`, `alt`).

- [ ] **Step 2: Multipart helper**

Check `src/api/http.ts`'s `apiFetch`. If it always sets `Content-Type: application/json`, add a helper that posts `FormData` WITHOUT a JSON content-type (let the browser set the multipart boundary) but WITH the CSRF header:
```ts
export async function apiUpload(url: string, form: FormData): Promise<Response> {
  return apiFetch(url, { method: "POST", body: form, headers: {} /* no JSON content-type; keep CSRF */ });
}
```
Match however `apiFetch` injects the XSRF header so multipart uploads still carry it. (If `apiFetch` already leaves content-type alone for non-string bodies, just use it directly.)

- [ ] **Step 3: Gallery editor in `show.tsx`** (editable branch only)

In the editable product form, render an image gallery section (faithful to the prototype's gallery: cover + thumbs). For each `product.images` (now `{id, url, alt}`):
- a thumbnail `<img src={img.url}>` (the first is the cover);
- buttons: **Borító** (set-cover → `POST /api/admin/products/${id}/images/${img.id}/cover`), **◀/▶** reorder (compute the new id order and `POST .../images/reorder` with `{ imageIds }`), **Törlés** (delete → `DELETE .../images/${img.id}`);
- an **upload** control: a hidden `<input type="file" accept="image/jpeg,image/png,image/webp">`; on change, build `FormData` with `file` and `POST /api/admin/products/${id}/images`.
- After every op: on `res.ok` → `message.success` + `queryResult.refetch()`; on error → `message.error(await res.text())` (413 → the size message). Use `apiFetch`/`apiUpload` + `API_BASE` + `App.useApp()` message, as elsewhere.
Keep styling consistent with the existing faithful port (plain divs + inline styles + `var(--…)`); the read-only view is unchanged (it already renders the gallery images read-only from P1).

- [ ] **Step 4: Build + test**

From `admin-ui/`: `npm run build` (pass) + `npm run test` (20 pass).

- [ ] **Step 5: Commit**
```bash
git add admin-ui/src/types.ts admin-ui/src/pages/products/show.tsx admin-ui/src/api/http.ts
git commit -m "feat(admin-ui): product image gallery editor (upload/cover/reorder/delete)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: ADR update + full verify

**Files:**
- Modify: `docs/adr/0006-image-storage-and-cdn.md`

- [ ] **Step 1: Update ADR 0006** — note what P2c actually built: the `StorageService` port + filesystem-on-Railway-Volume impl and an app-served `/media/{key}` endpoint for uploaded originals are **in place now**; **TransformImgs delivery is deferred** (a later slice wires `imageUrl` through it). Keep the rest (R2-swappable behind the port, hashed keys, no in-app processing).

- [ ] **Step 2: Full verify**

`mvn verify` → BUILD SUCCESS (new storage/media/image tests + ArchUnit; Docker). From `admin-ui/`: `npm run build` + `npm run test` green.

- [ ] **Step 3: Commit the ADR**
```bash
git add docs/adr/0006-image-storage-and-cdn.md
git commit -m "docs(adr): 0006 — storage port + /media serving in place; TransformImgs deferred (P2c)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Manual sanity (optional):** with the editor flag on, upload an image on a product → it appears (served from `/media/up/…`), set-cover/reorder/delete work, and the list/grid cover updates.

---

## Self-Review

- **Design coverage:** StorageService port + filesystem/Volume + hashed keys → T1; `/media` serving + `imageUrl` routing (TransformImgs deferred) → T2; upload (type jpeg/png/webp, ≤8 MB via multipart limits + 413 mapping) + delete + set-cover + reorder, flag-gated + ADMIN → T3; gallery editor UI → T4; ADR + verify → T5. Reorder = buttons (per the design); `/media` public-read (per the design). Variant-level images out of scope (product-level only — `ProductImage.product`).
- **Placeholder scan:** code is concrete. The controller-test bodies use `/* resolve id */`-style comments for the id-resolution + the two “…Disabled” siblings — these reuse the established `OrderAdminControllerTest` id-resolution helper + the flag-off pattern already used by `ProductAdminEditEndpointDisabledTest`; the executor copies those verbatim. The `AdminSpaConfig` step is conditional (only if it would swallow `/media`), with the check stated.
- **Type consistency:** `StorageService.put/load/delete/exists` consistent across impl, MediaController, ProductImageService, tests. `ImageView(Long id, String url, String alt)` ↔ SPA `ProductImageView {id,url,alt}`; endpoints return `ProductDetailView` (reused from P1). Flag guard reuses `ProductAdminEditService.EditorDisabledException` (→403) and `AdminProperties.productEditorEnabled()`; `NotFoundException` reused; `existsByStorageKey` added where used.

## Notes for the executor
- Uploaded originals live on the configured `webshop.storage-dir` (a **Railway Volume** in prod, set `STORAGE_DIR`); `.gitignore` the local `data/` dir. The keys are content-hashed → safe to cache forever and to dedupe.
- The whole gallery write path is gated by the (now default-on) product-editor flag + ADMIN, with flag-off (403) and non-admin (403) tests. Uploads are CSRF-protected like the other admin POSTs — the multipart helper must keep the XSRF header.
- TransformImgs is intentionally NOT wired here; `imageUrl` returns `/media/...` for uploaded keys today, and a later slice can return a TransformImgs URL behind the same method with no caller changes.
