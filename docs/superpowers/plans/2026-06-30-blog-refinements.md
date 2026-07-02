# Blog Refinements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (A) Cut derived blog excerpts at a sentence boundary in the 30–50 word range; (B) add an empty right sidebar (1/3) to the `/blog` (and category) page.

**Architecture:** Two independent changes. (A) Rework `ExcerptDeriver.derive`'s truncation to prefer the first sentence end at ≥30 words (≤50), else cut at 50 words + "…". (B) Restructure `blog/list.html` to wrap the post list + pager in a 2/3 content column beside an empty 1/3 `<aside>`, with grid CSS in `site.css`. No backend/DTO/schema change beyond `ExcerptDeriver` internals.

**Tech Stack:** Java 21 (flexmark `Parser` + `com.vladsch.flexmark.ast.util.TextCollectingVisitor`), Thymeleaf, CSS. Tests: JUnit unit (`ExcerptDeriverTest`) + Testcontainers MockMvc (`BlogControllerIT`).

## Global Constraints

- Derivation in the service layer; controllers thin; list/article HTML session-independent / cacheable (CLAUDE.md #1/#2).
- UI copy Hungarian; code/comments/commits English (CLAUDE.md #10).
- No DB schema / importer / DTO-field change; no re-import. Manual `excerpt` still wins (the `BlogQueryService.effectiveExcerpt` wiring is unchanged).
- Excerpt: prefer the FIRST sentence-ending word at word-index ≥30 and ≤50 → cut there, NO "…" (complete sentence). No sentence end in [30,50] → cut at 50 words (word boundary) + "…". Text ≤30 words → whole, no "…". Sentence-end chars: `.` `!` `?` `…` (trailing closing quote/paren allowed). null/blank → "". Constants `MIN_WORDS=30`, `MAX_WORDS=50`, safety `MAX_CHARS=500`.
- Sidebar: breadcrumb + `title-row` header stay full-width on top; below them a 2-col layout — content 2/3 (existing `.nk-blog-list` + `.pagin`) + empty `<aside>` 1/3; sidebar appears on BOTH `/blog` and `/blog/kategoria/{slug}`; single column under 900px.
- Build: Maven, NO `./mvnw` — use `mvn`.

---

## File Structure

**Modify:**
- `src/main/java/hu/deposoft/webshop/application/blog/ExcerptDeriver.java` (Task 1)
- `src/test/java/hu/deposoft/webshop/application/blog/ExcerptDeriverTest.java` (Task 1)
- `src/main/resources/templates/blog/list.html` (Task 2)
- `src/main/resources/static/css/site.css` (Task 2)
- `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java` (Task 2)

---

## Task 1: Sentence-boundary excerpt truncation

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/ExcerptDeriver.java`
- Test: `src/test/java/hu/deposoft/webshop/application/blog/ExcerptDeriverTest.java`

**Interfaces:**
- Produces: `ExcerptDeriver.derive(String markdown)` — same signature, new truncation behaviour (sentence boundary in [30,50] words; else 50 + "…"). Consumed unchanged by `BlogQueryService`.

**Current state:** `ExcerptDeriver` has `MAX_WORDS=30`, `MAX_CHARS=180`, a `private final Parser parser`, and `derive` that strips markdown (via `TextCollectingVisitor.collectAndGetText`), normalizes whitespace, then takes the first 30 words / ≤180 chars + "…". Keep the markdown-stripping + whitespace-normalize prefix; replace the truncation tail and the constants.

- [ ] **Step 1: Update the failing tests**

Replace the truncation-specific tests in `ExcerptDeriverTest.java` (keep the markdown-strip + null/blank tests) with these; add the new ones:
```java
@Test
void cutsAtFirstSentenceEndBetween30And50Words() {
    StringBuilder md = new StringBuilder();
    for (int i = 1; i <= 28; i++) md.append("szo").append(i).append(' ');
    md.append("vege."); // 29th word ends a sentence — BEFORE 30, must NOT cut here
    for (int i = 30; i <= 40; i++) md.append(" szo").append(i);
    md.append(" zaras."); // a sentence end at ~word 42 (in [30,50]) -> cut here
    md.append(" szo50 szo51 szo52 tovabbi szoveg ami nem kell.");
    String out = deriver.derive(md.toString());
    assertThat(out).endsWith("zaras.");      // cut at the in-window sentence end
    assertThat(out).doesNotEndWith("…");      // complete sentence -> no ellipsis
    assertThat(out).doesNotContain("szo51");  // nothing past the cut
    assertThat(out).contains("vege.");        // the pre-30 sentence end was NOT a cut point
}

@Test
void cutsAt50WordsWithEllipsisWhenNoSentenceEndInWindow() {
    StringBuilder md = new StringBuilder();
    for (int i = 1; i <= 70; i++) md.append("szo").append(i).append(' '); // no '.'/'!'/'?'
    String out = deriver.derive(md.toString().trim());
    assertThat(out).endsWith("…");
    assertThat(out).contains("szo50");
    assertThat(out).doesNotContain("szo51");
}

@Test
void shortBodyReturnedWholeNoEllipsis() {
    assertThat(deriver.derive("Rövid kis bevezető szöveg.")).isEqualTo("Rövid kis bevezető szöveg.");
}

@Test
void naturalEndUnderFiftyWordsNoEllipsis() {
    StringBuilder md = new StringBuilder();
    for (int i = 1; i <= 40; i++) md.append("szo").append(i).append(' '); // 40 words, no sentence punctuation, text ends
    String out = deriver.derive(md.toString().trim());
    assertThat(out).doesNotEndWith("…");      // whole text included, nothing truncated
    assertThat(out).contains("szo40");
}
```
Keep the existing `stripsMarkdownAndKeepsPlainProse`, `blankOrNullReturnsEmpty`, and `leadingImageDoesNotDominate` tests (markdown-stripping behaviour is unchanged).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=ExcerptDeriverTest`
Expected: the new sentence-boundary tests FAIL (current logic cuts at 30 words / 180 chars, not at sentence boundaries).

- [ ] **Step 3: Replace constants + truncation logic in `ExcerptDeriver`**

Set the constants:
```java
private static final int MIN_WORDS = 30;
private static final int MAX_WORDS = 50;
private static final int MAX_CHARS = 500; // safety cap against a pathological single token
```
Replace the post-normalize truncation tail of `derive(...)` (everything after `text` is the cleaned plain text) with:
```java
        if (text.isEmpty()) {
            return "";
        }
        String[] words = text.split(" ");

        // ≤ MIN_WORDS: return whole text.
        if (words.length <= MIN_WORDS) {
            return capChars(text, false);
        }

        StringBuilder out = new StringBuilder();
        boolean sentenceEnd = false;
        int taken = Math.min(words.length, MAX_WORDS);
        for (int i = 0; i < taken; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(words[i]);
            // a sentence ending only counts once we have at least MIN_WORDS words
            if (i + 1 >= MIN_WORDS && endsSentence(words[i])) {
                sentenceEnd = true;
                break;
            }
        }
        // ellipsis only when we truncated mid-text without landing on a sentence end
        boolean truncated = !sentenceEnd && words.length > MAX_WORDS;
        return capChars(out.toString(), truncated);
    }

    /** True if the word ends a sentence (., !, ?, … — trailing closing quote/paren allowed). */
    private static boolean endsSentence(String word) {
        String w = word.replaceAll("[\"'”’)\\]]+$", "");
        if (w.isEmpty()) {
            return false;
        }
        char c = w.charAt(w.length() - 1);
        return c == '.' || c == '!' || c == '?' || c == '…';
    }

    /** Append "…" when truncated; enforce the safety char cap. */
    private static String capChars(String s, boolean truncated) {
        String result = s;
        if (result.length() > MAX_CHARS) {
            result = result.substring(0, MAX_CHARS);
            int sp = result.lastIndexOf(' ');
            if (sp > 0) {
                result = result.substring(0, sp);
            }
            return result + "…";
        }
        return truncated ? result + "…" : result;
    }
```
(Remove the old 30-word/180-char loop and the old degenerate single-word block — `capChars` now handles the char safety. Keep the `null`/blank guard, the flexmark parse, and the `replaceAll("\\s+", " ").trim()` normalize exactly as they are.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=ExcerptDeriverTest`
Expected: PASS — all (the 4 truncation tests + the kept markdown/blank/image tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/ExcerptDeriver.java src/test/java/hu/deposoft/webshop/application/blog/ExcerptDeriverTest.java
git commit -m "feat(blog): cut excerpt at first sentence end in 30-50 word range (else 50 + ellipsis)"
```

---

## Task 2: Empty right sidebar (1/3) on the blog list

**Files:**
- Modify: `src/main/resources/templates/blog/list.html`
- Modify: `src/main/resources/static/css/site.css`
- Test: `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java`

**Interfaces:**
- Produces: `/blog` and `/blog/kategoria/{slug}` markup with a `.nk-blog-layout` grid → `.nk-blog-content` (2/3, holds the existing `.nk-blog-list` + `.pagin`) + empty `<aside class="nk-blog-sidebar">` (1/3).

**Current state:** `list.html` `<main class="wrap">` contains: breadcrumb `<nav class="crumbs">`, the `title-row` header, `<div class="nk-blog-list">…cards…</div>`, then `<nav class="pagin">`. Same template serves `/blog` and category.

- [ ] **Step 1: Write the failing test**

Add to `BlogControllerIT.java` (mirror the file's setup; seed a published post + category for the category case as the existing tests do):
```java
@Test
void blogPageHasContentAndEmptySidebarLayout() throws Exception {
    mvc().perform(get("/blog"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-layout")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-content")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-sidebar")));
}

@Test
void categoryPageAlsoHasSidebarLayout() throws Exception {
    var cat = hu.deposoft.webshop.domain.blog.BlogCategory.create("Hír", "hir-side");
    categories.save(cat);
    var p = hu.deposoft.webshop.domain.blog.BlogPost.create("hir-side-post", "Hír oldalsáv");
    p.setBodyMarkdown("tartalom");
    p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    p.setCategories(java.util.Set.of(cat));
    posts.save(p);
    mvc().perform(get("/blog/kategoria/hir-side"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-sidebar")));
}
```
> Implementer note: use the file's `mvc()` helper + autowired `posts`/`categories` (mirror existing tests like `categoryPaginationTrailingSlashResolves`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: the two new tests FAIL — `nk-blog-layout`/`nk-blog-sidebar` not in the current markup.

- [ ] **Step 3: Restructure `list.html` — wrap list + pager in a 2-col layout**

Keep the breadcrumb `<nav class="crumbs">` and the `<div class="title-row …">…</div>` exactly where they are (full-width, top). Then wrap the existing `<div class="nk-blog-list">…</div>` AND the `<nav class="pagin">…</nav>` in:
```html
<div class="nk-blog-layout">
    <div class="nk-blog-content">
        <!-- existing <div class="nk-blog-list"> … </div> stays here unchanged -->
        <!-- existing <nav class="pagin"> … </nav> stays here unchanged -->
    </div>
    <aside class="nk-blog-sidebar" aria-label="Oldalsáv"></aside>
</div>
```
i.e. move the `nk-blog-list` block and the `pagin` nav inside `.nk-blog-content`, and add the empty `<aside>` as its sibling inside `.nk-blog-layout`. Do not change the card markup or the pager links.

- [ ] **Step 4: Add the layout CSS to `site.css`**

Append (near the existing `.nk-blog-list` rules; use the project's space token):
```css
/* Blog page 2-col layout: content + empty sidebar */
.nk-blog-layout { display: grid; grid-template-columns: 2fr 1fr; gap: var(--nk-space-6); align-items: start; margin-top: var(--nk-space-6); }
.nk-blog-content { min-width: 0; }   /* allow the content column (with flex cards) to shrink */
@media (max-width: 900px) {
    .nk-blog-layout { grid-template-columns: 1fr; }
    .nk-blog-sidebar { display: none; }   /* empty for now — hide on narrow screens */
}
```
> Implementer note: `.nk-blog-content { min-width: 0 }` matters — without it the grid track won't shrink below the flex cards' content size. The empty `.nk-blog-sidebar` needs no rule of its own (the grid reserves its 1/3 track); add one only if you want a visible divider later.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=BlogControllerIT`
Expected: PASS — both new layout tests + all existing blog web tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/blog/list.html src/main/resources/static/css/site.css src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java
git commit -m "feat(blog): 2-col blog page — content 2/3 + empty right sidebar 1/3"
```

---

## Final verification

- [ ] **Focused suites:** `mvn test -Dtest=ExcerptDeriverTest,BlogControllerIT,BlogQueryServiceIT` → all green.
- [ ] **Visual check (controller runs the app on a free port with `--webshop.storage-dir` at the real `data/uploads`, headless-screenshots `/blog`):** the post list occupies the left 2/3, an empty 1/3 column sits on the right; excerpts end on a full sentence (no "…") where a sentence break falls in the 30–50 word window, otherwise "…" at 50 words; below 900px the list is full-width (sidebar hidden).

---

## Self-Review Notes

- **Spec coverage:** Feature A §Viselkedés (sentence end ≥30 ≤50 → no "…"; else 50 + "…"; ≤30 whole; null→""; drop 180 cap, keep 500 safety) → Task 1 logic + 4 tests. Feature B (header full-width top; content 2/3 + empty aside 1/3; both /blog + category; <900px single col) → Task 2 markup + CSS + 2 tests. Manual-excerpt-wins unchanged (BlogQueryService untouched).
- **Placeholder scan:** none — full code in every step. The two implementer notes are verify/clarify, not missing content.
- **Type consistency:** `derive(String):String` unchanged; `endsSentence`/`capChars` private helpers defined in Task 1 and used only there. CSS class names `nk-blog-layout`/`nk-blog-content`/`nk-blog-sidebar` match across Task 2 markup, CSS, and the IT assertions.
- **Note:** the existing `nk-blog-card` (square image at `flex: 0 0 50%`) now renders inside the 2/3 column — the square becomes ~1/3 of the page; acceptable per spec, confirmed at the visual check.
