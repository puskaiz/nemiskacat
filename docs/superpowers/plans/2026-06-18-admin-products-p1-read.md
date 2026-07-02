# Admin Products — P1 (read) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the real product catalog to the admin (read-only) — list + detail + categories REST endpoints — and wire the already-faithful Products grid/list + category chips + a new read-only detail view to real data.

**Architecture:** Mirror the existing `OrderAdminQueryService` + `OrderAdminController` pattern: a read-only `ProductAdminQueryService` (DTOs) + `ProductAdminController` under `/api/admin/products`, ADMIN-gated by the existing SecurityConfig. Reuse `WebshopProperties.imageUrl(storageKey)` for image URLs and mirror `CatalogQueryService.variantLabel` for variant labels. Frontend: swap the static fixtures in the ported Products pages for Refine data hooks against a new `products` resource. No writes (P2/P3 add editing behind a flag).

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, JUnit + Testcontainers (Postgres); admin SPA Refine + antd + react-router; Vite/Vitest. Spec: `docs/superpowers/specs/2026-06-18-admin-products-management-design.md`. ADR: `docs/adr/0005-product-catalog-ownership.md`.

**Test philosophy:** Backend money/inventory rules unaffected (read-only). Query service + controller tested with Testcontainers, seeding the catalog via the existing `CatalogImporter` (as `RefundServiceTest` does). Frontend gate = `npm run build` + existing `npm run test`.

---

## File Structure

- **Modify** `src/main/java/hu/deposoft/webshop/domain/catalog/ProductRepository.java` — add a paginated admin search finder.
- **Create** `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java` — read DTOs + `list`/`detail`/`categories`.
- **Create** `src/main/java/hu/deposoft/webshop/api/admin/ProductAdminController.java` — `GET /api/admin/products`, `/{id}`, and `GET /api/admin/categories`.
- **Create** tests: `ProductAdminQueryServiceTest.java`, `ProductAdminControllerTest.java`.
- **Modify** admin-ui: `src/App.tsx` (register `products` resource + `/products/show/:id` route), `src/pages/products/index.tsx` + `src/pages/categories/index.tsx` (wire to real API), **create** `src/pages/products/show.tsx` (read-only detail), `src/types.ts` (+ product types).

---

## Task 1: Backend — ProductAdminQueryService + repository finder

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/catalog/ProductRepository.java`
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryServiceTest.java`:
```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class ProductAdminQueryServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductAdminQueryService products;
    @Autowired hu.deposoft.webshop.application.catalog.CatalogImporter importer;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of());
        SourceProduct stamp = new SourceProduct(101L, "pecset", "Pecsét", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "PEC-1", 1900L, null, null, null, true, 4, 250, List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint, stamp)));
    }

    @Test
    void listReturnsProductsWithDefaultVariantPriceAndStock() {
        ProductAdminQueryService.PageResult page = products.list(null, null, 0, 20);
        assertThat(page.total()).isEqualTo(2);
        ProductAdminQueryService.ProductSummary fes = page.items().stream()
                .filter(p -> p.slug().equals("festek")).findFirst().orElseThrow();
        assertThat(fes.name()).isEqualTo("Festék");
        assertThat(fes.priceGrossHuf()).isEqualTo(3700L);
        assertThat(fes.stockQty()).isEqualTo(10);
        assertThat(fes.primaryCategory()).isEqualTo("Kat");
        assertThat(fes.variantCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void listFiltersByCategoryAndQuery() {
        assertThat(products.list("kat", null, 0, 20).total()).isEqualTo(2);
        assertThat(products.list(null, "pecs", 0, 20).items())
                .singleElement().satisfies(p -> assertThat(p.slug()).isEqualTo("pecset"));
        assertThat(products.list("nincs-ilyen", null, 0, 20).total()).isZero();
    }

    @Test
    void detailMapsVariantsCategoriesAndStatus() {
        Long id = products.list(null, "fest", 0, 20).items().get(0).id();
        ProductAdminQueryService.ProductDetailView d = products.detail(id);
        assertThat(d.name()).isEqualTo("Festék");
        assertThat(d.slug()).isEqualTo("festek");
        assertThat(d.variants()).isNotEmpty();
        assertThat(d.variants().get(0).sku()).isEqualTo("FES-1");
        assertThat(d.variants().get(0).regularPriceHuf()).isEqualTo(3700L);
        assertThat(d.categories()).extracting(ProductAdminQueryService.CategoryRef::name).contains("Kat");
    }

    @Test
    void detailThrowsForUnknownId() {
        assertThatThrownBy(() -> products.detail(999999L))
                .isInstanceOf(hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException.class);
    }

    @Test
    void categoriesListsImportedCategories() {
        assertThat(products.categories()).extracting(ProductAdminQueryService.CategoryRef::slug).contains("kat");
    }
}
```
(The `SourceProduct`/`SourceCategory` constructor arities match the ones used in `RefundServiceTest`/`ProductAdminQueryServiceTest` seeds — if the importer DTO signatures differ in this codebase, copy the exact seed shape from `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java`.)

