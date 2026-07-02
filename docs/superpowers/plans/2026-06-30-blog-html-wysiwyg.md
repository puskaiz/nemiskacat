# Blog HTML + TipTap WYSIWYG Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store blog post bodies as server-sanitized HTML edited with a TipTap WYSIWYG, replacing the Markdown textarea + flexmark pipeline, without losing existing content.

**Architecture:** Posts stay in Postgres (ADR 0008). A new `body_html` column holds sanitized HTML; a single server-side `BlogHtmlSanitizer` (jsoup `Safelist`) guards every write path (admin save + import). The public render path returns stored HTML directly. The importer stores sanitized WP HTML (no Markdown conversion), reusing the `wp:columns`→`.nk-figure-row` normalisation as HTML. The admin SPA edits with TipTap. See ADR 0009.

**Tech Stack:** Java 21 / Spring Boot 3 / Postgres + Flyway / jsoup (already present) / React + Refine + antd / TipTap (`@tiptap/react`, `@tiptap/starter-kit`, `@tiptap/extension-link`, `-image`, `-table`).

## Global Constraints

- Money/time/stock rules are irrelevant here; blog rules from CLAUDE.md apply:
- Slugs are immutable and 1:1 from WP (rule #7) — never alter slug logic.
- Public blog HTML must stay session-independent and cacheable (rule #2).
- Import is idempotent: upsert by slug (rule #4).
- Sanitization runs on EVERY write path; nothing renders that was not sanitized on write.
- Code/comments/commits in English; UI/content Hungarian.
- New libraries already human-approved for this plan: TipTap (admin-ui only). No new server dependency.
- Tests: JUnit + Testcontainers (Postgres) for backend; existing vitest for admin-ui units.

---

## Phase A — Server: sanitizer, schema, read/write paths

### Task A1: BlogHtmlSanitizer

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/blog/BlogHtmlSanitizer.java`
- Test: `src/test/java/hu/deposoft/webshop/application/blog/BlogHtmlSanitizerTest.java`

**Interfaces:**
- Produces: `@Component class BlogHtmlSanitizer { String sanitize(String html); }` — returns safe HTML; null/blank → `""`.

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.application.blog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BlogHtmlSanitizerTest {
    private final BlogHtmlSanitizer s = new BlogHtmlSanitizer();

    @Test void stripsScriptAndEventHandlers() {
        assertThat(s.sanitize("<p onclick=\"x()\">hi</p><script>evil()</script>"))
                .isEqualTo("<p>hi</p>");
    }
    @Test void keepsAllowedFormatting() {
        String in = "<h2>Cím</h2><p>egy <strong>bold</strong> <a href=\"https://x.hu\">link</a></p><ul><li>a</li></ul>";
        String out = s.sanitize(in);
        assertThat(out).contains("<h2>Cím</h2>").contains("<strong>bold</strong>")
                .contains("<a href=\"https://x.hu\"").contains("<ul>").contains("<li>a</li>");
    }
    @Test void keepsFigureRowLayoutAndImages() {
        String in = "<div class=\"nk-figure-row\"><figure><img src=\"/media/abc\" alt=\"k\"><figcaption>cap</figcaption></figure></div>";
        String out = s.sanitize(in);
        assertThat(out).contains("class=\"nk-figure-row\"").contains("<figure>")
                .contains("<img").contains("/media/abc").contains("<figcaption>cap</figcaption>");
    }
    @Test void keepsTables() {
        String in = "<table><thead><tr><th>a</th></tr></thead><tbody><tr><td>b</td></tr></tbody></table>";
        assertThat(s.sanitize(in)).contains("<table>").contains("<th>a</th>").contains("<td>b</td>");
    }
    @Test void dropsDisallowedClassButKeepsElement() {
        assertThat(s.sanitize("<div class=\"evil wp-block-foo\">x</div>")).isEqualTo("<div>x</div>");
    }
    @Test void blankReturnsEmpty() {
        assertThat(s.sanitize(null)).isEmpty();
        assertThat(s.sanitize("  ")).isEmpty();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -Dtest=BlogHtmlSanitizerTest test`
Expected: FAIL — `BlogHtmlSanitizer` does not exist (compilation error).

- [ ] **Step 3: Implement**

```java
package hu.deposoft.webshop.application.blog;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Single sanitization point for all stored blog HTML (admin save + import).
 * Allowlist-based (jsoup): unknown tags/attributes are dropped, so the rendered
 * body can never contain script/style/event handlers/unknown embeds.
 */
@Component
public class BlogHtmlSanitizer {

    private final Safelist safelist = buildSafelist();

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // prettyPrint(false): keep output compact and stable for storage/diffing.
        org.jsoup.nodes.Document.OutputSettings out =
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html, "", safelist, out);
    }

    private static Safelist buildSafelist() {
        Safelist sl = Safelist.relaxed()
                // relaxed() already allows h1-6, p, ul/ol/li, blockquote, a, img,
                // strong/em, code, pre, table family, figure/figcaption, etc.
                .addAttributes("a", "href", "title", "target", "rel")
                .addAttributes("img", "src", "alt", "title", "width", "height")
                .addAttributes("div", "class")
                .addAttributes("figure", "class")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                // images are served same-origin from /media — allow relative URLs
                .preserveRelativeLinks(true);
        return sl;
    }
}
```

Note: jsoup's class-attribute cleaning keeps the `class` attribute but does not
filter individual class tokens. The disallowed-class test (`A1` step 1) requires
token filtering — implement it by post-processing in `sanitize` (see Step 3b).

- [ ] **Step 3b: Add class-token allowlisting**

Replace the `return Jsoup.clean(...)` line with:

```java
        String cleaned = Jsoup.clean(html, "", safelist, out);
        org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(cleaned);
        for (org.jsoup.nodes.Element el : doc.select("[class]")) {
            String kept = el.classNames().stream()
                    .filter(ALLOWED_CLASSES::contains)
                    .reduce((a, b) -> a + " " + b).orElse("");
            if (kept.isBlank()) {
                el.removeAttr("class");
            } else {
                el.attr("class", kept);
            }
        }
        return doc.body().html();
```

Add the constant:

```java
    private static final java.util.Set<String> ALLOWED_CLASSES =
            java.util.Set.of("nk-figure-row");
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -Dtest=BlogHtmlSanitizerTest test`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogHtmlSanitizer.java \
        src/test/java/hu/deposoft/webshop/application/blog/BlogHtmlSanitizerTest.java
git commit -m "feat(blog): add BlogHtmlSanitizer (jsoup allowlist) for stored HTML"
```

---

### Task A2: Schema — add `body_html` and backfill

**Files:**
- Create: `src/main/resources/db/migration/V23__blog_body_html.sql`
- Create: `src/main/java/hu/deposoft/webshop/config/BlogHtmlBackfillRunner.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/blog/BlogPost.java` (add `bodyHtml` field)
- Test: `src/test/java/hu/deposoft/webshop/application/blog/BlogHtmlBackfillIT.java`

**Interfaces:**
- Produces: `blog_post.body_html TEXT NOT NULL DEFAULT ''`; `BlogPost.getBodyHtml()/setBodyHtml(String)`; a `@Profile("backfill-blog-html")` runner that fills `body_html` from rendered+sanitized `body_markdown` where `body_html=''`.

- [ ] **Step 1: Write the migration**

```sql
-- V23: blog body migrates from Markdown to sanitized HTML (ADR 0009).
-- body_markdown is retained (deprecated) for one release, then dropped.
ALTER TABLE blog_post ADD COLUMN body_html TEXT NOT NULL DEFAULT '';
```

- [ ] **Step 2: Add the entity field**

In `BlogPost.java`, after the `bodyMarkdown` field (line ~57) add:

```java
    @Column(name = "body_html", nullable = false)
    private String bodyHtml = "";
```

(Keep `@Getter/@Setter` consistent with the class; if Lombok class-level, nothing else needed.)

- [ ] **Step 3: Write the backfill runner**

```java
package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.blog.BlogHtmlSanitizer;
import hu.deposoft.webshop.application.blog.MarkdownRenderer;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time backfill: render existing body_markdown -> HTML (flexmark) -> sanitize
 * -> body_html, for rows whose body_html is still empty. Non-destructive; preserves
 * any manual Markdown edits. Run with profile {@code backfill-blog-html}, then exit.
 */
@Configuration
@Profile("backfill-blog-html")
public class BlogHtmlBackfillRunner {
    private static final Logger log = LoggerFactory.getLogger(BlogHtmlBackfillRunner.class);

    @Bean
    CommandLineRunner backfillBlogHtml(BlogPostRepository posts, MarkdownRenderer md,
                                       BlogHtmlSanitizer sanitizer, ApplicationContext ctx) {
        return args -> {
            int n = backfill(posts, md, sanitizer);
            log.info("Blog HTML backfill complete: {} posts updated", n);
            System.exit(SpringApplication.exit(ctx, () -> 0));
        };
    }

    @Transactional
    int backfill(BlogPostRepository posts, MarkdownRenderer md, BlogHtmlSanitizer sanitizer) {
        int n = 0;
        for (BlogPost p : posts.findAll()) {
            if (p.getBodyHtml() == null || p.getBodyHtml().isBlank()) {
                p.setBodyHtml(sanitizer.sanitize(md.toHtml(p.getBodyMarkdown())));
                n++;
            }
        }
        return n;
    }
}
```

- [ ] **Step 4: Write the backfill IT**

```java
package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.config.BlogHtmlBackfillRunner;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BlogHtmlBackfillIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17");

    @Autowired BlogPostRepository posts;
    @Autowired MarkdownRenderer md;
    @Autowired BlogHtmlSanitizer sanitizer;

    @Test
    void backfillsHtmlFromMarkdown() {
        BlogPost p = BlogPost.create("backfill-x", "Cím");
        p.setBodyMarkdown("# H\n\nbody **b**");
        posts.save(p);

        int n = new BlogHtmlBackfillRunner().backfill(posts, md, sanitizer);

        assertThat(n).isGreaterThanOrEqualTo(1);
        BlogPost reloaded = posts.findBySlug("backfill-x").orElseThrow();
        assertThat(reloaded.getBodyHtml()).contains("<h1>H</h1>").contains("<strong>b</strong>");
    }
}
```

- [ ] **Step 5: Run**

Run: `mvn -Dtest=BlogHtmlBackfillIT test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V23__blog_body_html.sql \
        src/main/java/hu/deposoft/webshop/domain/blog/BlogPost.java \
        src/main/java/hu/deposoft/webshop/config/BlogHtmlBackfillRunner.java \
        src/test/java/hu/deposoft/webshop/application/blog/BlogHtmlBackfillIT.java
git commit -m "feat(blog): add body_html column + non-destructive backfill runner"
```

---

### Task A3: Public render path returns stored HTML

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/BlogQueryService.java:106` (use `bodyHtml` instead of `markdown.toHtml(bodyMarkdown)`)
- Test: `src/test/java/hu/deposoft/webshop/application/blog/BlogQueryServiceIT.java` (extend)

**Interfaces:**
- Consumes: `BlogPost.getBodyHtml()`.
- Produces: `BlogPostView.bodyHtml` = the stored sanitized HTML, verbatim.

- [ ] **Step 1: Add a failing test** to `BlogQueryServiceIT` asserting a post with `bodyHtml="<p>kész</p>"` yields `BlogPostView.bodyHtml()` equal to `"<p>kész</p>"` (no flexmark re-render). Save the post with `setBodyHtml("<p>kész</p>")`.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -Dtest=BlogQueryServiceIT test`
Expected: FAIL — current code returns `markdown.toHtml(getBodyMarkdown())` which is `""`.

- [ ] **Step 3: Change the mapping at `BlogQueryService.java:106`**

```java
return new BlogPostView(p.getSlug(), p.getTitle(), p.getBodyHtml(),
```

Remove the now-unused `MarkdownRenderer markdown` field if nothing else in the class uses it (check: it is only used on line 106). Leave it if the admin preview still references the bean elsewhere — verify with `grep -rn "MarkdownRenderer" src/main/java`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -Dtest=BlogQueryServiceIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogQueryService.java \
        src/test/java/hu/deposoft/webshop/application/blog/BlogQueryServiceIT.java
git commit -m "feat(blog): render public post body from stored body_html"
```

---

### Task A4: Admin save sanitizes + persists HTML

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/BlogAdminService.java` — `PostUpsert`/`PostDetail` use `bodyHtml`; `applyCreate`/`applyUpdate` call `sanitizer.sanitize(cmd.bodyHtml())`; `toDetail` returns `bodyHtml`. Inject `BlogHtmlSanitizer`.
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/BlogPostController.java` — remove the `preview` endpoint + `PreviewRequest/Response`.
- Test: `src/test/java/hu/deposoft/webshop/api/admin/BlogPostControllerIT.java` (extend)

**Interfaces:**
- Consumes: `BlogHtmlSanitizer.sanitize`.
- Produces: `PostUpsert(... String bodyHtml ...)`, `PostDetail(... String bodyHtml ...)`. (Replace the `bodyMarkdown` component in both records.)

- [ ] **Step 1: Failing test** in `BlogPostControllerIT`: POST a create with `"bodyHtml":"<p>ok</p><script>x()</script>"`, then GET by id and assert `$.bodyHtml` == `"<p>ok</p>"` (script stripped). (Keep it `@Transactional` per the existing class — sanitization is in-tx, no lazy-collection issue here.)

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -Dtest=BlogPostControllerIT test`
Expected: FAIL — `bodyHtml` unknown / field still `bodyMarkdown`.

- [ ] **Step 3: Implement** — in `BlogAdminService`:
  - add `private final BlogHtmlSanitizer sanitizer;` (constructor via `@RequiredArgsConstructor`).
  - change both records' `String bodyMarkdown` component to `String bodyHtml`.
  - in `applyCreate`/`applyUpdate` replace `p.setBodyMarkdown(cmd.bodyMarkdown()==null?"":cmd.bodyMarkdown());` with `p.setBodyHtml(sanitizer.sanitize(cmd.bodyHtml()));`.
  - in `toDetail` replace `p.getBodyMarkdown()` with `p.getBodyHtml()`.
  - Remove the `preview(...)` service method.
  - In `BlogPostController`, delete the `/api/admin/blog/preview` mapping and the `PreviewRequest/PreviewResponse` records.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -Dtest=BlogPostControllerIT,BlogPostGetByIdIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogAdminService.java \
        src/main/java/hu/deposoft/webshop/api/admin/BlogPostController.java \
        src/test/java/hu/deposoft/webshop/api/admin/BlogPostControllerIT.java
git commit -m "feat(blog): admin save stores sanitized HTML; drop markdown preview endpoint"
```

---

## Phase B — Importer stores sanitized HTML

### Task B1: HTML import transform (columns→figure-row, image-URL rewrite in DOM)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/blog/BlogHtmlTransform.java` (jsoup: strip WP block comments, `wp:columns` image blocks → `<div class="nk-figure-row">…</div>`, leave the image `<img src>` pointing at the original URL for B2 to rewrite).
- Test: `src/test/java/hu/deposoft/webshop/application/blog/BlogHtmlTransformTest.java`

**Interfaces:**
- Produces: `@Component class BlogHtmlTransform { String normalize(String html); }` — returns HTML with image columns converted to `.nk-figure-row` and other content untouched.

- [ ] **Step 1: Failing test** — port the two assertions from the (now-superseded) `HtmlToMarkdownTest.gutenbergImageColumnsBecomeFigureRow`, but expect **HTML** output: a `wp-block-columns` of two image columns becomes `<div class="nk-figure-row"><figure><img src="…a.jpg" alt="Kép A"><figcaption>…</figcaption></figure><figure>…</figure></div>`; a text-only columns block is left unchanged. Use the same jsoup detection logic (`isImageColumns`) already proven in `HtmlToMarkdown`.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -Dtest=BlogHtmlTransformTest test`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement** — reuse the `isImageColumns` logic from `HtmlToMarkdown` (immediate `wp-block-column` children; any column with text but no image → not an image layout). For each image-columns block, replace it with a `<div class="nk-figure-row">` containing one `<figure>` per image (img + optional figcaption from `wp-element-caption`). Strip Gutenberg `<!-- wp:* -->` comments. Return `doc.body().html()`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -Dtest=BlogHtmlTransformTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogHtmlTransform.java \
        src/test/java/hu/deposoft/webshop/application/blog/BlogHtmlTransformTest.java
git commit -m "feat(blog): HTML import transform (wp:columns -> nk-figure-row)"
```

### Task B2: BlogImporter stores sanitized HTML with rewritten image URLs

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java` — replace `htmlToMarkdown.convert(...)` + `rewriteUploadsImages(markdown)` with: `normalize → rewrite <img src> uploads in the DOM → sanitize → setBodyHtml`. Inject `BlogHtmlTransform`, `BlogHtmlSanitizer`. Replace the markdown-regex image rewrite with a jsoup `img[src]` pass.
- Modify: `src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java` and `BlogImporterImageFailureIT.java` — assert on `getBodyHtml()` and stored `/media/<key>` `<img src>`.

**Interfaces:**
- Consumes: `BlogHtmlTransform.normalize`, `BlogHtmlSanitizer.sanitize`, `ImageFetcher`, `StorageService`.
- Produces: posts with `body_html` containing sanitized HTML and `/media/<key>` image srcs.

- [ ] **Step 1: Update the failing tests** to expect HTML (`getBodyHtml()` contains `<img src="/media/`, and the image-failure IT keeps the original URL on 404).

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -Dtest=BlogImporterIT,BlogImporterImageFailureIT test`
Expected: FAIL — importer still writes `bodyMarkdown`.

- [ ] **Step 3: Implement** — in `upsertPost`:

```java
String html = blogHtmlTransform.normalize(source.contentHtml());
html = rewriteUploadImages(html, report);     // jsoup img[src] pass (replaces rewriteUploadsImages)
post.setBodyHtml(htmlSanitizer.sanitize(html));
```

`rewriteUploadImages(String html, BlogImportReport report)`: `Jsoup.parseBodyFragment`, for each `img[src]` whose src contains `/wp-content/uploads/`, fetch via `imageFetcher`, `storage.put`, set `img.attr("src", "/media/"+key)` (`report.imageStored()`); on failure keep original src and `report.error(...)`. Return `doc.body().html()`. Delete the old `UPLOADS_IMG` regex + `rewriteUploadsImages`.

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -Dtest=BlogImporterIT,BlogImporterImageFailureIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java \
        src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java \
        src/test/java/hu/deposoft/webshop/application/blog/BlogImporterImageFailureIT.java
git commit -m "feat(blog): importer stores sanitized HTML with /media image srcs"
```

---

## Phase C — Admin SPA: TipTap editor

### Task C1: Add TipTap deps + editor component

**Files:**
- Modify: `admin-ui/package.json` (add `@tiptap/react`, `@tiptap/pm`, `@tiptap/starter-kit`, `@tiptap/extension-link`, `@tiptap/extension-image`, `@tiptap/extension-table`, `-table-row`, `-table-cell`, `-table-header`)
- Create: `admin-ui/src/components/HtmlEditor.tsx` (TipTap, antd-`Form.Item`-compatible `value: string` (HTML) + `onChange(html)`; toolbar: bold/italic, H2/H3, lists, link, image-by-`/media` URL, table insert)
- Create: `admin-ui/src/components/HtmlEditor.test.ts` (unit: a helper that serializes/normalizes editor HTML; keep DOM-free per existing test setup — test pure helpers only, e.g. a `figureRowHtml(images)` builder)

**Interfaces:**
- Produces: `export function HtmlEditor({ value, onChange }: { value?: string; onChange?: (html: string) => void })`.

- [ ] **Step 1: Add dependencies**

```bash
cd admin-ui && npm install @tiptap/react @tiptap/pm @tiptap/starter-kit \
  @tiptap/extension-link @tiptap/extension-image @tiptap/extension-table \
  @tiptap/extension-table-row @tiptap/extension-table-cell @tiptap/extension-table-header
```

- [ ] **Step 2: Implement `HtmlEditor.tsx`** — `useEditor({ extensions: [StarterKit, Link, Image, Table.configure({resizable:false}), TableRow, TableHeader, TableCell], content: value, onUpdate: ({editor}) => onChange?.(editor.getHTML()) })`. Render a small antd `Space` toolbar (buttons call `editor.chain().focus().toggleBold()` etc.) above `<EditorContent editor={editor} />`. Sync external `value` changes with `useEffect(() => { if (editor && value !== editor.getHTML()) editor.commands.setContent(value ?? "") }, [value])`.

- [ ] **Step 3: Pure-helper unit test** — extract `figureRowHtml(items: {src:string; alt:string; caption?:string}[]): string` into `HtmlEditor.tsx` (exported) and test it builds `<div class="nk-figure-row">…`. Run: `npm run test`. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add admin-ui/package.json admin-ui/package-lock.json \
        admin-ui/src/components/HtmlEditor.tsx admin-ui/src/components/HtmlEditor.test.ts
git commit -m "feat(admin-ui): TipTap HtmlEditor component"
```

### Task C2: Wire HtmlEditor into the blog edit page

**Files:**
- Modify: `admin-ui/src/pages/blog/edit.tsx` — replace the `bodyMarkdown` `Input.TextArea` + editor/preview tab buttons + `handlePreview`/`previewHtml`/`showPreview` with `<Form.Item name="bodyHtml"><HtmlEditor/></Form.Item>`. Rename the `PostUpsert`/`PostDetail` interface fields `bodyMarkdown` → `bodyHtml`. Drop `apiFetch(... /blog/preview ...)`.
- Modify: `admin-ui/src/pages/blog/index.tsx` — no change (list doesn't show body).

**Interfaces:**
- Consumes: `HtmlEditor`, the API now returning `bodyHtml`.

- [ ] **Step 1: Implement the edit-page changes** — update the two TS interfaces (`bodyHtml: string`), the `initialValues` block (`bodyHtml: loaded.bodyHtml`), and replace the markdown field block with the editor. Remove preview state/handlers.

- [ ] **Step 2: Build the SPA**

Run: `cd admin-ui && npm run build`
Expected: `tsc --noEmit` passes (no references to removed `bodyMarkdown`/preview), `vite build` succeeds.

- [ ] **Step 3: Commit**

```bash
git add admin-ui/src/pages/blog/edit.tsx
git commit -m "feat(admin-ui): edit blog body with TipTap (bodyHtml)"
```

---

## Phase D — Cleanup & docs

### Task D1: Retire the Markdown pipeline for the blog

**Files:**
- Delete: `src/main/java/hu/deposoft/webshop/application/blog/HtmlToMarkdown.java` + its test (logic ported to `BlogHtmlTransform`).
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/MarkdownRenderer.java` — remove the GFM `TablesExtension` only if nothing else needs it; **keep** the renderer (used by the backfill runner and the future AI-Markdown ingest). Confirm with `grep -rn "MarkdownRenderer\|HtmlToMarkdown" src/main/java`.
- Delete: `MarkdownRendererTest.rendersGfmPipeTable` if the extension is removed.

- [ ] **Step 1:** Run `grep -rn "HtmlToMarkdown\|markdown.toHtml\|bodyMarkdown" src/main/java` and confirm only the backfill runner references the renderer; remove `HtmlToMarkdown` + test.
- [ ] **Step 2:** `mvn test` — full suite green.
- [ ] **Step 3: Commit** `chore(blog): retire HtmlToMarkdown; Markdown path no longer used for rendering`.

### Task D2: Drop `body_markdown` (separate release)

**Files:**
- Create: `src/main/resources/db/migration/V24__blog_drop_body_markdown.sql` — `ALTER TABLE blog_post DROP COLUMN body_markdown;`
- Modify: `BlogPost.java` (remove `bodyMarkdown` field), `BlogHtmlBackfillRunner` (delete — backfill done), `MarkdownRenderer` (delete if AI ingest not yet built; otherwise keep).

> **Gate:** Only run D2 after the backfill has run against the real DB and the HTML path is confirmed in production-equivalent testing. Keep `body_markdown` until then so rollback is trivial.

- [ ] **Step 1:** Add migration; remove field + runner; `mvn test` green.
- [ ] **Step 2: Commit** `chore(blog): drop deprecated body_markdown column`.

### Task D3: Update CLAUDE.md rule #9 and the Stack section

**Files:**
- Modify: `CLAUDE.md` — rule #9 + Stack: blog authoring is sanitized HTML via TipTap (not Markdown); sanitizer is mandatory on every write path; reference ADR 0009.

- [ ] **Step 1:** Edit CLAUDE.md; **no automatic commit** — these are owner-facing rules, surface the diff for human confirmation before committing.

---

## Operational runbook (post-merge, human-run)

1. Deploy with `body_html` migration (V23). App starts; `body_html` empty.
2. Run backfill once: `mvn spring-boot:run -Dspring-boot.run.profiles=local,backfill-blog-html -Dspring-boot.run.arguments="--spring.main.web-application-type=none"` (or the staging equivalent). Verify a few posts render.
3. (Optional) Re-run the blog import to refresh from WP as sanitized HTML — same command as before, now producing `body_html`.
4. After confidence window, ship D2 (drop `body_markdown`).

---

## Self-Review

- **Spec coverage:** storage (A2), sanitizer (A1), read path (A3), admin save + preview removal (A4), importer (B1/B2), editor (C1/C2), cleanup + column drop + docs (D1–D3), non-destructive migration + runbook — all present.
- **Type consistency:** `bodyHtml` is used consistently across `BlogPost`, `PostUpsert`, `PostDetail`, `BlogPostView`, and the SPA interfaces; `BlogHtmlSanitizer.sanitize`, `BlogHtmlTransform.normalize`, `HtmlEditor({value,onChange})` signatures referenced identically across tasks.
- **Open risk flagged:** TipTap's StarterKit covers headings/lists/bold/italic/link/table/image; a fully custom `figureRow` *node* (so authors build columns visually rather than via raw HTML) is intentionally scoped OUT of v1 — v1 preserves imported `.nk-figure-row` blocks (sanitizer allowlists them) and renders them, but editing them in-place is a follow-up. Call this out at the C2 review gate.
