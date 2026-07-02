# P2b‑1 — Per‑variant price editing (net/gross) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin edit a variant's regular + sale price (with optional sale window) entering net or gross, behind the product‑editor flag; prices stored gross.

**Architecture:** Pure `domain/catalog` VAT math (`VatPricing`, single source of the 27/5 rule — `VatCalculator` delegates) → flag‑gated `ProductVariantPriceService` → thin `PUT …/variants/{variantId}/price` controller returning the updated `ProductDetailView` (which gains a read‑only `effectiveVatRatePercent`). Frontend adds per‑variant net/gross price inputs in the editable branch of `show.tsx`.

**Tech Stack:** Java 21, Spring Boot 4.1 / Spring 7, Spring Data JPA, JUnit 5 + Testcontainers (Postgres) + MockMvc, AssertJ; admin SPA Refine/React + Vitest. Spec: `docs/superpowers/specs/2026-06-19-admin-products-p2b1-variant-pricing-design.md`. Builds on P2a (`AdminProperties` flag, `ProductAdminEditService.EditorDisabledException`, `OrderAdminQueryService.NotFoundException`) and P2c (per‑resource admin endpoint + flag‑off/non‑admin test patterns).

**Important environment notes for the executor:**
- Stack is **Spring Boot 4.1 / Spring 7** (not 3.x). Money is `long` **HUF‑forint** (no subunit). Prices stored **gross**.
- Run backend tests with `mvn -q -o -Dskip.frontend=true -Dtest=... test` in the inner loop (skips the frontend build). Testcontainers needs Docker (available).
- The working branch is `admin-products-p2b1`. Commit **only** the files each task lists (the repo has other active branches; never `git add -A`).
- `AdminExceptionHandler` already maps `EditorDisabledException`→403, `OrderAdminQueryService.NotFoundException`→404, `IllegalArgumentException`→400 (verify by reading it). Reuse — do not add new handlers.

---

## File Structure

- **Create** `src/main/java/hu/deposoft/webshop/domain/catalog/VatPricing.java` — pure VAT rate rule + net↔gross.
- **Modify** `src/main/java/hu/deposoft/webshop/domain/checkout/VatCalculator.java` — delegate the rate rule to `VatPricing`.
- **Modify** `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java` — `ProductDetailView` gains `effectiveVatRatePercent`; `detail()` computes it.
- **Create** `src/main/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceService.java` — flag‑gated price update + request records.
- **Create** `src/main/java/hu/deposoft/webshop/api/admin/ProductVariantPriceController.java` — the endpoint.
- **Modify** `admin-ui/src/types.ts`, `admin-ui/src/pages/products/show.tsx`, `admin-ui/src/i18n/{hu,en}/products.json`.
- **Tests:** `VatPricingTest`, `ProductVariantPriceServiceTest` (+ `ProductVariantPriceDisabledTest`), `ProductVariantPriceControllerTest` (+ `ProductVariantPriceEndpointDisabledTest`).

---

## Task 1: `VatPricing` domain helper (+ `VatCalculator` delegates)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/domain/catalog/VatPricing.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/checkout/VatCalculator.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/catalog/VatPricingTest.java`

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class VatPricingTest {
    @Test void toGrossStandard()        { assertThat(VatPricing.toGross(1000, 27)).isEqualTo(1270); }
    @Test void toGrossReduced()         { assertThat(VatPricing.toGross(1000, 5)).isEqualTo(1050); }
    @Test void toNetExact()             { assertThat(VatPricing.toNet(1270, 27)).isEqualTo(1000); }
    @Test void toNetRounds()            { assertThat(VatPricing.toNet(1000, 27)).isEqualTo(787); } // 787.4 → 787
    @Test void rateForTaxClassReduced() { assertThat(VatPricing.rateForTaxClass("reduced-rate")).isEqualTo(5); }
    @Test void rateForTaxClassDefault() {
        assertThat(VatPricing.rateForTaxClass(null)).isEqualTo(27);
        assertThat(VatPricing.rateForTaxClass("standard")).isEqualTo(27);
    }
    @Test void effectiveExplicitWins()  { assertThat(VatPricing.effectiveRatePercent(18, "reduced-rate")).isEqualTo(18); }
    @Test void effectiveFallsBack() {
        assertThat(VatPricing.effectiveRatePercent(null, "reduced-rate")).isEqualTo(5);
        assertThat(VatPricing.effectiveRatePercent(null, null)).isEqualTo(27);
    }
}
```

- [ ] **Step 2: Run → fail**

Run: `mvn -q -o -Dtest=VatPricingTest test`
Expected: compile failure (`VatPricing` does not exist).

- [ ] **Step 3: Create `VatPricing`**

```java
package hu.deposoft.webshop.domain.catalog;