- [ ] **Step 2: Run it — expect failure**

Run: `mvn -q -Dtest=ProductAdminQueryServiceTest test`
Expected: COMPILE FAILURE — `ProductAdminQueryService` doesn't exist. (Requires Docker.)

- [ ] **Step 3: Add the repository finder**

In `src/main/java/hu/deposoft/webshop/domain/catalog/ProductRepository.java` add (with imports `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`):
```java
    /**
     * Admin product search: optional category-slug and name-substring filters, paginated.
     * DISTINCT because the category join can multiply rows.
     */
    @Query("""
            select distinct p from Product p
            left join p.categories c
            where (:category is null or c.slug = :category)
              and (:q is null or lower(p.name) like lower(concat('%', :q, '%')))
            """)
    Page<Product> adminSearch(@Param("category") String category, @Param("q") String q, Pageable pageable);
```

- [ ] **Step 4: Implement `ProductAdminQueryService`**

Create `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java`:
```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.WebshopProperties;
import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.Variant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only admin views over the real catalog (P1). Editing is P2/P3 (flag-gated). */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductAdminQueryService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final WebshopProperties properties;

    public record ProductSummary(Long id, String name, String slug, String primaryCategory,
                                 Long priceGrossHuf, int stockQty, ProductStatus status, int variantCount) {}
    public record VariantView(Long id, String label, String sku, Long regularPriceHuf, Long salePriceHuf,
                              int stockQty, int lowStockThreshold) {}
    public record ImageView(String url, String alt) {}
    public record CategoryRef(String name, String slug) {}
    public record ProductDetailView(Long id, String name, String slug, ProductStatus status,
                                    String shortDescription, String description, String seoTitle,
                                    String metaDescription, Integer vatRatePercent,
                                    List<CategoryRef> categories, List<ImageView> images,
                                    List<VariantView> variants) {}
    public record PageResult(List<ProductSummary> items, long total) {}

    public PageResult list(String category, String q, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        String cat = (category == null || category.isBlank()) ? null : category.trim();
        String term = (q == null || q.isBlank()) ? null : q.trim();
        Page<Product> result = products.adminSearch(cat, term, PageRequest.of(safePage, safeSize));
        List<ProductSummary> items = result.getContent().stream().map(this::toSummary).toList();
        return new PageResult(items, result.getTotalElements());
    }

    public ProductDetailView detail(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
        List<CategoryRef> cats = p.getCategories().stream().map(c -> new CategoryRef(c.getName(), c.getSlug())).toList();
        List<ImageView> imgs = images.findByProductOrderByPositionAsc(p).stream()
                .map(i -> new ImageView(properties.imageUrl(i.getStorageKey()), i.getAlt())).toList();
        List<VariantView> variants = p.getVariants().stream()
                .sorted(Comparator.comparingInt(Variant::getPosition))
                .map(this::toVariant).toList();
        return new ProductDetailView(p.getId(), p.getName(), p.getSlug(), p.getStatus(),
                p.getShortDescription(), p.getDescription(), p.getSeoTitle(), p.getMetaDescription(),
                p.getVatRatePercent(), cats, imgs, variants);
    }

    public List<CategoryRef> categories() {
        return categories.findAll().stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(c -> new CategoryRef(c.getName(), c.getSlug())).toList();
    }

    private ProductSummary toSummary(Product p) {
        Variant def = defaultVariant(p);
        String cat = p.getCategories().stream().findFirst().map(Category::getName).orElse("—");
        return new ProductSummary(p.getId(), p.getName(), p.getSlug(), cat,
                def == null ? null : def.getRegularPriceHuf(),
                def == null ? 0 : qty(def), p.getStatus(), p.getVariants().size());
    }

    private VariantView toVariant(Variant v) {
        return new VariantView(v.getId(), label(v), v.getSku(), v.getRegularPriceHuf(), v.getSalePriceHuf(),
                qty(v), v.getLowStockThreshold());
    }

    private Variant defaultVariant(Product p) {
        return p.getVariants().stream().filter(Variant::isDefault).findFirst()
                .orElseGet(() -> p.getVariants().stream().min(Comparator.comparingInt(Variant::getPosition)).orElse(null));
    }

    private int qty(Variant v) {
        return v.getLastSyncQty() == null ? 0 : v.getLastSyncQty();
    }

    /** Mirror of CatalogQueryService.variantLabel: SKU is a safe label in P1. */
    private String label(Variant v) {
        return v.isDefault() ? "Alap" : v.getSku();
    }
}
```
NOTE — verify against the real code before finishing:
- `ProductImageRepository` has `findByProductOrderByPositionAsc(Product)` returning `List<ProductImage>`. If only `findFirstByProductOrderByPositionAsc` exists, add the list finder to that repository.
- `WebshopProperties.imageUrl(String)` exists (used by `CatalogQueryService`).
- `Category` has `getSortOrder()`; `Variant` has `getRegularPriceHuf()`, `getSalePriceHuf()`, `getLastSyncQty()`, `getLowStockThreshold()`, `isDefault()`, `getPosition()`, `getSku()` (Lombok `@Getter`). If a richer variant label exists (`CatalogQueryService.variantLabel`), mirror it instead of the SKU fallback.

