# P2b‑2 — Variant CRUD + attribute‑combination editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin create/edit/delete/reorder a product's variants, assign each a combination of global attribute values, and add new terms to existing attributes — behind the product‑editor flag.

**Architecture:** Pure `domain/catalog` slug helper; flag‑gated `ProductVariantService` (create/update/delete/reorder, combo‑uniqueness + SKU + last‑variant guards) and `AttributeAdminService` (idempotent add‑term); thin `/variants` and `/attributes` controllers returning the updated `ProductDetailView` / attribute catalog; admin SPA variant editor. Mirrors the P2c/P2b‑1 per‑resource patterns.

**Tech Stack:** Java 21, Spring Boot 4.1 / Spring 7, Spring Data JPA, JUnit 5 + Testcontainers (Postgres) + MockMvc, AssertJ; admin SPA Refine/React + Vitest. Spec: `docs/superpowers/specs/2026-06-19-admin-products-p2b2-variant-crud-design.md`.

**Environment for the executor:**
- **Work ONLY in the worktree `/Users/zolika/Work/Claude/website-p2b2`** (branch `admin-products-p2b2`). `cd` there at the start of EVERY command (shell cwd resets between calls). Never touch `/Users/zolika/Work/Claude/website`.
- Stack is Spring Boot 4.1 / Spring 7. Backend tests: `mvn -q -o -Dskip.frontend=true -Dtest=... test` from the worktree root. Testcontainers needs Docker.
- Commit on `admin-products-p2b2`; stage only the files each task lists (never `git add -A`).
- Reuse (read to confirm): `AdminProperties.productEditorEnabled()`; `ProductAdminEditService.EditorDisabledException` (→403); `OrderAdminQueryService.NotFoundException` (→404); `AdminExceptionHandler` maps `IllegalArgumentException`→400. Domain factories: `Variant.create(Product, Long externalId, boolean isDefault)`, `AttributeValue.create(Attribute, String slug, String label, int sortOrder)`, `Attribute.create(Long externalId, String slug, String label, String type)`. Setters: `Variant.setSku/setPosition/replaceAttributeValues(Set)`. Repos: `VariantRepository.findBySku`, `AttributeValueRepository.findById/findByAttributeAndSlug`, `AttributeRepository.findById/findAll`. `ProductDetailView` already has `effectiveVatRatePercent` (P2b‑1).

---

## File Structure

- **Create** `src/main/java/hu/deposoft/webshop/domain/catalog/Slugs.java` — pure slugify.
- **Modify** `src/main/java/hu/deposoft/webshop/domain/catalog/Product.java` — `removeVariant`.
- **Modify** `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java` — `VariantView.attributeValues` + combo label + `attributes()` catalog (`AttributeView`/`AttributeValueOption`/`AttributeValueRef`).
- **Create** `src/main/java/hu/deposoft/webshop/application/catalog/ProductVariantService.java`.
- **Create** `src/main/java/hu/deposoft/webshop/application/catalog/AttributeAdminService.java`.
- **Create** `src/main/java/hu/deposoft/webshop/api/admin/ProductVariantController.java`, `.../AttributeController.java`.
- **Modify** `admin-ui/src/types.ts`, `admin-ui/src/pages/products/show.tsx`, `admin-ui/src/i18n/{hu,en}/products.json`.
- **Tests:** `SlugsTest`, `ProductVariantServiceTest` (+ disabled), `AttributeAdminServiceTest` (+ disabled), `ProductVariantControllerTest` (+ disabled), `AttributeControllerTest`.

---

## Task 1: `Slugs` slugify utility

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/domain/catalog/Slugs.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/catalog/SlugsTest.java`

- [ ] **Step 1: Failing test**
```java
package hu.deposoft.webshop.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SlugsTest {
    @Test void stripsDiacriticsAndLowercases() { assertThat(Slugs.slugify("Piros árnyalat")).isEqualTo("piros-arnyalat"); }
    @Test void handlesHungarianLongVowels()    { assertThat(Slugs.slugify("Zöld/Kék")).isEqualTo("zold-kek"); }
    @Test void doubleAcute()                    { assertThat(Slugs.slugify("Őzbarna űr")).isEqualTo("ozbarna-ur"); }
    @Test void collapsesAndTrims()              { assertThat(Slugs.slugify("  Extra   Nagy!!  ")).isEqualTo("extra-nagy"); }
    @Test void keepsDigits()                    { assertThat(Slugs.slugify("250 ml")).isEqualTo("250-ml"); }
    @Test void emptyForSymbolsOnly()            { assertThat(Slugs.slugify("!!!")).isEqualTo(""); }
    @Test void nullSafe()                       { assertThat(Slugs.slugify(null)).isEqualTo(""); }
}
```

- [ ] **Step 2: Run → fail** — `mvn -q -o -Dtest=SlugsTest test` (compile failure).

- [ ] **Step 3: Implement**
```java
package hu.deposoft.webshop.domain.catalog;

