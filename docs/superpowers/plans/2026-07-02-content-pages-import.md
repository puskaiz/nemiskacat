# WordPress Content Pages → DB CMS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate 10 Elementor-built WordPress content pages into a DB-backed CMS rendered by the Spring Boot app at their original root `/{slug}` URLs, with an idempotent re-runnable importer and a TipTap admin editor.

**Architecture:** Mirror the existing blog/workshop WordPress-migration pattern: a Python exporter walks each page's Elementor widget tree into assembled HTML → JSON snapshot; a Java `PageImporter` idempotently upserts into a new `content_page` table (server-side jsoup-sanitized, inline images re-hosted). Public rendering is unified with the blog at root `/{slug}` via a new `RootSlugController` (page → blog post → 404). Admin CRUD reuses the blog editor's `HtmlEditor` (TipTap).

**Tech Stack:** Java 21, Spring Boot 3.x, JPA/Hibernate, Flyway (PostgreSQL), Thymeleaf, jsoup; Python 3 (exporter); React + Refine + Ant Design + TipTap (admin-ui). Tests: JUnit + Testcontainers (Postgres), MockMvc, pytest.

## Global Constraints

- **Language:** UI text and content in Hungarian; code, commits, comments in English (CLAUDE.md #10).
- **Business logic in the service layer only;** Thymeleaf and REST controllers are thin (CLAUDE.md #1).
- **Public page HTML is session-independent and cacheable** — no user/cart data (CLAUDE.md #2).
- **Slugs are immutable** — WP `post_name` copied 1:1, never changed (CLAUDE.md #7).
- **All stored HTML passes server-side sanitization** (jsoup safelist) on every write path — import and admin save (CLAUDE.md #9). Reuse the existing `BlogHtmlSanitizer` bean.
- **Time stored in UTC** (CLAUDE.md #6). **Images stored content-hashed**; app does no image processing (CLAUDE.md #8).
- **Idempotent import:** re-running upserts without duplicates, key = WP `external_id` (CLAUDE.md #4).
- **WordPress is read-only;** live DB operations are human-only. The exporter reads only page tables.
- **Next Flyway version is `V35`** (latest committed is `V34`).
- **Reuse, do not re-create:** `BlogHtmlSanitizer`, `ImageFetcher` (`FetchedImage(byte[] bytes, String contentType)`), `StorageService.put(byte[], String) → String key`, `PublicationStatus` (DRAFT/PUBLISHED), `AuditService.record(action, entityType, entityId, detail)`, admin-ui `components/HtmlEditor`, `ProductController.MICRO_CACHE`.
- **Jackson is Jackson 3** — import `tools.jackson.databind.ObjectMapper` (its `readValue`/`writeValueAsString` throw unchecked; do not add `throws`).

---

## File Structure

**Backend (create):**
- `src/main/resources/db/migration/V35__content_page.sql` — table
- `src/main/java/hu/deposoft/webshop/domain/page/ContentPage.java` — entity
- `src/main/java/hu/deposoft/webshop/domain/page/ContentPageRepository.java` — repo
- `src/main/java/hu/deposoft/webshop/application/page/PageQueryService.java` — public read + JSON-LD
- `src/main/java/hu/deposoft/webshop/application/page/PageAdminService.java` — admin CRUD
- `src/main/java/hu/deposoft/webshop/application/page/PageImporter.java` — importer
- `src/main/java/hu/deposoft/webshop/application/page/PageImportReport.java` — report
- `src/main/java/hu/deposoft/webshop/application/content/InlineImageRewriter.java` — shared image re-hosting (also adopted by BlogImporter)
- `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourcePage.java`, `SourcePages.java` — DTOs
- `src/main/java/hu/deposoft/webshop/config/PageImportRunner.java` — import trigger
- `src/main/resources/application-import-pages.yml` — import profile
- `src/main/java/hu/deposoft/webshop/api/admin/PageAdminController.java` — REST
- `src/main/java/hu/deposoft/webshop/web/RootSlugController.java` — unified `/{slug}`
- `src/main/resources/templates/page.html` — public render

**Backend (modify):**
- `src/main/java/hu/deposoft/webshop/web/BlogController.java` — remove `/{slug}` handler
- `src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java` — use `InlineImageRewriter`
- `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java` — map page exceptions

**Exporter (create/modify):**
- `scripts/woo-export/export-pages.py`, `scripts/woo-export/test_export_pages.py` (create); `scripts/woo-export/README.md` (modify)

**Admin-ui (create/modify):**
- `admin-ui/src/pages/pages/list.tsx`, `admin-ui/src/pages/pages/edit.tsx` (create)
- `admin-ui/src/i18n/hu/pages.json`, `admin-ui/src/i18n/en/pages.json` (create)
- `admin-ui/src/i18n/index.ts` (modify — register namespace)
- `admin-ui/src/App.tsx` (modify — imports, resource, routes)

**Tests (create):**
- `src/test/java/hu/deposoft/webshop/application/page/PageImporterIT.java`
- `src/test/java/hu/deposoft/webshop/web/RootSlugControllerIT.java`
- `src/test/java/hu/deposoft/webshop/api/admin/PageAdminControllerIT.java`

**ADR:** `docs/adr/0011-content-pages-in-db.md`

---

### Task 1: `content_page` schema + entity + repository

**Files:**
- Create: `src/main/resources/db/migration/V35__content_page.sql`
- Create: `src/main/java/hu/deposoft/webshop/domain/page/ContentPage.java`
- Create: `src/main/java/hu/deposoft/webshop/domain/page/ContentPageRepository.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/page/ContentPageTest.java`

**Interfaces:**
- Produces: `ContentPage` entity (`create(Long externalId, String slug, String title)`, `publish(OffsetDateTime)`, `unpublish()`, `isValidSlug(String)`, getters, `setTitle/setBodyHtml/setSeoTitle/setSeoDescription`); `ContentPageRepository` (`findBySlug`, `findByExternalId`, `existsBySlug`, plus `JpaRepository`).
- Reuses: `hu.deposoft.webshop.domain.blog.PublicationStatus`.

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.domain.page;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;

class ContentPageTest {

    @Test
    void createRejectsInvalidSlug() {
        assertThatThrownBy(() -> ContentPage.create(1L, "Not A Slug", "T"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createStartsAsDraft() {
        ContentPage p = ContentPage.create(1L, "rolunk", "Rólunk");
        assertThat(p.getStatus()).isEqualTo(hu.deposoft.webshop.domain.blog.PublicationStatus.DRAFT);
        assertThat(p.getSlug()).isEqualTo("rolunk");
        assertThat(p.getExternalId()).isEqualTo(1L);
    }

    @Test
    void publishSetsTimestampOnceThenKeepsIt() {
        ContentPage p = ContentPage.create(1L, "rolunk", "Rólunk");
        OffsetDateTime first = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        p.publish(first);
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(p.getStatus()).isEqualTo(hu.deposoft.webshop.domain.blog.PublicationStatus.PUBLISHED);
        assertThat(p.getPublishedAt()).isEqualTo(first);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dskip.frontend=true -Dtest=ContentPageTest test`
Expected: FAIL — `ContentPage` does not exist (compilation error).

- [ ] **Step 3: Write the migration**

Create `V35__content_page.sql` (match `V22__blog.sql` conventions — `BIGINT GENERATED BY DEFAULT AS IDENTITY`, `TEXT`, `TIMESTAMPTZ`):

```sql
-- Content pages (WordPress page migration): DB-backed static pages rendered at root /{slug}.
CREATE TABLE content_page (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    external_id     BIGINT UNIQUE,
    slug            TEXT NOT NULL UNIQUE,
    title           TEXT NOT NULL,
    body_html       TEXT NOT NULL DEFAULT '',
    status          TEXT NOT NULL DEFAULT 'DRAFT',
    seo_title       TEXT,
    seo_description TEXT,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_content_page_status ON content_page (status);
```

- [ ] **Step 4: Write the entity**

Create `ContentPage.java`:

```java
package hu.deposoft.webshop.domain.page;

import hu.deposoft.webshop.domain.blog.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/** DB-backed static content page; HTML body (no markdown). Mirrors BlogPost's shape. */
@Entity
@Table(name = "content_page")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentPage {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true)
    private Long externalId;

    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    @Column(name = "body_html", nullable = false)
    private String bodyHtml = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus status = PublicationStatus.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Setter
    @Column(name = "seo_title")
    private String seoTitle;

    @Setter
    @Column(name = "seo_description")
    private String seoDescription;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    public static ContentPage create(Long externalId, String slug, String title) {
        if (!isValidSlug(slug)) {
            throw new IllegalArgumentException("Invalid content page slug: " + slug);
        }
        ContentPage p = new ContentPage();
        p.externalId = externalId;
        p.slug = slug;
        p.title = title;
        return p;
    }

    public void publish(OffsetDateTime now) {
        this.status = PublicationStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    public void unpublish() {
        this.status = PublicationStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 5: Write the repository**

Create `ContentPageRepository.java`:

```java
package hu.deposoft.webshop.domain.page;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContentPageRepository extends JpaRepository<ContentPage, Long> {
    Optional<ContentPage> findBySlug(String slug);
    Optional<ContentPage> findByExternalId(Long externalId);
    boolean existsBySlug(String slug);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -Dskip.frontend=true -Dtest=ContentPageTest test`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V35__content_page.sql \
        src/main/java/hu/deposoft/webshop/domain/page/ \
        src/test/java/hu/deposoft/webshop/domain/page/ContentPageTest.java
git commit -m "feat(page): content_page schema, entity and repository"
```

---

### Task 2: `PageQueryService` (public read + JSON-LD)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/page/PageQueryService.java`
- Test: covered by `RootSlugControllerIT` (Task 3); no separate unit test needed — the query is a thin mapper and the render IT exercises it end-to-end.

**Interfaces:**
- Consumes: `ContentPageRepository` (Task 1), `tools.jackson.databind.ObjectMapper`.
- Produces: `PageQueryService.PageView(String slug, String title, String bodyHtml, String seoTitle, String seoDescription, String jsonLd)`; `Optional<PageView> getPublishedBySlug(String slug)`.

- [ ] **Step 1: Write the service**

Create `PageQueryService.java`:

```java
package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.domain.blog.PublicationStatus;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Optional;

/** Session-independent (cacheable) content-page reads for the public site (CLAUDE.md #2). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageQueryService {

    private final ContentPageRepository pages;
    private final ObjectMapper objectMapper;

    public record PageView(String slug, String title, String bodyHtml,
                           String seoTitle, String seoDescription, String jsonLd) {}

    public Optional<PageView> getPublishedBySlug(String slug) {
        return pages.findBySlug(slug)
                .filter(p -> p.getStatus() == PublicationStatus.PUBLISHED)
                .map(this::toView);
    }

    private PageView toView(ContentPage p) {
        String description = p.getSeoDescription();
        LinkedHashMap<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "WebPage");
        ld.put("name", p.getTitle());
        if (description != null) {
            ld.put("description", description);
        }
        String jsonLd = objectMapper.writeValueAsString(ld);
        return new PageView(p.getSlug(), p.getTitle(), p.getBodyHtml(),
                p.getSeoTitle() != null ? p.getSeoTitle() : p.getTitle(),
                description, jsonLd);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -Dskip.frontend=true -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/page/PageQueryService.java
git commit -m "feat(page): public PageQueryService with WebPage JSON-LD"
```

---

### Task 3: Unified `RootSlugController` + `page.html` template

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/web/RootSlugController.java`
- Modify: `src/main/java/hu/deposoft/webshop/web/BlogController.java` (remove the `/{slug}` handler)
- Create: `src/main/resources/templates/page.html`
- Test: `src/test/java/hu/deposoft/webshop/web/RootSlugControllerIT.java`

**Interfaces:**
- Consumes: `PageQueryService.getPublishedBySlug` (Task 2), `BlogQueryService.getPublishedBySlug` (existing → `Optional<BlogPostView>`), `SidebarQueryService.sidebar()` (existing), `ProductController.MICRO_CACHE` (existing).
- Behavior: `GET /{slug}` resolves **page first, then blog post**, else 404. Page hit renders `page` view with model attr `page`; post hit renders `blog/post` with model attr `post`.

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class RootSlugControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @MockitoBean
    InstagramFeedQuery instagramFeedQuery;

    @Autowired WebApplicationContext context;
    @Autowired ContentPageRepository pages;
    @Autowired BlogPostRepository posts;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).build();
    }

    private ContentPage publishedPage(String slug, String title, String body) {
        ContentPage p = ContentPage.create(100L, slug, title);
        p.setBodyHtml(body);
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        return pages.save(p);
    }

    @Test
    void publishedPageRendersAtRootSlug() throws Exception {
        publishedPage("rolunk", "Rólunk", "<p>Bemutatkozás</p>");
        mvc.perform(get("/rolunk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rólunk")))
                .andExpect(content().string(containsString("Bemutatkozás")));
    }

    @Test
    void draftPageReturns404() throws Exception {
        ContentPage p = ContentPage.create(101L, "titkos", "Titkos");
        p.setBodyHtml("<p>x</p>");
        pages.save(p); // stays DRAFT
        mvc.perform(get("/titkos")).andExpect(status().isNotFound());
    }

    @Test
    void unknownSlugReturns404() throws Exception {
        mvc.perform(get("/nincs-ilyen")).andExpect(status().isNotFound());
    }

    @Test
    void publishedBlogPostStillRendersAtRootSlug() throws Exception {
        BlogPost post = BlogPost.create("egy-cikk", "Egy cikk");
        post.setBodyHtml("<p>Cikktörzs</p>");
        post.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(post);
        mvc.perform(get("/egy-cikk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Egy cikk")));
    }

    @Test
    void pageWinsOverBlogPostOnSharedSlug() throws Exception {
        publishedPage("kozos", "OLDAL CÍM", "<p>oldaltörzs</p>");
        BlogPost post = BlogPost.create("kozos", "POSZT CÍM");
        post.setBodyHtml("<p>poszttörzs</p>");
        post.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(post);
        mvc.perform(get("/kozos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nk-page-article")))
                .andExpect(content().string(containsString("OLDAL CÍM")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dskip.frontend=true -Dtest=RootSlugControllerIT test`
Expected: FAIL — `RootSlugController`/`page.html` do not exist yet, and `/{slug}` is still on `BlogController` (page routes 404 or ambiguous mapping).

- [ ] **Step 3: Remove the `/{slug}` handler from `BlogController`**

In `src/main/java/hu/deposoft/webshop/web/BlogController.java`, delete the `post(...)` method mapped to `@GetMapping({"/{slug}", "/{slug}/"})` (lines 44-50) and the now-unused imports if the compiler flags them (`PathVariable` stays used by `category`). Keep `/blog` and `/blog/kategoria/{slug}`.

- [ ] **Step 4: Create `RootSlugController`**

```java
package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.blog.BlogQueryService;
import hu.deposoft.webshop.application.page.PageQueryService;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the shared root {@code /{slug}} namespace. Resolves a content page first,
 * then a blog post, else 404. Session-independent, cacheable HTML (CLAUDE.md #2).
 * Exact routes (/blog, /product/{slug}, /termekkategoria/{slug}, /workshopok) are
 * more specific and still win over this catch-all.
 */
@Controller
@RequiredArgsConstructor
public class RootSlugController {

    private final PageQueryService pages;
    private final BlogQueryService blog;
    private final SidebarQueryService sidebarQuery;

    /** Blog post view needs ${sidebar}; content pages ignore it. */
    @ModelAttribute("sidebar")
    public SidebarQueryService.SidebarView sidebar() {
        return sidebarQuery.sidebar();
    }

    @GetMapping({"/{slug}", "/{slug}/"})
    public String bySlug(@PathVariable String slug, Model model, HttpServletResponse response) {
        var page = pages.getPublishedBySlug(slug);
        if (page.isPresent()) {
            response.setHeader("Cache-Control", ProductController.MICRO_CACHE);
            model.addAttribute("page", page.get());
            return "page";
        }
        var post = blog.getPublishedBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", post);
        return "blog/post";
    }
}
```

- [ ] **Step 5: Create `page.html`**

Create `src/main/resources/templates/page.html` (mirrors `blog/post.html` fragments, no sidebar/categories/recommended):

```html
<!DOCTYPE html>
<html lang="hu" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head(${page.seoTitle})}"></head>
<body>
<header th:replace="~{fragments/layout :: header}"></header>

<main class="wrap nk-page">
    <nav class="crumbs" aria-label="Navigáció">
        <a href="/">Webshop</a>
        <span class="crumbs__sep" aria-hidden="true"></span>
        <b th:text="${page.title}">Oldal</b>
    </nav>

    <article class="nk-page-article">
        <h1 class="nk-page-article__title" th:text="${page.title}">Cím</h1>
        <div class="nk-page-article__body" th:utext="${page.bodyHtml}">törzs</div>
    </article>
</main>

<footer th:replace="~{fragments/layout :: footer}"></footer>
<th:block th:replace="~{fragments/layout :: island-script}"></th:block>

<!-- WebPage JSON-LD for schema.org — at end of body, outside head fragment scope. -->
<script type="application/ld+json" th:utext="${page.jsonLd}"></script>
</body>
</html>
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -Dskip.frontend=true -Dtest=RootSlugControllerIT,BlogControllerIT test`
Expected: PASS. `BlogControllerIT` (which exercises `/{slug}` post rendering and draft-404) must remain green now that `RootSlugController` owns the route.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/web/RootSlugController.java \
        src/main/java/hu/deposoft/webshop/web/BlogController.java \
        src/main/resources/templates/page.html \
        src/test/java/hu/deposoft/webshop/web/RootSlugControllerIT.java
git commit -m "feat(page): render content pages at root /{slug} via unified RootSlugController"
```

---

### Task 4: Extract shared `InlineImageRewriter` (adopt in BlogImporter)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/content/InlineImageRewriter.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java`
- Test: existing `BlogImporterIT` is the regression net (do not modify it).

**Interfaces:**
- Produces: `InlineImageRewriter` bean; `InlineImageRewriter.Result(String html, int stored, java.util.List<String> errors)`; `Result rewrite(String html)`.
- Consumes: `ImageFetcher`, `StorageService`.

- [ ] **Step 1: Create `InlineImageRewriter`** (lifts `BlogImporter.rewriteUploadImages` verbatim into a reusable bean)

```java
package hu.deposoft.webshop.application.content;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Downloads every {@code wp-content/uploads} inline &lt;img&gt; into content-addressed
 * storage and rewrites its {@code src} to {@code /media/<key>} (CLAUDE.md #8); external
 * images are left untouched. Shared by the blog and content-page importers.
 */
@Component
@RequiredArgsConstructor
public class InlineImageRewriter {

    private final ImageFetcher imageFetcher;
    private final StorageService storage;

    public record Result(String html, int stored, List<String> errors) {}

    public Result rewrite(String html) {
        List<String> errors = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return new Result(html == null ? "" : html, 0, errors);
        }
        int stored = 0;
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (org.jsoup.nodes.Element img : doc.select("img[src]")) {
            String url = img.attr("src");
            if (!url.contains("/wp-content/uploads/")) {
                continue;
            }
            try {
                ImageFetcher.FetchedImage fetched = imageFetcher.fetch(url);
                String key = storage.put(fetched.bytes(), fetched.contentType());
                img.attr("src", "/media/" + key);
                stored++;
            } catch (RuntimeException e) {
                errors.add("inline image %s: %s".formatted(url, e.getMessage()));
            }
        }
        return new Result(doc.body().html(), stored, errors);
    }
}
```

- [ ] **Step 2: Refactor `BlogImporter` to use it**

In `BlogImporter.java`: add field `private final InlineImageRewriter inlineImageRewriter;` (add import `hu.deposoft.webshop.application.content.InlineImageRewriter;`). Replace the call site (line 100) and delete the private `rewriteUploadImages(...)` method (lines 176-194):

```java
        // was: html = rewriteUploadImages(html, report);
        InlineImageRewriter.Result rewritten = inlineImageRewriter.rewrite(html);
        html = rewritten.html();
        for (int i = 0; i < rewritten.stored(); i++) {
            report.imageStored();
        }
        rewritten.errors().forEach(report::error);
```

- [ ] **Step 3: Run the regression net**

Run: `mvn -Dskip.frontend=true -Dtest=BlogImporterIT,BlogImporterImageFailureIT test`
Expected: PASS unchanged (inline-image download/rewrite and failure handling still behave identically).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/content/InlineImageRewriter.java \
        src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java
git commit -m "refactor(import): extract shared InlineImageRewriter from BlogImporter"
```

---

### Task 5: Source DTOs + `PageImporter` + report + runner

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourcePage.java`, `SourcePages.java`
- Create: `src/main/java/hu/deposoft/webshop/application/page/PageImportReport.java`
- Create: `src/main/java/hu/deposoft/webshop/application/page/PageImporter.java`
- Create: `src/main/java/hu/deposoft/webshop/config/PageImportRunner.java`
- Create: `src/main/resources/application-import-pages.yml`
- Test: `src/test/java/hu/deposoft/webshop/application/page/PageImporterIT.java`

**Interfaces:**
- Consumes: `ContentPageRepository` (Task 1), `BlogHtmlSanitizer` (existing), `InlineImageRewriter` (Task 4).
- Produces: `SourcePages(List<SourcePage> pages)`; `SourcePage(long externalId, String slug, String title, String bodyHtml, String status, OffsetDateTime publishedAt, String seoTitle, String seoDescription)`; `PageImporter.run(SourcePages) → PageImportReport`.

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import hu.deposoft.webshop.domain.blog.PublicationStatus;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import hu.deposoft.webshop.integrations.wordpress.SourcePage;
import hu.deposoft.webshop.integrations.wordpress.SourcePages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class PageImporterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired PageImporter importer;
    @Autowired ContentPageRepository pages;
    @Autowired PageQueryService query;

    @MockitoBean ImageFetcher imageFetcher;
    @MockitoBean StorageService storage;

    private SourcePage page(long id, String slug, String title, String body, String status) {
        return new SourcePage(id, slug, title, body, status,
                OffsetDateTime.of(2021, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                "SEO " + title, "Leírás " + title);
    }

    @Test
    void importsAndPublishesPage() {
        importer.run(new SourcePages(List.of(
                page(1, "rolunk", "Rólunk", "<p>Szia</p>", "publish"))));
        var view = query.getPublishedBySlug("rolunk");
        assertThat(view).isPresent();
        assertThat(view.get().title()).isEqualTo("Rólunk");
        assertThat(view.get().bodyHtml()).contains("Szia");
    }

    @Test
    void reimportIsIdempotentAndUpdatesTitleButNotSlug() {
        importer.run(new SourcePages(List.of(page(1, "rolunk", "Rólunk", "<p>a</p>", "publish"))));
        // same externalId, changed slug + title on re-import
        importer.run(new SourcePages(List.of(page(1, "rolunk-uj", "Rólunk 2", "<p>b</p>", "publish"))));
        assertThat(pages.findAll()).hasSize(1);
        var stored = pages.findByExternalId(1L).orElseThrow();
        assertThat(stored.getSlug()).isEqualTo("rolunk");   // slug immutable (CLAUDE.md #7)
        assertThat(stored.getTitle()).isEqualTo("Rólunk 2"); // other fields refresh
    }

    @Test
    void draftIsHiddenFromPublicQuery() {
        importer.run(new SourcePages(List.of(page(2, "titkos", "Titkos", "<p>x</p>", "draft"))));
        assertThat(query.getPublishedBySlug("titkos")).isEmpty();
        assertThat(pages.findBySlug("titkos").orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void sanitizesBodyHtml() {
        importer.run(new SourcePages(List.of(
                page(3, "tiszta", "Tiszta", "<p>ok</p><script>alert(1)</script>", "publish"))));
        assertThat(pages.findBySlug("tiszta").orElseThrow().getBodyHtml())
                .doesNotContain("<script>");
    }

    @Test
    void rewritesUploadImagesAndKeepsExternal() {
        when(imageFetcher.fetch(anyString()))
                .thenReturn(new ImageFetcher.FetchedImage("x".getBytes(), "image/jpeg"));
        when(storage.put(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn("KEY1");
        String body = "<p><img src=\"https://nemiskacat.hu/wp-content/uploads/a.jpg\">"
                + "<img src=\"https://other.example/b.jpg\"></p>";
        importer.run(new SourcePages(List.of(page(4, "kepek", "Képek", body, "publish"))));
        String stored = pages.findBySlug("kepek").orElseThrow().getBodyHtml();
        assertThat(stored).contains("/media/KEY1");
        assertThat(stored).contains("https://other.example/b.jpg");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dskip.frontend=true -Dtest=PageImporterIT test`
Expected: FAIL — `SourcePage`/`SourcePages`/`PageImporter` do not exist.

- [ ] **Step 3: Write the DTOs**

`SourcePages.java`:

```java
package hu.deposoft.webshop.integrations.wordpress;

import java.util.List;

/** One content-page snapshot to import (JSON export): {@code { "pages": [ ... ] }}. */
public record SourcePages(List<SourcePage> pages) {
}
```

`SourcePage.java`:

```java
package hu.deposoft.webshop.integrations.wordpress;

import java.time.OffsetDateTime;

public record SourcePage(long externalId, String slug, String title, String bodyHtml,
                         String status, OffsetDateTime publishedAt,
                         String seoTitle, String seoDescription) {
}
```

- [ ] **Step 4: Write the report**

`PageImportReport.java`:

```java
package hu.deposoft.webshop.application.page;

import java.util.ArrayList;
import java.util.List;

public class PageImportReport {
    private int created;
    private int updated;
    private int skipped;
    private int imagesStored;
    private final List<String> errors = new ArrayList<>();

    public void created() { created++; }
    public void updated() { updated++; }
    public void skipped() { skipped++; }
    public void imageStored() { imagesStored++; }
    public void error(String message) { errors.add(message); }
    public List<String> errors() { return errors; }

    @Override
    public String toString() {
        return "PageImportReport{created=%d, updated=%d, skipped=%d, imagesStored=%d, errors=%d}"
                .formatted(created, updated, skipped, imagesStored, errors.size());
    }
}
```

- [ ] **Step 5: Write the importer**

`PageImporter.java`:

```java
package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.application.blog.BlogHtmlSanitizer;
import hu.deposoft.webshop.application.content.InlineImageRewriter;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import hu.deposoft.webshop.integrations.wordpress.SourcePage;
import hu.deposoft.webshop.integrations.wordpress.SourcePages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Idempotent WordPress content-page import: upserts by external id (WP page id),
 * falling back to slug (CLAUDE.md #4); never mutates the slug (CLAUDE.md #7);
 * re-hosts inline upload images (CLAUDE.md #8) and sanitizes the HTML (CLAUDE.md #9).
 * Mirrors {@code BlogImporter}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PageImporter {

    private final ContentPageRepository pages;
    private final BlogHtmlSanitizer sanitizer;
    private final InlineImageRewriter inlineImageRewriter;

    @Transactional
    public PageImportReport run(SourcePages source) {
        PageImportReport report = new PageImportReport();
        List<SourcePage> list = source.pages() == null ? List.of() : source.pages();
        for (SourcePage p : list) {
            try {
                upsert(p, report);
            } catch (RuntimeException e) {
                report.error("page slug=%s: %s".formatted(p.slug(), e.getMessage()));
                log.warn("Page import failed for slug={}", p.slug(), e);
            }
        }
        log.info("Page import finished: {}", report);
        return report;
    }

    private void upsert(SourcePage src, PageImportReport report) {
        if (src.slug() == null || src.slug().isBlank()) {
            report.skipped();
            report.error("page externalId=%d has no slug — skipped".formatted(src.externalId()));
            return;
        }
        InlineImageRewriter.Result rewritten = inlineImageRewriter.rewrite(src.bodyHtml());
        for (int i = 0; i < rewritten.stored(); i++) {
            report.imageStored();
        }
        rewritten.errors().forEach(report::error);
        String bodyHtml = sanitizer.sanitize(rewritten.html());

        ContentPage page = pages.findByExternalId(src.externalId())
                .or(() -> pages.findBySlug(src.slug()))
                .orElse(null);
        boolean created = page == null;
        if (created) {
            page = ContentPage.create(src.externalId(), src.slug(), src.title());
        } else {
            page.setTitle(src.title());   // slug intentionally left unchanged (immutable)
        }
        page.setBodyHtml(bodyHtml);
        page.setSeoTitle(src.seoTitle());
        page.setSeoDescription(src.seoDescription());

        if ("publish".equalsIgnoreCase(src.status())) {
            page.publish(src.publishedAt());
        } else {
            page.unpublish();
        }

        if (created) {
            pages.save(page);
            report.created();
        } else {
            report.updated();
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -Dskip.frontend=true -Dtest=PageImporterIT test`
Expected: PASS (5 tests).

- [ ] **Step 7: Write the runner and profile config**

`PageImportRunner.java` (mirrors `BlogImportRunner`):

```java
package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.page.PageImportReport;
import hu.deposoft.webshop.application.page.PageImporter;
import hu.deposoft.webshop.integrations.wordpress.SourcePages;
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
 * Manual content-page import: run with the {@code import-pages} profile and
 * {@code --webshop.import.pages-file=<pages.json>} to load the snapshot, then exit.
 * Mirrors {@code BlogImportRunner}. Live DB operations are human-only.
 */
@Configuration
@Profile("import-pages")
public class PageImportRunner {

    private static final Logger log = LoggerFactory.getLogger(PageImportRunner.class);

    @Bean
    CommandLineRunner importPages(PageImporter importer, ObjectMapper objectMapper,
                                  Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.pages-file");
            log.info("Importing pages from {}", file);
            SourcePages source = objectMapper.readValue(Path.of(file).toFile(), SourcePages.class);
            PageImportReport report = importer.run(source);
            log.info("Page import report: {}", report);
            report.errors().forEach(e -> log.warn("Page import error: {}", e));
            System.exit(SpringApplication.exit(ctx, () -> report.errors().isEmpty() ? 0 : 1));
        };
    }
}
```

`application-import-pages.yml`:

```yaml
# Import profile: one-shot content-page import (PageImportRunner), no web server.
spring:
  main:
    web-application-type: none
```

- [ ] **Step 8: Verify compile + commit**

Run: `mvn -Dskip.frontend=true -q -DskipTests compile`
Expected: BUILD SUCCESS.

```bash
git add src/main/java/hu/deposoft/webshop/integrations/wordpress/SourcePage.java \
        src/main/java/hu/deposoft/webshop/integrations/wordpress/SourcePages.java \
        src/main/java/hu/deposoft/webshop/application/page/PageImportReport.java \
        src/main/java/hu/deposoft/webshop/application/page/PageImporter.java \
        src/main/java/hu/deposoft/webshop/config/PageImportRunner.java \
        src/main/resources/application-import-pages.yml \
        src/test/java/hu/deposoft/webshop/application/page/PageImporterIT.java
git commit -m "feat(page): idempotent PageImporter + import-pages profile runner"
```

---

### Task 6: `PageAdminService`

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/page/PageAdminService.java`
- Test: covered by `PageAdminControllerIT` (Task 7) — the service is exercised end-to-end through the REST layer, matching how blog admin is tested.

**Interfaces:**
- Consumes: `ContentPageRepository`, `BlogHtmlSanitizer`, `AuditService`.
- Produces: records `PageUpsert(String slug, String title, String bodyHtml, String seoTitle, String seoDescription)`, `PageSummary(Long id, String slug, String title, String status, OffsetDateTime publishedAt, OffsetDateTime updatedAt)`, `PageDetail(Long id, String slug, String title, String bodyHtml, String status, String seoTitle, String seoDescription)`; exceptions `SlugConflictException`, `NotFoundException`; methods `list/get/create/update/delete/publish/unpublish`.

- [ ] **Step 1: Write the service**

```java
package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.blog.BlogHtmlSanitizer;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional
public class PageAdminService {

    private final ContentPageRepository pages;
    private final BlogHtmlSanitizer sanitizer;
    private final AuditService audit;

    public record PageUpsert(String slug, String title, String bodyHtml,
                             String seoTitle, String seoDescription) {}

    public record PageSummary(Long id, String slug, String title, String status,
                              OffsetDateTime publishedAt, OffsetDateTime updatedAt) {}

    public record PageDetail(Long id, String slug, String title, String bodyHtml,
                             String status, String seoTitle, String seoDescription) {}

    public static class SlugConflictException extends RuntimeException {
        public SlugConflictException(String m) { super(m); }
    }
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public Page<PageSummary> list(int page, int size) {
        return pages.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(p -> new PageSummary(p.getId(), p.getSlug(), p.getTitle(),
                        p.getStatus().name(), p.getPublishedAt(), p.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public PageDetail get(Long id) {
        return toDetail(find(id));
    }

    public PageDetail create(PageUpsert cmd) {
        if (pages.existsBySlug(cmd.slug())) {
            throw new SlugConflictException("Slug already exists: " + cmd.slug());
        }
        ContentPage p = ContentPage.create(null, cmd.slug(), cmd.title());
        apply(p, cmd);
        pages.save(p);
        audit.record("CONTENT_PAGE_CREATE", "content_page", String.valueOf(p.getId()), cmd.slug());
        return toDetail(p);
    }

    public PageDetail update(Long id, PageUpsert cmd) {
        ContentPage p = find(id);
        if (!p.getSlug().equals(cmd.slug()) && pages.existsBySlug(cmd.slug())) {
            throw new SlugConflictException("Slug already exists: " + cmd.slug());
        }
        // slug is immutable on the entity by design; updates keep the original slug.
        apply(p, cmd);
        audit.record("CONTENT_PAGE_UPDATE", "content_page", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    public void delete(Long id) {
        ContentPage p = find(id);
        pages.delete(p);
        audit.record("CONTENT_PAGE_DELETE", "content_page", String.valueOf(id), p.getSlug());
    }

    public PageDetail publish(Long id) {
        ContentPage p = find(id);
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        audit.record("CONTENT_PAGE_PUBLISH", "content_page", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    public PageDetail unpublish(Long id) {
        ContentPage p = find(id);
        p.unpublish();
        audit.record("CONTENT_PAGE_UNPUBLISH", "content_page", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    private ContentPage find(Long id) {
        return pages.findById(id)
                .orElseThrow(() -> new NotFoundException("Content page not found: " + id));
    }

    private void apply(ContentPage p, PageUpsert cmd) {
        p.setTitle(cmd.title());
        p.setBodyHtml(sanitizer.sanitize(cmd.bodyHtml() == null ? "" : cmd.bodyHtml()));
        p.setSeoTitle(cmd.seoTitle());
        p.setSeoDescription(cmd.seoDescription());
    }

    private PageDetail toDetail(ContentPage p) {
        return new PageDetail(p.getId(), p.getSlug(), p.getTitle(), p.getBodyHtml(),
                p.getStatus().name(), p.getSeoTitle(), p.getSeoDescription());
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -Dskip.frontend=true -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/page/PageAdminService.java
git commit -m "feat(page): PageAdminService (CRUD + publish, sanitize on write, audit)"
```

---

### Task 7: `PageAdminController` + exception mappings

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/api/admin/PageAdminController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/PageAdminControllerIT.java`

**Interfaces:**
- Consumes: `PageAdminService` (Task 6).
- Produces REST: `GET /api/admin/pages` (X-Total-Count), `GET/POST /api/admin/pages`, `GET/PUT/DELETE /api/admin/pages/{id}`, `POST /api/admin/pages/{id}/publish`, `POST /api/admin/pages/{id}/unpublish`. (Resource name `pages` → matches the admin-ui dataProvider path.)

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class PageAdminControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin@example.com").roles("ADMIN");
    }

    private static final String BODY = """
        {"slug":"rolunk","title":"Rólunk","bodyHtml":"<p>ok</p><script>x</script>",
         "seoTitle":null,"seoDescription":null}""";

    @Test
    void createSanitizesAndReturnsDraft() throws Exception {
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
    }

    @Test
    void listSetsTotalCountHeader() throws Exception {
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/pages").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"));
    }

    @Test
    void duplicateSlugReturns409() throws Exception {
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isConflict());
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/api/admin/pages/999999").with(admin())).andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dskip.frontend=true -Dtest=PageAdminControllerIT test`
Expected: FAIL — controller and exception mappings don't exist (404/500 instead of expected statuses).

- [ ] **Step 3: Write the controller**

`PageAdminController.java`:

```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.page.PageAdminService;
import hu.deposoft.webshop.application.page.PageAdminService.PageDetail;
import hu.deposoft.webshop.application.page.PageAdminService.PageSummary;
import hu.deposoft.webshop.application.page.PageAdminService.PageUpsert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PageAdminController {

    private final PageAdminService service;

    @GetMapping("/api/admin/pages")
    public List<PageSummary> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  HttpServletResponse response) {
        Page<PageSummary> result = service.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(result.getTotalElements()));
        return result.getContent();
    }

    @GetMapping("/api/admin/pages/{id}")
    public PageDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/admin/pages")
    public PageDetail create(@RequestBody PageUpsert cmd) {
        return service.create(cmd);
    }

    @PutMapping("/api/admin/pages/{id}")
    public PageDetail update(@PathVariable Long id, @RequestBody PageUpsert cmd) {
        return service.update(id, cmd);
    }

    @DeleteMapping("/api/admin/pages/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/api/admin/pages/{id}/publish")
    public PageDetail publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/api/admin/pages/{id}/unpublish")
    public PageDetail unpublish(@PathVariable Long id) {
        return service.unpublish(id);
    }
}
```

- [ ] **Step 4: Add exception mappings**

In `AdminExceptionHandler.java`, add (next to the `blogSlugConflict`/`blogNotFound` handlers):

```java
    @ExceptionHandler(hu.deposoft.webshop.application.page.PageAdminService.SlugConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String pageSlugConflict(RuntimeException e) { return e.getMessage(); }

    @ExceptionHandler(hu.deposoft.webshop.application.page.PageAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String pageNotFound(RuntimeException e) { return e.getMessage(); }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -Dskip.frontend=true -Dtest=PageAdminControllerIT test`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/PageAdminController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/test/java/hu/deposoft/webshop/api/admin/PageAdminControllerIT.java
git commit -m "feat(page): admin REST controller + exception mappings"
```

---

### Task 8: Exporter `export-pages.py` + test + README

**Files:**
- Create: `scripts/woo-export/export-pages.py`
- Create: `scripts/woo-export/test_export_pages.py`
- Modify: `scripts/woo-export/README.md`

**Interfaces:**
- Produces stdout JSON `{ "pages": [ {externalId, slug, title, bodyHtml, status, publishedAt, seoTitle, seoDescription} ] }` matching `SourcePages`/`SourcePage`.
- Pure function `walk(node, title, parts)` assembles Elementor widgets into HTML fragments (importable by the test).

- [ ] **Step 1: Write the failing test**

`test_export_pages.py`:

```python
import export_pages as ep


def test_walk_assembles_heading_and_text_in_order():
    data = [{"elements": [
        {"widgetType": "heading", "settings": {"title": "Bevezető"}},
        {"widgetType": "text-editor", "settings": {"editor": "<p>Szia</p>"}},
    ]}]
    parts = []
    ep.walk(data, "Rólunk", parts)
    assert parts == ["<h2>Bevezető</h2>", "<p>Szia</p>"]


def test_walk_skips_heading_equal_to_page_title():
    data = [{"widgetType": "heading", "settings": {"title": "Rólunk"}}]
    parts = []
    ep.walk(data, "Rólunk", parts)
    assert parts == []


def test_walk_toggle_becomes_h3_plus_content():
    data = [{"widgetType": "toggle", "settings": {"tabs": [
        {"tab_title": "Kérdés?", "tab_content": "<p>Válasz</p>"}]}}]
    parts = []
    ep.walk(data, "GYIK", parts)
    assert parts == ["<h3>Kérdés?</h3>", "<p>Válasz</p>"]


def test_to_iso_utc_handles_zero_date():
    assert ep.to_iso_utc("0000-00-00 00:00:00") is None
    assert ep.to_iso_utc("2021-06-01 10:00:00") == "2021-06-01T10:00:00Z"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd scripts/woo-export && python3 -m pytest test_export_pages.py -q`
Expected: FAIL — `No module named 'export_pages'`.

- [ ] **Step 3: Write the exporter**

`export-pages.py` — note: the module filename has a hyphen, so the test imports `export_pages`; create the file as `export_pages.py` **and** keep the invocation name consistent. (The other scripts use hyphens but are run directly, not imported. For importability, name this file `export_pages.py`.)

Create `scripts/woo-export/export_pages.py`:

```python
#!/usr/bin/env python3
"""Export selected WordPress content pages (Elementor) to a SourcePages JSON snapshot.

Walks each page's `_elementor_data` widget tree in document order and emits, per page:
externalId (wp page id), slug, title, assembled bodyHtml, status, publishedAt (UTC),
seoTitle, seoDescription. Images are NOT downloaded here — the Java PageImporter fetches
and re-keys them. Driven by an editable SLUGS list so it can be re-run on an updated set.

Usage:
    python3 scripts/woo-export/export_pages.py > /tmp/pages.json
Then import (from the app):
    mvn spring-boot:run -Dspring-boot.run.profiles=local,import-pages \
      -Dspring-boot.run.arguments=--webshop.import.pages-file=/tmp/pages.json
Re-running the import is safe: upsert by wp page id (externalId), slug never changes.
"""
import html
import json
import re
import subprocess
import sys

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"

# Editable list of page slugs to export (re-runnable before go-live).
SLUGS = [
    "butorfestes-kezdoknek", "rolunk", "latvanymuhely", "kapcsolat", "csapatunk",
    "itt-hallottal-meg-rolunk", "butorfestes-otletek", "gyakran-ismetelt-kerdesek",
    "altalanos-szerzodesi-feltetelek", "adatvedelem",
]


def t(name):
    return PREFIX + name


def query(sql):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", sql],
        capture_output=True, text=True, check=True)
    return result.stdout


def plain(htmltext):
    return html.unescape(re.sub(r"<[^>]+>", " ", htmltext or "")).strip()


def to_iso_utc(date_gmt):
    """WP post_date_gmt 'YYYY-MM-DD HH:MM:SS' (UTC) -> ISO-8601 '...Z'; None for the zero date."""
    if not date_gmt or date_gmt.startswith("0000-00-00"):
        return None
    return date_gmt.replace(" ", "T") + "Z"


def walk(node, title, parts):
    """Append HTML section fragments to `parts` in document order. Mirrors export-workshops.py."""
    if isinstance(node, dict):
        wt = node.get("widgetType")
        s = node.get("settings", {}) or {}
        if wt == "heading":
            text = plain(s.get("title", ""))
            if text and text.lower() != title.strip().lower():
                parts.append(f"<h2>{html.escape(text)}</h2>")
        elif wt == "text-editor":
            editor = (s.get("editor") or "").strip()
            if editor:
                parts.append(editor)
        elif wt in ("toggle", "accordion"):
            for tab in (s.get("tabs") or []):
                q = plain(tab.get("tab_title", ""))
                a = (tab.get("tab_content") or "").strip()
                if q:
                    parts.append(f"<h3>{html.escape(q)}</h3>")
                if a:
                    parts.append(a)
        elif wt == "image":
            img = s.get("image", {}) or {}
            url = img.get("url")
            if url:
                alt = html.escape(plain(s.get("alt", "")) or "")
                parts.append(f'<img src="{html.escape(url)}" alt="{alt}">')
        elif wt in ("media-carousel", "image-carousel"):
            for sl in (s.get("slides") or s.get("carousel") or []):
                if not isinstance(sl, dict):
                    continue
                img = sl.get("image", {}) if isinstance(sl.get("image"), dict) else {}
                url = img.get("url") or sl.get("url")
                if url:
                    parts.append(f'<img src="{html.escape(url)}" alt="">')
        for v in node.values():
            walk(v, title, parts)
    elif isinstance(node, list):
        for v in node:
            walk(v, title, parts)


def export_page(row):
    pid = row["id"]
    raw = query(
        f"SELECT meta_value FROM {t('postmeta')} "
        f"WHERE post_id={pid} AND meta_key='_elementor_data';").strip()
    parts = []
    if raw:
        walk(json.loads(raw), row["title"], parts)
    body = "\n".join(parts).strip() or (row.get("content") or "")
    return {
        "externalId": pid,
        "slug": row["slug"],
        "title": row["title"],
        "bodyHtml": body,
        "status": row["status"],
        "publishedAt": to_iso_utc(row.get("date_gmt")),
        "seoTitle": row.get("seo_title") or None,
        "seoDescription": row.get("seo_desc") or None,
    }


def main():
    slug_list = ",".join("'" + s.replace("'", "") + "'" for s in SLUGS)
    rows = [json.loads(line) for line in query(f"""
        SELECT JSON_OBJECT(
          'id', p.ID, 'slug', p.post_name, 'title', p.post_title,
          'content', p.post_content, 'status', p.post_status, 'date_gmt', p.post_date_gmt,
          'seo_title', (SELECT pm.meta_value FROM {t('postmeta')} pm
                        WHERE pm.post_id=p.ID AND pm.meta_key='_yoast_wpseo_title' LIMIT 1),
          'seo_desc', (SELECT pm.meta_value FROM {t('postmeta')} pm
                       WHERE pm.post_id=p.ID AND pm.meta_key='_yoast_wpseo_metadesc' LIMIT 1))
        FROM {t('posts')} p
        WHERE p.post_type='page' AND p.post_name IN ({slug_list})""").splitlines()
            if line.strip()]
    pages = [export_page(r) for r in rows]
    json.dump({"pages": pages}, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd scripts/woo-export && python3 -m pytest test_export_pages.py -q`
Expected: PASS (4 tests).

- [ ] **Step 5: Document in README**

Append a `## Content pages` section to `scripts/woo-export/README.md` (mirror the `## Blog` section):

```markdown
## Content pages

`export_pages.py` exports the content pages listed in its `SLUGS` array (Elementor
pages assembled to HTML) from the **`client7002dbnem`** database (container `wp_db`)
into a `SourcePages` JSON consumed by `PageImporter`. Edit `SLUGS` to change the set;
re-runnable before go-live.

```bash
# 1. export (wp_db container must be running)
python3 scripts/woo-export/export_pages.py > /tmp/pages.json

# 2. import into the local webshop Postgres (one-shot run, no web server)
mvn spring-boot:run -Dspring-boot.run.profiles=local,import-pages \
  -Dspring-boot.run.arguments=--webshop.import.pages-file=/tmp/pages.json
```

Re-running the import is safe: upsert by wp page id (externalId); slug never changes.
Images are downloaded from the live site by the importer (point `STORAGE_DIR` at the
uploads dir the app serves).
```

- [ ] **Step 6: Commit**

```bash
git add scripts/woo-export/export_pages.py scripts/woo-export/test_export_pages.py scripts/woo-export/README.md
git commit -m "feat(export): export_pages.py for WordPress content pages"
```

---

### Task 9: Admin-ui — Pages list + editor + routes + i18n

**Files:**
- Create: `admin-ui/src/pages/pages/list.tsx`, `admin-ui/src/pages/pages/edit.tsx`
- Create: `admin-ui/src/i18n/hu/pages.json`, `admin-ui/src/i18n/en/pages.json`
- Modify: `admin-ui/src/i18n/index.ts`, `admin-ui/src/App.tsx`

**Interfaces:**
- Consumes REST resource `pages` → `/api/admin/pages` (Task 7). Reuses `components/HtmlEditor`, `api/http` (`apiFetch`, `API_BASE`).
- Note: the current static `<Pages />` (from `./pages/cms`, mock `CMS_PAGES`) is replaced by the live `PageList`.

- [ ] **Step 1: Create i18n namespaces**

`admin-ui/src/i18n/hu/pages.json`:

```json
{
  "title": "Oldalak",
  "subtitle": "{{count}} tartalmi oldal",
  "newPage": "Új oldal",
  "empty": "Nincs oldal",
  "colTitle": "Cím",
  "colSlug": "URL",
  "colStatus": "Állapot",
  "colUpdatedAt": "Frissítve",
  "statusPublished": "Publikált",
  "statusDraft": "Piszkozat",
  "createTitle": "Új oldal",
  "editTitle": "Oldal szerkesztése",
  "back": "← Vissza",
  "save": "Mentés",
  "cancel": "Mégse",
  "saved": "Mentve",
  "created": "Létrehozva",
  "saveFailed": "A mentés nem sikerült",
  "boxBasics": "Alapadatok",
  "boxContent": "Tartalom",
  "boxSeo": "SEO",
  "fieldTitle": "Cím",
  "fieldSlug": "URL-slug",
  "fieldRequired": "Kötelező mező",
  "slugPattern": "Csak kisbetű, szám és kötőjel",
  "slugReadOnly": "A slug nem módosítható (SEO).",
  "fieldSeoTitle": "SEO cím",
  "fieldSeoDescription": "SEO leírás",
  "viewEdit": "Szerkesztés",
  "viewPreview": "Előnézet",
  "viewSplit": "Osztott",
  "previewEmpty": "Nincs tartalom"
}
```

`admin-ui/src/i18n/en/pages.json`:

```json
{
  "title": "Pages",
  "subtitle": "{{count}} content pages",
  "newPage": "New page",
  "empty": "No pages",
  "colTitle": "Title",
  "colSlug": "URL",
  "colStatus": "Status",
  "colUpdatedAt": "Updated",
  "statusPublished": "Published",
  "statusDraft": "Draft",
  "createTitle": "New page",
  "editTitle": "Edit page",
  "back": "← Back",
  "save": "Save",
  "cancel": "Cancel",
  "saved": "Saved",
  "created": "Created",
  "saveFailed": "Save failed",
  "boxBasics": "Basics",
  "boxContent": "Content",
  "boxSeo": "SEO",
  "fieldTitle": "Title",
  "fieldSlug": "URL slug",
  "fieldRequired": "Required",
  "slugPattern": "Lowercase letters, digits and hyphens only",
  "slugReadOnly": "Slug cannot be changed (SEO).",
  "fieldSeoTitle": "SEO title",
  "fieldSeoDescription": "SEO description",
  "viewEdit": "Edit",
  "viewPreview": "Preview",
  "viewSplit": "Split",
  "previewEmpty": "No content"
}
```

- [ ] **Step 2: Register the namespace in `i18n/index.ts`**

Add imports near the other blocks:

```ts
import huPages from "./hu/pages.json";
import enPages from "./en/pages.json";
```

Add `pages: huPages,` to the `hu:` resources object and `pages: enPages,` to the `en:` resources object, and add `"pages"` to the `ns:` array.

- [ ] **Step 3: Create the list component**

`admin-ui/src/pages/pages/list.tsx` (mirrors `pages/blog/index.tsx`, resource `pages`):

```tsx
import { type CSSProperties } from "react";
import { useTable } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Table, Tag } from "antd";
import { Pagination } from "../../components/ui/Pagination";

const accentPill: CSSProperties = {
  borderRadius: 999,
  padding: "9px 18px",
  fontSize: 13,
  fontWeight: 600,
  color: "#fff",
  background: "var(--accent)",
  boxShadow: "0 2px 6px rgba(22,24,29,.3)",
  cursor: "pointer",
  border: 0,
};

interface PageSummary {
  id: number;
  slug: string;
  title: string;
  status: string;
  updatedAt: string;
}

export function PageList() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { tableQueryResult, current, setCurrent, pageSize } = useTable<PageSummary>({
    resource: "pages",
    syncWithLocation: true,
    pagination: { pageSize: 10 },
  });

  const rows = tableQueryResult.data?.data ?? [];
  const total = tableQueryResult.data?.total ?? 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("pages:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("pages:subtitle", { count: total })}</div>
        </div>
        <div style={{ flex: 1 }} />
        <button type="button" style={accentPill} onClick={() => navigate("/pages/create")}>
          {t("pages:newPage")}
        </button>
      </div>

      <Table<PageSummary>
        dataSource={rows}
        rowKey="id"
        loading={tableQueryResult.isLoading}
        pagination={false}
        locale={{ emptyText: t("pages:empty") }}
        onRow={(r) => ({
          onClick: () => navigate(`/pages/edit/${r.id}`),
          style: { cursor: "pointer" },
        })}
      >
        <Table.Column title={t("pages:colTitle")} dataIndex="title" />
        <Table.Column title={t("pages:colSlug")} dataIndex="slug" />
        <Table.Column<PageSummary>
          title={t("pages:colStatus")}
          dataIndex="status"
          render={(status: string) =>
            status === "PUBLISHED" ? (
              <Tag color="green">{t("pages:statusPublished")}</Tag>
            ) : (
              <Tag>{t("pages:statusDraft")}</Tag>
            )
          }
        />
        <Table.Column<PageSummary>
          title={t("pages:colUpdatedAt")}
          dataIndex="updatedAt"
          render={(v: string) => new Date(v).toLocaleString("hu-HU")}
        />
      </Table>
      <Pagination current={current} pageSize={pageSize} total={total} onChange={setCurrent} />
    </div>
  );
}
```

- [ ] **Step 4: Create the editor component**

`admin-ui/src/pages/pages/edit.tsx` (mirrors `pages/blog/edit.tsx` minus categories/products/cover):

```tsx
import { useEffect, useState, type CSSProperties } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useCreate, useOne, useUpdate } from "@refinedev/core";
import { App, Button, Form, Input, Segmented, Switch } from "antd";
import DOMPurify from "dompurify";
import { useTranslation } from "react-i18next";
import { apiFetch, API_BASE } from "../../api/http";
import { HtmlEditor } from "../../components/HtmlEditor";

interface PageUpsert {
  slug: string;
  title: string;
  bodyHtml: string;
  seoTitle: string | null;
  seoDescription: string | null;
}

interface PageDetail {
  id: number;
  slug: string;
  title: string;
  bodyHtml: string;
  status: string;
  seoTitle: string | null;
  seoDescription: string | null;
}

type ContentView = "edit" | "preview" | "split";

const box: CSSProperties = {
  background: "var(--surface)",
  border: "1px solid var(--border)",
  borderRadius: 2,
  boxShadow: "0 1px 2px rgba(16,24,40,.04)",
  padding: 18,
  display: "flex",
  flexDirection: "column",
  gap: 16,
};
const boxTitle: CSSProperties = { fontSize: 13, fontWeight: 700 };

export function PageEdit() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = id != null;

  const [form] = Form.useForm<PageUpsert>();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string>("DRAFT");
  const [bodyHtml, setBodyHtml] = useState<string>("");
  const [contentView, setContentView] = useState<ContentView>("edit");

  const { data: pageData } = useOne<PageDetail>({
    resource: "pages",
    id: id ?? "",
    queryOptions: { enabled: isEdit },
  });
  const loaded = pageData?.data;

  useEffect(() => {
    if (loaded) {
      setStatus(loaded.status);
      setBodyHtml(loaded.bodyHtml ?? "");
    }
  }, [loaded]);

  const { mutate: createPage } = useCreate<PageDetail>();
  const { mutate: updatePage } = useUpdate<PageDetail>();

  const handleSave = async () => {
    let values: Omit<PageUpsert, never>;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    if (isEdit) {
      updatePage(
        { resource: "pages", id: id!, values },
        {
          onSuccess: () => message.success(t("pages:saved")),
          onError: (err) => message.error((err as { message?: string }).message ?? t("pages:saveFailed")),
        },
      );
    } else {
      createPage(
        { resource: "pages", values },
        {
          onSuccess: (result) => {
            message.success(t("pages:created"));
            navigate(`/pages/edit/${result.data.id}`);
          },
          onError: (err) => message.error((err as { message?: string }).message ?? t("pages:saveFailed")),
        },
      );
    }
  };

  const handlePublish = () => {
    if (!isEdit) return;
    setBusy(true);
    apiFetch(`${API_BASE}/pages/${id}/${status === "PUBLISHED" ? "unpublish" : "publish"}`, { method: "POST" })
      .then((res) => {
        if (res.ok) {
          setStatus(status === "PUBLISHED" ? "DRAFT" : "PUBLISHED");
        } else {
          return res.text().then((txt) => message.error(txt || t("pages:saveFailed")));
        }
      })
      .catch(() => message.error(t("pages:saveFailed")))
      .finally(() => setBusy(false));
  };

  const handleBodyChange = (html: string) => {
    setBodyHtml(html);
    form.setFieldValue("bodyHtml", html);
  };

  const showEditor = contentView === "edit" || contentView === "split";
  const showPreview = contentView === "preview" || contentView === "split";

  const previewPane = (
    <div
      className="blog-preview-body"
      style={{
        flex: 1, minWidth: 0, overflow: "auto", border: "1px solid var(--border)",
        borderRadius: 6, padding: "16px 18px", background: "var(--surface)",
        fontSize: 15, lineHeight: 1.65, color: "var(--text)",
      }}
    >
      {bodyHtml.trim() ? (
        <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(bodyHtml) }} />
      ) : (
        <div style={{ color: "var(--faint)" }}>{t("pages:previewEmpty")}</div>
      )}
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 900 }}>
      <div onClick={() => navigate("/pages")} style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}>
        {t("pages:back")}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <div style={{ fontSize: 23, fontWeight: 700, letterSpacing: "-.3px" }}>
          {isEdit ? t("pages:editTitle") : t("pages:createTitle")}
        </div>
        <div style={{ flex: 1 }} />
        {isEdit && (
          <Switch
            checked={status === "PUBLISHED"}
            onChange={handlePublish}
            disabled={busy}
            checkedChildren={t("pages:statusPublished")}
            unCheckedChildren={t("pages:statusDraft")}
          />
        )}
        <Button type="primary" onClick={handleSave} disabled={busy}>{t("pages:save")}</Button>
        <Button onClick={() => navigate("/pages")}>{t("pages:cancel")}</Button>
      </div>

      <Form
        form={form}
        layout="vertical"
        key={isEdit ? (loaded?.id ?? "loading") : "create"}
        initialValues={loaded ? {
          title: loaded.title,
          slug: loaded.slug,
          bodyHtml: loaded.bodyHtml,
          seoTitle: loaded.seoTitle ?? "",
          seoDescription: loaded.seoDescription ?? "",
        } : undefined}
        style={{ display: "flex", flexDirection: "column", gap: 16 }}
      >
        <div style={box}>
          <div style={boxTitle}>{t("pages:boxBasics")}</div>
          <Form.Item label={t("pages:fieldTitle")} name="title" rules={[{ required: true, message: t("pages:fieldRequired") }]} style={{ marginBottom: 0 }}>
            <Input />
          </Form.Item>
          <Form.Item
            label={t("pages:fieldSlug")}
            name="slug"
            rules={[
              { required: true, message: t("pages:fieldRequired") },
              { pattern: /^[a-z0-9-]+$/, message: t("pages:slugPattern") },
            ]}
            extra={isEdit ? t("pages:slugReadOnly") : undefined}
            style={{ marginBottom: 0 }}
          >
            <Input disabled={isEdit} />
          </Form.Item>
        </div>

        <div style={box}>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div style={boxTitle}>{t("pages:boxContent")}</div>
            <div style={{ flex: 1 }} />
            <Segmented<ContentView>
              size="small"
              value={contentView}
              onChange={(v) => setContentView(v)}
              options={[
                { label: t("pages:viewEdit"), value: "edit" },
                { label: t("pages:viewPreview"), value: "preview" },
                { label: t("pages:viewSplit"), value: "split" },
              ]}
            />
          </div>
          <Form.Item name="bodyHtml" noStyle>
            <input type="hidden" />
          </Form.Item>
          <div style={{ display: "flex", gap: 16, alignItems: "stretch", flexWrap: "wrap" }}>
            {showEditor && (
              <div style={{ flex: 1, minWidth: 0, overflow: "auto" }}>
                <HtmlEditor value={bodyHtml} onChange={handleBodyChange} />
              </div>
            )}
            {showPreview && previewPane}
          </div>
        </div>

        <div style={box}>
          <div style={boxTitle}>{t("pages:boxSeo")}</div>
          <Form.Item label={t("pages:fieldSeoTitle")} name="seoTitle" style={{ marginBottom: 0 }}>
            <Input />
          </Form.Item>
          <Form.Item label={t("pages:fieldSeoDescription")} name="seoDescription" style={{ marginBottom: 0 }}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </div>
      </Form>
    </div>
  );
}
```

- [ ] **Step 5: Wire routes and resource in `App.tsx`**

1. Replace the import `import { Pages } from "./pages/cms";` with:
```ts
import { PageList } from "./pages/pages/list";
import { PageEdit } from "./pages/pages/edit";
```
2. Update the resource entry (line ~69) to add create/edit:
```ts
{ name: "pages", list: "/pages", create: "/pages/create", edit: "/pages/edit/:id", meta: { label: "Oldalak" } },
```
3. Replace the route `<Route path="/pages" element={<Pages />} />` with:
```tsx
              <Route path="/pages">
                <Route index element={<PageList />} />
                <Route path="create" element={<PageEdit />} />
                <Route path="edit/:id" element={<PageEdit />} />
              </Route>
```

- [ ] **Step 6: Build the admin-ui**

Run: `cd admin-ui && npm run build`
Expected: `tsc --noEmit` passes and `vite build` succeeds (no type errors, no unused-import errors from the removed `Pages`/`CMS_PAGES` — if `pages/cms/index.tsx` or `data/pages.ts` are now unused and flagged, leave them in place; they are not imported anymore and cause no error).

- [ ] **Step 7: Commit**

```bash
git add admin-ui/src/pages/pages/ admin-ui/src/i18n/hu/pages.json admin-ui/src/i18n/en/pages.json \
        admin-ui/src/i18n/index.ts admin-ui/src/App.tsx
git commit -m "feat(admin-ui): live content Pages list + TipTap editor"
```

---

### Task 10: ADR

**Files:**
- Create: `docs/adr/0011-content-pages-in-db.md`

- [ ] **Step 1: Write the ADR**

Create `docs/adr/0011-content-pages-in-db.md` (follow the format of `docs/adr/0008-blog-in-database.md`), recording:
- **Context:** ~50 WordPress content pages; TERV.md originally planned an SSG/CDN static layer, but the blog was pivoted into the DB (ADR 0008). Goal: whole site functional from Spring Boot now, CDN later.
- **Decision:** store selected content pages in Postgres (`content_page`), import from WordPress via the same export→import pattern as the blog/workshops, render at root `/{slug}` through a unified `RootSlugController` that resolves page → blog post → 404 (page precedence on slug collision), edit via the shared TipTap admin. All write paths sanitize (jsoup).
- **Consequences:** publish without deploy; one root-slug owner; nav-menu wiring, campaign/funnel pages with forms, functional pages (cart/checkout/account), and CDN remain out of scope; ~15 of the site's pages are curated content, the rest are functional/marketing.

- [ ] **Step 2: Commit**

```bash
git add docs/adr/0011-content-pages-in-db.md
git commit -m "docs(adr): 0011 content pages in DB + unified root-slug resolution"
```

---

## Final verification

- [ ] Run the full backend test suite: `mvn -Dskip.frontend=true test` — all green (new `ContentPageTest`, `RootSlugControllerIT`, `PageImporterIT`, `PageAdminControllerIT`, plus unchanged `BlogControllerIT`, `BlogImporterIT`, `BlogImporterImageFailureIT`).
- [ ] Run the admin-ui build: `cd admin-ui && npm run build` — passes.
- [ ] Run the exporter test: `cd scripts/woo-export && python3 -m pytest test_export_pages.py -q` — passes.
- [ ] **Acceptance (manual, with `wp_db` running):**
  1. `python3 scripts/woo-export/export_pages.py > /tmp/pages.json` → JSON with 10 pages.
  2. `STORAGE_DIR=./data/uploads mvn spring-boot:run -Dspring-boot.run.profiles=local,import-pages -Dspring-boot.run.arguments=--webshop.import.pages-file=/tmp/pages.json` → report shows created=10, errors=0.
  3. Start the app; visit `/rolunk`, `/adatvedelem`, etc. → pages render at root slug with `/media/*` images; drafts 404; existing blog post URLs still render.
  4. In the admin SPA → "Oldalak" → list shows the 10 pages; open one → edit in TipTap → save → publish toggle works.
  5. Re-run the import → no duplicates; edited WP title refreshes; slug unchanged.
