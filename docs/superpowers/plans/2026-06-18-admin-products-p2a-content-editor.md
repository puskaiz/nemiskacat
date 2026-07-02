# Admin Products P2a — content editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Behind a feature flag, let an admin edit a product's webshop content (name, descriptions, SEO, status, categories) — writing only to this app's DB — turning the P1 read-only detail into an editable form.

**Architecture:** A config flag `webshop.admin.product-editor-enabled` (default off) delivered to the SPA via `GET /api/admin/auth/me`. A `ProductAdminEditService.updateContent` (flag-guarded) mutates the `Product` (Lombok setters; categories replaced by slug) and returns the P1 `ProductDetailView`. A `PUT /api/admin/products/{id}` endpoint. Frontend: `pages/products/show.tsx` renders editable inputs + Save when the flag is on, else stays the P1 read-only view. No price/variants/images here (P2b/P2c). Slug locked; Woo importer untouched.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, JUnit + Testcontainers; admin SPA Refine + react-router + Vite/Vitest. Spec: `docs/superpowers/specs/2026-06-18-admin-products-p2-editor-design.md` (P2a). ADR 0005.

**Test philosophy:** Backend tested with Testcontainers (seed via `CatalogImporter`); the flag toggled via `@SpringBootTest(properties=...)`. Frontend gate = `npm run build` + existing `npm run test`. No money/stock writes in P2a.

---

## File Structure

- **Create** `src/main/java/hu/deposoft/webshop/config/AdminProperties.java` — `webshop.admin` flag.
- **Modify** `src/main/java/hu/deposoft/webshop/api/admin/AdminAuthController.java` — `MeResponse` carries the flag; inject `AdminProperties`.
- **Create** `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminEditService.java` — `updateContent` + `ContentUpdate` + `EditorDisabledException`.
- **Modify** `src/main/java/hu/deposoft/webshop/api/admin/ProductAdminController.java` — add `PUT /{id}`.
- **Modify** `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java` — map `EditorDisabledException` → 403.
- **Create/Modify** tests: `ProductAdminEditServiceTest`, extend `ProductAdminControllerTest` (+ a flag-off case).
- **Modify** admin-ui: `src/types.ts` (`AdminIdentity` + flag), `src/providers/authProvider.ts` (return flag), `src/pages/products/show.tsx` (editable when flag on), `src/i18n/{hu,en}/products.json` (edit labels).

---

## Task 1: Feature flag + `/me` delivery

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/config/AdminProperties.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminAuthController.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/AdminAuthMeFlagTest.java`

- [ ] **Step 1: Create the flag config**

`src/main/java/hu/deposoft/webshop/config/AdminProperties.java`:
```java
package hu.deposoft.webshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Admin-side feature flags. The product editor is off until the Woo cutover (ADR 0005). */
@ConfigurationProperties(prefix = "webshop.admin")
public record AdminProperties(@DefaultValue("false") boolean productEditorEnabled) {
}
```
Verify how `@ConfigurationProperties` records are registered: if the main `@SpringBootApplication` class (or a config) has `@ConfigurationPropertiesScan`, this is auto-registered. If instead properties are listed in `@EnableConfigurationProperties({...})`, add `AdminProperties.class` there. (Check how `WebshopProperties` is registered and mirror it.)

- [ ] **Step 2: Add the property to `application.yml`**

Under the existing `webshop:` block, add:
```yaml
  admin:
    product-editor-enabled: ${ADMIN_PRODUCT_EDITOR_ENABLED:false}
