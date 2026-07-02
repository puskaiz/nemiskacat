# WordPress Blog Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import the nemiskacat WordPress blog posts + categories into the existing DB-backed blog CMS, idempotently, with HTML→Markdown conversion and images pulled into hashed storage.

**Architecture:** Two-stage pipeline mirroring the existing catalog/workshop imports — a Python exporter (`export-blog.py`) `docker exec`s into the `wp_db` MySQL container and emits a `blog.json` snapshot; a Java `BlogImporter` reads it (`@Profile("import-blog")` runner) and upserts by slug into `blog_post`/`blog_category`, converting post HTML to Markdown (flexmark html2md) and downloading cover + inline `wp-content/uploads` images into the content-addressed storage.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, flexmark (`flexmark-all` + `flexmark-html2md-converter`), `java.net.http.HttpClient`, Jackson 3 (`tools.jackson`); Python 3 exporter via `docker exec … mysql`. Tests: JUnit + Testcontainers (Postgres); a fixture-based Python test.

## Global Constraints

- Idempotency (CLAUDE.md #4): re-running upserts by **slug** (WP `post_name`) — no duplicates; WP-side edits propagate on the next run.
- Slug preservation (CLAUDE.md #7): WP `post_name` taken 1:1; slug is immutable on update.
- Time (CLAUDE.md #6): `post_date_gmt` stored as UTC `published_at`.
- Images (CLAUDE.md #8): downloaded into hashed/content-addressed storage via `StorageService.put(bytes, contentType)`; the app does no image processing.
- Business logic in services only (CLAUDE.md #1); code/comments/commits in English (#10).
- Read-only on WordPress: the exporter only reads blog tables; **live DB operations are human-only** — CI/staging may run the import.
- Status mapping: WP `publish`→`PUBLISHED` (+`published_at`), `draft`→`DRAFT`; `revision`/`auto-draft`/`trash`/`inherit` excluded.
- Inline-image rule: only images whose URL contains `/wp-content/uploads/` are downloaded + rewritten to `/media/<key>`; external image URLs are left untouched.
- WordPress source (same install as products): container `wp_db`, DB `client7002dbnem`, table prefix `guxdop_`, `mysql -uroot -proot_password`.
- Recommended products: WP has none → imported posts get empty recommendations.
- Build: Maven, NO `./mvnw` wrapper — use `mvn`.

---

## File Structure

**Create (Java):**
- `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlog.java`
- `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlogPost.java`
- `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlogCategory.java`
- `src/main/java/hu/deposoft/webshop/application/blog/HtmlToMarkdown.java`
- `src/main/java/hu/deposoft/webshop/application/catalog/ImageFetcher.java` (generalized; shared)
- `src/main/java/hu/deposoft/webshop/application/catalog/HttpImageFetcher.java` (generalized; shared)
- `src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java`
- `src/main/java/hu/deposoft/webshop/application/blog/BlogImportReport.java`
- `src/main/java/hu/deposoft/webshop/config/BlogImportRunner.java`

**Modify (Java):**
- `pom.xml` — add `flexmark-html2md-converter`
- `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopImporter.java` — use shared `ImageFetcher`
- delete `application/workshop/WorkshopImageFetcher.java` + `HttpWorkshopImageFetcher.java` (replaced by shared)

**Create (Python):**
- `scripts/woo-export/export-blog.py`
- `scripts/woo-export/test_export_blog.py`
- update `scripts/woo-export/README.md`

**Test (Java):**
- `src/test/java/hu/deposoft/webshop/application/blog/HtmlToMarkdownTest.java`
- `src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java`

---

## Task 1: flexmark-html2md dependency + HtmlToMarkdown component

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/hu/deposoft/webshop/application/blog/HtmlToMarkdown.java`
- Test: `src/test/java/hu/deposoft/webshop/application/blog/HtmlToMarkdownTest.java`

**Interfaces:**
- Produces: `HtmlToMarkdown` `@Component` with `String convert(String html)` — null/blank → `""`. Used by `BlogImporter` (Task 4).

- [ ] **Step 1: Add the dependency to `pom.xml`**

Next to the existing `flexmark-all` dependency add:
```xml
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-html2md-converter</artifactId>
    <version>0.64.8</version>
</dependency>
```
(`flexmark-all` 0.64.8 does NOT bundle the html2md converter — it must be declared explicitly.)

- [ ] **Step 2: Write the failing test**

```java
package hu.deposoft.webshop.application.blog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HtmlToMarkdownTest {

    private final HtmlToMarkdown converter = new HtmlToMarkdown();

    @Test
    void convertsHeadingBoldAndLink() {
        String md = converter.convert("<h1>Cím</h1><p>Egy <strong>félkövér</strong> és egy <a href=\"https://x.hu\">link</a>.</p>");
        assertThat(md).contains("# Cím");
        assertThat(md).contains("**félkövér**");
        assertThat(md).contains("[link](https://x.hu)");
    }

    @Test
    void blankInputReturnsEmpty() {
        assertThat(converter.convert(null)).isEmpty();
        assertThat(converter.convert("   ")).isEmpty();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=HtmlToMarkdownTest`
Expected: FAIL — `HtmlToMarkdown` does not exist (compile error).

- [ ] **Step 4: Implement `HtmlToMarkdown`**

```java
package hu.deposoft.webshop.application.blog;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.springframework.stereotype.Component;

/**
 * Converts WordPress post HTML to Markdown so imported posts share the single
 * flexmark Markdown render path (CLAUDE.md #9 / blog CMS). Best-effort: WP
 * shortcodes / Gutenberg block comments survive as text and are cleaned by hand.
 */
@Component
public class HtmlToMarkdown {

    private final FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();

    public String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return converter.convert(html);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=HtmlToMarkdownTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/hu/deposoft/webshop/application/blog/HtmlToMarkdown.java src/test/java/hu/deposoft/webshop/application/blog/HtmlToMarkdownTest.java
git commit -m "feat(blog-import): add flexmark html2md converter + HtmlToMarkdown component"
```

---

## Task 2: Generalize the image fetcher (shared ImageFetcher)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/ImageFetcher.java`
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/HttpImageFetcher.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopImporter.java`
- Delete: `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopImageFetcher.java`
- Delete: `src/main/java/hu/deposoft/webshop/application/workshop/HttpWorkshopImageFetcher.java`

**Interfaces:**
- Produces: `ImageFetcher` interface in `application.catalog` with nested `record FetchedImage(byte[] bytes, String contentType)` and `FetchedImage fetch(String url)`. `HttpImageFetcher` `@Component` implements it. Consumed by `WorkshopImporter` (now) and `BlogImporter` (Task 4).

> Rationale: the blog importer needs the exact HTTP-fetch-to-storage logic that already exists in `HttpWorkshopImageFetcher`. Generalize it (rename + move to the shared `application.catalog` package next to `StorageService`) rather than duplicate. The existing workshop import tests are the regression guard.

- [ ] **Step 1: Create `ImageFetcher` (generalized interface)**

```java
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
```

- [ ] **Step 2: Create `HttpImageFetcher` (move the proven impl verbatim)**

Copy the body of the existing `HttpWorkshopImageFetcher` verbatim, changing only the package, class name, and the return/record type to `ImageFetcher.FetchedImage`:
```java
package hu.deposoft.webshop.application.catalog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Fetches images over HTTP for import pipelines. */
@Slf4j
@Component
public class HttpImageFetcher implements ImageFetcher {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public FetchedImage fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " fetching " + url);
            }
            String contentType = response.headers().firstValue("content-type")
                    .map(HttpImageFetcher::stripParams)
                    .filter(ct -> ct.startsWith("image/"))
                    .orElseGet(() -> contentTypeFromUrl(url));
            return new FetchedImage(response.body(), contentType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch image " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted fetching image " + url, e);
        }
    }

    private static String stripParams(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase();
    }

    private static String contentTypeFromUrl(String url) {
        String u = url.toLowerCase();
        if (u.endsWith(".png")) return "image/png";
        if (u.endsWith(".webp")) return "image/webp";
        if (u.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
```
> Implementer note: open the existing `HttpWorkshopImageFetcher` and preserve any helper logic it has that differs from the above (e.g. exact `stripParams`/extension handling) — copy faithfully, don't regress behavior.

- [ ] **Step 3: Point `WorkshopImporter` at the shared fetcher**

In `WorkshopImporter.java`: change the import and field type from `WorkshopImageFetcher` to `hu.deposoft.webshop.application.catalog.ImageFetcher`, and the local type `WorkshopImageFetcher.FetchedImage` → `ImageFetcher.FetchedImage`. No logic change.

- [ ] **Step 4: Delete the workshop-specific fetcher files**

```bash
git rm src/main/java/hu/deposoft/webshop/application/workshop/WorkshopImageFetcher.java
git rm src/main/java/hu/deposoft/webshop/application/workshop/HttpWorkshopImageFetcher.java
```
Then search for any remaining references (e.g. test fakes) and update them to `ImageFetcher`:
```bash
grep -rn "WorkshopImageFetcher" src/
```
Update every hit to the shared `ImageFetcher` type (test fakes/mocks included).

- [ ] **Step 5: Verify the workshop import still works (regression guard)**

Run the workshop import test(s):
```bash
mvn test -Dtest='*Workshop*Import*'
```
Expected: PASS — same behavior, now via `ImageFetcher`. If no such test name matches, run the workshop importer's actual test class (find it: `grep -rln "WorkshopImporter" src/test`).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(import): extract shared ImageFetcher/HttpImageFetcher from workshop import"
```

---

## Task 3: Source DTOs (SourceBlog / SourceBlogPost / SourceBlogCategory)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlog.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlogPost.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlogCategory.java`

**Interfaces:**
- Produces (consumed by `BlogImporter` Task 4 and `BlogImportRunner` Task 5):
  - `SourceBlog(List<SourceBlogPost> posts, List<SourceBlogCategory> categories)`
  - `SourceBlogPost(long externalId, String slug, String title, String excerpt, String contentHtml, String status, OffsetDateTime publishedAt, String coverImageUrl, String seoTitle, String seoDescription, List<String> categorySlugs)`
  - `SourceBlogCategory(String name, String slug)`

> Plain Java records, Jackson-3 deserialized by component-name match (like `SourceCatalog`/`SourceWorkshop`). JSON keys in `blog.json` must match these names exactly (Task 6). `publishedAt` is an ISO-8601 string (or null) in JSON → `OffsetDateTime`.

- [ ] **Step 1: Create the three records**

`SourceBlogCategory.java`:
```java
package hu.deposoft.webshop.integrations.wordpress;

/** A WordPress blog category (taxonomy=category): display name + 1:1-preserved slug. */
public record SourceBlogCategory(String name, String slug) {
}
```

`SourceBlogPost.java`:
```java
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
        List<String> categorySlugs) {
}
```

`SourceBlog.java`:
```java
package hu.deposoft.webshop.integrations.wordpress;

import java.util.List;

/** One blog snapshot to import, source-agnostic (JSON export now). The export file is
 *  {@code { "posts": [ ... ], "categories": [ ... ] }}. */
public record SourceBlog(
        List<SourceBlogPost> posts,
        List<SourceBlogCategory> categories) {
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -q test-compile`
Expected: BUILD SUCCESS (no test yet; these are data carriers exercised in Task 4).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlog.java src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlogPost.java src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlogCategory.java
git commit -m "feat(blog-import): SourceBlog/SourceBlogPost/SourceBlogCategory DTOs"
```

---

## Task 4: BlogImporter + BlogImportReport (idempotent core)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/blog/BlogImportReport.java`
- Create: `src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java`
- Test: `src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java`

**Interfaces:**
- Consumes: `HtmlToMarkdown.convert` (Task 1); `ImageFetcher.fetch` + `ImageFetcher.FetchedImage` (Task 2); `StorageService.put(byte[],String):String`; `SourceBlog`/`SourceBlogPost`/`SourceBlogCategory` (Task 3); `BlogPostRepository` (`findBySlug`, `save`), `BlogCategoryRepository` (`findBySlug`, `save`), `BlogPost` (`create`, `setExcerpt`, `setBodyMarkdown`, `setSeoTitle`, `setSeoDescription`, `setCoverImageKey`, `getCategories`, `setCategories`, `publish(OffsetDateTime)`, `unpublish`), `BlogCategory.create(name,slug)` — all from the merged blog CMS.
- Produces: `BlogImporter` `@Service` with `@Transactional BlogImportReport run(SourceBlog source)`; `BlogImportReport` (mutable counters `postsCreated/postsUpdated/postsSkipped/categoriesCreated/imagesStored` + `errors()`).

- [ ] **Step 1: Write `BlogImportReport`**

```java
package hu.deposoft.webshop.application.blog;

import java.util.ArrayList;
import java.util.List;

/** Mutable run report of a blog import; counters plus per-item errors. Mirrors
 *  {@code WorkshopImportReport}. */
public class BlogImportReport {

    private int postsCreated;
    private int postsUpdated;
    private int postsSkipped;
    private int categoriesCreated;
    private int imagesStored;
    private final List<String> errors = new ArrayList<>();

    void postCreated() { postsCreated++; }
    void postUpdated() { postsUpdated++; }
    void postSkipped() { postsSkipped++; }
    void categoryCreated() { categoriesCreated++; }
    void imageStored() { imagesStored++; }
    void error(String message) { errors.add(message); }

    public int postsCreated() { return postsCreated; }
    public int postsUpdated() { return postsUpdated; }
    public int postsSkipped() { return postsSkipped; }
    public int categoriesCreated() { return categoriesCreated; }
    public int imagesStored() { return imagesStored; }
    public List<String> errors() { return List.copyOf(errors); }

    @Override
    public String toString() {
        return "BlogImportReport{posts=%d+%d (skipped %d), categories=%d, images=%d, errors=%d}"
                .formatted(postsCreated, postsUpdated, postsSkipped, categoriesCreated, imagesStored, errors.size());
    }
}
```

- [ ] **Step 2: Write the failing integration test**

```java
package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.domain.blog.PublicationStatus;
import hu.deposoft.webshop.integrations.wordpress.SourceBlog;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogCategory;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogPost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class BlogImporterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    /** Deterministic fetcher: any URL -> fixed 1-byte PNG, so no real HTTP in tests. */
    @TestConfiguration
    static class FakeFetcherConfig {
        @Bean @Primary
        ImageFetcher fakeImageFetcher() {
            return url -> new ImageFetcher.FetchedImage(new byte[]{1, 2, 3}, "image/png");
        }
    }

    @Autowired BlogImporter importer;
    @Autowired BlogPostRepository posts;

    private static final OffsetDateTime PUB = OffsetDateTime.of(2024, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC);

    private SourceBlogPost post(String slug, String title, String html, String status) {
        return new SourceBlogPost(1L, slug, title, "kivonat", html, status, PUB,
                null, null, null, List.of("hirek"));
    }

    private SourceBlog blog(SourceBlogPost... p) {
        return new SourceBlog(List.of(p), List.of(new SourceBlogCategory("Hírek", "hirek")));
    }

    @Test
    void importsPublishedPostConvertingHtmlToMarkdown() {
        importer.run(blog(post("elso", "Első", "<h1>Cím</h1><p><strong>x</strong></p>", "publish")));
        BlogPost p = posts.findBySlug("elso").orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(p.getPublishedAt()).isEqualTo(PUB);
        assertThat(p.getBodyMarkdown()).contains("# Cím").contains("**x**");
        assertThat(p.getCategories()).extracting(c -> c.getSlug()).containsExactly("hirek");
    }

    @Test
    void draftPostImportedAsDraft() {
        importer.run(blog(post("piszk", "Piszkozat", "<p>szöveg</p>", "draft")));
        assertThat(posts.findBySlug("piszk").orElseThrow().getStatus()).isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void secondRunIsIdempotent_noDuplicatesUpdatesInPlace() {
        importer.run(blog(post("egy", "Régi cím", "<p>a</p>", "publish")));
        BlogImportReport r2 = importer.run(blog(post("egy", "Új cím", "<p>b</p>", "publish")));
        assertThat(posts.findAll()).filteredOn(p -> p.getSlug().equals("egy")).hasSize(1);
        assertThat(posts.findBySlug("egy").orElseThrow().getTitle()).isEqualTo("Új cím");
        assertThat(r2.postsUpdated()).isEqualTo(1);
        assertThat(r2.postsCreated()).isZero();
    }

    @Test
    void uploadsImagesAreDownloadedAndRewritten_externalLeftAlone() {
        String html = "<p><img src=\"https://nemiskacat.hu/wp-content/uploads/2024/01/a.jpg\"></p>"
                + "<p><img src=\"https://other.example/x.png\"></p>";
        importer.run(blog(post("kepes", "Képes", html, "publish")));
        String md = posts.findBySlug("kepes").orElseThrow().getBodyMarkdown();
        assertThat(md).contains("/media/");
        assertThat(md).doesNotContain("wp-content/uploads");
        assertThat(md).contains("https://other.example/x.png");
    }

    @Test
    void coverImageDownloadedToStorage() {
        var p = new SourceBlogPost(1L, "borito", "Borító", "", "<p>x</p>", "publish", PUB,
                "https://nemiskacat.hu/wp-content/uploads/2024/01/cover.jpg", null, null, List.of());
        importer.run(new SourceBlog(List.of(p), List.of()));
        assertThat(posts.findBySlug("borito").orElseThrow().getCoverImageKey()).isNotBlank();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=BlogImporterIT`
Expected: FAIL — `BlogImporter` does not exist.

- [ ] **Step 4: Implement `BlogImporter`**

```java
package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.integrations.wordpress.SourceBlog;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogCategory;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Idempotent WordPress blog import: upserts categories then posts by slug
 * (CLAUDE.md #4, #7), converts post HTML to Markdown, and downloads cover + inline
 * {@code wp-content/uploads} images into content-addressed storage (CLAUDE.md #8),
 * rewriting their Markdown URLs to {@code /media/<key>}. Mirrors {@code WorkshopImporter}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlogImporter {

    /** Markdown image whose URL is a WP upload — these get pulled into our storage. */
    private static final Pattern UPLOADS_IMG =
            Pattern.compile("!\\[([^\\]]*)\\]\\((https?://[^)\\s]*/wp-content/uploads/[^)\\s]+)\\)");

    private final BlogPostRepository posts;
    private final BlogCategoryRepository categories;
    private final HtmlToMarkdown htmlToMarkdown;
    private final ImageFetcher imageFetcher;
    private final StorageService storage;

    @Transactional
    public BlogImportReport run(SourceBlog source) {
        BlogImportReport report = new BlogImportReport();
        Map<String, BlogCategory> categoryBySlug = upsertCategories(source, report);
        for (SourceBlogPost post : nullToEmpty(source.posts())) {
            try {
                upsertPost(post, categoryBySlug, report);
            } catch (RuntimeException e) {
                report.error("post slug=%s: %s".formatted(post.slug(), e.getMessage()));
                log.warn("Blog import failed for slug={}", post.slug(), e);
            }
        }
        log.info("Blog import finished: {}", report);
        return report;
    }

    private Map<String, BlogCategory> upsertCategories(SourceBlog source, BlogImportReport report) {
        Map<String, BlogCategory> bySlug = new LinkedHashMap<>();
        for (SourceBlogCategory c : nullToEmpty(source.categories())) {
            BlogCategory entity = categories.findBySlug(c.slug()).orElse(null);
            if (entity == null) {
                entity = categories.save(BlogCategory.create(c.name(), c.slug()));
                report.categoryCreated();
            }
            bySlug.put(c.slug(), entity);
        }
        return bySlug;
    }

    private void upsertPost(SourceBlogPost source, Map<String, BlogCategory> categoryBySlug,
                            BlogImportReport report) {
        if (source.slug() == null || source.slug().isBlank()) {
            report.postSkipped();
            report.error("post externalId=%d has no slug — skipped".formatted(source.externalId()));
            return;
        }
        String markdown = rewriteUploadsImages(htmlToMarkdown.convert(source.contentHtml()), report);

        BlogPost post = posts.findBySlug(source.slug()).orElse(null);
        boolean created = post == null;
        if (created) {
            post = BlogPost.create(source.slug(), source.title());
        } else {
            post.setTitle(source.title());
        }
        post.setExcerpt(source.excerpt());
        post.setBodyMarkdown(markdown);
        post.setSeoTitle(source.seoTitle());
        post.setSeoDescription(source.seoDescription());

        // categories: replace via in-place mutation on update (Hibernate dirty-tracking),
        // setter on create (entity not yet managed) — mirrors BlogAdminService.
        Set<BlogCategory> resolved = new LinkedHashSet<>();
        if (source.categorySlugs() != null) {
            for (String slug : source.categorySlugs()) {
                BlogCategory c = categoryBySlug.get(slug);
                if (c == null) {
                    c = categories.findBySlug(slug).orElse(null);
                }
                if (c != null) {
                    resolved.add(c);
                }
            }
        }
        if (created) {
            post.setCategories(resolved);
        } else {
            post.getCategories().clear();
            post.getCategories().addAll(resolved);
        }

        if (source.coverImageUrl() != null && !source.coverImageUrl().isBlank()) {
            try {
                ImageFetcher.FetchedImage img = imageFetcher.fetch(source.coverImageUrl());
                post.setCoverImageKey(storage.put(img.bytes(), img.contentType()));
                report.imageStored();
            } catch (RuntimeException e) {
                report.error("cover %s: %s".formatted(source.coverImageUrl(), e.getMessage()));
                log.warn("Cover image fetch failed for slug={} url={}", source.slug(), source.coverImageUrl(), e);
            }
        }

        if ("publish".equalsIgnoreCase(source.status())) {
            post.publish(source.publishedAt());   // sets publishedAt only if null (re-import keeps original)
        } else {
            post.unpublish();
        }

        if (created) {
            posts.save(post);
            report.postCreated();
        } else {
            report.postUpdated();
        }
    }

    private String rewriteUploadsImages(String markdown, BlogImportReport report) {
        Matcher m = UPLOADS_IMG.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String alt = m.group(1);
            String url = m.group(2);
            String replacement;
            try {
                ImageFetcher.FetchedImage img = imageFetcher.fetch(url);
                String key = storage.put(img.bytes(), img.contentType());
                report.imageStored();
                replacement = "![" + alt + "](/media/" + key + ")";
            } catch (RuntimeException e) {
                report.error("inline image %s: %s".formatted(url, e.getMessage()));
                log.warn("Inline image fetch failed url={}", url, e);
                replacement = m.group(0); // keep original on failure
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static <T> java.util.List<T> nullToEmpty(java.util.List<T> list) {
        return list == null ? java.util.List.of() : list;
    }
}
```
> Implementer note: `post.publish(null)` (publish status but null `publishedAt`) leaves `publishedAt` null — acceptable; surface it only if a real export produces it. If `BlogPost.publish` rejects null, guard with `source.publishedAt()` non-null before calling, else set status without timestamp.

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=BlogImporterIT`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java src/main/java/hu/deposoft/webshop/application/blog/BlogImportReport.java src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java
git commit -m "feat(blog-import): idempotent BlogImporter (html->md, categories, images, status)"
```

---

## Task 5: BlogImportRunner (@Profile import-blog)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/config/BlogImportRunner.java`

**Interfaces:**
- Consumes: `BlogImporter.run(SourceBlog)`, `SourceBlog`, Jackson `ObjectMapper`.
- Produces: a `CommandLineRunner` active under profile `import-blog`, reading property `webshop.import.blog-file`.

- [ ] **Step 1: Implement `BlogImportRunner` (mirror `WorkshopImportRunner`)**

```java
package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.blog.BlogImportReport;
import hu.deposoft.webshop.application.blog.BlogImporter;
import hu.deposoft.webshop.integrations.wordpress.SourceBlog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * Manual blog import trigger: run the app with the {@code import-blog} profile and
 * {@code --webshop.import.blog-file=<blog.json>} to load the blog snapshot, then exit.
 * Mirrors {@code WorkshopImportRunner}. Live DB operations are human-only.
 */
@Configuration
@Profile("import-blog")
public class BlogImportRunner {

    private static final Logger log = LoggerFactory.getLogger(BlogImportRunner.class);

    @Bean
    CommandLineRunner importBlog(BlogImporter importer, ObjectMapper objectMapper,
                                 Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.blog-file");
            log.info("Importing blog from {}", file);
            SourceBlog source = objectMapper.readValue(Path.of(file).toFile(), SourceBlog.class);
            BlogImportReport report = importer.run(source);
            log.info("Blog import report: {}", report);
            report.errors().forEach(e -> log.warn("Blog import error: {}", e));
            System.exit(SpringApplication.exit(ctx, () -> report.errors().isEmpty() ? 0 : 1));
        };
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -q test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/config/BlogImportRunner.java
git commit -m "feat(blog-import): import-blog profile runner (--webshop.import.blog-file)"
```

---

## Task 6: Python exporter (export-blog.py) + test + README

**Files:**
- Create: `scripts/woo-export/export-blog.py`
- Create: `scripts/woo-export/test_export_blog.py`
- Modify: `scripts/woo-export/README.md`

**Interfaces:**
- Produces a `blog.json` of shape `{ "posts": [SourceBlogPost...], "categories": [SourceBlogCategory...] }` matching the Task-3 records exactly (field names: `externalId, slug, title, excerpt, contentHtml, status, publishedAt, coverImageUrl, seoTitle, seoDescription, categorySlugs` / `name, slug`).

- [ ] **Step 1: Implement `export-blog.py` (mirror `export.py`)**

Reuse `export.py`'s `mysql_json_rows(query)` helper, the `docker exec -i wp_db mysql -uroot -proot_password … client7002dbnem -e <query>` invocation, and the `t = lambda name: PREFIX + name` prefix helper with `CONTAINER="wp_db"`, `DB="client7002dbnem"`, `PREFIX="guxdop_"`.

```python
#!/usr/bin/env python3
"""Export the WordPress blog (posts + categories) from the local wp_db container to JSON.

Produces a SourceBlog snapshot (see integrations/wordpress DTOs) consumed by the
BlogImporter via the `import-blog` profile. Reads ONLY blog tables. publish + draft.

Usage:
    python3 scripts/woo-export/export-blog.py > /tmp/blog.json
Then import (from the app):
    mvn spring-boot:run -Dspring-boot.run.profiles=local,import-blog \
      -Dspring-boot.run.arguments=--webshop.import.blog-file=/tmp/blog.json
"""
import json
import subprocess
import sys

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"


def mysql_json_rows(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]


def mysql_scalar(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return result.stdout.strip()


def nonempty(v):
    return v if v not in (None, "") else None


def to_iso_utc(date_gmt):
    """WP post_date_gmt 'YYYY-MM-DD HH:MM:SS' (UTC) -> ISO-8601 'Z'; null for the zero date."""
    if not date_gmt or date_gmt.startswith("0000-00-00"):
        return None
    return date_gmt.replace(" ", "T") + "Z"


def main():
    t = lambda name: PREFIX + name
    site_url = mysql_scalar(f"SELECT option_value FROM {t('options')} WHERE option_name='siteurl' LIMIT 1")
    uploads_base = site_url.rstrip("/") + "/wp-content/uploads/"

    posts = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'id', p.ID, 'slug', p.post_name, 'title', p.post_title,
          'excerpt', p.post_excerpt, 'content', p.post_content,
          'status', p.post_status, 'date_gmt', p.post_date_gmt,
          'thumb_id', (SELECT pm.meta_value FROM {t('postmeta')} pm
                       WHERE pm.post_id = p.ID AND pm.meta_key = '_thumbnail_id' LIMIT 1),
          'seo_title', (SELECT pm.meta_value FROM {t('postmeta')} pm
                        WHERE pm.post_id = p.ID AND pm.meta_key = '_yoast_wpseo_title' LIMIT 1),
          'seo_desc', (SELECT pm.meta_value FROM {t('postmeta')} pm
                       WHERE pm.post_id = p.ID AND pm.meta_key = '_yoast_wpseo_metadesc' LIMIT 1))
        FROM {t('posts')} p
        WHERE p.post_type = 'post' AND p.post_status IN ('publish','draft')""")

    # featured-image relative file path per attachment id
    files = mysql_json_rows(f"""
        SELECT JSON_OBJECT('id', a.ID, 'file',
          (SELECT pm.meta_value FROM {t('postmeta')} pm
           WHERE pm.post_id = a.ID AND pm.meta_key = '_wp_attached_file' LIMIT 1))
        FROM {t('posts')} a WHERE a.post_type = 'attachment'""")
    file_by_id = {f["id"]: f.get("file") for f in files}

    categories = mysql_json_rows(f"""
        SELECT JSON_OBJECT('name', t.name, 'slug', t.slug)
        FROM {t('terms')} t
        JOIN {t('term_taxonomy')} tt ON tt.term_id = t.term_id
        WHERE tt.taxonomy = 'category'""")

    rels = mysql_json_rows(f"""
        SELECT JSON_OBJECT('post_id', tr.object_id, 'slug', t.slug)
        FROM {t('term_relationships')} tr
        JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
        JOIN {t('terms')} t ON t.term_id = tt.term_id
        WHERE tt.taxonomy = 'category'""")
    cats_by_post = {}
    for r in rels:
        cats_by_post.setdefault(r["post_id"], []).append(r["slug"])

    out_posts = []
    for p in posts:
        thumb_id = p.get("thumb_id")
        cover = None
        if thumb_id and int(thumb_id) in file_by_id and file_by_id[int(thumb_id)]:
            cover = uploads_base + file_by_id[int(thumb_id)]
        out_posts.append({
            "externalId": p["id"],
            "slug": p["slug"],
            "title": p["title"],
            "excerpt": nonempty(p.get("excerpt")),
            "contentHtml": p.get("content") or "",
            "status": p["status"],
            "publishedAt": to_iso_utc(p.get("date_gmt")),
            "coverImageUrl": cover,
            "seoTitle": nonempty(p.get("seo_title")),
            "seoDescription": nonempty(p.get("seo_desc")),
            "categorySlugs": cats_by_post.get(p["id"], []),
        })

    json.dump({"posts": out_posts, "categories": categories}, sys.stdout, ensure_ascii=False, indent=1)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
```
> Implementer note: confirm `_thumbnail_id` meta_value is the integer attachment id (string) — the `int(thumb_id)` cast handles that. If `JSON_OBJECT` returns the id as a number for `thumb_id`, adjust the lookup accordingly (`file_by_id` keys are ints from `a.ID`).

- [ ] **Step 2: Write a fixture-based test (mirror `test_export_orders.py`)**

Open `scripts/woo-export/test_export_orders.py` for the exact mocking style, then write `test_export_blog.py` that monkeypatches `export_blog.mysql_json_rows`/`mysql_scalar` with canned rows (one publish post with a thumbnail + one category + one relationship) and asserts the assembled JSON: post has `externalId/slug/contentHtml/status='publish'`, `publishedAt` is ISO `…Z`, `coverImageUrl` ends with the uploads path, `categorySlugs == ['hirek']`, and the top level has `posts` + `categories` keys.

```python
import export_blog


def test_assembles_post_with_cover_and_categories(monkeypatch, capsys):
    monkeypatch.setattr(export_blog, "mysql_scalar", lambda q: "https://nemiskacat.hu")

    def fake_rows(q):
        if "post_type = 'post'" in q:
            return [{"id": 7, "slug": "elso", "title": "Első", "excerpt": "",
                     "content": "<p>x</p>", "status": "publish",
                     "date_gmt": "2024-03-01 09:00:00", "thumb_id": "11",
                     "seo_title": None, "seo_desc": None}]
        if "post_type = 'attachment'" in q:
            return [{"id": 11, "file": "2024/01/cover.jpg"}]
        if "taxonomy = 'category'" in q and "term_relationships" not in q:
            return [{"name": "Hírek", "slug": "hirek"}]
        return [{"post_id": 7, "slug": "hirek"}]  # relationships

    monkeypatch.setattr(export_blog, "mysql_json_rows", fake_rows)
    export_blog.main()
    out = capsys.readouterr().out
    import json
    data = json.loads(out)
    assert set(data.keys()) == {"posts", "categories"}
    post = data["posts"][0]
    assert post["externalId"] == 7 and post["slug"] == "elso" and post["status"] == "publish"
    assert post["publishedAt"] == "2024-03-01T09:00:00Z"
    assert post["coverImageUrl"] == "https://nemiskacat.hu/wp-content/uploads/2024/01/cover.jpg"
    assert post["categorySlugs"] == ["hirek"]
    assert data["categories"][0]["slug"] == "hirek"
```

- [ ] **Step 3: Run the Python test**

Run: `cd scripts/woo-export && python3 -m pytest test_export_blog.py -q` (or mirror however `test_export_orders.py` is run per the README).
Expected: PASS.

- [ ] **Step 4: Update `scripts/woo-export/README.md`**

Add a "Blog" section documenting: `python3 scripts/woo-export/export-blog.py > /tmp/blog.json` then the `import-blog` profile command (as in the script docstring). Mirror the existing sections' wording.

- [ ] **Step 5: Commit**

```bash
git add scripts/woo-export/export-blog.py scripts/woo-export/test_export_blog.py scripts/woo-export/README.md
git commit -m "feat(blog-import): WordPress blog exporter (export-blog.py) + fixture test"
```

---

## Final verification

- [ ] **Run the full backend suite** — `mvn test` → BUILD SUCCESS (blog import + the refactored workshop import + everything else green).
- [ ] **Run the Python test** — `cd scripts/woo-export && python3 -m pytest test_export_blog.py -q`.
- [ ] **Manual smoke (optional, human-run against local WP):** `python3 scripts/woo-export/export-blog.py > /tmp/blog.json`; start the app with `local,import-blog` + `--webshop.import.blog-file=/tmp/blog.json`; confirm posts appear at `/blog` and `/blog/{slug}` with Markdown bodies, `/media` images, categories; re-run and confirm no duplicates.

---

## Self-Review Notes

- **Spec coverage:** exporter §3 → Task 6; Source DTOs §4 → Task 3; importer §5 (idempotent slug upsert, HTML→MD, image download+rewrite, categories, status/publishedAt, recommendations empty) → Task 4 (+ Task 1 converter, Task 2 fetcher); runner §6 → Task 5; error handling §7 → Task 4 (try/catch per post + per image, skip blank slug); testing §8 → Task 4 IT + Task 6 Python test. flexmark html2md dependency gap → Task 1.
- **Deviations from spec (deliberate):** (1) No separate `JsonFileBlogSource` — the runner deserializes `SourceBlog` directly via `ObjectMapper`, matching the closer analog `WorkshopImportRunner` (YAGNI). (2) Image fetcher generalized into shared `application.catalog.ImageFetcher`/`HttpImageFetcher` (DRY) rather than a blog-specific copy — Task 2 migrates the workshop importer to it, guarded by the workshop import tests.
- **Type consistency:** `SourceBlog(posts, categories)` / `SourceBlogPost(...)` field names identical across Tasks 3, 4, 6, and the exporter JSON keys. `ImageFetcher.FetchedImage(bytes, contentType)` consistent across Tasks 2 and 4. `BlogImportReport` counter names consistent between Task 4 impl and test.
- **Profile name:** `import-blog` (Task 5 runner + Task 6 docstring) — distinct from `import` (catalog) and `import-workshops`.