- [ ] **Step 5: Run — expect pass**

Run: `mvn -q -Dtest=ProductAdminQueryServiceTest test`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/catalog/ProductRepository.java \
        src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryServiceTest.java
git commit -m "feat(admin): read-only product catalog query service (list/detail/categories)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Backend — ProductAdminController

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/api/admin/ProductAdminController.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/ProductAdminControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/hu/deposoft/webshop/api/admin/ProductAdminControllerTest.java`, mirroring `OrderAdminControllerTest` (same `@SpringBootTest` + `webAppContextSetup(context).apply(springSecurity())` + `@WithMockUser(roles="ADMIN")` setup — copy that test's class skeleton and seeding). Assertions:
```java
    @Test
    void listReturnsProductsWithTotalCountHeader() throws Exception {
        mvc.perform(get("/api/admin/products").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void detailReturnsVariants() throws Exception {
        // resolve an id from the list, then GET /{id}; assert 200 + variants[0].sku present
    }

    @Test
    void categoriesEndpointReturnsList() throws Exception {
        mvc.perform(get("/api/admin/categories").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").exists());
    }
```
Copy the exact MockMvc/security harness, seeding (via `CatalogImporter`), and id-resolution helper from `src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java` — do not invent a different harness.

- [ ] **Step 2: Run — expect failure (no controller)**

Run: `mvn -q -Dtest=ProductAdminControllerTest test` → COMPILE/404 failure.

- [ ] **Step 3: Implement the controller**

Create `src/main/java/hu/deposoft/webshop/api/admin/ProductAdminController.java` (mirror `OrderAdminController`):
```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminQueryService;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.CategoryRef;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.PageResult;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductSummary;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only product + category views for the admin SPA (P1). ADMIN-gated by SecurityConfig. */
@RestController
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductAdminQueryService query;

    @GetMapping("/api/admin/products")
    public List<ProductSummary> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletResponse response) {
        PageResult result = query.list(category, q, page, size);
        response.setHeader("X-Total-Count", String.valueOf(result.total()));
        return result.items();
    }

    @GetMapping("/api/admin/products/{id}")
    public ProductDetailView detail(@PathVariable Long id) {
        return query.detail(id);
    }

    @GetMapping("/api/admin/categories")
    public List<CategoryRef> categories() {
        return query.categories();
    }
}
```
Verify SecurityConfig already gates `/api/admin/**` to ROLE_ADMIN (the orders endpoints rely on the same) — if it's path-listed per-controller, add `/api/admin/products` + `/api/admin/categories`. Confirm `/api/admin/categories` doesn't collide with any existing mapping.

- [ ] **Step 4: Run — expect pass**

Run: `mvn -q -Dtest=ProductAdminControllerTest test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/ProductAdminController.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductAdminControllerTest.java
git commit -m "feat(admin): product + category read REST endpoints (/api/admin/products, /categories)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Frontend — wire Products list + Categories to the real API

**Files:**
- Modify: `admin-ui/src/types.ts` (add product types)
- Modify: `admin-ui/src/App.tsx` (register `products` resource + show route)
- Modify: `admin-ui/src/pages/products/index.tsx`, `admin-ui/src/pages/categories/index.tsx`

- [ ] **Step 1: Add types** to `admin-ui/src/types.ts`:
```ts
export type ProductStatus = "PUBLISHED" | "DRAFT";
export interface ProductSummary {
  id: number; name: string; slug: string; primaryCategory: string;
  priceGrossHuf: number | null; stockQty: number; status: ProductStatus; variantCount: number;
}
export interface ProductVariantView {
  id: number; label: string; sku: string; regularPriceHuf: number | null;
  salePriceHuf: number | null; stockQty: number; lowStockThreshold: number;
}
export interface ProductCategoryRef { name: string; slug: string }
export interface ProductImageView { url: string; alt: string | null }
export interface ProductDetail {
  id: number; name: string; slug: string; status: ProductStatus;
  shortDescription: string | null; description: string | null; seoTitle: string | null;
  metaDescription: string | null; vatRatePercent: number | null;
  categories: ProductCategoryRef[]; images: ProductImageView[]; variants: ProductVariantView[];
}
```

- [ ] **Step 2: Register the `products` resource + show route in `App.tsx`**

The `products` resource currently has `list: "/products"`. Add `show: "/products/show/:id"`. The Refine `dataProvider` already maps resource name → `/api/admin/<name>` and reads `X-Total-Count` (orders works this way). Add the route inside the protected `<Route>` block:
```tsx
  <Route path="/products">
    <Route index element={<Products />} />
    <Route path="categories" element={<Categories />} />
    <Route path="show/:id" element={<ProductShow />} />
  </Route>
```
and import `ProductShow` from `./pages/products/show` (created in Task 4). Update the `products` resource entry: `{ name: "products", list: "/products", show: "/products/show/:id", meta: { label: "Termékek" } }`.

- [ ] **Step 3: Wire `pages/products/index.tsx` to real data**

Replace the static fixture import/usage with Refine's `useList<ProductSummary>({ resource: "products", pagination: { current, pageSize: 8 }, filters })` (keep the existing faithful markup — grid + list + chips + the shared `Pagination`). Specifics:
- `const [current, setCurrent] = useState(1);` `const [category, setCategory] = useState<string | undefined>();` `const [view, setView] = useState<"grid"|"list">("grid");`
- products query: `useList<ProductSummary>({ resource: "products", pagination: { current, pageSize: 8 }, filters: category ? [{ field: "category", operator: "eq", value: category }] : [] })`.
- categories for the chips: `useList<ProductCategoryRef>({ resource: "categories", pagination: { mode: "off" } })`; render "Összes" + one chip per category; active = `category` (Összes clears).
- rows = `data?.data ?? []`; total = `data?.total ?? 0`; map each `ProductSummary` to the grid card / list row: name, `primaryCategory`, price = `priceGrossHuf` formatted `n.toLocaleString("hu-HU")} Ft`, stock = `stockQty`, status via `<StatusPill status={s} label={s === "PUBLISHED" ? "Közzétéve" : "Vázlat"} />`. Row/card click → `useNavigate()` → `/products/show/${id}`.
- `<Pagination current={current} pageSize={8} total={total} onChange={setCurrent} />`.
- Remove the now-unused `src/data/products.ts` import (delete the file if nothing else uses it).

- [ ] **Step 4: Wire `pages/categories/index.tsx` to real data**

Replace the fixture with `useList<ProductCategoryRef>({ resource: "categories", pagination: { mode: "off" } })`; render the faithful table (Kategória name / Slug monospace). The categories endpoint returns name + slug only; drop the product-count/status columns OR show "—" for them (P1 endpoint doesn't compute counts) — keep the table shape, render "—" where data is absent. Remove the unused `src/data/categories.ts` import.

- [ ] **Step 5: Build + tests**

Run (from `admin-ui/`): `npm run build` → must pass. `npm run test` → 20 pass.

- [ ] **Step 6: Commit**

```bash
git add admin-ui/src/types.ts admin-ui/src/App.tsx admin-ui/src/pages/products admin-ui/src/pages/categories admin-ui/src/data
git commit -m "feat(admin-ui): wire Products list + Categories to the real catalog API

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Frontend — read-only product detail view

**Files:**
- Create: `admin-ui/src/pages/products/show.tsx`

- [ ] **Step 1: Build the read-only detail**

Create `admin-ui/src/pages/products/show.tsx`, export `ProductShow`. Use `useShow<ProductDetail>()` (resource inferred from the route) → `queryResult.data?.data`. Render the prototype product-detail layout (Admin Mid-fi HU.dc.html lines 270-295) **read-only**, as plain divs + inline styles + `var(--…)` tokens (match the established faithful-port style in `src/pages/orders/show.tsx`):
- back link "← Vissza a termékekhez" → `useNavigate()` → `/products`.
- header: product name (23/700) + status pill (`StatusPill`, PUBLISHED→"Közzétéve"/DRAFT→"Vázlat"). No edit/save buttons in P1.
- left card: name, description (render `description` text; if null show "—"), image gallery from `images` (cover + thumbs; if empty show the placeholder boxes), and the **Variants table** (Variáns `label` / SKU `sku` / Ár `regularPriceHuf` formatted / Készlet `stockQty`).
- right column: "Árazás és készlet" card (price = default/first variant `regularPriceHuf` formatted, "ÁFA-kulcs" = `vatRatePercent`% , "Összes készlet" = sum of variant `stockQty`), and "Szervezés" card (Kategória = `categories[0]?.name ?? "—"`, Állapot = status label). All read-only display (no inputs).
- loading: if `!order`/`!product` show `t("loading")` (reuse the `common` namespace) or a simple placeholder.

Use a small `auth`/`products` i18n namespace key set as needed, or reuse `nav:products` for the title context; keep new strings in the existing `products` namespace (add keys for the detail labels: description, images, variants, sku, price, stock, vat, totalStock, category, status, back).

- [ ] **Step 2: Build**

Run: `npm run build` → must pass (App.tsx imports `ProductShow`).

- [ ] **Step 3: Commit**

```bash
git add admin-ui/src/pages/products/show.tsx admin-ui/src/i18n
git commit -m "feat(admin-ui): read-only product detail view (variants/pricing/organization)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Full verify

- [ ] **Step 1:** `mvn verify` → BUILD SUCCESS (all backend tests incl. the two new ones + ArchUnit `ModularityTest`; requires Docker).
- [ ] **Step 2:** from `admin-ui/`: `npm run build` + `npm run test` green.
- [ ] **Step 3:** Commit nothing new (verification only); if ArchUnit flags the new `application/catalog` → `config`/`domain` dependency, confirm it mirrors `CatalogQueryService`'s existing dependencies (which already inject `WebshopProperties`) — it should pass.

---

## Self-Review

- **Spec coverage (P1 §):** read list endpoint w/ X-Total-Count + category/q filters → Task 1 (finder) + Task 2 (controller); detail endpoint → Tasks 1/2; categories endpoint → Tasks 1/2; default-variant price/stock + numeric stock display → `toSummary`/`qty`; frontend list+chips wired → Task 3; read-only detail view → Task 4; no writes / no flag (P2/P3) → correctly absent. Storefront availability rule untouched (admin shows `lastSyncQty`, a deliberate admin-only numeric per spec).
- **Placeholder scan:** code is concrete; the two "verify against real code" notes (image-repo finder name, variant label) are explicit verification steps, not deferred work — the fallback (`label = SKU`/add the list finder) is specified.
- **Type consistency:** `ProductSummary`/`ProductDetailView`/`VariantView`/`CategoryRef`/`PageResult` (backend) ↔ `ProductSummary`/`ProductDetail`/`ProductVariantView`/`ProductCategoryRef`/`ProductImageView` (frontend types) match field names; `priceGrossHuf`/`stockQty`/`primaryCategory`/`variantCount` consistent list↔card; resource name `products`/`categories` consistent across App.tsx, useList calls, and the controller paths.

## Notes for the executor
- `NotFoundException` is reused from `OrderAdminQueryService` (as the order code does) — surfaces as the shared admin 404; don't create a new type.
- P1 is read-only: no money/stock writes, no feature flag. The editor (P2) and stock mask (P3) are separate plans.
- Backend gate `mvn verify` (Docker); frontend gate `npm run build` + `npm run test`.