```

- [ ] **Step 3: Extend `MeResponse` + deliver the flag**

In `AdminAuthController.java`:
- Add `AdminProperties` to the constructor deps: `private final AdminProperties adminProperties;` (the class is `@RequiredArgsConstructor`).
- Change the record: `public record MeResponse(String email, String role, boolean productEditorEnabled) {}`.
- In `login(...)`, the success return becomes `ResponseEntity.ok(new MeResponse(auth.getName(), "ADMIN", adminProperties.productEditorEnabled()))`.
- In `me(...)`: `return new MeResponse(authentication.getName(), "ADMIN", adminProperties.productEditorEnabled());`.

- [ ] **Step 4: Write the test (flag surfaced in `/me`)**

`src/test/java/hu/deposoft/webshop/api/admin/AdminAuthMeFlagTest.java` — mirror the security/MockMvc harness of `OrderAdminControllerTest` (same `@SpringBootTest` + Testcontainers + `webAppContextSetup(context).apply(springSecurity())`), with the flag ON:
```java
@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
// ...same @Testcontainers + setUp() building `mvc` as OrderAdminControllerTest...
class AdminAuthMeFlagTest {
    @Test
    void meReportsProductEditorEnabled() throws Exception {
        mvc.perform(get("/api/admin/auth/me").with(user("a@b.hu").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productEditorEnabled").value(true));
    }
}
```
(Copy the exact `@Container`/`@ServiceConnection` + `webAppContextSetup` setup from `OrderAdminControllerTest`.)

- [ ] **Step 5: Run → fail then pass**

Run: `mvn -q -Dtest=AdminAuthMeFlagTest test` → fails (record arity / field missing) until Steps 1-3 done, then PASS. (Docker required.)

- [ ] **Step 6: Commit**
```bash
git add src/main/java/hu/deposoft/webshop/config/AdminProperties.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminAuthController.java \
        src/main/resources/application.yml \
        src/test/java/hu/deposoft/webshop/api/admin/AdminAuthMeFlagTest.java
git commit -m "feat(admin): product-editor feature flag (webshop.admin) surfaced in /me

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
(Include the main app/config class if you had to register `AdminProperties` there.)

---

## Task 2: Content update service + endpoint

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminEditService.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/ProductAdminController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminEditServiceTest.java`, extend `ProductAdminControllerTest.java`

- [ ] **Step 1: Write the failing service test**

`src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminEditServiceTest.java` — seed via `CatalogImporter` exactly like `ProductAdminQueryServiceTest` (copy its `@BeforeEach` seed). Flag ON for this class:
```java
@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
@Testcontainers
@Transactional
class ProductAdminEditServiceTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @Autowired ProductAdminEditService edit;
    @Autowired ProductAdminQueryService query;
    @Autowired hu.deposoft.webshop.application.catalog.CatalogImporter importer;
    // @BeforeEach seed(): copy from ProductAdminQueryServiceTest (paint + a "kat" category)

    private Long festekId() { return query.list(null, "fest", 0, 20).items().get(0).id(); }

    @Test
    void updatesContentFieldsAndCategories() {
        Long id = festekId();
        var view = edit.updateContent(id, new ProductAdminEditService.ContentUpdate(
                "Festék Pro", "rövid", "hosszú leírás", "SEO cím", "meta", 
                hu.deposoft.webshop.domain.catalog.ProductStatus.DRAFT, java.util.List.of("kat")));
        assertThat(view.name()).isEqualTo("Festék Pro");
        assertThat(view.status()).isEqualTo(hu.deposoft.webshop.domain.catalog.ProductStatus.DRAFT);
        assertThat(view.description()).isEqualTo("hosszú leírás");
        assertThat(view.categories()).extracting(ProductAdminQueryService.CategoryRef::slug).containsExactly("kat");
    }

    @Test
    void rejectsBlankName() {
        Long id = festekId();
        assertThatThrownBy(() -> edit.updateContent(id, new ProductAdminEditService.ContentUpdate(
                "  ", null, null, null, null, hu.deposoft.webshop.domain.catalog.ProductStatus.PUBLISHED, java.util.List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownCategory() {
        Long id = festekId();
        assertThatThrownBy(() -> edit.updateContent(id, new ProductAdminEditService.ContentUpdate(
                "X", null, null, null, null, hu.deposoft.webshop.domain.catalog.ProductStatus.PUBLISHED, java.util.List.of("nope"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```
Plus a separate flag-OFF class:
```java
@SpringBootTest(properties = "webshop.admin.product-editor-enabled=false")
@Testcontainers @Transactional
class ProductAdminEditDisabledTest {
    // same container + seed + festekId()
    @Test void rejectsWhenEditorDisabled() {
        assertThatThrownBy(() -> edit.updateContent(festekId(), new ProductAdminEditService.ContentUpdate(
                "X", null, null, null, null, hu.deposoft.webshop.domain.catalog.ProductStatus.PUBLISHED, java.util.List.of())))
                .isInstanceOf(ProductAdminEditService.EditorDisabledException.class);
    }
}
```

- [ ] **Step 2: Run → expect failure (no service)**

Run: `mvn -q -Dtest=ProductAdminEditServiceTest test` → compile failure.

- [ ] **Step 3: Implement `ProductAdminEditService`**

```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated product content edits (P2a) — webshop fields only; writes to our DB, never Woo. */
@Service
@RequiredArgsConstructor
public class ProductAdminEditService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    /** The product editor is disabled by the feature flag. */
    public static class EditorDisabledException extends RuntimeException {
        public EditorDisabledException(String message) { super(message); }
    }

    public record ContentUpdate(String name, String shortDescription, String description,
                                String seoTitle, String metaDescription, ProductStatus status,
                                List<String> categorySlugs) {}

    @Transactional
    public ProductAdminQueryService.ProductDetailView updateContent(Long id, ContentUpdate cmd) {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (cmd.status() == null) {
            throw new IllegalArgumentException("Product status is required");
        }
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
        p.setName(cmd.name().trim());
        p.setShortDescription(cmd.shortDescription());
        p.setDescription(cmd.description());
        p.setSeoTitle(cmd.seoTitle());
        p.setMetaDescription(cmd.metaDescription());
        p.setStatus(cmd.status());
        // slug intentionally not editable (Woo 1:1, CLAUDE.md)
        Set<Category> resolved = new LinkedHashSet<>();
        for (String slug : (cmd.categorySlugs() == null ? List.<String>of() : cmd.categorySlugs())) {
            resolved.add(categories.findBySlug(slug)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + slug)));
        }
        p.getCategories().clear();
        p.getCategories().addAll(resolved);
        products.save(p);
        return query.detail(id);
    }
}
```

- [ ] **Step 4: Map `EditorDisabledException` → 403**

In `AdminExceptionHandler.java` add (mirroring the existing `@ExceptionHandler`+`@ResponseStatus` handlers that return a plain string):
```java
    @org.springframework.web.bind.annotation.ExceptionHandler(hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    public String editorDisabled(hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException e) {
        return e.getMessage();
    }
```
(`IllegalArgumentException` → 400 and `NotFoundException` → 404 are already mapped.)

- [ ] **Step 5: Add the `PUT` endpoint**

In `ProductAdminController.java`: inject `private final ProductAdminEditService editService;` (add to the constructor — it's `@RequiredArgsConstructor`) and add:
```java
    @org.springframework.web.bind.annotation.PutMapping("/api/admin/products/{id}")
    public ProductDetailView update(@org.springframework.web.bind.annotation.PathVariable Long id,
                                    @org.springframework.web.bind.annotation.RequestBody ProductAdminEditService.ContentUpdate cmd) {
        return editService.updateContent(id, cmd);
    }
```

- [ ] **Step 6: Extend the controller test**

In `ProductAdminControllerTest.java`: add `properties = "webshop.admin.product-editor-enabled=true"` to its `@SpringBootTest` (flag on doesn't affect the existing read tests). Add:
- `updatePersistsContent`: PUT `/api/admin/products/{id}` with a JSON body `{name, status, categorySlugs:["kat"], ...}` as ADMIN + csrf → 200, body `name` updated.
- `updateRejectsBlankName`: PUT with blank name → 400.
- `updateForbiddenForNonAdmin`: PUT without admin role → 403.
And create a sibling `ProductAdminEditEndpointDisabledTest` with `properties = "webshop.admin.product-editor-enabled=false"` asserting PUT → 403 (EditorDisabledException). (Reuse the `OrderAdminControllerTest` harness + the id-resolution helper.)

- [ ] **Step 7: Run → pass**

Run: `mvn -q -Dtest=ProductAdminEditServiceTest,ProductAdminEditDisabledTest,ProductAdminControllerTest,ProductAdminEditEndpointDisabledTest test` → all PASS.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminEditService.java \
        src/main/java/hu/deposoft/webshop/api/admin/ProductAdminController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminEditServiceTest.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductAdminControllerTest.java
git commit -m "feat(admin): flag-gated product content update (PUT /api/admin/products/{id})

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```
(Add the two new flag-off test files too.)

---

## Task 3: Frontend — editable detail when the flag is on

**Files:**
- Modify: `admin-ui/src/types.ts`, `admin-ui/src/providers/authProvider.ts`, `admin-ui/src/pages/products/show.tsx`, `admin-ui/src/i18n/{hu,en}/products.json`

- [ ] **Step 1: Surface the flag in identity**

`src/types.ts` — extend `AdminIdentity`:
```ts
export interface AdminIdentity { email: string; role: string; productEditorEnabled: boolean }
```
`src/providers/authProvider.ts` — in `getIdentity`, return the flag:
```ts
    const me = (await res.json()) as AdminIdentity;
    return { id: me.email, name: me.email, productEditorEnabled: me.productEditorEnabled };
```

- [ ] **Step 2: Make `show.tsx` editable behind the flag**

In `admin-ui/src/pages/products/show.tsx`:
- `const { data: identity } = useGetIdentity<{ productEditorEnabled?: boolean }>();` `const editable = !!identity?.productEditorEnabled;`
- When `!editable`: render exactly the current P1 read-only layout (unchanged).
- When `editable`: render the content fields as inputs — name (text), short description (text), description (textarea), SEO title (text), meta description (textarea), status (`<select>` PUBLISHED/DRAFT), categories (multi-select fed by `useList<ProductCategoryRef>({ resource: "categories", pagination: { mode: "off" } })`) — styled like the prototype field boxes (border 1px var(--border), radius 3, padding "11px 13px", bg var(--input); match `pages/login.tsx`'s `field` style). Seed input state from the loaded product. A **Mentés** primary pill button calls `apiFetch(\`${API_BASE}/products/${id}\`, { method: "PUT", body: JSON.stringify({ name, shortDescription, description, seoTitle, metaDescription, status, categorySlugs }) })`; on ok → success toast + `queryResult.refetch()`; on non-ok → error toast with the response text (red field for 400 on name). Price/variants/images stay read-only (P2b/P2c).
- Use `App.useApp()` `message` for toasts (as orders pages do). `apiFetch`/`API_BASE` from `../../api/http`.

- [ ] **Step 3: i18n**

Add edit labels to `src/i18n/{hu,en}/products.json` (e.g. `editName`, `editShortDesc`, `editDescription`, `editSeoTitle`, `editMeta`, `editStatus`, `editCategories`, `save`, `saved`, `saveFailed`) — hu+en in sync.

- [ ] **Step 4: Build + test**

Run (from `admin-ui/`): `npm run build` (pass) + `npm run test` (20 pass).

- [ ] **Step 5: Commit**
```bash
git add admin-ui/src/types.ts admin-ui/src/providers/authProvider.ts \
        admin-ui/src/pages/products/show.tsx admin-ui/src/i18n
git commit -m "feat(admin-ui): editable product content form behind the product-editor flag

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Full verify

- [ ] **Step 1:** `mvn verify` → BUILD SUCCESS (all backend incl. the new edit tests + ArchUnit; Docker).
- [ ] **Step 2:** from `admin-ui/`: `npm run build` + `npm run test` green.
- [ ] **Step 3:** Manual sanity (optional): with `ADMIN_PRODUCT_EDITOR_ENABLED=true` the product detail shows the editable form + Save persists; with it false (default) the detail is read-only and PUT returns 403.

---

## Self-Review

- **Spec coverage (P2a):** flag `webshop.admin.product-editor-enabled` + default off → Task 1 (`AdminProperties` + yml); `/me` delivery → Task 1 (`MeResponse`); write endpoint name/desc/SEO/status/categories, flag-guarded, DB-only, slug locked → Task 2 (`updateContent` + `PUT`); validation (blank name, unknown slug) + 403/400 mapping → Task 2; editable form behind the flag, read-only when off → Task 3. Price/variants/images correctly absent (P2b/P2c).
- **Placeholder scan:** code is concrete; the one "verify how `@ConfigurationProperties` is registered" note is an explicit check with the fallback stated (`@EnableConfigurationProperties`/`@ConfigurationPropertiesScan`).
- **Type consistency:** `AdminProperties.productEditorEnabled()` ↔ `MeResponse.productEditorEnabled` ↔ SPA `AdminIdentity.productEditorEnabled`; `ContentUpdate(name, shortDescription, description, seoTitle, metaDescription, status, categorySlugs)` identical in service, controller, tests, and the SPA PUT body; returns the P1 `ProductAdminQueryService.ProductDetailView` (reused). `EditorDisabledException`→403, `IllegalArgumentException`→400, `NotFoundException`→404 all via `AdminExceptionHandler`.

## Notes for the executor
- Reuse the shared `OrderAdminQueryService.NotFoundException` (don't make a new one) and the existing `OrderAdminControllerTest` MockMvc/security/seeding harness for the new controller tests.
- The flag default is **off**; the editable UI and the write path must both no-op/403 when off — there are tests for both.
- No money/stock writes in P2a; price (net/gross + VAT) is P2b, images are P2c.
