# Blog List Page Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay out `/blog` (and `/blog/kategoria/{slug}`) posts as horizontal rows — square cover image on the left (~half width) with an overlaid category tag, and title + date + short description + "Tovább olvasom »" on the right — matching the nemiskacat.hu/blog reference.

**Architecture:** Front-end only. Restructure the card markup in `list.html` (category tag becomes an overlay inside a `position: relative` media wrapper — NOT nested in the image anchor; add a read-more link; placeholder when no cover) and add new `.nk-blog-card*` rules to `site.css` (these classes are currently unstyled). No backend/DTO/schema change.

**Tech Stack:** Thymeleaf template, CSS (existing `site.css` + design tokens from `static/design/colors_and_type.css`). Test: JUnit + Testcontainers MockMvc (extend `BlogControllerIT`).

## Global Constraints

- Public blog HTML stays session-independent / cacheable (CLAUDE.md #2) — no user/cart data.
- No business logic in the controller (CLAUDE.md #1); render only the existing `BlogListItem` fields (`slug, title, excerpt, coverImageUrl, publishedDate, categories`).
- UI copy Hungarian; code/comments/commits English (CLAUDE.md #10).
- No backend/DTO/schema/importer change — template + CSS + test only.
- Post links are root `/{slug}`; category links are `/blog/kategoria/{slug}`; the breadcrumb, "Blog" header (`title-row`), and numbered pager (`.pagin`) stay unchanged.
- HTML validity: do NOT nest `<a>` inside `<a>` (the image link and the category links must be siblings, not nested).
- Use existing design tokens (e.g. `--nk-space-*`, `--nk-radius-*`, `--nk-fg-muted`, font-size tokens) — confirm exact names in `static/design/colors_and_type.css`; reuse the category-pill styling already used by the article page's `nk-blog-article__cat` for visual consistency.
- Build: Maven, NO `./mvnw` — use `mvn`.

---

## File Structure

**Modify:**
- `src/main/resources/templates/blog/list.html` — restructure the card (media wrapper + overlay cats + read-more + placeholder).
- `src/main/resources/static/css/site.css` — add `.nk-blog-list` + `.nk-blog-card*` rules.
- `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java` — assert the new row-layout markup.

---

## Task 1: Horizontal-row blog cards (markup + CSS + test)

**Files:**
- Modify: `src/main/resources/templates/blog/list.html`
- Modify: `src/main/resources/static/css/site.css`
- Test: `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java`

**Interfaces:**
- Consumes: `BlogQueryService.publishedList(int)` / `publishedListByCategory(...)` → `BlogListView { items: List<BlogListItem>, page, totalPages, categoryName, categorySlug }`; `BlogListItem { slug, title, excerpt, coverImageUrl, publishedDate, categories: List<CategoryRef{name,slug}> }`. (All unchanged.)
- Produces: row-layout markup with classes `nk-blog-list`, `nk-blog-card`, `nk-blog-card__media`, `nk-blog-card__image-link`, `nk-blog-card__image`, `nk-blog-card__image--placeholder`, `nk-blog-card__cats`, `nk-blog-card__cat`, `nk-blog-card__body`, `nk-blog-card__title(-link)`, `nk-blog-card__date`, `nk-blog-card__excerpt`, `nk-blog-card__readmore`.

**Current state:** `list.html` renders `<div class="nk-blog-grid">` with `<article class="nk-blog-card">` containing an `image-link` (`<a>` wrapping `<img>`) and a `__body` (cats, title, date, excerpt). None of these classes have CSS.

- [ ] **Step 1: Write the failing test**

Add to `BlogControllerIT.java` (seed a published post with a cover + category in-method, mirroring the existing tests' setup):
```java
@Test
void blogListRendersRowLayoutMarkup() throws Exception {
    var cat = hu.deposoft.webshop.domain.blog.BlogCategory.create("Hírek", "hirek");
    categories.save(cat);
    var p = hu.deposoft.webshop.domain.blog.BlogPost.create("listazott-cikk", "Listázott cikk");
    p.setExcerpt("Rövid leírás a listához.");
    p.setBodyMarkdown("törzs");
    p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    p.setCategories(java.util.Set.of(cat));
    posts.save(p);

    mvc().perform(get("/blog"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-list")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-card__media")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-card__readmore")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tovább olvasom")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/listazott-cikk\"")));
}
```
> Implementer note: match the existing tests' autowired fields (`posts`, `categories`) and the `mvc()` helper. If the helper or field names differ, adapt to the file.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: `blogListRendersRowLayoutMarkup` FAILS — `nk-blog-list` / `nk-blog-card__media` / `nk-blog-card__readmore` are not in the current markup.

- [ ] **Step 3: Restructure the card markup in `list.html`**

Replace the current `<div class="nk-blog-grid"> … </div>` block with:
```html
<div class="nk-blog-list">
    <article class="nk-blog-card" th:each="item : ${list.items}">
        <div class="nk-blog-card__media">
            <a th:href="@{'/' + ${item.slug}}" class="nk-blog-card__image-link" tabindex="-1" aria-hidden="true">
                <img th:if="${item.coverImageUrl}"
                     class="nk-blog-card__image"
                     th:src="${item.coverImageUrl}"
                     th:alt="${item.title}"
                     loading="lazy"/>
                <span th:unless="${item.coverImageUrl}"
                      class="nk-blog-card__image nk-blog-card__image--placeholder"></span>
            </a>
            <div class="nk-blog-card__cats" th:if="${!item.categories.isEmpty()}">
                <a th:each="c : ${item.categories}"
                   th:href="@{'/blog/kategoria/' + ${c.slug}}"
                   th:text="${c.name}"
                   class="nk-blog-card__cat">kategória</a>
            </div>
        </div>
        <div class="nk-blog-card__body">
            <a th:href="@{'/' + ${item.slug}}" class="nk-blog-card__title-link">
                <h2 class="nk-blog-card__title" th:text="${item.title}">Cím</h2>
            </a>
            <p class="nk-blog-card__date"
               th:if="${item.publishedDate}"
               th:text="${#temporals.format(item.publishedDate, 'yyyy. MM. dd.')}">dátum</p>
            <p class="nk-blog-card__excerpt" th:if="${item.excerpt}" th:text="${item.excerpt}">kivonat</p>
            <a class="nk-blog-card__readmore" th:href="@{'/' + ${item.slug}}">Tovább olvasom »</a>
        </div>
    </article>
</div>
```
Key points: the image-link `<a>` and the category `<a>`s are SIBLINGS inside `.nk-blog-card__media` (no nested anchors); the image link is marked `aria-hidden`/`tabindex="-1"` because the title link is the primary, accessible link to the same post (avoids duplicate-link noise). The breadcrumb, `title-row` header, and `.pagin` pager above/below are unchanged.

- [ ] **Step 4: Add the CSS to `site.css`**

Append (use the project's real token names — verify against `static/design/colors_and_type.css`; the values below name the tokens used elsewhere in `site.css`):
```css
/* ── Blog list (horizontal rows) ──────────────────────────────────────────── */
.nk-blog-list { display: flex; flex-direction: column; gap: var(--nk-space-6); margin: var(--nk-space-6) 0; }
.nk-blog-card { display: flex; gap: var(--nk-space-6); align-items: stretch; }
.nk-blog-card__media { position: relative; flex: 0 0 50%; aspect-ratio: 1 / 1; }
.nk-blog-card__image-link { display: block; width: 100%; height: 100%; }
.nk-blog-card__image { width: 100%; height: 100%; object-fit: cover; display: block; border-radius: var(--nk-radius-md); }
.nk-blog-card__image--placeholder { background: var(--nk-bg-subtle, #ece8e1); }
.nk-blog-card__cats { position: absolute; top: var(--nk-space-3); left: var(--nk-space-3); display: flex; flex-wrap: wrap; gap: 6px; }
.nk-blog-card__cat { background: var(--nk-accent, #c9a25a); color: #fff; font-size: var(--nk-fs-body-s); font-weight: 600; padding: 4px 10px; border-radius: 999px; text-decoration: none; }
.nk-blog-card__body { flex: 1 1 50%; display: flex; flex-direction: column; justify-content: center; gap: var(--nk-space-2); }
.nk-blog-card__title-link { text-decoration: none; color: inherit; }
.nk-blog-card__title { margin: 0; }
.nk-blog-card__date { margin: 0; font-size: var(--nk-fs-body-s); color: var(--nk-fg-muted); }
.nk-blog-card__excerpt { margin: 0; color: var(--nk-fg-muted); display: -webkit-box; -webkit-line-clamp: 4; line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
.nk-blog-card__readmore { align-self: flex-start; margin-top: var(--nk-space-2); font-weight: 600; text-decoration: none; color: var(--nk-accent, #c9a25a); }
@media (max-width: 640px) {
    .nk-blog-card { flex-direction: column; }
    .nk-blog-card__media { flex: 0 0 auto; width: 100%; }
}
```
> Implementer note: replace the placeholder hex fallbacks with the project's actual tokens once confirmed (e.g. the accent/category-pill color used by `nk-blog-article__cat`, the muted-foreground token, a subtle-bg token). Reuse `nk-blog-article__cat`'s exact pill styling if it exists, for consistency. Keep the new block near the existing `nk-blog-article` block in `site.css`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: PASS (all existing tests + the new `blogListRendersRowLayoutMarkup`).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/blog/list.html src/main/resources/static/css/site.css src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java
git commit -m "feat(blog): horizontal-row blog list (square image left, title/excerpt/read-more right)"
```

---

## Final verification

- [ ] **Focused suite:** `mvn test -Dtest=BlogControllerIT` → all green.
- [ ] **Visual check (controller will run the app on a free port with `--webshop.storage-dir` pointing at the real `data/uploads`, then headless-screenshot `/blog`):** each post is a row with a square cover image on the left (~half width), category tag overlaid top-left, and title + faint date + clamped excerpt + "Tovább olvasom »" on the right; a post without a cover shows the placeholder square; at narrow width the image stacks above the text. Tune the 50% split / square size if it reads too large.

---

## Self-Review Notes

- **Spec coverage:** §4 row layout (square image left ~50%, overlay cats, right column title/date/excerpt/read-more) → Task 1 markup + CSS; §5 markup changes (grid→list, cats overlay, read-more, placeholder) → Task 1 Step 3; §6 testing (markup assertions + visual) → Task 1 test + final visual; responsive stack → CSS media query. Pager/breadcrumb/header unchanged per scope.
- **Placeholder scan:** none — full markup + CSS provided. The two implementer notes flag token-name confirmation (verify-against-actual), not missing content.
- **HTML validity:** category links are siblings of the image link inside `.nk-blog-card__media` (no nested `<a>`).
- **Type/class consistency:** the class names in the test assertions (`nk-blog-list`, `nk-blog-card__media`, `nk-blog-card__readmore`) match the markup (Step 3) and CSS (Step 4); post links `/{slug}`, category links `/blog/kategoria/{slug}` consistent with the rest of the blog.
