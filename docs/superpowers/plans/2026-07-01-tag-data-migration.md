# Tag Data Migration (Blog + Product) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate WordPress `post_tag` and WooCommerce `product_tag` into Postgres as per-entity tag tables, so blog posts and products carry tags — export → source record → schema → idempotent importer, for both entities.

**Architecture:** Two parallel vertical slices, each mirroring that entity's existing **category** handling. Blog tags are keyed by **slug** (like `blog_category`); product tags by **Woo term id** (like product `category`). Each slice: extend the Python export script, add source-record fields, add a tag table + join + entity + repo, and extend the importer to upsert tags idempotently and link them. No public-surface or admin change.

**Tech Stack:** Python 3 (export scripts, pytest); Java 21 / Spring Boot / Spring Data JPA / Flyway / PostgreSQL; JUnit + Testcontainers; Jackson 3.

## Global Constraints

- **Idempotency (CLAUDE.md #4):** importers upsert tags without duplication on re-run — blog by slug, product by Woo term id.
- **Slugs 1:1 from source, immutable (CLAUDE.md #7):** tag slugs are taken verbatim.
- **No public-render change:** this slice only adds data; `SidebarQueryService`, blog fragment, product pages untouched.
- **Migration versioning:** blog tags = **V25** (`V25__blog_tags.sql`), product tags = **V26** (`V26__product_tags.sql`). `main`'s latest is V24. The unmerged `instagram-feed` worktree holds an on-disk `V25__instagram_feed.sql` NOT applied to any DB; it will be renumbered when it merges (versions follow merge order) — do not let it block these.
- **Record constructor changes are breaking:** adding a component to `SourceBlogPost`/`SourceBlog`/`SourceProduct`/`SourceCatalog` breaks every `new ...(...)` call site. Add the new component **last** and fix all call sites (the compiler lists them) — pass `List.of()` where tags aren't provided.
- Enums `@Enumerated(EnumType.STRING)`; entities `@NoArgsConstructor(PROTECTED)` + Lombok `@Getter`; code/comments/commits English.
- Backend build/test: `mvn -q -Dskip.frontend=true -Dtest=<IT> test`. Python tests: `python3 -m pytest scripts/woo-export/<file> -q`.

---

## File Structure

Blog slice:
- Modify `scripts/woo-export/export-blog.py` (add `post_tag`), `scripts/woo-export/test_export_blog.py` (assert tags).
- Modify `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlog.java`, `SourceBlogPost.java`; create `SourceBlogTag.java`.
- Create `src/main/resources/db/migration/V25__blog_tags.sql`; `domain/blog/BlogTag.java`, `domain/blog/BlogTagRepository.java`; modify `domain/blog/BlogPost.java`.
- Modify `application/blog/BlogImporter.java`, `application/blog/BlogImportReport.java`; test `application/blog/BlogImporterIT.java`.

Product slice:
- Modify `scripts/woo-export/export.py`; create `scripts/woo-export/test_export.py`.
- Modify `integrations/woo/SourceCatalog.java`, `SourceProduct.java`; create `integrations/woo/SourceTag.java`.
- Create `src/main/resources/db/migration/V26__product_tags.sql`; `domain/catalog/ProductTag.java`, `domain/catalog/ProductTagRepository.java`; modify `domain/catalog/Product.java`.
- Modify `application/catalog/CatalogImporter.java`, `application/catalog/ImportReport.java`; test `application/catalog/CatalogImporterTest.java`.

---

## Task 1: Blog export — add `post_tag`

**Files:**
- Modify: `scripts/woo-export/export-blog.py`
- Test: `scripts/woo-export/test_export_blog.py`

**Interfaces:**
- Produces: JSON with a top-level `"tags"` array (`{name, slug}`) and each post carrying `"tagSlugs"`.

- [ ] **Step 1: Update the test to expect tags**

In `scripts/woo-export/test_export_blog.py`, extend `fake_rows` and assertions. Replace the tail of `fake_rows` and the assertions:

```python
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
        if "taxonomy = 'category'" in q:
            return [{"post_id": 7, "slug": "hirek"}]
        if "taxonomy = 'post_tag'" in q and "term_relationships" not in q:
            return [{"name": "Vintage", "slug": "vintage"}]
        if "taxonomy = 'post_tag'" in q:
            return [{"post_id": 7, "slug": "vintage"}]
        return []

    monkeypatch.setattr(export_blog, "mysql_json_rows", fake_rows)
    export_blog.main()
    out = capsys.readouterr().out
    import json
    data = json.loads(out)
    assert set(data.keys()) == {"posts", "categories", "tags"}
    post = data["posts"][0]
    assert post["categorySlugs"] == ["hirek"]
    assert post["tagSlugs"] == ["vintage"]
    assert data["tags"][0]["slug"] == "vintage"
```

- [ ] **Step 2: Run to verify it fails**

Run: `python3 -m pytest scripts/woo-export/test_export_blog.py -q`
Expected: FAIL (`tags` key missing / `tagSlugs` KeyError).

- [ ] **Step 3: Add the `post_tag` queries + emit tagSlugs**

In `scripts/woo-export/export-blog.py`, after the category `rels`/`cats_by_post` block (around line 82-91), add:

```python
    tags = mysql_json_rows(f"""
        SELECT JSON_OBJECT('name', t.name, 'slug', t.slug)
        FROM {t('terms')} t
        JOIN {t('term_taxonomy')} tt ON tt.term_id = t.term_id
        WHERE tt.taxonomy = 'post_tag'""")

    tag_rels = mysql_json_rows(f"""
        SELECT JSON_OBJECT('post_id', tr.object_id, 'slug', t.slug)
        FROM {t('term_relationships')} tr
        JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
        JOIN {t('terms')} t ON t.term_id = tt.term_id
        WHERE tt.taxonomy = 'post_tag'""")
    tags_by_post = {}
    for r in tag_rels:
        tags_by_post.setdefault(r["post_id"], []).append(r["slug"])
```

In the `out_posts.append({...})` dict, add after `"categorySlugs": ...`:
```python
            "tagSlugs": tags_by_post.get(p["id"], []),
```

Change the final dump to include tags:
```python
    json.dump({"posts": out_posts, "categories": categories, "tags": tags}, sys.stdout, ensure_ascii=False, indent=1)
```

- [ ] **Step 4: Run to verify it passes**

Run: `python3 -m pytest scripts/woo-export/test_export_blog.py -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/woo-export/export-blog.py scripts/woo-export/test_export_blog.py
git commit -m "feat(export): blog export emits post_tag tags + per-post tagSlugs"
```

---

## Task 2: Blog tag schema + entity + repo + source-record fields

**Files:**
- Create: `src/main/resources/db/migration/V25__blog_tags.sql`, `domain/blog/BlogTag.java`, `domain/blog/BlogTagRepository.java`, `integrations/wordpress/SourceBlogTag.java`
- Modify: `domain/blog/BlogPost.java`, `integrations/wordpress/SourceBlog.java`, `integrations/wordpress/SourceBlogPost.java`, and all call sites the compiler flags
- Test: `src/test/java/hu/deposoft/webshop/domain/blog/BlogTagRepositoryIT.java`

**Interfaces:**
- Produces:
  - `BlogTag` entity (`getId/getName/getSlug`, `create(name, slug)`), `BlogTagRepository` (`findBySlug`, `existsBySlug`)
  - `BlogPost.getTags(): Set<BlogTag>` + `setTags(Set)` (join `blog_post_tag`)
  - `SourceBlogTag(String name, String slug)`; `SourceBlog.tags(): List<SourceBlogTag>`; `SourceBlogPost.tagSlugs(): List<String>`

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V25__blog_tags.sql`:

```sql
-- Blog tags (migrated from WordPress post_tag). Parallel to blog_category (V22).
CREATE TABLE blog_tag (
    id   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE
);
CREATE TABLE blog_post_tag (
    blog_post_id BIGINT NOT NULL REFERENCES blog_post (id) ON DELETE CASCADE,
    blog_tag_id  BIGINT NOT NULL REFERENCES blog_tag (id)  ON DELETE CASCADE,
    PRIMARY KEY (blog_post_id, blog_tag_id)
);
```

- [ ] **Step 2: Create `BlogTag` + repository**

Create `src/main/java/hu/deposoft/webshop/domain/blog/BlogTag.java` (mirrors `BlogCategory`):

```java
package hu.deposoft.webshop.domain.blog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Managed blog tag (migrated from WordPress post_tag). Slug stable (CLAUDE.md #7). */
@Entity
@Table(name = "blog_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    public static BlogTag create(String name, String slug) {
        if (!BlogPost.isValidSlug(slug)) {
            throw new IllegalArgumentException("Invalid blog tag slug: " + slug);
        }
        BlogTag t = new BlogTag();
        t.name = name;
        t.slug = slug;
        return t;
    }
}
```

Create `src/main/java/hu/deposoft/webshop/domain/blog/BlogTagRepository.java`:

```java
package hu.deposoft.webshop.domain.blog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BlogTagRepository extends JpaRepository<BlogTag, Long> {
    Optional<BlogTag> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<BlogTag> findAllByOrderByNameAsc();
}
```

- [ ] **Step 3: Add `tags` to `BlogPost`**

In `src/main/java/hu/deposoft/webshop/domain/blog/BlogPost.java`, after the `categories` field (around line 92) add:

```java
    @Setter
    @ManyToMany
    @JoinTable(name = "blog_post_tag",
            joinColumns = @JoinColumn(name = "blog_post_id"),
            inverseJoinColumns = @JoinColumn(name = "blog_tag_id"))
    private Set<BlogTag> tags = new LinkedHashSet<>();
```

(`ManyToMany`, `JoinTable`, `JoinColumn`, `Set`, `LinkedHashSet` are already imported for `categories`.)

- [ ] **Step 4: Add source-record fields (add new components LAST)**

Create `src/main/java/hu/deposoft/webshop/integrations/wordpress/SourceBlogTag.java`:

```java
package hu.deposoft.webshop.integrations.wordpress;

public record SourceBlogTag(String name, String slug) {
}
```

In `SourceBlog.java`, add `tags` as the last component. Current is `record SourceBlog(List<SourceBlogPost> posts, List<SourceBlogCategory> categories)`. Change to:

```java
public record SourceBlog(List<SourceBlogPost> posts, List<SourceBlogCategory> categories,
                         List<SourceBlogTag> tags) {
}
```

In `SourceBlogPost.java`, add `tagSlugs` as the last component (after `categorySlugs`):

```java
        List<String> categorySlugs,
        List<String> tagSlugs) {
```

- [ ] **Step 5: Fix all broken call sites**

Adding those components breaks every constructor call. Build to list them:
Run: `mvn -q -Dskip.frontend=true -DskipTests compile 2>&1 | grep -i "constructor\|SourceBlog"` — then update each site:
- `application/config/BlogImportRunner.java` (or wherever `SourceBlog` is deserialized) — Jackson deserialization by component name is unaffected, but any manual `new SourceBlog(...)`/`new SourceBlogPost(...)` must add the trailing arg.
- Any test/helper constructing these records — pass `List.of()` for tags/tagSlugs where not relevant.
Search: `grep -rn "new SourceBlog(\|new SourceBlogPost(" src` and fix each (append `, List.of()` / `List.of()`).

- [ ] **Step 6: Write the repository IT**

Create `src/test/java/hu/deposoft/webshop/domain/blog/BlogTagRepositoryIT.java`:

```java
package hu.deposoft.webshop.domain.blog;

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
class BlogTagRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BlogTagRepository tags;
    @Autowired BlogPostRepository posts;

    @Test
    void savesAndFindsBySlug() {
        tags.save(BlogTag.create("Vintage", "vintage"));
        assertThat(tags.findBySlug("vintage")).isPresent();
        assertThat(tags.existsBySlug("vintage")).isTrue();
    }

    @Test
    void postTagsRoundTripThroughJoinTable() {
        BlogTag t = tags.save(BlogTag.create("Vintage", "vintage"));
        BlogPost p = BlogPost.create("cikk", "Cikk");
        p.setTags(new java.util.LinkedHashSet<>(java.util.List.of(t)));
        posts.save(p);
        BlogPost loaded = posts.findBySlug("cikk").orElseThrow();
        assertThat(loaded.getTags()).extracting(BlogTag::getSlug).containsExactly("vintage");
    }
}
```

- [ ] **Step 7: Build + run the IT**

Run: `mvn -q -Dskip.frontend=true -Dtest=BlogTagRepositoryIT test`
Expected: PASS (2 tests); the full module compiles (all call sites fixed).

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V25__blog_tags.sql \
        src/main/java/hu/deposoft/webshop/domain/blog/BlogTag.java \
        src/main/java/hu/deposoft/webshop/domain/blog/BlogTagRepository.java \
        src/main/java/hu/deposoft/webshop/domain/blog/BlogPost.java \
        src/main/java/hu/deposoft/webshop/integrations/wordpress/ \
        src/test/java/hu/deposoft/webshop/domain/blog/BlogTagRepositoryIT.java
git add -u   # picks up fixed call sites
git commit -m "feat(blog): blog_tag schema + entity + BlogPost.tags + source-record tag fields"
```

---

## Task 3: `BlogImporter` — upsert + link blog tags

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java`, `src/main/java/hu/deposoft/webshop/application/blog/BlogImportReport.java`
- Test: `src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java`

**Interfaces:**
- Consumes: `BlogTag`/`BlogTagRepository`, `BlogPost.setTags/getTags`, `SourceBlog.tags()`, `SourceBlogPost.tagSlugs()`.
- Produces: tags upserted by slug + linked to posts; `BlogImportReport.tagCreated()` + `tagsCreated()` count.

- [ ] **Step 1: Write the failing importer IT**

In `src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java`, add a test. (The file constructs `SourceBlogPost` and `SourceBlog` — after Task 2 those take the extra trailing args; use them.) Add:

```java
    @Test
    void importsAndLinksTagsIdempotently() {
        var post = new SourceBlogPost(1L, "tagos", "Tagos", "", "<p>x</p>", "publish", PUB,
                null, null, null, java.util.List.of(), java.util.List.of("vintage"));
        var source = new SourceBlog(java.util.List.of(post),
                java.util.List.of(),
                java.util.List.of(new hu.deposoft.webshop.integrations.wordpress.SourceBlogTag("Vintage", "vintage")));
        importer.run(source);
        importer.run(source); // re-run: idempotent

        var loaded = posts.findBySlug("tagos").orElseThrow();
        assertThat(loaded.getTags()).extracting(BlogTag::getSlug).containsExactly("vintage");
        assertThat(tags.findAll()).extracting(BlogTag::getSlug).containsExactlyInAnyOrder("vintage");
    }
```

Add `@Autowired BlogTagRepository tags;` to the test class and the imports for `BlogTag`. (Match the exact `SourceBlogPost` component order from Task 2: `externalId, slug, title, excerpt, contentHtml, status, publishedAt, coverImageUrl, seoTitle, seoDescription, categorySlugs, tagSlugs`.)

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -Dskip.frontend=true -Dtest=BlogImporterIT#importsAndLinksTagsIdempotently test`
Expected: FAIL — importer ignores tags (post has no tags / `tags` repo empty).

- [ ] **Step 3: Add the tag counter to the report**

In `src/main/java/hu/deposoft/webshop/application/blog/BlogImportReport.java`, mirroring `categoriesCreated` (around lines 13, 20, 27):
- Add field: `private int tagsCreated;`
- Add: `void tagCreated() { tagsCreated++; }`
- Add: `public int tagsCreated() { return tagsCreated; }`
(If `toString()` lists counters, add `tagsCreated` there too.)

- [ ] **Step 4: Wire tags into `BlogImporter`**

In `BlogImporter.java`:
- Add imports: `hu.deposoft.webshop.domain.blog.BlogTag`, `hu.deposoft.webshop.domain.blog.BlogTagRepository`, `hu.deposoft.webshop.integrations.wordpress.SourceBlogTag`.
- Add field: `private final BlogTagRepository tags;`
- In `run(...)`, after `Map<String, BlogCategory> categoryBySlug = upsertCategories(...)` add:
  ```java
  Map<String, BlogTag> tagBySlug = upsertTags(source, report);
  ```
  and change the loop to pass it: `upsertPost(post, categoryBySlug, tagBySlug, report);`
- Add the upsert method (mirrors `upsertCategories`):
  ```java
  private Map<String, BlogTag> upsertTags(SourceBlog source, BlogImportReport report) {
      Map<String, BlogTag> bySlug = new LinkedHashMap<>();
      for (SourceBlogTag tsrc : nullToEmpty(source.tags())) {
          BlogTag entity = tags.findBySlug(tsrc.slug()).orElse(null);
          if (entity == null) {
              entity = tags.save(BlogTag.create(tsrc.name(), tsrc.slug()));
              report.tagCreated();
          }
          bySlug.put(tsrc.slug(), entity);
      }
      return bySlug;
  }
  ```
- Change `upsertPost` signature to accept `Map<String, BlogTag> tagBySlug` and, mirroring the category block, resolve + set tags:
  ```java
  Set<BlogTag> resolvedTags = new LinkedHashSet<>();
  if (source.tagSlugs() != null) {
      for (String slug : source.tagSlugs()) {
          BlogTag tg = tagBySlug.get(slug);
          if (tg == null) tg = tags.findBySlug(slug).orElse(null);
          if (tg != null) resolvedTags.add(tg);
      }
  }
  if (created) {
      post.setTags(resolvedTags);
  } else {
      post.getTags().clear();
      post.getTags().addAll(resolvedTags);
  }
  ```
  Place this next to the existing category resolution (before the cover-image block).

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -q -Dskip.frontend=true -Dtest=BlogImporterIT test`
Expected: PASS (existing tests + the new tag test).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogImporter.java \
        src/main/java/hu/deposoft/webshop/application/blog/BlogImportReport.java \
        src/test/java/hu/deposoft/webshop/application/blog/BlogImporterIT.java
git commit -m "feat(blog): BlogImporter upserts + links post tags (idempotent)"
```

---

## Task 4: Product export — add `product_tag`

**Files:**
- Modify: `scripts/woo-export/export.py`
- Create: `scripts/woo-export/test_export.py`

**Interfaces:**
- Produces: catalog JSON with a top-level `"tags"` array (`{wooTermId, slug, name}`) and each product carrying `"tagWooTermIds"`.

- [ ] **Step 1: Write the failing test**

Create `scripts/woo-export/test_export.py` (mirror `test_export_blog.py`'s import + monkeypatch approach). It should stub `mysql_json_rows`/`mysql_scalar` so that a single product with one `product_tag` term produces `tagWooTermIds` and a top-level `tags` entry. Model it on `test_export_blog.py`; the key assertions:

```python
    data = json.loads(out)   # 'out' is the stdout JSON (export.py prints a summary to stderr)
    assert "tags" in data
    assert data["tags"][0]["slug"] == "vintage"
    assert data["products"][0]["tagWooTermIds"] == [42]
```

Return stub rows so: the `product_tag` terms query (matched by `"taxonomy = 'product_tag'"` and no `term_relationships`) yields `[{"wooTermId": 42, "slug": "vintage", "name": "Vintage"}]`; the `product_tag` relationships query (matched by `"taxonomy = 'product_tag'"` present) yields `[{"postId": <product wooId>, "termId": 42}]`; and a minimal product row so `out_products` has one entry. (Read `export.py`'s `main()` to see which query-substring branches you must satisfy — products, product_cat terms+rels, attributes, pa_ values, variations, meta, types, attachments — return `[]` for the ones your minimal fixture doesn't exercise.)

- [ ] **Step 2: Run to verify it fails**

Run: `python3 -m pytest scripts/woo-export/test_export.py -q`
Expected: FAIL (`tags` / `tagWooTermIds` missing).

- [ ] **Step 3: Add `product_tag` queries + emit tagWooTermIds**

In `scripts/woo-export/export.py`:
- After the `categories` terms query (around line 70-76), add a tags terms query:
  ```python
  tags = mysql_json_rows(f"""
      SELECT JSON_OBJECT('wooTermId', t.term_id, 'slug', t.slug, 'name', t.name)
      FROM {t('term_taxonomy')} tt JOIN {t('terms')} t ON t.term_id = tt.term_id
      WHERE tt.taxonomy = 'product_tag'""")
  ```
- Next to `product_cats` (around line 133), add:
  ```python
  product_tags = mysql_json_rows(f"""
      SELECT JSON_OBJECT('postId', tr.object_id, 'termId', tt.term_id)
      FROM {t('term_relationships')} tr
      JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
        AND tt.taxonomy = 'product_tag'""")
  ```
- Near `cats_by_id` assembly (around line 148), add:
  ```python
  tags_by_id = {}
  for r in product_tags:
      tags_by_id.setdefault(r["postId"], []).append(r["termId"])
  ```
- In the `out_products.append({...})` dict, after `"categoryWooTermIds": ...` add:
  ```python
  "tagWooTermIds": tags_by_id.get(pid, []),
  ```
- Add `tags` to the catalog dict: `catalog = {"categories": categories, "tags": tags, "attributes": attributes, "products": out_products}`

- [ ] **Step 4: Run to verify it passes**

Run: `python3 -m pytest scripts/woo-export/test_export.py -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/woo-export/export.py scripts/woo-export/test_export.py
git commit -m "feat(export): catalog export emits product_tag tags + per-product tagWooTermIds"
```

---

## Task 5: Product tag schema + entity + repo + source-record fields

**Files:**
- Create: `src/main/resources/db/migration/V26__product_tags.sql`, `domain/catalog/ProductTag.java`, `domain/catalog/ProductTagRepository.java`, `integrations/woo/SourceTag.java`
- Modify: `domain/catalog/Product.java`, `integrations/woo/SourceCatalog.java`, `integrations/woo/SourceProduct.java`, and all call sites the compiler flags
- Test: `src/test/java/hu/deposoft/webshop/domain/catalog/ProductTagRepositoryIT.java`

**Interfaces:**
- Produces:
  - `ProductTag` entity (`getId/getExternalId/getSlug/getName`, `create(externalId, slug, name)`), `ProductTagRepository` (`findByExternalId`)
  - `Product.getTags(): Set<ProductTag>` + `replaceTags(Set)` (join `product_tag_map`)
  - `SourceTag(long wooTermId, String slug, String name)`; `SourceCatalog.tags(): List<SourceTag>`; `SourceProduct.tagWooTermIds(): List<Long>`

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V26__product_tags.sql`:

```sql
-- Product tags (migrated from WooCommerce product_tag). Parallel to category/product_category (V2).
CREATE TABLE product_tag (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    external_id BIGINT NOT NULL UNIQUE,   -- Woo term id
    slug        TEXT NOT NULL,
    name        TEXT NOT NULL
);
CREATE TABLE product_tag_map (
    product_id     BIGINT NOT NULL REFERENCES product (id)     ON DELETE CASCADE,
    product_tag_id BIGINT NOT NULL REFERENCES product_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, product_tag_id)
);
```

- [ ] **Step 2: Create `ProductTag` + repository**

Create `src/main/java/hu/deposoft/webshop/domain/catalog/ProductTag.java` (mirrors `Category`, minus the tree):

```java
package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Product tag (migrated from WooCommerce product_tag). Keyed by Woo term id. */
@Entity
@Table(name = "product_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private Long externalId;

    /** Immutable once created (CLAUDE.md #7). */
    @Column(nullable = false)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String name;

    public static ProductTag create(Long externalId, String slug, String name) {
        ProductTag t = new ProductTag();
        t.externalId = externalId;
        t.slug = slug;
        t.name = name;
        return t;
    }
}
```

Create `src/main/java/hu/deposoft/webshop/domain/catalog/ProductTagRepository.java`:

```java
package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {
    Optional<ProductTag> findByExternalId(Long externalId);
}
```

- [ ] **Step 3: Add `tags` to `Product`**

In `src/main/java/hu/deposoft/webshop/domain/catalog/Product.java`, after the `categories` field (around line 115) add:

```java
    @ManyToMany
    @JoinTable(name = "product_tag_map",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "product_tag_id"))
    private Set<ProductTag> tags = new LinkedHashSet<>();