import java.text.Normalizer;
import java.util.Locale;

/** Derives a URL/identifier slug from a human label. Pure & stateless. Used for admin-created
 *  attribute terms (AttributeValue slugs are immutable at create time and have no source slug). */
public final class Slugs {

    private Slugs() {}

    public static String slugify(String label) {
        if (label == null) {
            return "";
        }
        String noDiacritics = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noDiacritics.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
    }
}
```

- [ ] **Step 4: Run → pass** — `mvn -q -o -Dtest=SlugsTest test` (7 green).

- [ ] **Step 5: Commit**
```bash
cd /Users/zolika/Work/Claude/website-p2b2
git add src/main/java/hu/deposoft/webshop/domain/catalog/Slugs.java \
        src/test/java/hu/deposoft/webshop/domain/catalog/SlugsTest.java
git commit -m "feat(catalog): Slugs.slugify utility (diacritic-stripping, Hungarian-aware)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Query layer — structured combo + attribute catalog

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryServiceTest.java`

- [ ] **Step 1: Failing test additions**

Read `ProductAdminQueryServiceTest.java` first (its seed + the autowired `query`). Add a `@BeforeEach`‑level or in‑test creation of an attribute with two values (inject `AttributeRepository attributeRepo` and `AttributeValueRepository attributeValueRepo`), then tests:
```java
    @Test void attributesCatalogListsValuesSortedBySortOrder() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        attributeValueRepo.save(hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "kek", "Kék", 1));
        attributeValueRepo.save(hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "piros", "Piros", 0));
        var cat = query.attributes();
        var view = cat.stream().filter(a -> a.slug().equals("szin")).findFirst().orElseThrow();
        assertThat(view.label()).isEqualTo("Szín");
        assertThat(view.values()).extracting(ProductAdminQueryService.AttributeValueOption::label).containsExactly("Piros", "Kék");
    }

    @Test void variantViewExposesAttributeCombo() {
        // a product whose default variant has an attribute value attached
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        var piros = attributeValueRepo.save(hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "piros", "Piros", 0));
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        var detail = query.detail(pid);
        // attach via the entity (test-only) to verify the view mapping; reload through query
        // NOTE: simplest is to assert the empty case first, then Task 3 exercises population.
        assertThat(detail.variants().get(0).attributeValues()).isNotNull();
    }
```
(If creating an `Attribute` with the same `szin` slug twice across tests collides on the unique slug, give each test a distinct slug, e.g. `szin`, `szin2`.)

- [ ] **Step 2: Run → fail** — `mvn -q -o -Dskip.frontend=true -Dtest=ProductAdminQueryServiceTest test` (compile failure: `attributes()`, `AttributeValueOption`, `attributeValues()` missing).

- [ ] **Step 3: Add records, the catalog query, the combo on `VariantView`, and the label upgrade**

In `ProductAdminQueryService.java`:
- Add imports: `import hu.deposoft.webshop.domain.catalog.Attribute;`, `import hu.deposoft.webshop.domain.catalog.AttributeRepository;`, `import hu.deposoft.webshop.domain.catalog.AttributeValue;`
- Inject `AttributeRepository`: add `private final AttributeRepository attributeRepo;` to the fields (it's `@RequiredArgsConstructor`).
- Add records:
```java
    public record AttributeValueRef(Long id, Long attributeId, String attributeLabel, String valueLabel) {}
    public record AttributeValueOption(Long id, String slug, String label) {}
    public record AttributeView(Long id, String slug, String label, List<AttributeValueOption> values) {}
```
- Change `VariantView` to carry the combo (append the new field):
```java
    public record VariantView(Long id, String label, String sku, Long regularPriceHuf, Long salePriceHuf,
                              int stockQty, int lowStockThreshold, List<AttributeValueRef> attributeValues) {}
```
- Update `toVariant` to map the combo, and upgrade `label`:
```java
    private VariantView toVariant(Variant v) {
        List<AttributeValueRef> avs = v.getAttributeValues().stream()
                .sorted(Comparator.comparing(av -> av.getAttribute().getSlug()))
                .map(av -> new AttributeValueRef(av.getId(), av.getAttribute().getId(),
                        av.getAttribute().getLabel(), av.getLabel()))
                .toList();
        return new VariantView(v.getId(), label(v), v.getSku(), v.getRegularPriceHuf(), v.getSalePriceHuf(),
                qty(v), v.getLowStockThreshold(), avs);
    }

    /** Combo label (attribute values sorted by attribute slug, joined " · "); else "Alap"/SKU. */
    private String label(Variant v) {
        if (!v.getAttributeValues().isEmpty()) {
            return v.getAttributeValues().stream()
                    .sorted(Comparator.comparing(av -> av.getAttribute().getSlug()))
                    .map(AttributeValue::getLabel)
                    .reduce((a, b) -> a + " · " + b).orElse("");
        }
        return v.isDefault() || v.getSku() == null ? "Alap" : v.getSku();
    }
