# Blog Sidebar — Phase 1 (Public, Seeded) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render a populated right sidebar on both the blog list (`/blog`,
`/blog/kategoria/{slug}`) and the article page (`/{slug}`), driven by a new
admin-ready `sidebar_block` table seeded with today's content.

**Architecture:** A generic `sidebar_block` table (type + display_order +
enabled + JSON content) is read by a thin `SidebarQueryService` that parses each
block's JSON into typed records and attaches the live (not-hidden, alphabetical)
category list to the CATEGORIES block. `BlogController` injects the resulting
`SidebarView` for all blog endpoints via `@ModelAttribute`; a shared Thymeleaf
fragment renders each block with `th:switch`. No admin UI in this phase — blocks
are seeded by the migration. Phase 2 adds admin management.

**Tech Stack:** Java 21, Spring Boot 3.x/4.x, Spring Data JPA, Flyway,
PostgreSQL, Thymeleaf, Jackson 3 (`tools.jackson.databind.ObjectMapper`), JUnit
+ Testcontainers.

## Global Constraints

- Public blog HTML stays session-independent and cacheable: no user-/cart-
  dependent data in the sidebar (CLAUDE.md #2).
- Business logic only in the service layer; controller and templates are thin
  (CLAUDE.md #1).
- Money/stock rules N/A here.
- Code, comments, commits in English; user-facing strings Hungarian
  (CLAUDE.md #10).
- Enums persisted as `@Enumerated(EnumType.STRING)` into a `TEXT` column (mirror
  `BlogPost.status`). No `jsonb`/Hibernate-JSON — `content` is a `TEXT` column
  holding JSON, parsed with the injected `ObjectMapper`.
- Migrations are forward-only; next version is **V24** (latest is V23).
- Slugs are stable (CLAUDE.md #7) — the CATEGORIES block links to existing
  `/blog/kategoria/{slug}`.
- Run Java build/tests with Maven: `mvn -q -Dtest=... test` for a single IT,
  `mvn -q verify` for the full suite (Testcontainers needs Docker).

---

## File Structure

- Create `src/main/resources/db/migration/V24__sidebar_blocks.sql` — table,
  `blog_category.sidebar_hidden`, seed.
- Create `src/main/java/hu/deposoft/webshop/domain/sidebar/BlockType.java` — enum.
- Create `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlock.java` — entity.
- Create `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepository.java`.
- Modify `src/main/java/hu/deposoft/webshop/domain/blog/BlogCategory.java` — add `sidebarHidden`.
- Modify `src/main/java/hu/deposoft/webshop/domain/blog/BlogCategoryRepository.java` — finder.
- Create `src/main/java/hu/deposoft/webshop/application/sidebar/SidebarQueryService.java` — view + records.
- Modify `src/main/java/hu/deposoft/webshop/web/BlogController.java` — `@ModelAttribute`.
- Create `src/main/resources/templates/fragments/blog.html` — `sidebar` fragment.
- Modify `src/main/resources/templates/blog/post.html` — wrap in grid.
- Modify `src/main/resources/templates/blog/list.html` — fragment in aside.
- Modify `src/main/resources/static/css/site.css` — sidebar styles.
- Create `src/main/resources/static/design/assets/blog/author-placeholder.svg`,
  `cta-placeholder.svg`.
- Create `src/test/java/hu/deposoft/webshop/application/sidebar/SidebarQueryServiceIT.java`.
- Modify `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java` — sidebar render tests.

---

## Task 1: `sidebar_block` schema + entity + repository + category hide flag (seeded)

**Files:**
- Create: `src/main/resources/db/migration/V24__sidebar_blocks.sql`
- Create: `src/main/java/hu/deposoft/webshop/domain/sidebar/BlockType.java`
- Create: `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlock.java`
- Create: `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepository.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/blog/BlogCategory.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/blog/BlogCategoryRepository.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepositoryIT.java`

**Interfaces:**
- Produces:
  - `enum BlockType { AUTHOR, CATEGORIES, CTA, CONTACT, SOCIAL }`
  - `SidebarBlock` entity: getters `getId()`, `getBlockType()`,
    `getDisplayOrder()`, `isEnabled()`, `getContent()`; setters
    `setEnabled(boolean)`, `setDisplayOrder(int)`, `setContent(String)`.
  - `SidebarBlockRepository.findByEnabledTrueOrderByDisplayOrderAsc() -> List<SidebarBlock>`
  - `BlogCategory.isSidebarHidden()`, `BlogCategory.setSidebarHidden(boolean)`
  - `BlogCategoryRepository.findBySidebarHiddenFalseOrderByNameAsc() -> List<BlogCategory>`

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V24__sidebar_blocks.sql`:

```sql
-- Blog sidebar (T-BLOG-SIDEBAR): admin-ready, generic block model.
ALTER TABLE blog_category
    ADD COLUMN sidebar_hidden BOOLEAN NOT NULL DEFAULT false;
-- keep the catch-all out of the public sidebar
UPDATE blog_category SET sidebar_hidden = true WHERE slug = 'uncategorized';

CREATE TABLE sidebar_block (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    block_type    TEXT    NOT NULL,
    display_order INT     NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT true,
    content       TEXT    NOT NULL
);
CREATE INDEX ix_sidebar_block_order ON sidebar_block (display_order);

INSERT INTO sidebar_block (block_type, display_order, enabled, content) VALUES
 ('AUTHOR', 1, true,
  '{"name":"Szendrődi Enikő","bio":"Szendrődi Enikő vagyok, a Nemiskacat tulajdonosa, hobbibútorfestő.","photoUrl":"/design/assets/blog/author-placeholder.svg"}'),
 ('CATEGORIES', 2, true,
  '{"title":"Kategóriák"}'),
 ('CTA', 3, true,
  '{"title":"Már tudod is mit fogsz festeni?","buttonLabel":"Irány a webshop","url":"/termekkategoria/kretafestek-annie-sloan-chalk-paint/","imageUrl":"/design/assets/blog/cta-placeholder.svg"}'),
 ('CONTACT', 4, true,
  '{"title":"Elérhetőség","phone":"+36 20 269 9113","email":"nemiskacat@nemiskacat.hu","address":"Biatorbágy, Csillag u. 7."}'),
 ('SOCIAL', 5, true,
  '{"title":"Kövess minket","links":[{"network":"facebook","url":"https://www.facebook.com/Nemiskacat"},{"network":"instagram","url":"https://www.instagram.com/nemiskacat/"},{"network":"youtube","url":"https://www.youtube.com/user/Nemiskacatponthu"}]}');
```

- [ ] **Step 2: Create the `BlockType` enum**

Create `src/main/java/hu/deposoft/webshop/domain/sidebar/BlockType.java`:

```java
package hu.deposoft.webshop.domain.sidebar;

public enum BlockType {
    AUTHOR,
    CATEGORIES,
    CTA,
    CONTACT,
    SOCIAL
}
```

- [ ] **Step 3: Create the `SidebarBlock` entity**

Create `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlock.java`:

```java
package hu.deposoft.webshop.domain.sidebar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A single managed sidebar block. `content` is type-specific JSON (see BlockType). */
@Entity
@Table(name = "sidebar_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SidebarBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false)
    private BlockType blockType;

    @Setter
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Setter
    @Column(nullable = false)
    private boolean enabled = true;

    @Setter
    @Column(nullable = false)
    private String content;

    public static SidebarBlock create(BlockType type, int displayOrder, String content) {
        SidebarBlock b = new SidebarBlock();
        b.blockType = type;
        b.displayOrder = displayOrder;
        b.enabled = true;
        b.content = content;
        return b;
    }
}
```

- [ ] **Step 4: Create the repository**

Create `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepository.java`:

```java
package hu.deposoft.webshop.domain.sidebar;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SidebarBlockRepository extends JpaRepository<SidebarBlock, Long> {
    List<SidebarBlock> findByEnabledTrueOrderByDisplayOrderAsc();
}
```

- [ ] **Step 5: Add `sidebarHidden` to `BlogCategory`**

In `src/main/java/hu/deposoft/webshop/domain/blog/BlogCategory.java`, add the
import and field (after the `slug` field). Add `import lombok.Setter;` is
already present (used on `name`). Add:

```java
    @Setter
    @Column(name = "sidebar_hidden", nullable = false)
    private boolean sidebarHidden = false;
```

Leave the `create(name, slug)` factory unchanged (defaults to visible).

- [ ] **Step 6: Add the category finder**

In `src/main/java/hu/deposoft/webshop/domain/blog/BlogCategoryRepository.java`,
add:

```java
    List<BlogCategory> findBySidebarHiddenFalseOrderByNameAsc();
```

- [ ] **Step 7: Write the repository IT**

Create `src/test/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepositoryIT.java`:

```java
package hu.deposoft.webshop.domain.sidebar;

import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarBlockRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired SidebarBlockRepository blocks;
    @Autowired BlogCategoryRepository categories;

    @Test
    void seedExposesFiveEnabledBlocksInOrder() {
        List<SidebarBlock> enabled = blocks.findByEnabledTrueOrderByDisplayOrderAsc();
        assertThat(enabled).extracting(SidebarBlock::getBlockType)
                .containsExactly(BlockType.AUTHOR, BlockType.CATEGORIES,
                        BlockType.CTA, BlockType.CONTACT, BlockType.SOCIAL);
    }

    @Test
    void disabledBlockIsExcluded() {
        SidebarBlock social = blocks.findByEnabledTrueOrderByDisplayOrderAsc()
                .stream().filter(b -> b.getBlockType() == BlockType.SOCIAL).findFirst().orElseThrow();
        social.setEnabled(false);
        blocks.save(social);
        assertThat(blocks.findByEnabledTrueOrderByDisplayOrderAsc())
                .extracting(SidebarBlock::getBlockType).doesNotContain(BlockType.SOCIAL);
    }

    @Test
    void categoryFinderExcludesHiddenAndSortsByName() {
        categories.save(BlogCategory.create("Zebra", "zebra"));
        BlogCategory hidden = BlogCategory.create("Alma", "alma");
        hidden.setSidebarHidden(true);
        categories.save(hidden);
        categories.save(BlogCategory.create("Béka", "beka"));

        List<BlogCategory> visible = categories.findBySidebarHiddenFalseOrderByNameAsc();
        assertThat(visible).extracting(BlogCategory::getName)
                .containsSubsequence("Béka", "Zebra")   // alphabetical
                .doesNotContain("Alma");                  // hidden excluded
    }
}
```

- [ ] **Step 8: Run the IT**

Run: `mvn -q -Dtest=SidebarBlockRepositoryIT test`
Expected: PASS (3 tests). Flyway runs V24 against the Testcontainers DB, so the
seed is present.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/V24__sidebar_blocks.sql \
        src/main/java/hu/deposoft/webshop/domain/sidebar/ \
        src/main/java/hu/deposoft/webshop/domain/blog/BlogCategory.java \
        src/main/java/hu/deposoft/webshop/domain/blog/BlogCategoryRepository.java \
        src/test/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepositoryIT.java
git commit -m "feat(blog): sidebar_block table + category sidebar_hidden flag (seeded)"
```

---

## Task 2: `SidebarQueryService` — parse blocks + attach category list

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/sidebar/SidebarQueryService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/sidebar/SidebarQueryServiceIT.java`

**Interfaces:**
- Consumes: `SidebarBlockRepository`, `BlogCategoryRepository`,
  `BlogQueryService.CategoryRef`, `ObjectMapper`.
- Produces (all nested in `SidebarQueryService`):
  - `SidebarView(List<SidebarBlockView> blocks)`
  - `SidebarBlockView(String type, AuthorContent author, CategoriesContent categories, CtaContent cta, ContactContent contact, SocialContent social)`
  - `AuthorContent(String name, String bio, String photoUrl)`
  - `CategoriesContent(String title, List<BlogQueryService.CategoryRef> items)`
  - `CtaContent(String title, String buttonLabel, String url, String imageUrl)`
  - `ContactContent(String title, String phone, String email, String address)`
  - `SocialLink(String network, String url)`
  - `SocialContent(String title, List<SocialLink> links)`
  - method `SidebarView sidebar()`

- [ ] **Step 1: Write the failing service IT**

Create `src/test/java/hu/deposoft/webshop/application/sidebar/SidebarQueryServiceIT.java`:

```java
package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.sidebar.SidebarQueryService.SidebarBlockView;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.SidebarView;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarQueryServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired SidebarQueryService sidebar;
    @Autowired BlogCategoryRepository categories;

    @Test
    void blocksAreOrderedAndAuthorParsed() {
        SidebarView view = sidebar.sidebar();
        assertThat(view.blocks()).extracting(SidebarBlockView::type)
                .containsExactly("AUTHOR", "CATEGORIES", "CTA", "CONTACT", "SOCIAL");
        SidebarBlockView author = view.blocks().get(0);
        assertThat(author.author().name()).isEqualTo("Szendrődi Enikő");
        assertThat(author.author().bio()).contains("hobbibútorfestő");
    }

    @Test
    void categoriesBlockAttachesVisibleAlphabeticalCategories() {
        categories.save(BlogCategory.create("Zebra", "zebra"));
        BlogCategory hidden = BlogCategory.create("Alma", "alma");
        hidden.setSidebarHidden(true);
        categories.save(hidden);
        categories.save(BlogCategory.create("Béka", "beka"));

        SidebarBlockView cats = sidebar.sidebar().blocks().stream()
                .filter(b -> b.type().equals("CATEGORIES")).findFirst().orElseThrow();
        assertThat(cats.categories().title()).isEqualTo("Kategóriák");
        assertThat(cats.categories().items())
                .extracting(c -> c.name())
                .containsSubsequence("Béka", "Zebra")
                .doesNotContain("Alma");
    }

    @Test
    void socialAndContactParsed() {
        SidebarView view = sidebar.sidebar();
        SidebarBlockView social = view.blocks().stream()
                .filter(b -> b.type().equals("SOCIAL")).findFirst().orElseThrow();
        assertThat(social.social().links()).extracting(l -> l.network())
                .contains("facebook", "instagram", "youtube");
        SidebarBlockView contact = view.blocks().stream()
                .filter(b -> b.type().equals("CONTACT")).findFirst().orElseThrow();
        assertThat(contact.contact().phone()).isEqualTo("+36 20 269 9113");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -Dtest=SidebarQueryServiceIT test`
Expected: FAIL — `SidebarQueryService` does not exist (compilation error).

- [ ] **Step 3: Implement the service**

Create `src/main/java/hu/deposoft/webshop/application/sidebar/SidebarQueryService.java`:

```java
package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.blog.BlogQueryService.CategoryRef;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.sidebar.SidebarBlock;
import hu.deposoft.webshop.domain.sidebar.SidebarBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SidebarQueryService {

    private final SidebarBlockRepository blocks;
    private final BlogCategoryRepository categories;
    private final ObjectMapper objectMapper;

    public record AuthorContent(String name, String bio, String photoUrl) {}
    public record CategoriesContent(String title, List<CategoryRef> items) {}
    public record CtaContent(String title, String buttonLabel, String url, String imageUrl) {}
    public record ContactContent(String title, String phone, String email, String address) {}
    public record SocialLink(String network, String url) {}
    public record SocialContent(String title, List<SocialLink> links) {}

    public record SidebarBlockView(String type, AuthorContent author,
                                   CategoriesContent categories, CtaContent cta,
                                   ContactContent contact, SocialContent social) {}

    public record SidebarView(List<SidebarBlockView> blocks) {}

    // JSON shape for the CATEGORIES block (the list itself is loaded live).
    private record CategoriesMeta(String title) {}

    public SidebarView sidebar() {
        List<SidebarBlockView> views = blocks.findByEnabledTrueOrderByDisplayOrderAsc()
                .stream().map(this::toView).toList();
        return new SidebarView(views);
    }

    private SidebarBlockView toView(SidebarBlock b) {
        String json = b.getContent();
        return switch (b.getBlockType()) {
            case AUTHOR -> new SidebarBlockView("AUTHOR",
                    objectMapper.readValue(json, AuthorContent.class), null, null, null, null);
            case CATEGORIES -> {
                CategoriesMeta meta = objectMapper.readValue(json, CategoriesMeta.class);
                List<CategoryRef> items = categories.findBySidebarHiddenFalseOrderByNameAsc()
                        .stream().map(c -> new CategoryRef(c.getName(), c.getSlug())).toList();
                yield new SidebarBlockView("CATEGORIES", null,
                        new CategoriesContent(meta.title(), items), null, null, null);
            }
            case CTA -> new SidebarBlockView("CTA", null, null,
                    objectMapper.readValue(json, CtaContent.class), null, null);
            case CONTACT -> new SidebarBlockView("CONTACT", null, null, null,
                    objectMapper.readValue(json, ContactContent.class), null);
            case SOCIAL -> new SidebarBlockView("SOCIAL", null, null, null, null,
                    objectMapper.readValue(json, SocialContent.class));
        };
    }
}
```

Note: `CategoryRef` is `BlogQueryService.CategoryRef` (existing record). Jackson 3
`readValue(String, Class)` throws unchecked, so no checked-exception handling is
needed (matches `BlogQueryService`'s existing `writeValueAsString` usage).

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -Dtest=SidebarQueryServiceIT test`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/sidebar/SidebarQueryService.java \
        src/test/java/hu/deposoft/webshop/application/sidebar/SidebarQueryServiceIT.java
git commit -m "feat(blog): SidebarQueryService parses blocks + attaches category list"
```

---

## Task 3: Wire into BlogController + templates + CSS + assets

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/web/BlogController.java`
- Create: `src/main/resources/templates/fragments/blog.html`
- Modify: `src/main/resources/templates/blog/post.html`
- Modify: `src/main/resources/templates/blog/list.html`
- Modify: `src/main/resources/static/css/site.css`
- Create: `src/main/resources/static/design/assets/blog/author-placeholder.svg`
- Create: `src/main/resources/static/design/assets/blog/cta-placeholder.svg`
- Test: `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java` (add tests)

**Interfaces:**
- Consumes: `SidebarQueryService.sidebar()` and the `SidebarView` record tree.
- Produces: a `sidebar` model attribute on every BlogController response and a
  rendered `fragments/blog :: sidebar`.

- [ ] **Step 1: Inject the sidebar into `BlogController`**

In `src/main/java/hu/deposoft/webshop/web/BlogController.java`:

Add imports:
```java
import hu.deposoft.webshop.application.sidebar.SidebarQueryService;
import org.springframework.web.bind.annotation.ModelAttribute;
```
Add a field (alongside `private final BlogQueryService blog;`):
```java
    private final SidebarQueryService sidebarQuery;
```
Add this method inside the class (e.g. after the constructor-injected fields,
before `list`):
```java
    /** Available to every blog view; the sidebar fragment reads ${sidebar}. */
    @ModelAttribute("sidebar")
    public SidebarQueryService.SidebarView sidebar() {
        return sidebarQuery.sidebar();
    }
```

- [ ] **Step 2: Create the sidebar fragment**

Create `src/main/resources/templates/fragments/blog.html`:

```html
<!DOCTYPE html>
<html lang="hu" xmlns:th="http://www.thymeleaf.org">
<body>
<aside th:fragment="sidebar" class="nk-blog-sidebar" aria-label="Oldalsáv">
    <th:block th:if="${sidebar != null}" th:each="block : ${sidebar.blocks}">

        <!-- AUTHOR -->
        <section th:if="${block.type == 'AUTHOR'}" class="nk-sb-block nk-sb-author">
            <img class="nk-sb-author__photo" th:src="${block.author.photoUrl}"
                 th:alt="${block.author.name}" width="96" height="96">
            <h2 class="nk-sb-author__name" th:text="${block.author.name}">Név</h2>
            <p class="nk-sb-author__bio" th:text="${block.author.bio}">bio</p>
        </section>

        <!-- CATEGORIES -->
        <section th:if="${block.type == 'CATEGORIES' and !block.categories.items.isEmpty()}"
                 class="nk-sb-block nk-sb-cats">
            <h2 class="nk-sb-block__title" th:text="${block.categories.title}">Kategóriák</h2>
            <ul class="nk-sb-cats__list">
                <li th:each="c : ${block.categories.items}">
                    <a th:href="@{'/blog/kategoria/' + ${c.slug}}" th:text="${c.name}">Kategória</a>
                </li>
            </ul>
        </section>

        <!-- CTA -->
        <section th:if="${block.type == 'CTA'}" class="nk-sb-block nk-sb-cta">
            <h2 class="nk-sb-block__title" th:text="${block.cta.title}">CTA</h2>
            <img th:if="${block.cta.imageUrl}" class="nk-sb-cta__img"
                 th:src="${block.cta.imageUrl}" alt="" loading="lazy">
            <a class="nk-sb-cta__btn" th:href="${block.cta.url}"
               th:text="${block.cta.buttonLabel}">Webshop</a>
        </section>

        <!-- CONTACT -->
        <section th:if="${block.type == 'CONTACT'}" class="nk-sb-block nk-sb-contact">
            <h2 class="nk-sb-block__title" th:text="${block.contact.title}">Elérhetőség</h2>
            <ul class="nk-sb-contact__list">
                <li th:if="${block.contact.phone}">
                    <a th:href="'tel:' + ${block.contact.phone}" th:text="${block.contact.phone}">tel</a>
                </li>
                <li th:if="${block.contact.email}">
                    <a th:href="'mailto:' + ${block.contact.email}" th:text="${block.contact.email}">email</a>
                </li>
                <li th:if="${block.contact.address}" th:text="${block.contact.address}">cím</li>
            </ul>
        </section>

        <!-- SOCIAL -->
        <section th:if="${block.type == 'SOCIAL'}" class="nk-sb-block nk-sb-social">
            <h2 class="nk-sb-block__title" th:text="${block.social.title}">Kövess minket</h2>
            <div class="nk-sb-social__links">
                <a th:each="l : ${block.social.links}" th:href="${l.url}"
                   target="_blank" rel="noopener" th:title="${l.network}"
                   th:aria-label="${l.network}" class="nk-sb-social__link">
                    <svg th:if="${l.network == 'facebook'}" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                        <path fill="currentColor" d="M13 22v-8h2.7l.4-3H13V9.1c0-.9.3-1.5 1.6-1.5H16V5c-.3 0-1.2-.1-2.2-.1-2.2 0-3.8 1.4-3.8 3.9V11H7.3v3H10v8h3z"/>
                    </svg>
                    <svg th:if="${l.network == 'instagram'}" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                        <path fill="currentColor" d="M12 7.4A4.6 4.6 0 1 0 12 16.6 4.6 4.6 0 0 0 12 7.4zm0 7.6A3 3 0 1 1 12 9a3 3 0 0 1 0 6zm4.8-7.8a1.1 1.1 0 1 1-1.1-1.1 1.1 1.1 0 0 1 1.1 1.1zM20 7.6c-.1-1.4-.4-2.6-1.4-3.6S16.4 2.7 15 2.6C13.6 2.5 10.4 2.5 9 2.6c-1.4.1-2.6.4-3.6 1.4S3.7 5.6 3.6 7c-.1 1.4-.1 4.6 0 6 .1 1.4.4 2.6 1.4 3.6s2.2 1.3 3.6 1.4c1.4.1 4.6.1 6 0 1.4-.1 2.6-.4 3.6-1.4s1.3-2.2 1.4-3.6c.1-1.4.1-4.6 0-6zM18 14.6a3 3 0 0 1-1.7 1.7c-1.2.5-3.9.4-5.1.4s-4 .1-5.1-.4a3 3 0 0 1-1.7-1.7c-.5-1.2-.4-3.9-.4-5.1s-.1-4 .4-5.1A3 3 0 0 1 6.9 4.7c1.2-.5 3.9-.4 5.1-.4s4-.1 5.1.4A3 3 0 0 1 18.8 6.4c.5 1.2.4 3.9.4 5.1s.1 4-.4 5.1z"/>
                    </svg>
                    <svg th:if="${l.network == 'youtube'}" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                        <path fill="currentColor" d="M23 7.5a3 3 0 0 0-2.1-2.1C19 4.9 12 4.9 12 4.9s-7 0-8.9.5A3 3 0 0 0 1 7.5C.5 9.4.5 12 .5 12s0 2.6.5 4.5a3 3 0 0 0 2.1 2.1c1.9.5 8.9.5 8.9.5s7 0 8.9-.5A3 3 0 0 0 23 16.5c.5-1.9.5-4.5.5-4.5s0-2.6-.5-4.5zM9.8 15.3V8.7l5.7 3.3-5.7 3.3z"/>
                    </svg>
                    <span class="nk-sb-social__label" th:text="${l.network}">net</span>
                </a>
            </div>
        </section>

    </th:block>
</aside>
</body>
</html>
```

(`th:if` per type is used instead of `th:switch` so each `<section>` keeps its
own `th:if` guards — e.g. CATEGORIES also checks the list is non-empty.)

- [ ] **Step 3: Wrap `post.html` in the layout grid**

In `src/main/resources/templates/blog/post.html`, replace the block from
`<article class="nk-blog-article">` through the closing `</section>` of
`nk-blog-recommended` so the article and recommended section sit inside
`.nk-blog-content`, with the sidebar fragment beside them. The new `<main>` body
(breadcrumb unchanged above it) becomes:

```html
    <div class="nk-blog-layout">
        <div class="nk-blog-content">
            <article class="nk-blog-article">
                <header class="nk-blog-article__header">
                    <div class="nk-blog-article__cats" th:if="${!post.categories.isEmpty()}">
                        <a th:each="c : ${post.categories}"
                           th:href="@{'/blog/kategoria/' + ${c.slug}}"
                           th:text="${c.name}"
                           class="nk-blog-article__cat">kategória</a>
                    </div>
                    <h1 class="nk-blog-article__title" th:text="${post.title}">Cím</h1>
                    <p class="nk-blog-article__date"
                       th:if="${post.publishedDate}"
                       th:text="${#temporals.format(post.publishedDate, 'yyyy. MM. dd.')}">dátum</p>
                </header>

                <div class="nk-blog-article__body" th:utext="${post.bodyHtml}">törzs</div>
            </article>

            <section class="nk-blog-recommended" th:if="${!post.recommendedProducts.isEmpty()}">
                <h2>Ajánlott termékek</h2>
                <div class="nk-prod-grid">
                    <th:block th:each="item : ${post.recommendedProducts}">
                        <div th:replace="~{fragments/components :: productCard(${item})}"></div>
                    </th:block>
                </div>
            </section>
        </div>
        <th:block th:replace="~{fragments/blog :: sidebar}"></th:block>
    </div>
```

- [ ] **Step 4: Put the fragment in `list.html`'s aside**

In `src/main/resources/templates/blog/list.html`, replace the empty aside
(currently `<aside class="nk-blog-sidebar" aria-label="Oldalsáv"></aside>`) with:

```html
        <th:block th:replace="~{fragments/blog :: sidebar}"></th:block>
```

(The fragment's root `<aside class="nk-blog-sidebar">` becomes the second grid
child of `.nk-blog-layout`, replacing the empty one.)

- [ ] **Step 5: Add the placeholder assets**

Create `src/main/resources/static/design/assets/blog/author-placeholder.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96" width="96" height="96" role="img" aria-label="Szerző">
  <circle cx="48" cy="48" r="48" fill="#efe7df"/>
  <circle cx="48" cy="38" r="16" fill="#cbb8a6"/>
  <path d="M16 84a32 32 0 0 1 64 0z" fill="#cbb8a6"/>
</svg>
```

Create `src/main/resources/static/design/assets/blog/cta-placeholder.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 180" width="320" height="180" role="img" aria-label="Webshop">
  <rect width="320" height="180" fill="#efe7df"/>
  <rect x="120" y="58" width="80" height="64" rx="6" fill="#cbb8a6"/>
  <rect x="138" y="44" width="44" height="18" rx="4" fill="#b39c86"/>
</svg>
```

- [ ] **Step 6: Add sidebar CSS and fix the responsive rule**

In `src/main/resources/static/css/site.css`, replace the existing media-query
rule (currently `.nk-blog-sidebar { display: none; ... }`) so the sidebar stacks
below the content on narrow screens instead of disappearing, and append the
block styles. Replace:

```css
@media (max-width: 900px) {
    .nk-blog-layout { grid-template-columns: 1fr; }
    .nk-blog-sidebar { display: none; }   /* empty for now — hide on narrow screens */
}
```

with:

```css
@media (max-width: 900px) {
    .nk-blog-layout { grid-template-columns: 1fr; }
}

/* ── Blog sidebar blocks ──────────────────────────────────────────────────── */
.nk-blog-sidebar { display: flex; flex-direction: column; gap: var(--nk-space-6); }
.nk-sb-block { background: #fff; border: 1px solid var(--nk-border, #ece6df);
    border-radius: var(--nk-radius-md); padding: var(--nk-space-5); }
.nk-sb-block__title { font-size: 1.05rem; margin: 0 0 var(--nk-space-3); }
.nk-sb-author { text-align: center; }
.nk-sb-author__photo { width: 96px; height: 96px; border-radius: 50%; object-fit: cover; }
.nk-sb-author__name { font-size: 1.1rem; margin: var(--nk-space-3) 0 var(--nk-space-2); }
.nk-sb-author__bio { color: var(--nk-muted, #6b6258); font-size: .92rem; margin: 0; }
.nk-sb-cats__list, .nk-sb-contact__list { list-style: none; margin: 0; padding: 0;
    display: flex; flex-direction: column; gap: var(--nk-space-2); }
.nk-sb-cats__list a { text-decoration: none; }
.nk-sb-cta { text-align: center; }
.nk-sb-cta__img { width: 100%; height: auto; border-radius: var(--nk-radius-sm); margin-bottom: var(--nk-space-3); }
.nk-sb-cta__btn { display: inline-block; padding: 10px 18px; border-radius: var(--nk-radius-sm);
    background: var(--nk-accent, #2f2a24); color: #fff; text-decoration: none; }
.nk-sb-social__links { display: flex; gap: var(--nk-space-3); }
.nk-sb-social__link { display: inline-flex; align-items: center; gap: 6px; color: inherit; text-decoration: none; }
.nk-sb-social__label { font-size: .85rem; }
```

(If a referenced CSS variable is undefined in this project the fallback in the
`var(...)` keeps it working; leave them.)

- [ ] **Step 7: Add the controller render tests**

In `src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java`, add these
tests (the class already has `mvc()`, `posts`, `categories`, the container, and
the `get`/`content`/`status`/`containsString`/`not` static imports):

```java
    @Test
    void blogListRendersSidebarWithVisibleCategoriesOnly() throws Exception {
        categories.save(hu.deposoft.webshop.domain.blog.BlogCategory.create("Festéktan", "festektan"));
        var hidden = hu.deposoft.webshop.domain.blog.BlogCategory.create("Rejtett kategória", "rejtett-kat");
        hidden.setSidebarHidden(true);
        categories.save(hidden);

        mvc().perform(get("/blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nk-blog-sidebar")))
                .andExpect(content().string(containsString("Szendrődi Enikő")))     // author block
                .andExpect(content().string(containsString("Festéktan")))           // visible category
                .andExpect(content().string(not(containsString("Rejtett kategória")))); // hidden absent
    }

    @Test
    void articlePageRendersSidebar() throws Exception {
        var p = hu.deposoft.webshop.domain.blog.BlogPost.create("oldalsav-cikk", "Oldalsáv cikk");
        p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        posts.save(p);

        mvc().perform(get("/oldalsav-cikk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nk-blog-sidebar")))
                .andExpect(content().string(containsString("Kövess minket")));       // social block
    }
```

- [ ] **Step 8: Build + run the affected ITs**

Run: `mvn -q -Dtest=BlogControllerIT,SidebarQueryServiceIT,SidebarBlockRepositoryIT test`
Expected: PASS. If a template parse error occurs, the failure names the
`fragments/blog.html` line — fix the Thymeleaf expression and re-run.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/web/BlogController.java \
        src/main/resources/templates/fragments/blog.html \
        src/main/resources/templates/blog/post.html \
        src/main/resources/templates/blog/list.html \
        src/main/resources/static/css/site.css \
        src/main/resources/static/design/assets/blog/ \
        src/test/java/hu/deposoft/webshop/web/BlogControllerIT.java
git commit -m "feat(blog): render managed sidebar on list + article pages"
```

---

## Manual verification (after Task 3; human or browser-driven)

1. Build + run the app against the dev DB (Flyway applies V24). Open `/blog` and
   an article (`/ombre-butorfestes`).
2. Confirm the right sidebar shows: author bio, category list (alphabetical, no
   `Uncategorized`), CTA button linking to the webshop, contact details, and the
   three social links.
3. Confirm article body/recommended products are unchanged and the layout
   matches `/blog` (content `2fr` + sidebar `1fr`; sidebar stacks below on a
   narrow window).

---

## Self-Review

- **Spec coverage:** block model + seed (Task 1), per-type parse + live category
  list with hidden-exclusion (Task 2), controller injection + both templates +
  fragment + CSS + assets (Task 3), tests at each layer, manual verification.
  Phase 2 (admin) is a separate plan, per spec. All Phase 1 spec sections
  covered.
- **Placeholder scan:** every step has concrete code/SQL/commands; the author
  photo/CTA image are real placeholder SVG assets, not TODOs.
- **Type consistency:** `BlockType` values, `SidebarBlock` getters/setters,
  `findByEnabledTrueOrderByDisplayOrderAsc`,
  `findBySidebarHiddenFalseOrderByNameAsc`, the `SidebarView`/`SidebarBlockView`
  record tree, and the `block.type` string literals in the fragment match across
  Tasks 1–3. `CategoryRef` is reused from `BlogQueryService`.
- **Cacheability:** sidebar data is global (no session/cart), so blog HTML stays
  cacheable (CLAUDE.md #2).
- **Known latitude:** exact CSS variable names fall back via `var(...)`;
  inline-SVG glyph paths are conventional brand marks and can be refined.