```

And add a `replaceTags` mirroring `replaceCategories` (near line 151):

```java
    public void replaceTags(Set<ProductTag> newTags) {
        tags.clear();
        tags.addAll(newTags);
    }
```

Add a `getTags()` — Lombok `@Getter` on the class already generates it (confirm the class has class-level `@Getter`; `Product` does). (`ManyToMany`/`JoinTable`/`JoinColumn`/`Set`/`LinkedHashSet` already imported for `categories`.)

- [ ] **Step 4: Add source-record fields (add new components LAST)**

Create `src/main/java/hu/deposoft/webshop/integrations/woo/SourceTag.java`:

```java
package hu.deposoft.webshop.integrations.woo;

public record SourceTag(long wooTermId, String slug, String name) {
}
```

In `SourceCatalog.java`, add `tags` as the last component. Current: `record SourceCatalog(List<SourceCategory> categories, List<SourceAttribute> attributes, List<SourceProduct> products)` (verify exact existing components and append `List<SourceTag> tags` last).

In `SourceProduct.java`, add `List<Long> tagWooTermIds` as the **last** component (after `images`).

- [ ] **Step 5: Fix all broken call sites**

Run: `mvn -q -Dskip.frontend=true -DskipTests compile 2>&1 | grep -i "constructor\|SourceCatalog\|SourceProduct"` and fix each `new SourceCatalog(...)` / `new SourceProduct(...)` (append `List.of()` for the new trailing arg). Check `integrations/woo/JsonFileCatalogSource.java` (Jackson binds by name — unaffected) and `CatalogImporterTest.java` + any fixtures constructing these records.

- [ ] **Step 6: Write the repository IT**

Create `src/test/java/hu/deposoft/webshop/domain/catalog/ProductTagRepositoryIT.java`:

```java
package hu.deposoft.webshop.domain.catalog;

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
class ProductTagRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductTagRepository tags;

    @Test
    void savesAndFindsByExternalId() {
        tags.save(ProductTag.create(42L, "vintage", "Vintage"));
        assertThat(tags.findByExternalId(42L)).isPresent();
        assertThat(tags.findByExternalId(42L).orElseThrow().getSlug()).isEqualTo("vintage");
    }
}
```

- [ ] **Step 7: Build + run the IT**

Run: `mvn -q -Dskip.frontend=true -Dtest=ProductTagRepositoryIT test`
Expected: PASS; the module compiles (all call sites fixed).

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V26__product_tags.sql \
        src/main/java/hu/deposoft/webshop/domain/catalog/ProductTag.java \
        src/main/java/hu/deposoft/webshop/domain/catalog/ProductTagRepository.java \
        src/main/java/hu/deposoft/webshop/domain/catalog/Product.java \
        src/main/java/hu/deposoft/webshop/integrations/woo/ \
        src/test/java/hu/deposoft/webshop/domain/catalog/ProductTagRepositoryIT.java
git add -u
git commit -m "feat(catalog): product_tag schema + entity + Product.tags + source-record tag fields"
```