/**
 * Single source of truth for the HUF VAT rate rule and net↔gross conversion (CLAUDE.md #6).
 * Catalog prices are stored GROSS; net is derived for display only. Pure & stateless.
 */
public final class VatPricing {

    public static final int STANDARD_RATE = 27;
    public static final int REDUCED_RATE = 5;

    private VatPricing() {}

    /** Tax-class default: "reduced-rate" → 5, everything else (incl. null/blank) → 27. */
    public static int rateForTaxClass(String taxClass) {
        return "reduced-rate".equals(taxClass) ? REDUCED_RATE : STANDARD_RATE;
    }

    /** Explicit product rate if set, else the tax-class default. */
    public static int effectiveRatePercent(Integer vatRatePercent, String taxClass) {
        return vatRatePercent != null ? vatRatePercent : rateForTaxClass(taxClass);
    }

    public static long toGross(long net, int ratePercent) {
        return Math.round(net * (100 + ratePercent) / 100.0);
    }

    public static long toNet(long gross, int ratePercent) {
        return Math.round(gross * 100.0 / (100 + ratePercent));
    }
}
```

- [ ] **Step 4: Delegate the rate rule from `VatCalculator`**

Read `src/main/java/hu/deposoft/webshop/domain/checkout/VatCalculator.java` first. It currently declares `STANDARD_RATE = 27`, `REDUCED_RATE = 5`, and `ratePercentFor(String taxClass)` returning `"reduced-rate".equals(...) ? 5 : 27`. Make it the single source by delegating:
- Replace the body of `ratePercentFor(String taxClass)` with `return VatPricing.rateForTaxClass(taxClass);`
- Replace the two rate constants' initializers to reference `VatPricing` (e.g. `public static final int STANDARD_RATE = VatPricing.STANDARD_RATE;` and likewise `REDUCED_RATE`). Keep them so existing callers still compile.
- Leave `breakdown(...)` and everything else unchanged.
- Add `import hu.deposoft.webshop.domain.catalog.VatPricing;` (it's a sibling domain module — `domain/checkout` already depends on `domain/catalog`, so this is allowed).

- [ ] **Step 5: Run → pass (incl. regression)**

Run: `mvn -q -o -Dtest=VatPricingTest,VatCalculatorTest test`
Expected: PASS — `VatPricingTest` (8) + `VatCalculatorTest` (3, unchanged) green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/catalog/VatPricing.java \
        src/main/java/hu/deposoft/webshop/domain/checkout/VatCalculator.java \
        src/test/java/hu/deposoft/webshop/domain/catalog/VatPricingTest.java
git commit -m "feat(catalog): VatPricing (net↔gross + rate rule); VatCalculator delegates

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `ProductDetailView` gains `effectiveVatRatePercent`

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryServiceTest.java`

- [ ] **Step 1: Add a failing assertion**

In `ProductAdminQueryServiceTest.java`, add a test that a seeded product's detail exposes the effective rate (the seed products have no explicit `vatRatePercent`/`reduced-rate` tax class, so the default is 27). Mirror how the existing tests resolve a product id (`query.list(...)` → `.items().get(0).id()`), then:

