# Taxonomy Admin (blog categories, blog tags, product tags) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Full-CRUD admin screens for blog categories, blog tags, and product tags — create / rename (name only, slug immutable) / delete — reusing existing conventions, with a shared SPA table component.

**Architecture:** Blog categories already have complete REST (`/api/admin/blog/categories`, `update` is already name-only) — they only need a SPA screen. Blog tags and product tags get new admin services + REST resources mirroring the social-links admin. Product tags get a nullable `external_id` so admin-created tags (no Woo id) coexist with imported ones. One shared `TaxonomyAdmin` React component backs all three screens.

**Tech Stack:** Java 21 / Spring Boot / JPA / Flyway; JUnit + Testcontainers; admin-ui React + Refine + antd + i18next.

## Global Constraints

- Admin endpoints path-protected ADMIN (no `@PreAuthorize`); plain-String errors via `AdminExceptionHandler` (service-nested `NotFoundException → 404`; duplicate slug → `IllegalArgumentException` → 400, already global); audit every write via `AuditService.record(action, entityType, entityId, summary)`.
- **Rename edits the display name only; slug is immutable** (CLAUDE.md #7). Create validates the slug (`BlogPost.isValidSlug`) and uniqueness.
- Product-tag manual create: `external_id = null` (nullable column; Postgres UNIQUE allows multiple NULLs; importer's `findByExternalId` only matches Woo ids).
- Entities `@NoArgsConstructor(PROTECTED)` + `@Getter`; reads `@Transactional(readOnly=true)`.
- Migration is the next free version (**V33** — main is at V32; if V33 is already taken on the branch base, use the next free number and note it). Cross-branch: versions follow merge order; re-check at merge.
- Code/comments/commits English; admin-ui strings Hungarian via i18next.
- Backend build/test: `mvn -q -Dskip.frontend=true -Dtest=<IT> test`. admin-ui from `admin-ui/`: `npm run build` / `npm test` (symlink `node_modules` from the main workspace if absent; do not `npm install`).

---

## File Structure

- Create `db/migration/V33__product_tag_external_id_nullable.sql`; modify `domain/catalog/ProductTag.java`, `domain/catalog/ProductTagRepository.java`.
- Create `application/blog/BlogTagAdminService.java`, `api/admin/BlogTagController.java`.
- Create `application/catalog/ProductTagAdminService.java`, `api/admin/ProductTagController.java`.
- Modify `api/admin/AdminExceptionHandler.java` (two NotFoundException→404 handlers).
- Modify `api/admin/BlogCategoryController.java` (add `X-Total-Count` — optional; see Task 4 note).
- admin-ui: create `components/TaxonomyAdmin.tsx`, `pages/blog/categories.tsx`, `pages/blog/tags.tsx`, `pages/products/tags.tsx`, `i18n/hu/taxonomy.json`, `i18n/en/taxonomy.json`; modify `App.tsx`, `components/layout/nav.config.ts`, `i18n/index.ts`, `types.ts`.
- Tests: `ProductTagRepositoryIT` (extend), `BlogTagAdminServiceIT`, `BlogTagControllerIT`, `ProductTagAdminServiceIT`, `ProductTagControllerIT`.

---

## Task 1: Product tag — nullable external_id + repo methods

**Files:**
- Create: `src/main/resources/db/migration/V33__product_tag_external_id_nullable.sql`
- Modify: `src/main/java/hu/deposoft/webshop/domain/catalog/ProductTag.java`, `src/main/java/hu/deposoft/webshop/domain/catalog/ProductTagRepository.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/catalog/ProductTagRepositoryIT.java` (extend)

**Interfaces:**
- Produces: `ProductTag.createManual(String slug, String name)` (externalId null); `ProductTagRepository.existsBySlug`, `findAllByOrderByNameAsc`.

- [ ] **Step 1: Migration** — create `V33__product_tag_external_id_nullable.sql` (if V33 is taken on the branch base, use the next free number):
```sql
-- Allow admin-created product tags (no Woo term id). Postgres UNIQUE permits multiple NULLs.
ALTER TABLE product_tag ALTER COLUMN external_id DROP NOT NULL;
```

- [ ] **Step 2: Entity** — in `ProductTag.java`, change the `external_id` column to nullable and add a manual factory. Replace:
```java
    @Column(name = "external_id", nullable = false, unique = true)
    private Long externalId;
```
with:
```java
    @Column(name = "external_id", unique = true)
    private Long externalId;
```
Add, after the existing `create(...)` factory:
```java
    /** Admin-created tag (no Woo term id). */
    public static ProductTag createManual(String slug, String name) {
        ProductTag t = new ProductTag();
        t.slug = slug;
        t.name = name;
        return t;
    }
```

- [ ] **Step 3: Repository** — in `ProductTagRepository.java`, add:
```java
    boolean existsBySlug(String slug);
    java.util.List<ProductTag> findAllByOrderByNameAsc();
```

- [ ] **Step 4: Extend the repo IT** — in `ProductTagRepositoryIT.java`, add:
```java
    @Test
    void manualTagHasNullExternalIdAndIsFoundBySlug() {
        tags.save(ProductTag.createManual("kezi-cimke", "Kézi címke"));
        assertThat(tags.existsBySlug("kezi-cimke")).isTrue();
        assertThat(tags.findAllByOrderByNameAsc()).extracting(ProductTag::getSlug).contains("kezi-cimke");
        assertThat(tags.findAllByOrderByNameAsc().stream()
                .filter(t -> t.getSlug().equals("kezi-cimke")).findFirst().orElseThrow()
                .getExternalId()).isNull();
    }
```

- [ ] **Step 5: Run + commit**

Run: `mvn -q -Dskip.frontend=true -Dtest=ProductTagRepositoryIT test` → PASS.
```bash
git add src/main/resources/db/migration/V33__product_tag_external_id_nullable.sql \
        src/main/java/hu/deposoft/webshop/domain/catalog/ProductTag.java \
        src/main/java/hu/deposoft/webshop/domain/catalog/ProductTagRepository.java \
        src/test/java/hu/deposoft/webshop/domain/catalog/ProductTagRepositoryIT.java
git commit -m "feat(catalog): product_tag external_id nullable + createManual + slug lookups"
```

---

## Task 2: Blog tag admin service + REST

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/blog/BlogTagAdminService.java`, `src/main/java/hu/deposoft/webshop/api/admin/BlogTagController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/BlogTagControllerIT.java`

**Interfaces:**
- Produces (nested in `BlogTagAdminService`): `TagView(Long id, String name, String slug)`, `TagUpsert(String name, String slug)`, `RenameRequest(String name)`, `NotFoundException`; `list()`, `create(TagUpsert)`, `rename(Long, RenameRequest)`, `delete(Long)`.

- [ ] **Step 1: Admin service** — create `BlogTagAdminService.java`:
```java
package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.blog.BlogTag;
import hu.deposoft.webshop.domain.blog.BlogTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BlogTagAdminService {

    private final BlogTagRepository tags;
    private final AuditService audit;

    public record TagView(Long id, String name, String slug) {}
    public record TagUpsert(String name, String slug) {}
    public record RenameRequest(String name) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public List<TagView> list() {
        return tags.findAllByOrderByNameAsc().stream()
                .map(t -> new TagView(t.getId(), t.getName(), t.getSlug())).toList();
    }

    public TagView create(TagUpsert cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (tags.existsBySlug(cmd.slug())) {
            throw new IllegalArgumentException("Blog tag slug already exists: " + cmd.slug());
        }
        BlogTag t = tags.save(BlogTag.create(cmd.name().trim(), cmd.slug())); // create() validates the slug
        audit.record("BLOG_TAG_CREATE", "blog_tag", String.valueOf(t.getId()), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public TagView rename(Long id, RenameRequest cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        BlogTag t = find(id);
        t.setName(cmd.name().trim());   // slug immutable (CLAUDE.md #7)
        audit.record("BLOG_TAG_UPDATE", "blog_tag", String.valueOf(id), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public void delete(Long id) {
        BlogTag t = find(id);
        tags.delete(t);
        audit.record("BLOG_TAG_DELETE", "blog_tag", String.valueOf(id), t.getSlug());
    }

    private BlogTag find(Long id) {
        return tags.findById(id).orElseThrow(() -> new NotFoundException("Blog tag not found: " + id));
    }
}
```
(`BlogTag.create(name, slug)` already validates the slug via `BlogPost.isValidSlug` and throws `IllegalArgumentException` on an invalid slug → 400.)

- [ ] **Step 2: Controller** — create `BlogTagController.java`:
```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.blog.BlogTagAdminService;
import hu.deposoft.webshop.application.blog.BlogTagAdminService.RenameRequest;
import hu.deposoft.webshop.application.blog.BlogTagAdminService.TagUpsert;
import hu.deposoft.webshop.application.blog.BlogTagAdminService.TagView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BlogTagController {

    private final BlogTagAdminService service;

    @GetMapping("/api/admin/blog/tags")
    public List<TagView> list(HttpServletResponse response) {
        List<TagView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @PostMapping("/api/admin/blog/tags")
    public TagView create(@RequestBody TagUpsert cmd) { return service.create(cmd); }

    @PutMapping("/api/admin/blog/tags/{id}")
    public TagView rename(@PathVariable Long id, @RequestBody RenameRequest cmd) { return service.rename(id, cmd); }

    @DeleteMapping("/api/admin/blog/tags/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Exception handler** — in `AdminExceptionHandler.java` add import `hu.deposoft.webshop.application.blog.BlogTagAdminService;` and:
```java
    @ExceptionHandler(BlogTagAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String blogTagNotFound(BlogTagAdminService.NotFoundException e) { return e.getMessage(); }
```

- [ ] **Step 4: Controller IT** — create `BlogTagControllerIT.java`, mirroring `SocialLinkControllerIT` boilerplate (`@SpringBootTest @Testcontainers @Transactional`, `webAppContextSetup(context).apply(springSecurity()).build()`, `admin()` = `user("admin@example.com").roles("ADMIN")`, `.with(csrf())` on writes). Tests:
```java
    @Test void unauthListReturns401() // GET /api/admin/blog/tags → 401
    @Test void createRenameThenDelete() // POST {name:"Vintage",slug:"vintage"} → 200 slug vintage;
                                        // PUT {id} {name:"Retro"} → 200 name Retro AND slug STILL "vintage";
                                        // DELETE {id} → 204
    @Test void duplicateSlugReturns400() // POST vintage twice → 2nd is 400
    @Test void invalidSlugReturns400()   // POST {name:"x",slug:"Bad Slug!"} → 400
    @Test void renameUnknownReturns404() // PUT /api/admin/blog/tags/999999 {name:"x"} → 404
```
Extract the created id from the create response body (as `BlogPostControllerIT`/`SocialLinkControllerIT` do). The rename test MUST assert the slug is unchanged after rename.

- [ ] **Step 5: Run + commit**

Run: `mvn -q -Dskip.frontend=true -Dtest=BlogTagControllerIT test` → PASS.
```bash
git add src/main/java/hu/deposoft/webshop/application/blog/BlogTagAdminService.java \
        src/main/java/hu/deposoft/webshop/api/admin/BlogTagController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/test/java/hu/deposoft/webshop/api/admin/BlogTagControllerIT.java
git commit -m "feat(admin): /api/admin/blog/tags CRUD (rename=name-only, slug immutable)"
```

---

## Task 3: Product tag admin service + REST

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/ProductTagAdminService.java`, `src/main/java/hu/deposoft/webshop/api/admin/ProductTagController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/ProductTagControllerIT.java`

**Interfaces:**
- Produces (nested in `ProductTagAdminService`): `TagView(Long id, String name, String slug)`, `TagUpsert(String name, String slug)`, `RenameRequest(String name)`, `NotFoundException`; `list()`, `create(TagUpsert)` (manual, external_id null), `rename(Long, RenameRequest)`, `delete(Long)`.

- [ ] **Step 1: Admin service** — create `ProductTagAdminService.java` (mirrors Task 2's `BlogTagAdminService`, but uses `ProductTag.createManual(slug, name)` and validates the slug with `BlogPost.isValidSlug` since `ProductTag.create` does not validate):
```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.catalog.ProductTag;
import hu.deposoft.webshop.domain.catalog.ProductTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductTagAdminService {

    private final ProductTagRepository tags;
    private final AuditService audit;

    public record TagView(Long id, String name, String slug) {}
    public record TagUpsert(String name, String slug) {}
    public record RenameRequest(String name) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public List<TagView> list() {
        return tags.findAllByOrderByNameAsc().stream()
                .map(t -> new TagView(t.getId(), t.getName(), t.getSlug())).toList();
    }

    public TagView create(TagUpsert cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (!BlogPost.isValidSlug(cmd.slug())) {
            throw new IllegalArgumentException("Invalid product tag slug: " + cmd.slug());
        }
        if (tags.existsBySlug(cmd.slug())) {
            throw new IllegalArgumentException("Product tag slug already exists: " + cmd.slug());
        }
        ProductTag t = tags.save(ProductTag.createManual(cmd.slug(), cmd.name().trim()));
        audit.record("PRODUCT_TAG_CREATE", "product_tag", String.valueOf(t.getId()), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public TagView rename(Long id, RenameRequest cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        ProductTag t = find(id);
        t.setName(cmd.name().trim());   // slug immutable (CLAUDE.md #7)
        audit.record("PRODUCT_TAG_UPDATE", "product_tag", String.valueOf(id), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public void delete(Long id) {
        ProductTag t = find(id);
        tags.delete(t);
        audit.record("PRODUCT_TAG_DELETE", "product_tag", String.valueOf(id), t.getSlug());
    }

    private ProductTag find(Long id) {
        return tags.findById(id).orElseThrow(() -> new NotFoundException("Product tag not found: " + id));
    }
}
```

- [ ] **Step 2: Controller** — create `ProductTagController.java` at base path `/api/admin/products/tags` (structure identical to `BlogTagController` from Task 2, swapping the service type and the 4 paths to `/api/admin/products/tags`). Provide the full class:
```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductTagAdminService;
import hu.deposoft.webshop.application.catalog.ProductTagAdminService.RenameRequest;
import hu.deposoft.webshop.application.catalog.ProductTagAdminService.TagUpsert;
import hu.deposoft.webshop.application.catalog.ProductTagAdminService.TagView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductTagController {

    private final ProductTagAdminService service;

    @GetMapping("/api/admin/products/tags")
    public List<TagView> list(HttpServletResponse response) {
        List<TagView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @PostMapping("/api/admin/products/tags")
    public TagView create(@RequestBody TagUpsert cmd) { return service.create(cmd); }

    @PutMapping("/api/admin/products/tags/{id}")
    public TagView rename(@PathVariable Long id, @RequestBody RenameRequest cmd) { return service.rename(id, cmd); }

    @DeleteMapping("/api/admin/products/tags/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Exception handler** — in `AdminExceptionHandler.java` add import `hu.deposoft.webshop.application.catalog.ProductTagAdminService;` and:
```java
    @ExceptionHandler(ProductTagAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String productTagNotFound(ProductTagAdminService.NotFoundException e) { return e.getMessage(); }
```

- [ ] **Step 4: Controller IT** — create `ProductTagControllerIT.java` mirroring Task 2's IT (same auth/csrf boilerplate), against `/api/admin/products/tags`. Tests: `unauthListReturns401`; `createRenameThenDelete` (create `{name:"Kézi",slug:"kezi"}` → 200; assert the created tag has null external_id is NOT visible in `TagView` — skip that, just check name/slug; rename `{name:"Átnevezett"}` → slug still "kezi"; delete → 204); `duplicateSlugReturns400`; `invalidSlugReturns400`; `renameUnknownReturns404`.

- [ ] **Step 5: Run + commit**

Run: `mvn -q -Dskip.frontend=true -Dtest=ProductTagControllerIT test` → PASS.
```bash
git add src/main/java/hu/deposoft/webshop/application/catalog/ProductTagAdminService.java \
        src/main/java/hu/deposoft/webshop/api/admin/ProductTagController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductTagControllerIT.java
git commit -m "feat(admin): /api/admin/products/tags CRUD (manual create, slug immutable)"
```

---

## Task 4: Shared `TaxonomyAdmin` component + three screens + wiring

**Files:**
- Create: `admin-ui/src/components/TaxonomyAdmin.tsx`, `admin-ui/src/pages/blog/categories.tsx`, `admin-ui/src/pages/blog/tags.tsx`, `admin-ui/src/pages/products/tags.tsx`, `admin-ui/src/i18n/hu/taxonomy.json`, `admin-ui/src/i18n/en/taxonomy.json`
- Modify: `admin-ui/src/App.tsx`, `admin-ui/src/components/layout/nav.config.ts`, `admin-ui/src/i18n/index.ts`, `admin-ui/src/types.ts`

**Interfaces:**
- Consumes: REST resources `blog/categories` (existing), `blog/tags`, `products/tags`.

- [ ] **Step 1: Type** — in `admin-ui/src/types.ts` append:
```ts
export interface Taxonomy { id: number; name: string; slug: string; }
```

- [ ] **Step 2: i18n** — create `admin-ui/src/i18n/hu/taxonomy.json`:
```json
{
  "colName": "Név",
  "colSlug": "Slug",
  "colActions": "Műveletek",
  "add": "Új",
  "namePlaceholder": "Név",
  "slugPlaceholder": "slug (nem módosítható később)",
  "save": "Mentés",
  "edit": "Szerkesztés",
  "cancel": "Mégse",
  "delete": "Törlés",
  "saved": "Mentve",
  "saveFailed": "A mentés nem sikerült",
  "blogCategories": "Blog kategóriák",
  "blogTags": "Blog címkék",
  "productTags": "Termék címkék"
}
```
Create `admin-ui/src/i18n/en/taxonomy.json` with the same keys, English values. Register in `admin-ui/src/i18n/index.ts` (import both, add `"taxonomy"` to the `hu`/`en` resources objects + the `ns` array), following the existing per-namespace pattern. Add `nav:blogCategories`, `nav:blogTags`, `nav:productTags` keys to `hu/nav.json` + `en/nav.json`.

- [ ] **Step 3: Shared component** — create `admin-ui/src/components/TaxonomyAdmin.tsx`. Model it on `admin-ui/src/pages/settings/social.tsx` (read it) — a table with inline add/edit/delete. Props: `{ resource: string; titleKey: string }`. Behaviour:
  - `useTable<Taxonomy>({ resource, pagination: { mode: "off" } })`; sort a copy by `name`.
  - Columns: **Név** (inline-editable via an `Input` when the row is in edit mode, else text), **Slug** (always read-only text), **Műveletek** (Szerkesztés/Mentés+Mégse, Törlés).
  - Add row at the bottom: name `Input` + slug `Input` + **Új** button → `useCreate({ resource, values: { name, slug } })`.
  - Rename: **Szerkesztés** puts the row in edit mode (name editable only, slug shown read-only); **Mentés** → `useUpdate({ resource, id, values: { name } })` (slug NOT sent). Cancel exits edit mode.
  - Delete: `useDelete({ resource, id })`.
  - `onSuccess` → `message.success(t("taxonomy:saved"))` + refetch; `onError` → `message.error(t("taxonomy:saveFailed"))`.
  - Title: `<h1>{t(titleKey)}</h1>`. All strings via `t("taxonomy:…")`.
  Keep the create-slug field visible only for the add row (existing rows never edit slug).

- [ ] **Step 4: Three screen wrappers** — thin components:
`admin-ui/src/pages/blog/categories.tsx`:
```tsx
import { TaxonomyAdmin } from "../../components/TaxonomyAdmin";
export const BlogCategories = () => <TaxonomyAdmin resource="blog/categories" titleKey="taxonomy:blogCategories" />;
```
`admin-ui/src/pages/blog/tags.tsx`:
```tsx
import { TaxonomyAdmin } from "../../components/TaxonomyAdmin";
export const BlogTags = () => <TaxonomyAdmin resource="blog/tags" titleKey="taxonomy:blogTags" />;
```
`admin-ui/src/pages/products/tags.tsx`:
```tsx
import { TaxonomyAdmin } from "../../components/TaxonomyAdmin";
export const ProductTags = () => <TaxonomyAdmin resource="products/tags" titleKey="taxonomy:productTags" />;
```

- [ ] **Step 5: Register resources + routes + nav** — in `admin-ui/src/App.tsx` import the three screens and add to `resources`:
```tsx
            { name: "blog/categories", list: "/blog/categories", meta: { label: "Blog kategóriák", parent: "blog/posts" } },
            { name: "blog/tags", list: "/blog/tags", meta: { label: "Blog címkék", parent: "blog/posts" } },
            { name: "products/tags", list: "/products/tags", meta: { label: "Termék címkék", parent: "products" } },
```
Add routes inside the protected `<Route>` block:
```tsx
              <Route path="/blog/categories" element={<BlogCategories />} />
              <Route path="/blog/tags" element={<BlogTags />} />
              <Route path="/products/tags" element={<ProductTags />} />
```
(Ensure these static paths are declared so they aren't shadowed by any dynamic `/blog/:x` route; if `/blog/edit/:id` etc. exist, static `/blog/categories` and `/blog/tags` rank higher in React Router v6 — fine.)
In `admin-ui/src/components/layout/nav.config.ts`, add three `NavRow`s (reuse defined `ICON` keys) routing to `/blog/categories`, `/blog/tags`, `/products/tags` with `labelKey` `nav:blogCategories` / `nav:blogTags` / `nav:productTags`.

- [ ] **Step 6: Build + test + commit**

Run (from `admin-ui/`): `npm run build` → tsc + vite succeed (fix any antd `Table`/`Input` generic typings the compiler flags). `npm test` → suite green.
```bash
git add admin-ui/src/components/TaxonomyAdmin.tsx admin-ui/src/pages/blog/categories.tsx \
        admin-ui/src/pages/blog/tags.tsx admin-ui/src/pages/products/tags.tsx \
        admin-ui/src/types.ts admin-ui/src/App.tsx admin-ui/src/components/layout/nav.config.ts \
        admin-ui/src/i18n/
git commit -m "feat(admin-ui): shared TaxonomyAdmin + blog categories/tags + product tags screens"
```

---

## Manual verification (after Task 4; human)

1. Build SPA + run app (Flyway applies V33). Open `/admin/blog/categories`, `/admin/blog/tags`, `/admin/products/tags`.
2. Each: add a new one (name+slug), rename it (name changes, slug stays), delete it. Duplicate slug shows an error.
3. Confirm renamed blog category/tag slug is unchanged on the public side (URLs intact).

## Self-Review

- **Spec coverage:** product-tag nullable + repo (T1); blog-tag CRUD (T2); product-tag CRUD (T3); shared component + 3 screens + wiring (T4). Blog category CRUD reused (no backend task needed — `updateCategory` is already name-only). All spec sections covered.
- **Placeholder scan:** concrete migration/entities/services/controllers/handlers; T2/T3/T4 ITs and the shared component reference named mirror files (`SocialLinkControllerIT`, `social.tsx`) for boilerplate only, with exact tests/props/paths given.
- **Type consistency:** `TagView(id,name,slug)` / `TagUpsert(name,slug)` / `RenameRequest(name)` identical across blog-tag and product-tag services; REST paths `/api/admin/blog/tags`, `/api/admin/products/tags` match the Refine resources `blog/tags`, `products/tags`; blog categories reuse `blog/categories`; rename = name-only everywhere (slug omitted in the PUT body).
- **Constraints:** slug immutable on rename (#7); product-tag manual create → null external_id; audit on writes; path-based auth; V33 additive; cross-branch caveat noted.