---

## Task 6: `CatalogImporter` — upsert + link product tags

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/catalog/CatalogImporter.java`, `src/main/java/hu/deposoft/webshop/application/catalog/ImportReport.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/CatalogImporterTest.java`

**Interfaces:**
- Consumes: `ProductTag`/`ProductTagRepository`, `Product.replaceTags`, `SourceCatalog.tags()`, `SourceProduct.tagWooTermIds()`.
- Produces: tags upserted by Woo term id + linked to products; `ImportReport.tagCreated()/tagUpdated()`.

- [ ] **Step 1: Write the failing test**

In `src/test/java/hu/deposoft/webshop/application/catalog/CatalogImporterTest.java`, add a test that builds a `SourceCatalog` with one `SourceTag(42, "vintage", "Vintage")` and one product whose `tagWooTermIds` is `List.of(42L)`, imports twice, and asserts the product has the tag and only one `product_tag` row exists. Match the exact `SourceProduct`/`SourceCatalog` component order from Task 5. Add `@Autowired ProductTagRepository productTags;`. Assertion core:

```java
        importer.run(catalog);
        importer.run(catalog); // idempotent
        Product p = products.findByExternalId(<wooId>).orElseThrow();
        assertThat(p.getTags()).extracting(ProductTag::getSlug).containsExactly("vintage");
        assertThat(productTags.findAll()).hasSize(1);