```java
    @Test
    void detailExposesEffectiveVatRate() {
        Long id = query.list(null, null, 0, 20).items().get(0).id();
        var detail = query.detail(id);
        assertThat(detail.effectiveVatRatePercent()).isEqualTo(27); // no explicit rate/tax-class → standard
    }
```

- [ ] **Step 2: Run → fail**

Run: `mvn -q -o -Dskip.frontend=true -Dtest=ProductAdminQueryServiceTest test`
Expected: compile failure — `effectiveVatRatePercent()` does not exist on the record.

- [ ] **Step 3: Add the field + compute it**

In `ProductAdminQueryService.java`:
- Add the import `import hu.deposoft.webshop.domain.catalog.VatPricing;`
- Change the record to add `Integer effectiveVatRatePercent` immediately after `vatRatePercent`:

```java
    public record ProductDetailView(Long id, String name, String slug, ProductStatus status,
                                    String shortDescription, String description, String seoTitle,
                                    String metaDescription, Integer vatRatePercent,
                                    Integer effectiveVatRatePercent,
                                    List<CategoryRef> categories, List<ImageView> images,
                                    List<VariantView> variants) {}
```
- In `detail(Long id)`, compute and pass it (the `Product` has `getVatRatePercent()` and `getTaxClass()`):

```java
        Integer effectiveRate = VatPricing.effectiveRatePercent(p.getVatRatePercent(), p.getTaxClass());
        return new ProductDetailView(p.getId(), p.getName(), p.getSlug(), p.getStatus(),
                p.getShortDescription(), p.getDescription(), p.getSeoTitle(), p.getMetaDescription(),
                p.getVatRatePercent(), effectiveRate, cats, imgs, variants);
```
(Adjust the local variable names `cats`/`imgs`/`variants` to whatever `detail()` already uses.)

- [ ] **Step 4: Run → pass**