```
- Add the catalog query:
```java
    public List<AttributeView> attributes() {
        return attributeRepo.findAll().stream()
                .sorted(Comparator.comparing(Attribute::getSlug))
                .map(a -> new AttributeView(a.getId(), a.getSlug(), a.getLabel(),
                        a.getValues().stream()
                                .sorted(Comparator.comparingInt(AttributeValue::getSortOrder))
                                .map(av -> new AttributeValueOption(av.getId(), av.getSlug(), av.getLabel()))
                                .toList()))
                .toList();
    }
```

- [ ] **Step 4: Fix other `new VariantView(` call sites**

Search the worktree for `new VariantView(`. The only producer is `toVariant`. If any test constructs it directly, append the new `List.of()`/combo arg.

- [ ] **Step 5: Run → pass** — `mvn -q -o -Dskip.frontend=true -Dtest=ProductAdminQueryServiceTest test` (existing + 2 new green).

- [ ] **Step 6: Commit**
```bash
cd /Users/zolika/Work/Claude/website-p2b2
git add src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryServiceTest.java
git commit -m "feat(admin): expose variant attribute combo + attribute catalog; real combo label

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `ProductVariantService` (+ `Product.removeVariant`)

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/catalog/Product.java`
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/ProductVariantService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/ProductVariantServiceTest.java`, `.../ProductVariantDisabledTest.java`

- [ ] **Step 1: Add `Product.removeVariant`**

In `Product.java`, next to `addVariant`:
```java
    public void removeVariant(Variant variant) {
        variants.remove(variant);
    }
```

- [ ] **Step 2: Failing service test (Testcontainers, flag ON)**

Mirror `ProductAdminEditServiceTest`'s harness (`@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")`, `@Testcontainers`/`@ServiceConnection`, `@Transactional`, the `CatalogImporter` seed, autowired `ProductAdminQueryService query`). Also inject `@Autowired AttributeRepository attributeRepo; @Autowired AttributeValueRepository attributeValueRepo; @Autowired ProductVariantService variantService;`. In `@BeforeEach` after the product seed, create an attribute with two values:
```java
    Long pirosId, kekId;
    @BeforeEach void seedAttributes() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        pirosId = attributeValueRepo.save(hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "piros", "Piros", 0)).getId();
        kekId   = attributeValueRepo.save(hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "kek", "Kék", 1)).getId();
    }
    private Long firstProductId() { return query.list(null, null, 0, 20).items().get(0).id(); }
```
Tests:
```java
    @Test void createAppendsVariantWithComboAndLabel() {
        Long pid = firstProductId();
        var d = variantService.createVariant(pid, new ProductVariantService.CreateVariant("SKU-RED", java.util.List.of(pirosId)));
        var created = d.variants().stream().filter(v -> "SKU-RED".equals(v.sku())).findFirst().orElseThrow();
        assertThat(created.label()).isEqualTo("Piros");
        assertThat(created.attributeValues()).extracting(ProductAdminQueryService.AttributeValueRef::valueLabel).containsExactly("Piros");
    }
    @Test void rejectsDuplicateSku() {
        Long pid = firstProductId();
        variantService.createVariant(pid, new ProductVariantService.CreateVariant("DUP", java.util.List.of(pirosId)));
        assertThatThrownBy(() -> variantService.createVariant(pid, new ProductVariantService.CreateVariant("DUP", java.util.List.of(kekId))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsDuplicateCombo() {
        Long pid = firstProductId();
        variantService.createVariant(pid, new ProductVariantService.CreateVariant(null, java.util.List.of(pirosId)));
        assertThatThrownBy(() -> variantService.createVariant(pid, new ProductVariantService.CreateVariant(null, java.util.List.of(pirosId))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsUnknownAttributeValue() {
        Long pid = firstProductId();
        assertThatThrownBy(() -> variantService.createVariant(pid, new ProductVariantService.CreateVariant(null, java.util.List.of(999999L))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void updatesComboAndSku() {
        Long pid = firstProductId();
        var d0 = variantService.createVariant(pid, new ProductVariantService.CreateVariant("A", java.util.List.of(pirosId)));
        Long vid = d0.variants().stream().filter(v -> "A".equals(v.sku())).findFirst().orElseThrow().id();
        var d1 = variantService.updateVariant(pid, vid, new ProductVariantService.UpdateVariant("B", java.util.List.of(kekId)));
        var v = d1.variants().stream().filter(x -> x.id().equals(vid)).findFirst().orElseThrow();
        assertThat(v.sku()).isEqualTo("B");
        assertThat(v.attributeValues()).extracting(ProductAdminQueryService.AttributeValueRef::valueLabel).containsExactly("Kék");
    }
    @Test void deleteRemovesVariant() {
        Long pid = firstProductId();
        var d0 = variantService.createVariant(pid, new ProductVariantService.CreateVariant("X", java.util.List.of(pirosId)));
        int before = d0.variants().size();
        Long vid = d0.variants().stream().filter(v -> "X".equals(v.sku())).findFirst().orElseThrow().id();
        var d1 = variantService.deleteVariant(pid, vid);
        assertThat(d1.variants()).hasSize(before - 1);
        assertThat(d1.variants()).noneMatch(v -> v.id().equals(vid));
    }
    @Test void rejectsDeletingLastVariant() {
        Long pid = firstProductId();
        // the seeded simple product has exactly one (default) variant
        Long onlyId = query.detail(pid).variants().get(0).id();
        assertThatThrownBy(() -> variantService.deleteVariant(pid, onlyId)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reorderSetsPositions() {
        Long pid = firstProductId();
        var d0 = variantService.createVariant(pid, new ProductVariantService.CreateVariant("S1", java.util.List.of(pirosId)));
        var d1 = variantService.createVariant(pid, new ProductVariantService.CreateVariant("S2", java.util.List.of(kekId)));
        var ids = d1.variants().stream().map(ProductAdminQueryService.VariantView::id).toList();
        var reversed = new java.util.ArrayList<>(ids); java.util.Collections.reverse(reversed);
        var d2 = variantService.reorderVariants(pid, reversed);
        assertThat(d2.variants().stream().map(ProductAdminQueryService.VariantView::id).toList()).isEqualTo(reversed);
    }
    @Test void rejectsTwoValuesOfSameAttribute() {
        Long pid = firstProductId();
        assertThatThrownBy(() -> variantService.createVariant(pid,
                new ProductVariantService.CreateVariant(null, java.util.List.of(pirosId, kekId))))
                .isInstanceOf(IllegalArgumentException.class);
    }
```
(Note: the detail view orders variants by `position`, so the reorder assertion compares the id order after reorder. Confirm `detail()` sorts variants by position — it sorts via `Comparator.comparingInt(Variant::getPosition)`; reorder sets positions to the reversed index order, so the returned order equals `reversed`.)

- [ ] **Step 3: Run → fail** — `mvn -q -o -Dskip.frontend=true -Dtest=ProductVariantServiceTest test` (compile failure).

- [ ] **Step 4: Implement `ProductVariantService`**
```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.AttributeValueRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated variant management (P2b-2): create/update/delete/reorder with SKU-uniqueness,
 *  attribute-combination resolution + uniqueness, and the "keep >=1 variant" rule. Variant
 *  entities are managed within the transaction (dirty-checked); new ones are saved explicitly. */
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    public record CreateVariant(String sku, List<Long> attributeValueIds) {}
    public record UpdateVariant(String sku, List<Long> attributeValueIds) {}

    private final ProductRepository products;
    private final VariantRepository variants;
    private final AttributeValueRepository attributeValues;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.ProductDetailView createVariant(Long productId, CreateVariant cmd) {
        guard();
        Product p = product(productId);
        String sku = normalizeSku(cmd.sku());
        if (sku != null) requireSkuFree(sku, null);
        Set<AttributeValue> combo = resolveCombo(cmd.attributeValueIds());
        requireComboUnique(p, combo, null);
        int nextPos = p.getVariants().stream().mapToInt(Variant::getPosition).max().orElse(-1) + 1;
        Variant v = Variant.create(p, null, false);
        v.setSku(sku);
        v.setPosition(nextPos);
        v.replaceAttributeValues(combo);
        p.addVariant(v);
        variants.save(v);
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView updateVariant(Long productId, Long variantId, UpdateVariant cmd) {
        guard();
        Product p = product(productId);
        Variant v = variantOf(p, variantId);
        String sku = normalizeSku(cmd.sku());
        if (sku != null) requireSkuFree(sku, variantId);
        Set<AttributeValue> combo = resolveCombo(cmd.attributeValueIds());
        requireComboUnique(p, combo, variantId);
        v.setSku(sku);
        v.replaceAttributeValues(combo);
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView deleteVariant(Long productId, Long variantId) {
        guard();
        Product p = product(productId);
        Variant v = variantOf(p, variantId);
        if (p.getVariants().size() <= 1) {
            throw new IllegalArgumentException("A product must keep at least one variant");
        }
        p.removeVariant(v);
        reindex(p);
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView reorderVariants(Long productId, List<Long> variantIds) {
        guard();
        Product p = product(productId);
        List<Variant> current = p.getVariants();
        if (variantIds.size() != current.size()
                || !variantIds.stream().sorted().toList().equals(current.stream().map(Variant::getId).sorted().toList())) {
            throw new IllegalArgumentException("Reorder list must contain exactly the product's variant ids");
        }
        for (int i = 0; i < variantIds.size(); i++) {
            Long id = variantIds.get(i);
            Variant v = current.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            v.setPosition(i);
        }
        return query.detail(productId);
    }

    private String normalizeSku(String sku) {
        if (sku == null) return null;
        String s = sku.trim();
        return s.isEmpty() ? null : s;
    }

    private void requireSkuFree(String sku, Long selfVariantId) {
        variants.findBySku(sku).ifPresent(existing -> {
            if (!existing.getId().equals(selfVariantId)) {
                throw new IllegalArgumentException("SKU already in use: " + sku);
            }
        });
    }

    /** Resolve ids to AttributeValues; reject unknown ids and more than one value per attribute. */
    private Set<AttributeValue> resolveCombo(List<Long> ids) {
        Set<AttributeValue> combo = new LinkedHashSet<>();
        if (ids == null) return combo;
        for (Long id : ids) {
            AttributeValue av = attributeValues.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown attribute value: " + id));
            combo.add(av);
        }
        long distinctAttributes = combo.stream().map(av -> av.getAttribute().getId()).distinct().count();
        if (distinctAttributes != combo.size()) {
            throw new IllegalArgumentException("A variant may have at most one value per attribute");
        }
        return combo;
    }

    private void requireComboUnique(Product p, Set<AttributeValue> combo, Long selfVariantId) {
        Set<Long> comboIds = combo.stream().map(AttributeValue::getId).collect(Collectors.toSet());
        for (Variant other : p.getVariants()) {
            if (other.getId() != null && other.getId().equals(selfVariantId)) continue;
            Set<Long> otherIds = other.getAttributeValues().stream().map(AttributeValue::getId).collect(Collectors.toSet());
            if (otherIds.equals(comboIds)) {
                throw new IllegalArgumentException("Another variant already has this attribute combination");
            }
        }
    }

    private void reindex(Product p) {
        List<Variant> ordered = p.getVariants().stream()
                .sorted(Comparator.comparingInt(Variant::getPosition)).toList();
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
    }

    private Variant variantOf(Product p, Long variantId) {
        return p.getVariants().stream().filter(x -> x.getId().equals(variantId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No variant " + variantId + " on product " + p.getId()));
    }

    private void guard() {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }
}
```

- [ ] **Step 5: Flag‑off test**

`ProductVariantDisabledTest.java` — mirror `ProductAdminEditDisabledTest` (`...enabled=false`, same product + attribute seed):
```java
    @Test void createThrowsWhenEditorDisabled() {
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        assertThatThrownBy(() -> variantService.createVariant(pid,
                new ProductVariantService.CreateVariant("Z", java.util.List.of(pirosId))))
                .isInstanceOf(hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException.class);
    }
```

- [ ] **Step 6: Run → pass** — `mvn -q -o -Dskip.frontend=true -Dtest=ProductVariantServiceTest,ProductVariantDisabledTest test` (10 + 1 green).

- [ ] **Step 7: Commit**
```bash
cd /Users/zolika/Work/Claude/website-p2b2
git add src/main/java/hu/deposoft/webshop/domain/catalog/Product.java \
        src/main/java/hu/deposoft/webshop/application/catalog/ProductVariantService.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductVariantServiceTest.java \
        src/test/java/hu/deposoft/webshop/application/catalog/ProductVariantDisabledTest.java
git commit -m "feat(admin): flag-gated variant CRUD + reorder (SKU/combo uniqueness, keep >=1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `AttributeAdminService` (add term)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/catalog/AttributeAdminService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/catalog/AttributeAdminServiceTest.java`, `.../AttributeAdminDisabledTest.java`

- [ ] **Step 1: Failing test (flag ON)**

Mirror the Testcontainers harness; inject `AttributeRepository attributeRepo`, `AttributeAdminService attributeAdmin`, `ProductAdminQueryService query`.
```java
    @Test void addsNewTermWithDerivedSlug() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        var view = attributeAdmin.addValue(szin.getId(), "Piros árnyalat");
        assertThat(view.values()).extracting(ProductAdminQueryService.AttributeValueOption::slug).contains("piros-arnyalat");
        assertThat(view.values()).extracting(ProductAdminQueryService.AttributeValueOption::label).contains("Piros árnyalat");
    }
    @Test void addValueIsIdempotentOnExistingSlug() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        attributeAdmin.addValue(szin.getId(), "Piros");
        var view = attributeAdmin.addValue(szin.getId(), "Piros"); // same slug
        assertThat(view.values()).filteredOn(o -> o.slug().equals("piros")).hasSize(1);
    }
    @Test void unknownAttributeThrowsNotFound() {
        assertThatThrownBy(() -> attributeAdmin.addValue(999999L, "X"))
                .isInstanceOf(hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException.class);
    }
    @Test void blankLabelRejected() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        assertThatThrownBy(() -> attributeAdmin.addValue(szin.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
```

- [ ] **Step 2: Run → fail.**

- [ ] **Step 3: Implement**
```java
package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Attribute;
import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.AttributeValueRepository;
import hu.deposoft.webshop.domain.catalog.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated: add a new term (value) to an existing global attribute. Idempotent on slug. */
@Service
@RequiredArgsConstructor
public class AttributeAdminService {

    private final AttributeRepository attributes;
    private final AttributeValueRepository attributeValues;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.AttributeView addValue(Long attributeId, String label) {
        guard();
        Attribute attribute = attributes.findById(attributeId)
                .orElseThrow(() -> new NotFoundException("No attribute " + attributeId));
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Label must not be blank");
        }
        String slug = Slugs.slugify(trimmed);
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Label has no slug-able characters: " + label);
        }
        if (attributeValues.findByAttributeAndSlug(attribute, slug).isEmpty()) {
            int nextSort = attribute.getValues().stream().mapToInt(AttributeValue::getSortOrder).max().orElse(-1) + 1;
            AttributeValue created = AttributeValue.create(attribute, slug, trimmed, nextSort);
            attribute.getValues().add(created);   // cascade ALL persists; keeps the in-memory view consistent
            attributes.save(attribute);
        }
        return query.attributes().stream().filter(a -> a.id().equals(attributeId)).findFirst().orElseThrow();
    }

    private void guard() {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }
}
```

- [ ] **Step 4: Flag‑off test** — `AttributeAdminDisabledTest.java` (`...enabled=false`): `addValue` on a seeded attribute throws `ProductAdminEditService.EditorDisabledException`.

- [ ] **Step 5: Run → pass** — `mvn -q -o -Dskip.frontend=true -Dtest=AttributeAdminServiceTest,AttributeAdminDisabledTest test`.

- [ ] **Step 6: Commit**
```bash
cd /Users/zolika/Work/Claude/website-p2b2
git add src/main/java/hu/deposoft/webshop/application/catalog/AttributeAdminService.java \
        src/test/java/hu/deposoft/webshop/application/catalog/AttributeAdminServiceTest.java \
        src/test/java/hu/deposoft/webshop/application/catalog/AttributeAdminDisabledTest.java
git commit -m "feat(admin): flag-gated add-term to an existing attribute (idempotent, slug-derived)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Controllers (`ProductVariantController` + `AttributeController`)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/api/admin/ProductVariantController.java`, `.../AttributeController.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/ProductVariantControllerTest.java`, `.../ProductVariantEndpointDisabledTest.java`, `.../AttributeControllerTest.java`

- [ ] **Step 1: Failing controller tests**

Mirror `ProductAdminControllerTest`'s MockMvc + `webAppContextSetup(context).apply(springSecurity())` + seed (copy verbatim), and add the attribute seed via `@Autowired AttributeRepository`/`AttributeValueRepository` in `@BeforeEach`. Resolve a product id from `GET /api/admin/products`. Key tests (`ProductVariantControllerTest`):
```java
    @Test void createVariant200() throws Exception {
        long pid = firstProductId();
        mvc.perform(post("/api/admin/products/" + pid + "/variants")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NEW-1\",\"attributeValueIds\":[" + pirosId + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants[?(@.sku == 'NEW-1')].label").value(org.hamcrest.Matchers.hasItem("Piros")));
    }
    @Test void duplicateSku400() throws Exception {
        long pid = firstProductId();
        mvc.perform(post("/api/admin/products/" + pid + "/variants").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"D\",\"attributeValueIds\":[" + pirosId + "]}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/admin/products/" + pid + "/variants").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"D\",\"attributeValueIds\":[" + kekId + "]}"))
            .andExpect(status().isBadRequest());
    }
    @Test void deleteUnknownVariant404() throws Exception {
        long pid = firstProductId();
        mvc.perform(delete("/api/admin/products/" + pid + "/variants/999999").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNotFound());
    }
    @Test void createForbiddenForNonAdmin() throws Exception {
        long pid = firstProductId();
        mvc.perform(post("/api/admin/products/" + pid + "/variants").with(user("u").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"N\",\"attributeValueIds\":[" + pirosId + "]}"))
            .andExpect(status().isForbidden());
    }
```
`AttributeControllerTest`:
```java
    @Test void listsCatalog() throws Exception {
        mvc.perform(get("/api/admin/attributes").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.slug == 'szin')].values[*].label").value(org.hamcrest.Matchers.hasItem("Piros")));
    }
    @Test void addValue200() throws Exception {
        long attrId = /* resolve szin id from the catalog JSON, or inject the seeded id */ ;
        mvc.perform(post("/api/admin/attributes/" + attrId + "/values").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Sárga\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.values[*].slug").value(org.hamcrest.Matchers.hasItem("sarga")));
    }
    @Test void addValueForbiddenForNonAdmin() throws Exception {
        long attrId = /* seeded szin id */ ;
        mvc.perform(post("/api/admin/attributes/" + attrId + "/values").with(user("u").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Sárga\"}"))
            .andExpect(status().isForbidden());
    }
```
(Keep the seeded attribute id in a field from `@BeforeEach` so the attribute tests can reference it directly.)

- [ ] **Step 2: Run → fail** — controllers missing.

- [ ] **Step 3: Implement the controllers**

`ProductVariantController.java`:
```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductVariantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin: variant create/update/delete/reorder. Rules live in ProductVariantService. */
@RestController
@RequestMapping("/api/admin/products/{id}/variants")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService service;

    public record ReorderRequest(List<Long> variantIds) {}

    @PostMapping
    public ProductDetailView create(@PathVariable Long id, @RequestBody ProductVariantService.CreateVariant body) {
        return service.createVariant(id, body);
    }

    @PutMapping("/{variantId}")
    public ProductDetailView update(@PathVariable Long id, @PathVariable Long variantId,
                                    @RequestBody ProductVariantService.UpdateVariant body) {
        return service.updateVariant(id, variantId, body);
    }

    @DeleteMapping("/{variantId}")
    public ProductDetailView delete(@PathVariable Long id, @PathVariable Long variantId) {
        return service.deleteVariant(id, variantId);
    }

    @PostMapping("/reorder")
    public ProductDetailView reorder(@PathVariable Long id, @RequestBody ReorderRequest body) {
        return service.reorderVariants(id, body.variantIds());
    }
}
```
`AttributeController.java`:
```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.AttributeAdminService;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.AttributeView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Global attribute catalog (read) + add-term (flag-gated write, in AttributeAdminService). */
@RestController
@RequestMapping("/api/admin/attributes")
@RequiredArgsConstructor
public class AttributeController {

    private final ProductAdminQueryService query;
    private final AttributeAdminService attributeAdmin;

    public record AddValueRequest(String label) {}

    @GetMapping
    public List<AttributeView> list() {
        return query.attributes();
    }

    @PostMapping("/{attributeId}/values")
    public AttributeView addValue(@PathVariable Long attributeId, @RequestBody AddValueRequest body) {
        return attributeAdmin.addValue(attributeId, body.label());
    }
}
```

- [ ] **Step 4: Flag‑off endpoint test** — `ProductVariantEndpointDisabledTest.java` (`...enabled=false`): an ADMIN `POST …/variants` returns 403.

- [ ] **Step 5: Run → pass** — `mvn -q -o -Dskip.frontend=true -Dtest=ProductVariantControllerTest,ProductVariantEndpointDisabledTest,AttributeControllerTest test`.

- [ ] **Step 6: Commit**
```bash
cd /Users/zolika/Work/Claude/website-p2b2
git add src/main/java/hu/deposoft/webshop/api/admin/ProductVariantController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AttributeController.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductVariantControllerTest.java \
        src/test/java/hu/deposoft/webshop/api/admin/ProductVariantEndpointDisabledTest.java \
        src/test/java/hu/deposoft/webshop/api/admin/AttributeControllerTest.java
git commit -m "feat(admin): variant CRUD/reorder + attribute catalog/add-term endpoints

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Frontend — variant editor in `show.tsx`

**Files:**
- Modify: `admin-ui/src/types.ts`, `admin-ui/src/pages/products/show.tsx`, `admin-ui/src/i18n/{hu,en}/products.json`

- [ ] **Step 1: Types** (`types.ts`)
```ts
export interface AttributeValueRef { id: number; attributeId: number; attributeLabel: string; valueLabel: string; }
export interface AttributeValueOption { id: number; slug: string; label: string; }
export interface AttributeView { id: number; slug: string; label: string; values: AttributeValueOption[]; }
export interface CreateVariant { sku: string | null; attributeValueIds: number[]; }
export interface UpdateVariant { sku: string | null; attributeValueIds: number[]; }
```
Add `attributeValues: AttributeValueRef[]` to `ProductVariantView`.

- [ ] **Step 2: i18n keys (both `hu` and `en`)**
```
hu: "variantAdd":"Változat hozzáadása","variantSku":"Cikkszám","variantSave":"Mentés",
    "variantDelete":"Törlés","variantSaved":"Változat mentve","variantDeleted":"Változat törölve",
    "variantNone":"—","attrAddTerm":"Új érték","attrNewTermPrompt":"Új érték neve","attrSelect":"válassz…"
en: "variantAdd":"Add variant","variantSku":"SKU","variantSave":"Save","variantDelete":"Delete",
    "variantSaved":"Variant saved","variantDeleted":"Variant deleted","variantNone":"—",
    "attrAddTerm":"New value","attrNewTermPrompt":"New value name","attrSelect":"select…"
```

- [ ] **Step 3: Variant editor in the editable branch** (read‑only branch unchanged)

Read `show.tsx`. Fetch the attribute catalog once in the editable component: `apiFetch(`${API_BASE}/attributes`)` → `AttributeView[]` (store in state; reload after add‑term). Build a `VariantEditor` per row + an "add variant" form:
- **Per attribute dimension** (from the catalog), a `<select>` of its `values` (option value = `AttributeValueOption.id`), pre‑selected from the variant's `attributeValues` (match by `attributeId`); plus an **"Új érték"** button that `prompt()`s for a label and `POST {API_BASE}/attributes/${attr.id}/values` `{label}`, then re‑fetches the catalog and selects the returned new value.
- **SKU** `<input>`.
- **Save** (`PUT {API_BASE}/products/${product.id}/variants/${v.id}`) with `{ sku, attributeValueIds }` (the selected option ids, omitting unset dimensions); **Delete** (`DELETE …/variants/${v.id}`); reorder **▲/▼** (`POST …/variants/reorder` with the full id order). The existing P2b‑1 price editor stays on the row.
- **Add variant** form: same pickers + SKU → `POST {API_BASE}/products/${product.id}/variants`.
- After each op: `res.ok` → success toast + `refetch()`; else `message.error(await res.text())`. Same `apiFetch`/`API_BASE`/`App.useApp()`/`refetch` wiring as the gallery/price editors. Keep inline‑style + `var(--…)` idiom.

- [ ] **Step 4: Build + test** (from `admin-ui/`): `npm run build` + `npm run test` green. Update any snapshot minimally if needed.

- [ ] **Step 5: Commit**
```bash
cd /Users/zolika/Work/Claude/website-p2b2
git add admin-ui/src/types.ts admin-ui/src/pages/products/show.tsx \
        admin-ui/src/i18n/hu/products.json admin-ui/src/i18n/en/products.json
git commit -m "feat(admin-ui): variant editor (combo pickers, add-term, SKU, reorder, delete)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Full verify gate

**Files:** none (verification only).

- [ ] **Step 1:** From the worktree root: `mvn verify` → BUILD SUCCESS (all tests + ArchUnit `ModularityTest` — confirms `Slugs`/services placement). Then from `admin-ui/`: `npm run build` && `npm run test` → green.
- [ ] **Step 2 (optional manual):** with the editor flag on, add a variant with an attribute combo, add a new term, reorder, delete; confirm duplicate SKU/combo and deleting the last variant are rejected with the server message; price a new variant via the P2b‑1 editor.

---

## Self-Review

- **Spec coverage:** `Slugs` → T1; structured combo + label + attribute catalog → T2; create/update/delete/reorder + SKU/combo/last‑variant guards → T3; add‑term (idempotent, slug‑derived) → T4; endpoints + 400/403/404 + non‑admin + flag‑off → T5; frontend editor (pickers, add‑term, SKU, reorder, delete) → T6; full verify incl. ArchUnit → T7. Out‑of‑scope items (price, stock, attribute CRUD, explicit default) correctly absent.
- **Placeholder scan:** production code is complete and verbatim. The controller tests use a couple of `/* resolve … id */` notes for the seeded attribute id — the seed creates it in `@BeforeEach`, so the executor stores that id in a field (stated). Test seed/MockMvc boilerplate is "copy from ProductAdminControllerTest", consistent with how P2b‑1's plan handled it.
- **Type consistency:** `CreateVariant`/`UpdateVariant(String sku, List<Long> attributeValueIds)` identical across service (T3), controller JSON (T5), SPA (T6). `AttributeView(id,slug,label,values)`, `AttributeValueOption(id,slug,label)`, `AttributeValueRef(id,attributeId,attributeLabel,valueLabel)` consistent T2↔T5↔T6. `VariantView` gains exactly one field `attributeValues` (T2), consumed by the SPA (T6). Endpoints `/api/admin/products/{id}/variants[...]` (T5) match the SPA calls (T6) and don't collide with P2b‑1's `…/variants/{variantId}/price`. Guard reuses `ProductAdminEditService.EditorDisabledException`/`OrderAdminQueryService.NotFoundException`; `IllegalArgumentException`→400 via the existing handler.