```

(Read the existing tests in this file for how it builds `SourceCatalog`/`SourceProduct` and obtains the repositories, and reuse that fixture shape with the tag fields added.)

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -Dskip.frontend=true -Dtest=CatalogImporterTest test`
Expected: FAIL — importer ignores tags.

- [ ] **Step 3: Add tag counters to `ImportReport`**

In `src/main/java/hu/deposoft/webshop/application/catalog/ImportReport.java`, mirroring `categoriesCreated`/`categoriesUpdated`:
- Fields: `private int tagsCreated; private int tagsUpdated;`
- `void tagCreated() { tagsCreated++; }`, `void tagUpdated() { tagsUpdated++; }`
- `public int tagsCreated() { return tagsCreated; }`, `public int tagsUpdated() { return tagsUpdated; }`

- [ ] **Step 4: Wire tags into `CatalogImporter`**

In `CatalogImporter.java`:
- Add imports: `hu.deposoft.webshop.domain.catalog.ProductTag`, `ProductTagRepository`, `hu.deposoft.webshop.integrations.woo.SourceTag`.
- Add field: `private final ProductTagRepository productTags;`
- In the `run(...)`/importing method, after `Map<Long, Category> categoryByExternalId = upsertCategories(...)` add:
  ```java
  Map<Long, ProductTag> tagByExternalId = upsertTags(catalog, report);
  ```
  and pass it to `upsertProduct(source, categoryByExternalId, tagByExternalId, importedAt, report);`
