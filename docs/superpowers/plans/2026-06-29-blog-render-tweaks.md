# Blog Rendering Tweaks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve blog posts at root `/{slug}` (matching WordPress 1:1), hide the cover hero on the article page, and lay out WP gallery images side-by-side.

**Architecture:** Front-end only. One route change in `BlogController` (post mapping `/blog/{slug}` → `/{slug}`; list + category unchanged), link updates in the list template, removal of the cover `<img>` in the post template, and new blog-article CSS in `site.css` (galleries already survive conversion as multiple `<img>` in one `<p>`). No schema/importer/DTO/data changes; no re-import.

**Tech Stack:** Spring MVC (Thymeleaf controllers), Thymeleaf templates, CSS. Tests: JUnit + Testcontainers MockMvc (mirror existing `BlogControllerIT`).

## Global Constraints

- Public blog HTML stays session-independent / cacheable (CLAUDE.md #2) — no user/cart data in the model.
- Slug is 1:1 from WP; `/{slug}` is the WP-canonical form (CLAUDE.md #7).
- Controller stays thin — no business logic (CLAUDE.md #1).
- UI copy Hungarian; code/comments/commits English (CLAUDE.md #10).
- NO per-page `canonical`/`og:url` (the shared `th:replace` head fragment + the product-page convention preclude it without a shared-fragment change — out of scope).
- The list (`/blog`) and category (`/blog/kategoria/{slug}` + trailing slash) routes are UNCHANGED.
- Build: Maven, NO `./mvnw` — use `mvn`.
- Cover image is removed ONLY from the article page; it stays on list cards and in the `Article` JSON-LD `image`.

---

## File Structure

**Modify:**
- `src/main/java/hu/deposoft/webshop/web/BlogController.java` — post route → `/{slug}` (+ `/{slug}/`).
- `src/main/resources/templates/blog/list.html` — 2 card links `/blog/{slug}` → `/{slug}`.
- `src/main/resources/templates/blog/post.html` — remove the cover `<img>` block.
- `src/main/resources/static/css/site.css` — add `.nk-blog-article__*` + gallery rules.
- `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java` — update post-URL tests; add cover-absent + gallery-structure assertions.

---

## Task 1: Serve posts at root `/{slug}`

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/web/BlogController.java`
- Modify: `src/main/resources/templates/blog/list.html`
- Test: `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java`

**Interfaces:**
- Consumes: `BlogQueryService.getPublishedBySlug(String) : Optional<BlogPostView>` (unchanged), `publishedList`, `publishedListByCategory`.
- Produces: post route at `/{slug}` and `/{slug}/`; list cards link to `/{slug}`.

**Current state (for reference):** `BlogController` has `@GetMapping("/blog")` (list), `@GetMapping({"/blog/kategoria/{slug}", "/blog/kategoria/{slug}/"})` (category), and `@GetMapping({"/blog/{slug}", "/blog/{slug}/"})` (post). `list.html` links posts via `@{'/blog/' + ${item.slug}}` at two places (image-link + title-link).

- [ ] **Step 1: Update the failing tests first**

In `BlogControllerIT.java`, change every post fetch from `/blog/{slug}` to `/{slug}` (and the trailing-slash post test from `/blog/{slug}/` to `/{slug}/`). Leave the `/blog` list test and the `/blog/kategoria/{slug}[/]` category tests unchanged. Then add a new test that an unknown root slug 404s (proving `/{slug}` doesn't hijack other pages):

```java
@Test
void unknownRootSlugReturns404() throws Exception {
    mvc.perform(get("/nincs-ilyen-cikk")).andExpect(status().isNotFound());
}

@Test
void listAndCategoryRoutesStillResolve() throws Exception {
    mvc.perform(get("/blog")).andExpect(status().isOk());
    // a published post + category are seeded by the existing category test's helper;
    // reuse that setup or assert /blog is 200 which needs no seed.
}
```
For the existing published-post test, change e.g. `get("/blog/web-cikk")` → `get("/web-cikk")` and the trailing variant `get("/web-cikk/")`. Keep the JSON-LD + `<h1>` assertions.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: FAIL — the post is still mapped at `/blog/{slug}`, so `get("/web-cikk")` 404s (and `unknownRootSlugReturns404` may pass already, but the moved post tests fail).

- [ ] **Step 3: Change the post route to root**

In `BlogController.java`, change ONLY the post mapping (leave list + category):
```java
@GetMapping({"/{slug}", "/{slug}/"})
public String post(@PathVariable String slug, Model model) {
    // body unchanged: getPublishedBySlug -> 404 if empty -> "blog/post"
}
```
(The list `@GetMapping("/blog")` and category `@GetMapping({"/blog/kategoria/{slug}", "/blog/kategoria/{slug}/"})` are more specific / literal and keep precedence over `/{slug}`.)

- [ ] **Step 4: Update the list-card links**

In `list.html`, change both post links:
```html
<a th:href="@{'/' + ${item.slug}}" class="nk-blog-card__image-link">
...
<a th:href="@{'/' + ${item.slug}}" class="nk-blog-card__title-link">
```
Leave the category links (`@{'/blog/kategoria/' + ...}`) and pager links unchanged.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: PASS — posts resolve at `/{slug}` and `/{slug}/`; `/blog` + category still 200; unknown root slug 404.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/web/BlogController.java src/main/resources/templates/blog/list.html src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java
git commit -m "feat(blog): serve posts at root /{slug} (match WP), keep /blog list + category"
```

---

## Task 2: Hide cover hero on the article page + side-by-side gallery CSS

**Files:**
- Modify: `src/main/resources/templates/blog/post.html`
- Modify: `src/main/resources/static/css/site.css`
- Test: `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java`

**Interfaces:**
- Consumes: `BlogPostView.bodyHtml` (already rendered via `th:utext`), `BlogPostView.coverImageUrl` (stays in JSON-LD; removed from the page body).
- Produces: article page with no cover `<img>`; `.nk-blog-article__body` styling where a `<p>` containing multiple `<img>` lays out as a wrapping row.

**Current state:** `post.html` renders the cover at:
```html
<img th:if="${post.coverImageUrl}"
     class="nk-blog-article__cover"
     th:src="${post.coverImageUrl}"
     th:alt="${post.title}"/>
```
The `nk-blog-article__*` classes have NO CSS rules anywhere yet. `site.css` already holds the `nk-blog-card` (list) rules.

- [ ] **Step 1: Write the failing test**

Add to `BlogControllerIT.java` (the published-post is fetched at `/{slug}` after Task 1):
```java
@Test
void articlePageHasNoCoverImage() throws Exception {
    // seed a published post WITH a coverImageKey, then:
    mvc.perform(get("/web-cikk"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("nk-blog-article__cover"))));
}

@Test
void galleryParagraphRendersMultipleImagesInOneParagraph() throws Exception {
    // seed a published post whose bodyMarkdown puts two images on ONE line:
    //   "![a](/media/x.jpg) ![b](/media/y.jpg)"
    // flexmark renders them as two <img> within a single <p>.
    mvc.perform(get("/galeria-cikk"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                    "(?s).*<p>\\s*<img[^>]*>\\s*<img[^>]*>.*")));
}
```
> Implementer note: seed the posts in-test via `BlogPostRepository` (mirror the existing tests' setup). For `articlePageHasNoCoverImage`, set a cover key so the OLD template would have rendered the cover — proving removal. Adjust the gallery regex to the actual flexmark output if it wraps differently (inspect the rendered HTML once); the assertion's intent is "≥2 `<img>` in the same `<p>`".

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: `articlePageHasNoCoverImage` FAILS (cover still rendered). `galleryParagraphRendersMultipleImagesInOneParagraph` likely PASSES already (flexmark groups same-line images) — that's fine; it guards the structure the CSS relies on.

- [ ] **Step 3: Remove the cover block from `post.html`**

Delete the entire cover `<img th:if="${post.coverImageUrl}" class="nk-blog-article__cover" .../>` element from `post.html`. Leave the header (cats, title, date) and the `nk-blog-article__body` div intact.

- [ ] **Step 4: Add blog-article + gallery CSS to `site.css`**

Append to `src/main/resources/static/css/site.css` (mirror the file's existing nk-* conventions / variables):
```css
/* Blog article body */
.nk-blog-article__body { max-width: 760px; margin: 0 auto; }
.nk-blog-article__body img { max-width: 100%; height: auto; border-radius: 8px; }
/* Single-image paragraph: block, centered */
.nk-blog-article__body p > img:only-child { display: block; margin: 1.25rem auto; }
/* Gallery: a paragraph with multiple images -> wrapping row (side by side) */
.nk-blog-article__body p:has(img + img) {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin: 1.25rem 0;
}
.nk-blog-article__body p:has(img + img) > img {
    flex: 1 1 240px;   /* desktop: one row; narrow screens: wrap to ~2 columns */
    width: auto;
    object-fit: cover;
}
```
> Implementer note: match `site.css`'s existing spacing/radius tokens if it defines CSS variables (e.g. `var(--radius)`) rather than hard-coding; keep values consistent with the `nk-blog-card` block already in the file.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: PASS — cover absent; gallery paragraph still has ≥2 `<img>` in one `<p>`.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/blog/post.html src/main/resources/static/css/site.css src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java
git commit -m "feat(blog): hide cover hero on article page; side-by-side gallery image CSS"
```

---

## Final verification

- [ ] **Full focused suite:** `mvn test -Dtest=BlogControllerIT` → all green (post at `/{slug}`, list/category at `/blog…`, cover absent, gallery structure).
- [ ] **Build the app + manual visual check (human-run, port-free, e.g. `--spring.main.web-application-type` not needed for the running IntelliJ instance):** open `http://localhost:8085/ombre-butorfestes` — loads with NO `/blog` prefix, NO cover hero, and the 4-image group renders in a row (wrapping to ~2 columns on a narrow window). Confirm `/blog` list and a `/blog/kategoria/{slug}` page still render and their cards link to `/{slug}`.

---

## Self-Review Notes

- **Spec coverage:** §4 root URL → Task 1 (route + links + IT); §5 hide cover → Task 2 (post.html + IT); §6 side-by-side CSS → Task 2 (site.css + gallery structure IT); §4 SEO/canonical → explicitly dropped per spec (no per-page canonical; matches product-page convention). §7 testing → Task 1 + Task 2 ITs + final visual check.
- **Placeholder scan:** none — all code blocks concrete; the two implementer notes flag verify-against-actual-output points (flexmark gallery HTML shape, site.css tokens), not missing content.
- **Type/route consistency:** post route `/{slug}` (+ trailing) consistent across Task 1 controller + all ITs; list/category routes left at `/blog…` consistently; `nk-blog-article__cover` / `nk-blog-article__body` class names consistent between post.html and site.css.
- **Deviation (documented):** dropped canonical/og:url (spec §4) — would require a shared head-fragment change; the product page sets no per-page canonical either.
