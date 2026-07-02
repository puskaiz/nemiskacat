# Blog Auto-Excerpt Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a blog post has no manual excerpt, derive a short plain-text description from its Markdown body so the `/blog` list cards (and the article meta/`Article` JSON-LD description) show a summary, like the WordPress reference.

**Architecture:** A small `ExcerptDeriver` component turns Markdown into a plain-text first-~30-words summary (via flexmark's `TextCollectingVisitor`, already on the classpath). `BlogQueryService` gains an `effectiveExcerpt(BlogPost)` helper (manual excerpt if present, else derived) used for the list-item excerpt and the article description fallback. Query-time derivation — no DB/import change, no re-import.

**Tech Stack:** Java 21, Spring (`@Component`), flexmark (`flexmark-all` — `Parser` + `com.vladsch.flexmark.util.ast.TextCollectingVisitor`). Tests: JUnit (unit) + Testcontainers MockMvc (`BlogQueryServiceIT`).

## Global Constraints

- Derivation lives in the service layer (`BlogQueryService` / `ExcerptDeriver`); controllers stay thin (CLAUDE.md #1).
- Public list/article HTML stays session-independent / cacheable (CLAUDE.md #2) — derived text only, no user/cart data.
- UI copy Hungarian; code/comments/commits English (CLAUDE.md #10).
- No DB schema, importer, or DTO-field change; no re-import. A manually-set `excerpt` always wins over the derived one.
- Excerpt length: first **~30 words**, hard cap **~180 chars**, cut at a word boundary, append `…` only when truncated. null/blank/whitespace body → `""`.
- Build: Maven, NO `./mvnw` — use `mvn`.

---

## File Structure

**Create:**
- `src/main/java/hu/deposoft/webshop/application/blog/ExcerptDeriver.java`
- `src/test/java/hu/deposoft/webshop/application/blog/ExcerptDeriverTest.java`

**Modify:**
- `src/main/java/hu/deposoft/webshop/application/blog/BlogQueryService.java` (inject `ExcerptDeriver`; add `effectiveExcerpt`; use in `toListView` + `toPostView`).
- `src/test/java/hu/deposoft/webshop/application/blog/BlogQueryServiceIT.java` (assert derived vs manual excerpt).

---

## Task 1: ExcerptDeriver (Markdown → short plain-text summary)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/blog/ExcerptDeriver.java`
- Test: `src/test/java/hu/deposoft/webshop/application/blog/ExcerptDeriverTest.java`

**Interfaces:**
- Produces: `ExcerptDeriver` `@Component` with `String derive(String markdown)` — strips Markdown to plain text, returns the first ~30 words (≤180 chars, word boundary) + `…` if truncated; null/blank → `""`. Consumed by `BlogQueryService` (Task 2).

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.application.blog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExcerptDeriverTest {

    private final ExcerptDeriver deriver = new ExcerptDeriver();

    @Test
    void stripsMarkdownAndKeepsPlainProse() {
        String md = "# Cím\n\nEz egy **fontos** bekezdés egy [linkkel](https://x.hu) és egyéb szöveggel.";
        String out = deriver.derive(md);
        assertThat(out).contains("Ez egy fontos bekezdés egy linkkel és egyéb szöveggel");
        assertThat(out).doesNotContain("#").doesNotContain("*").doesNotContain("](");
    }

    @Test
    void truncatesToAround30WordsWithEllipsis() {
        StringBuilder md = new StringBuilder();
        for (int i = 1; i <= 60; i++) md.append("szo").append(i).append(' ');
        String out = deriver.derive(md.toString());
        assertThat(out).endsWith("…");
        // first word kept, far word dropped
        assertThat(out).startsWith("szo1 ");
        assertThat(out).doesNotContain("szo60");
        assertThat(out.length()).isLessThanOrEqualTo(181); // ~180 + ellipsis tolerance
    }

    @Test
    void shortBodyHasNoEllipsis() {
        assertThat(deriver.derive("Rövid kis szöveg.")).isEqualTo("Rövid kis szöveg.");
    }

    @Test
    void blankOrNullReturnsEmpty() {
        assertThat(deriver.derive(null)).isEmpty();
        assertThat(deriver.derive("   ")).isEmpty();
    }

    @Test
    void leadingImageDoesNotDominate() {
        String md = "![](/media/up/abc.jpg)\n\nEz a tényleges bevezető szöveg a cikkhez.";
        String out = deriver.derive(md);
        assertThat(out).contains("Ez a tényleges bevezető szöveg a cikkhez");
        assertThat(out).doesNotContain("/media/").doesNotContain(".jpg");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ExcerptDeriverTest`
Expected: FAIL — `ExcerptDeriver` does not exist (compile error).

- [ ] **Step 3: Implement `ExcerptDeriver`**

```java
package hu.deposoft.webshop.application.blog;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;
import org.springframework.stereotype.Component;

/**
 * Derives a short plain-text excerpt from a Markdown body — used as the list-card
 * description and article meta/JSON-LD description when a post has no manual excerpt
 * (CLAUDE.md #9 blog). Mirrors WordPress's auto-excerpt behaviour.
 */
@Component
public class ExcerptDeriver {

    private static final int MAX_WORDS = 30;
    private static final int MAX_CHARS = 180;

    private final Parser parser = Parser.builder().build();

    public String derive(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        String text = new TextCollectingVisitor().collectAndGetText(document);
        if (text == null) {
            return "";
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "";
        }

        String[] words = text.split(" ");
        StringBuilder out = new StringBuilder();
        boolean truncated = false;
        for (int i = 0; i < words.length; i++) {
            if (i >= MAX_WORDS) {
                truncated = true;
                break;
            }
            String candidate = out.length() == 0 ? words[i] : out + " " + words[i];
            if (candidate.length() > MAX_CHARS) {
                truncated = true;
                break;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(words[i]);
        }
        // Degenerate case: a single first word longer than MAX_CHARS — hard-cut it.
        if (out.length() == 0) {
            out.append(text, 0, Math.min(MAX_CHARS, text.length()));
            truncated = text.length() > MAX_CHARS;
        }
        return truncated ? out + "…" : out.toString();
    }
}
```
> Implementer note: `TextCollectingVisitor.collectAndGetText(Node)` is the flexmark API on the classpath via `flexmark-all`. If the exact method name differs in 0.64.8, use the equivalent (`collectAndGetText()` after `collect(document)`), but keep the behaviour: plain text of the document, markup stripped. Confirm imports compile with `mvn test-compile`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ExcerptDeriverTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/ExcerptDeriver.java src/test/java/hu/deposoft/webshop/application/blog/ExcerptDeriverTest.java
git commit -m "feat(blog): ExcerptDeriver — short plain-text excerpt from Markdown body"
```

---

## Task 2: Wire effectiveExcerpt into BlogQueryService

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/BlogQueryService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/blog/BlogQueryServiceIT.java`

**Interfaces:**
- Consumes: `ExcerptDeriver.derive(String)` (Task 1); existing `BlogQueryService` internals — `toListView` builds `BlogListItem(slug, title, excerpt, coverImageUrl, publishedDate, categories)` (excerpt currently `p.getExcerpt()`); `toPostView` computes `resolvedDescription = p.getSeoDescription() != null ? p.getSeoDescription() : p.getExcerpt()` and uses it for the meta description + JSON-LD `description`.
- Produces: `BlogListItem.excerpt` and the article description now fall back to the derived excerpt when no manual one exists.

**Current state (for reference):** `BlogQueryService` is `@Service @RequiredArgsConstructor` with fields incl. `private final MarkdownRenderer markdown;`. `toListView` (≈line 80): `new BlogListItem(p.getSlug(), p.getTitle(), p.getExcerpt(), …)`. `toPostView` (≈line 88): `String resolvedDescription = p.getSeoDescription() != null ? p.getSeoDescription() : p.getExcerpt();`.

- [ ] **Step 1: Write the failing test**

Add to `BlogQueryServiceIT.java` (mirror the file's existing Testcontainers + autowired `posts` setup):
```java
@Test
void listExcerptDerivedWhenNoManualExcerpt() {
    var p = hu.deposoft.webshop.domain.blog.BlogPost.create("derivalt", "Derivált");
    p.setBodyMarkdown("# Cím\n\nEz a cikk bevezető szövege, amiből a kivonat származik.");
    // no setExcerpt(...) -> excerpt is null
    p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    posts.save(p);

    var item = blog.publishedList(1).items().stream()
            .filter(i -> i.slug().equals("derivalt")).findFirst().orElseThrow();
    assertThat(item.excerpt()).isNotBlank();
    assertThat(item.excerpt()).contains("Ez a cikk bevezető szövege");
    assertThat(item.excerpt()).doesNotContain("#");
}

@Test
void listExcerptUsesManualWhenPresent() {
    var p = hu.deposoft.webshop.domain.blog.BlogPost.create("kezi", "Kézi");
    p.setExcerpt("Kézi kivonat.");
    p.setBodyMarkdown("Egészen más bevezető szöveg a body-ban.");
    p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    posts.save(p);

    var item = blog.publishedList(1).items().stream()
            .filter(i -> i.slug().equals("kezi")).findFirst().orElseThrow();
    assertThat(item.excerpt()).isEqualTo("Kézi kivonat.");
}
```
> Implementer note: use the file's existing autowired field names (`blog` for `BlogQueryService`, `posts` for `BlogPostRepository`) and the `@SpringBootTest @Testcontainers @Transactional` setup already in the file. `publishedList(int)` returns `BlogListView`; `.items()` is `List<BlogListItem>` with `.slug()` / `.excerpt()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=BlogQueryServiceIT`
Expected: FAIL — `listExcerptDerivedWhenNoManualExcerpt` fails because `BlogListItem.excerpt` is currently `p.getExcerpt()` (null for a post with no manual excerpt).

- [ ] **Step 3: Inject `ExcerptDeriver` and add `effectiveExcerpt`**

In `BlogQueryService.java`, add the field next to the other `private final` deps (constructor is Lombok `@RequiredArgsConstructor`):
```java
private final ExcerptDeriver excerptDeriver;
```
Add a private helper (near `categoryRefs`/`publishedDate`):
```java
private String effectiveExcerpt(BlogPost p) {
    if (p.getExcerpt() != null && !p.getExcerpt().isBlank()) {
        return p.getExcerpt();
    }
    String derived = excerptDeriver.derive(p.getBodyMarkdown());
    return derived.isBlank() ? null : derived;
}
```

- [ ] **Step 4: Use `effectiveExcerpt` in `toListView` and `toPostView`**

In `toListView`, change the `BlogListItem` excerpt argument from `p.getExcerpt()` to `effectiveExcerpt(p)`:
```java
.map(p -> new BlogListItem(p.getSlug(), p.getTitle(), effectiveExcerpt(p),
        mediaUrl(p.getCoverImageKey()), publishedDate(p), categoryRefs(p)))
```
In `toPostView`, change the description fallback from `p.getExcerpt()` to `effectiveExcerpt(p)`:
```java
String resolvedDescription = p.getSeoDescription() != null ? p.getSeoDescription() : effectiveExcerpt(p);
```
(The rest of `toPostView` — JSON-LD assembly using `resolvedDescription` — is unchanged.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=BlogQueryServiceIT`
Expected: PASS — derived excerpt for the no-manual-excerpt post; manual excerpt preserved for the other; existing tests still green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogQueryService.java src/test/java/hu/deposoft/webshop/application/blog/BlogQueryServiceIT.java
git commit -m "feat(blog): derive list/description excerpt from body when no manual excerpt"
```

---

## Final verification

- [ ] **Focused suites:** `mvn test -Dtest=ExcerptDeriverTest,BlogQueryServiceIT,BlogControllerIT` → all green.
- [ ] **Visual check (controller runs the app on a free port with `--webshop.storage-dir` pointing at the real `data/uploads`, then headless-screenshots `/blog`):** each card now shows a short description under the title (derived from the body, markup-free, ending with `…` when truncated); a post with a manual excerpt shows that instead.

---

## Self-Review Notes

- **Spec coverage:** §4 ExcerptDeriver (strip markdown, first ~30 words/180 chars/word-boundary/`…`, blank→"") → Task 1 + unit tests; §5 wiring (`effectiveExcerpt`, list excerpt, article description fallback, constructor inject) → Task 2 Steps 3–4; §6 testing (unit + IT derived-vs-manual) → Task 1 + Task 2 tests + final visual. Manual-excerpt-wins → `effectiveExcerpt` guard + `listExcerptUsesManualWhenPresent`.
- **Placeholder scan:** none — full code in every step. The one implementer note flags a flexmark-API confirmation (verify-against-classpath), not missing content.
- **Type consistency:** `ExcerptDeriver.derive(String):String` consistent across Task 1 (definition) and Task 2 (call); `effectiveExcerpt(BlogPost):String` used in both `toListView` and `toPostView`; `BlogListItem.excerpt()` / `publishedList().items()` match the existing record API.