- Add the upsert method (mirrors `upsertCategories`):
  ```java
  private Map<Long, ProductTag> upsertTags(SourceCatalog catalog, ImportReport report) {
      Map<Long, ProductTag> byExternalId = new HashMap<>();
      for (SourceTag st : nullToEmptyTags(catalog.tags())) {
          ProductTag tag = productTags.findByExternalId(st.wooTermId()).orElse(null);
          if (tag == null) {
              tag = productTags.save(ProductTag.create(st.wooTermId(), st.slug(), st.name()));
              report.tagCreated();
          } else {
              report.tagUpdated();
          }
          tag.setName(st.name());
          byExternalId.put(st.wooTermId(), tag);
      }
      return byExternalId;
  }

  private static java.util.List<SourceTag> nullToEmptyTags(java.util.List<SourceTag> l) {
      return l == null ? java.util.List.of() : l;
  }
  ```
- Change `upsertProduct` to accept `Map<Long, ProductTag> tagByExternalId` and, mirroring the category link block, resolve + `product.replaceTags(...)`:
  ```java
  Set<ProductTag> tagSet = new LinkedHashSet<>();
  for (Long termId : source.tagWooTermIds()) {
      ProductTag tg = tagByExternalId.get(termId);
      if (tg != null) tagSet.add(tg);
      else report.error("product woo_id=%d references unknown tag woo_term_id=%d"
              .formatted(source.wooId(), termId));
  }
  product.replaceTags(tagSet);
  ```
  Place it right after the existing `product.replaceCategories(cats);`.

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -q -Dskip.frontend=true -Dtest=CatalogImporterTest test`
Expected: PASS (existing + new tag test).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/catalog/CatalogImporter.java \
        src/main/java/hu/deposoft/webshop/application/catalog/ImportReport.java \
        src/test/java/hu/deposoft/webshop/application/catalog/CatalogImporterTest.java
git commit -m "feat(catalog): CatalogImporter upserts + links product tags (idempotent)"
```