Run: `mvn -q -o -Dskip.frontend=true -Dtest=ProductAdminQueryServiceTest test`
Expected: PASS (all existing query tests + the new one). If any other test constructs `ProductDetailView` directly, update that call site to include the new arg (search: `new ProductDetailView(`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryServiceTest.java
git commit -m "feat(admin): expose effectiveVatRatePercent on product detail

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `ProductVariantPriceService` (flag‑gated)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceServiceTest.java`, `src/test/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceDisabledTest.java`

- [ ] **Step 1: Write the failing service test (Testcontainers, flag ON)**

Mirror the harness of `ProductAdminEditServiceTest` (same `@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")`, `@Testcontainers`/`@ServiceConnection` Postgres, `@Transactional`, and its `CatalogImporter` seed — copy the seed verbatim). Resolve a variant id with `query.detail(query.list(null, null, 0, 20).items().get(0).id()).variants().get(0).id()`.

```java
    @Autowired ProductVariantPriceService priceService;
    @Autowired ProductAdminQueryService query;
    // ... importer + seed copied from ProductAdminEditServiceTest

    private record Ids(Long productId, Long variantId) {}
    private Ids firstVariant() {
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        Long vid = query.detail(pid).variants().get(0).id();
        return new Ids(pid, vid);
    }
    private static ProductVariantPriceService.PriceInput net(long a)   { return new ProductVariantPriceService.PriceInput(a, ProductVariantPriceService.PriceBasis.NET); }
    private static ProductVariantPriceService.PriceInput gross(long a) { return new ProductVariantPriceService.PriceInput(a, ProductVariantPriceService.PriceBasis.GROSS); }

    @Test void netInputStoredAsGross() {
        var ids = firstVariant();
        var d = priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(net(1000), null, null, null)); // 27% default
        var v = d.variants().stream().filter(x -> x.id().equals(ids.variantId())).findFirst().orElseThrow();
        assertThat(v.regularPriceHuf()).isEqualTo(1270L);
        assertThat(v.salePriceHuf()).isNull();
    }

    @Test void grossInputStoredVerbatim() {
        var ids = firstVariant();
        var d = priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), null, null, null));
        var v = d.variants().stream().filter(x -> x.id().equals(ids.variantId())).findFirst().orElseThrow();
        assertThat(v.regularPriceHuf()).isEqualTo(2000L);
    }

    @Test void saleSetThenCleared() {
        var ids = firstVariant();
        priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), gross(1500), null, null));
        var d = priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), null, null, null));
        var v = d.variants().stream().filter(x -> x.id().equals(ids.variantId())).findFirst().orElseThrow();
        assertThat(v.salePriceHuf()).isNull();
    }

    @Test void rejectsNegative() {
        var ids = firstVariant();
        assertThatThrownBy(() -> priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(-1), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsSaleAboveRegular() {
        var ids = firstVariant();
        assertThatThrownBy(() -> priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(1000), gross(2000), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsInvertedWindow() {
        var ids = firstVariant();
        var from = java.time.OffsetDateTime.parse("2026-07-01T00:00:00Z");
        var to   = java.time.OffsetDateTime.parse("2026-06-01T00:00:00Z");
        assertThatThrownBy(() -> priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), gross(1500), from, to)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void variantNotOnProductIs404Cause() {
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        assertThatThrownBy(() -> priceService.updatePrice(pid, 999999L,
                new ProductVariantPriceService.PriceUpdate(gross(100), null, null, null)))
                .isInstanceOf(hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException.class);
    }
```

- [ ] **Step 2: Run → fail**

Run: `mvn -q -o -Dskip.frontend=true -Dtest=ProductVariantPriceServiceTest test`
Expected: compile failure (`ProductVariantPriceService` does not exist).

- [ ] **Step 3: Implement the service**

```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VatPricing;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated per-variant price editing (P2b-1): regular + sale (net or gross) with an optional
 *  sale window. Prices are stored GROSS; net inputs are converted via the product's effective VAT
 *  rate. Variant entities are managed within the transaction, so field writes persist on commit. */
@Service
@RequiredArgsConstructor
public class ProductVariantPriceService {

    public enum PriceBasis { NET, GROSS }
    public record PriceInput(long amount, PriceBasis basis) {}
    public record PriceUpdate(PriceInput regular, PriceInput sale,
                              OffsetDateTime saleFrom, OffsetDateTime saleTo) {}

    private final ProductRepository products;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.ProductDetailView updatePrice(Long productId, Long variantId, PriceUpdate cmd) {
        guard();
        Product p = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("No product " + productId));
        Variant v = p.getVariants().stream().filter(x -> x.getId().equals(variantId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No variant " + variantId + " on product " + productId));

        int rate = VatPricing.effectiveRatePercent(p.getVatRatePercent(), p.getTaxClass());

        Long regularGross = grossOf(cmd.regular(), rate);
        if (regularGross == null) {
            // An unpriced variant cannot be on sale: clear regular, sale and window together.
            v.setRegularPriceHuf(null);
            v.setSalePriceHuf(null);
            v.setSaleFrom(null);
            v.setSaleTo(null);
            return query.detail(productId);
        }

        Long saleGross = grossOf(cmd.sale(), rate);
        if (saleGross != null && saleGross > regularGross) {
            throw new IllegalArgumentException("Sale price must not exceed the regular price");
        }
        if (cmd.saleFrom() != null && cmd.saleTo() != null && cmd.saleFrom().isAfter(cmd.saleTo())) {
            throw new IllegalArgumentException("Sale window start must not be after its end");
        }

        v.setRegularPriceHuf(regularGross);
        if (saleGross == null) {
            v.setSalePriceHuf(null);
            v.setSaleFrom(null);
            v.setSaleTo(null);
        } else {
            v.setSalePriceHuf(saleGross);
            v.setSaleFrom(cmd.saleFrom());
            v.setSaleTo(cmd.saleTo());
        }
        return query.detail(productId);
    }

    private static Long grossOf(PriceInput in, int rate) {
        if (in == null) return null;
        if (in.amount() < 0) throw new IllegalArgumentException("Price must not be negative");
        return in.basis() == PriceBasis.GROSS ? in.amount() : VatPricing.toGross(in.amount(), rate);
    }

    private void guard() {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }
}
```

- [ ] **Step 4: Write the flag‑off test**

`ProductVariantPriceDisabledTest.java` — mirror `ProductAdminEditDisabledTest` (`@SpringBootTest(properties = "webshop.admin.product-editor-enabled=false")` + same seed):

```java
    @Test void updatePriceThrowsWhenEditorDisabled() {
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        Long vid = query.detail(pid).variants().get(0).id();
        assertThatThrownBy(() -> priceService.updatePrice(pid, vid,
                new ProductVariantPriceService.PriceUpdate(
                        new ProductVariantPriceService.PriceInput(1000, ProductVariantPriceService.PriceBasis.GROSS),
                        null, null, null)))
                .isInstanceOf(hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException.class);
    }
```

- [ ] **Step 5: Run → pass**

Run: `mvn -q -o -Dskip.frontend=true -Dtest=ProductVariantPriceServiceTest,ProductVariantPriceDisabledTest test`
Expected: PASS (7 service + 1 disabled).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceService.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceServiceTest.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceDisabledTest.java
git commit -m "feat(admin): flag-gated per-variant price editing (net/gross, sale window)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `ProductVariantPriceController` + endpoint

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/api/admin/ProductVariantPriceController.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/ProductVariantPriceControllerTest.java`, `src/test/java/hu/deposoft/webshop/api/admin/ProductVariantPriceEndpointDisabledTest.java`

- [ ] **Step 1: Write the failing controller test**

Mirror `ProductAdminControllerTest` (MockMvc + Testcontainers + seed; ADMIN auth via `.with(user("admin").roles("ADMIN"))` and `.with(csrf())`; resolve a product id from `GET /api/admin/products` and a variant id from `GET /api/admin/products/{id}`). JSON body matches `PriceUpdate`: `{"regular":{"amount":1000,"basis":"NET"},"sale":null,"saleFrom":null,"saleTo":null}`.

```java
    @Test void putPriceStoresGross() throws Exception {
        long pid = firstProductId();
        long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"NET\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants[?(@.id == " + vid + ")].regularPriceHuf").value(org.hamcrest.Matchers.hasItem(1270)));
    }

    @Test void putPriceRejectsNegative() throws Exception {
        long pid = firstProductId();
        long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":-5,\"basis\":\"GROSS\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isBadRequest());
    }

    @Test void putPriceRejectsSaleAboveRegular() throws Exception {
        long pid = firstProductId();
        long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"GROSS\"},\"sale\":{\"amount\":2000,\"basis\":\"GROSS\"},\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isBadRequest());
    }

    @Test void putPriceUnknownVariantIs404() throws Exception {
        long pid = firstProductId();
        mvc.perform(put("/api/admin/products/" + pid + "/variants/999999/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"GROSS\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isNotFound());
    }

    @Test void putPriceForbiddenForNonAdmin() throws Exception {
        long pid = firstProductId();
        long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("u").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"GROSS\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isForbidden());
    }
```

Copy the `firstProductId()` / `firstVariantId(long)` helpers from how `ProductAdminControllerTest` resolves ids (read it — it parses the list/detail JSON). Reuse its MockMvc + seed setup verbatim.

- [ ] **Step 2: Run → fail**

Run: `mvn -q -o -Dskip.frontend=true -Dtest=ProductVariantPriceControllerTest test`
Expected: 404/handler‑mapping failures or compile failure (controller does not exist).

- [ ] **Step 3: Implement the controller**

```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductVariantPriceService;
import hu.deposoft.webshop.application.catalog.ProductVariantPriceService.PriceUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin: per-variant price editing. All business rules live in ProductVariantPriceService. */
@RestController
@RequestMapping("/api/admin/products/{id}/variants/{variantId}")
@RequiredArgsConstructor
public class ProductVariantPriceController {

    private final ProductVariantPriceService priceService;

    @PutMapping("/price")
    public ProductDetailView updatePrice(@PathVariable Long id, @PathVariable Long variantId,
                                         @RequestBody PriceUpdate body) {
        return priceService.updatePrice(id, variantId, body);
    }
}
```

- [ ] **Step 4: Write the flag‑off endpoint test**

`ProductVariantPriceEndpointDisabledTest.java` — mirror `ProductAdminEditEndpointDisabledTest` (`@SpringBootTest(properties = "webshop.admin.product-editor-enabled=false")`): an ADMIN PUT to the price endpoint returns 403.

```java
    @Test void putPriceForbiddenWhenEditorDisabled() throws Exception {
        long pid = firstProductId();
        long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"GROSS\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isForbidden());
    }
```

- [ ] **Step 5: Run → pass**

Run: `mvn -q -o -Dskip.frontend=true -Dtest=ProductVariantPriceControllerTest,ProductVariantPriceEndpointDisabledTest test`
Expected: PASS (5 + 1).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/ProductVariantPriceController.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductVariantPriceControllerTest.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductVariantPriceEndpointDisabledTest.java
git commit -m "feat(admin): PUT /products/{id}/variants/{variantId}/price endpoint

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Frontend — per‑variant price editing in `show.tsx`

**Files:**
- Modify: `admin-ui/src/types.ts`
- Modify: `admin-ui/src/pages/products/show.tsx`
- Modify: `admin-ui/src/i18n/hu/products.json`, `admin-ui/src/i18n/en/products.json`

- [ ] **Step 1: Types**

In `types.ts`:
- Add `effectiveVatRatePercent: number | null;` to the `ProductDetail` interface.
- Add the request types:
```ts
export type PriceBasis = "NET" | "GROSS";
export interface PriceInput { amount: number; basis: PriceBasis; }
export interface PriceUpdate {
  regular: PriceInput | null;
  sale: PriceInput | null;
  saleFrom: string | null;  // ISO instant (UTC)
  saleTo: string | null;
}
```

- [ ] **Step 2: Add the i18n keys (both files)**

Read the existing `products.json` files to match key style, then add to **both** `hu` and `en`:
```
hu: "priceRegular":"Listaár","priceSale":"Akciós ár","priceNet":"Nettó","priceGross":"Bruttó",
    "priceSaleFrom":"Akció kezdete","priceSaleTo":"Akció vége","priceVatRate":"Áfa",
    "priceSave":"Ár mentése","priceSaved":"Ár mentve","priceClear":"Ár törlése"
en: "priceRegular":"Regular price","priceSale":"Sale price","priceNet":"Net","priceGross":"Gross",
    "priceSaleFrom":"Sale from","priceSaleTo":"Sale to","priceVatRate":"VAT",
    "priceSave":"Save price","priceSaved":"Price saved","priceClear":"Clear price"
```

- [ ] **Step 3: Price editor in the editable branch of `show.tsx`**

Read `show.tsx` first. In the **editable** branch only (the read‑only/flag‑off branch stays unchanged), make each variant row's price editable. For each variant render, alongside its read‑only label/SKU:
- Show the product's effective rate once near the variant section: `{t("products:priceVatRate")}: {product.effectiveVatRatePercent}%`.
- **Regular** and **Sale** each: a numeric `amount` input + a **Nettó/Bruttó** basis toggle (two small buttons or a segmented control using the existing inline‑style idiom + `var(--…)`). Show the computed counterpart as a read‑only hint using the same rounding as the server:
  ```ts
  const toNet = (gross: number, rate: number) => Math.round(gross * 100 / (100 + rate));
  const toGross = (net: number, rate: number) => Math.round(net * (100 + rate) / 100);
  ```
- **Sale window:** two optional `<input type="datetime-local">` (from/to). Convert to ISO UTC on submit (`new Date(local).toISOString()`); empty → `null`.
- A per‑row **Mentés** (`t("products:priceSave")`) button builds the `PriceUpdate` from the row's state (basis from each toggle; `sale`/window `null` when the sale amount is empty) and calls:
  ```ts
  const res = await apiFetch(`${API_BASE}/products/${product.id}/variants/${v.id}/price`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
  ```
  On `res.ok` → `message.success(t("products:priceSaved"))` + `refetch()`; else `message.error(await res.text().catch(() => "..."))`. Use the same `apiFetch`/`API_BASE`/`App.useApp()` message + `refetch` wiring the `GalleryEditor` uses (mirror it).
- Initialise each row's inputs from the current `v.regularPriceHuf`/`v.salePriceHuf` (gross) defaulting the basis toggle to **Bruttó**.

Keep styling consistent with the existing faithful port (plain divs + inline styles + `var(--…)`); do not introduce antd form components the file doesn't already use.

- [ ] **Step 4: Build + test**

Run (from `admin-ui/`): `npm run build` (tsc + vite → success) and `npm run test` (existing suite green).
Expected: build OK; all existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/types.ts admin-ui/src/pages/products/show.tsx \
        admin-ui/src/i18n/hu/products.json admin-ui/src/i18n/en/products.json
git commit -m "feat(admin-ui): per-variant net/gross price editor (regular + sale + window)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Full verify gate

**Files:** none (verification only).

- [ ] **Step 1: Full backend + frontend verify**

Run: `mvn verify` → BUILD SUCCESS (all tests + ArchUnit `ModularityTest` — confirms `VatPricing` in `domain/catalog` and the `VatCalculator` delegation keep module boundaries intact; Docker required).
Then from `admin-ui/`: `npm run build` && `npm run test` → green.

- [ ] **Step 2: Manual sanity (optional)**

With the editor flag on, open a product in the admin, set a regular price as **Nettó** and confirm the stored/displayed gross matches `net × (100+rate)/100`; set a sale price + window; clear the sale; verify a sale > regular is rejected with the server message.

---

## Self-Review

- **Spec coverage:** net↔gross + rate rule (single source) → T1; `effectiveVatRatePercent` on detail → T2; flag‑gated regular/sale/window price write with net‑or‑gross + validation → T3; endpoint + 400/403/404 + flag‑off → T4; frontend editor (net/gross toggle, sale window, per‑row save, effective rate shown) → T5; money‑rule tests (never skipped) across T1/T3/T4; full verify incl. ArchUnit → T6. Stock/variant‑CRUD/attribute editing correctly absent (P2b‑2 / separate mask).
- **Placeholder scan:** all code is concrete. The frontend task references the existing `GalleryEditor`/`apiFetch`/`refetch`/`message` patterns and the `firstProductId`/`firstVariantId` test helpers by pointing at the exact files to copy them from — no invented APIs.
- **Type consistency:** `VatPricing.{rateForTaxClass,effectiveRatePercent,toGross,toNet}` used identically in T1/T2/T3 and mirrored client‑side in T5. `PriceBasis`/`PriceInput`/`PriceUpdate` identical across service (T3), controller (T4 JSON), and SPA (T5). `ProductDetailView` gains exactly one field `effectiveVatRatePercent` (T2), consumed by the SPA (T5). Endpoint path `/api/admin/products/{id}/variants/{variantId}/price` consistent T4↔T5. Guard reuses `ProductAdminEditService.EditorDisabledException` (→403) and `OrderAdminQueryService.NotFoundException` (→404); `IllegalArgumentException`→400 via the existing handler.