---

## Manual verification (after Task 6; human)

1. Re-export blog + catalog from the `wp_db` container (`export-blog.py`, `export.py`) — confirm the JSON now carries `tags` + `tagSlugs`/`tagWooTermIds`.
2. Re-run both imports against the dev DB (Flyway applies V25/V26). Confirm `blog_tag`/`blog_post_tag` and `product_tag`/`product_tag_map` populate, and re-running the import does not duplicate tags.

---

## Self-Review

- **Spec coverage:** blog export (T1) + blog schema/domain/records (T2) + blog importer (T3); product export (T4) + product schema/domain/records (T5) + product importer (T6). Idempotency asserted in T3/T6; keying (blog slug, product Woo term id) consistent end-to-end; V25/V26 with the instagram-feed note in Global Constraints. Surfaces/admin excluded per spec.
- **Placeholder scan:** concrete SQL/records/methods/tests. T4/T6 tests say "mirror the existing file" for fixture *shape* but give the exact assertions and the exact new fields — the fixture pattern is read from a named existing file, not invented.
- **Type consistency:** `BlogTag.create(name, slug)`/`findBySlug`; `ProductTag.create(externalId, slug, name)`/`findByExternalId`; `BlogPost.setTags/getTags`; `Product.replaceTags/getTags`; `SourceBlogTag(name,slug)`, `SourceTag(wooTermId,slug,name)`; record fields appended last (`tagSlugs`, `tagWooTermIds`, `tags`). Consistent across tasks.
- **Breaking-change handling:** T2/T5 include explicit "fix all call sites" steps (compiler-guided) — the known risk of record-component additions.
