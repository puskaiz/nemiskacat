# P2b‑2 — Variant CRUD + attribute‑combination editing — Design

Date: 2026-06-19
Status: Approved (brainstormed 2026-06-19)
Builds on: P2b‑1 (per‑variant price editing — the price endpoint and `effectiveVatRatePercent` stay
as‑is), ADR 0005 (product catalog ownership), and the P2c/P2b‑1 per‑resource admin endpoint patterns.

## Goal

Let an admin manage a product's variants in the admin SPA: **create / edit / delete / reorder**
variants, assigning each a combination of **global attribute values** (e.g. szín, kiszerelés), and
**add a new term** to an existing global attribute on the fly — all behind the product‑editor flag.

## Scope

**In scope**
- Create a variant: SKU (optional, unique) + an attribute‑value combination; appended at the end.
- Edit a variant's SKU and attribute‑value combination.
- Delete a variant (a product must keep **at least one**).
- Reorder variants (position).
- Add a new **term** (value) to an **existing** global attribute.
- Read the global attribute catalog for the pickers.
- Flag‑gated (`webshop.admin.product-editor-enabled`, default on) + ADMIN + CSRF for all writes.

**Out of scope**
- Price editing (done in P2b‑1; the per‑row price editor stays).
- Stock fields (`manageStock`, `lowStockThreshold`, `manualAvailability`) — the separate stock mask
  (ADR 0005).
- Creating/deleting/renaming whole **attributes** (only adding *values* to existing ones) or deleting
  attribute values; full attribute catalog CRUD.
- Explicit default‑variant selection — the primary variant stays **derived** (`isDefault` if set,
  else lowest position). Deleting the default lets the derivation promote the next; no `isDefault`
  flip is performed (and `Variant.isDefault` has no setter).
- Slugs of products/variants (variants have none).

## Key model facts (constraints the design works within)

- **Attributes are global** (`Attribute`: immutable `slug`, `label`, `type`, `values`); there is **no
  per‑product attribute registry** (the `product_attribute` table is dead/unmapped). A product's
  "dimensions" are derived from its variants' attribute values.
- **No DB constraint** prevents two variants of a product from sharing an identical attribute‑value
  combination → the **service enforces combo‑uniqueness**.
- All domain types use protected constructors + static `create(...)` factories; `slug` fields are
  **immutable** (no setter); `Variant.isDefault` is factory‑only. There is **no `removeVariant`** on
  `Product` and **no SKU/term pre‑check** anywhere — a raw duplicate would surface as a 500, so the
  service must pre‑check and throw `IllegalArgumentException` (→400).
- `Product.variants` is `@OneToMany(cascade = ALL, orphanRemoval = true)` ordered by `position`.
- Existing exception mapping (`AdminExceptionHandler`): `IllegalArgumentException`→400,
  `OrderAdminQueryService.NotFoundException`→404, `ProductAdminEditService.EditorDisabledException`→403.

## Architecture

Thin REST controllers → flag‑gated application services → domain entities (managed within
`@Transactional`, dirty‑checked). Variant mutations return the updated `ProductDetailView`; the
attribute catalog is a separate read.

### 1. Domain additions (`domain/catalog`)

- **`Slugs`** — a pure utility (none exists today): `slugify(String) -> String` via
  `java.text.Normalizer` NFD → strip diacritics → lowercase → non‑`[a-z0-9]`→`-` → collapse/trim
  hyphens. Hungarian‑aware ("Piros árnyalat" → `piros-arnyalat`). Unit‑tested. Needed because
  `AttributeValue` slugs are immutable and must be supplied at `create(...)` time.
- **`Product.removeVariant(Variant)`** — mirrors `addVariant`; removes from the list so
  `orphanRemoval` deletes it on flush.
- **Variant label upgrade** — the admin label is currently a P1 stub (`"Alap"`/SKU). Replace with the
  real combo label: attribute values **sorted by attribute slug**, joined with `" · "`; fall back to
  `"Alap"` for the default variant, else the SKU. Mirrors `CatalogQueryService.variantLabel`.

### 2. Query — structured combo + attribute catalog (`ProductAdminQueryService`)

- `VariantView` gains `List<AttributeValueRef> attributeValues`, where
  `AttributeValueRef(Long id, Long attributeId, String attributeLabel, String valueLabel)`, sorted by
  attribute slug. Mapped in `toVariant` from `variant.getAttributeValues()`.
- New records + method:
  - `AttributeValueOption(Long id, String slug, String label)`
  - `AttributeView(Long id, String slug, String label, List<AttributeValueOption> values)`
  - `List<AttributeView> attributes()` — all attributes (sorted by slug) with their values (sorted by
    `sortOrder`), for the pickers.

### 3. Application services (`application/catalog`, flag‑gated)

**`ProductVariantService`** — reuses `AdminProperties.productEditorEnabled()` guard
(→`ProductAdminEditService.EditorDisabledException`), `OrderAdminQueryService.NotFoundException`,
`ProductRepository`, `VariantRepository`, `AttributeValueRepository`, `ProductAdminQueryService`
(for the return view).

```java
public record CreateVariant(String sku, List<Long> attributeValueIds) {}
public record UpdateVariant(String sku, List<Long> attributeValueIds) {}

ProductDetailView createVariant(Long productId, CreateVariant cmd);
ProductDetailView updateVariant(Long productId, Long variantId, UpdateVariant cmd);
ProductDetailView deleteVariant(Long productId, Long variantId);
ProductDetailView reorderVariants(Long productId, List<Long> variantIds);
```

- **createVariant:** guard; load product (404); resolve + validate the combo; SKU (trim; blank→null;
  else must be free via `VariantRepository.findBySku` → else `IllegalArgumentException`); build
  `Variant.create(product, null, false)`, `setSku`, `setPosition(maxPos+1)`,
  `replaceAttributeValues(set)`; `product.addVariant` + `variants.save`; return detail.
- **updateVariant:** guard; load product + variant‑on‑product (404); same SKU + combo validation
  **excluding this variant**; apply `setSku` + `replaceAttributeValues`; return detail.
- **deleteVariant:** guard; load product + variant (404); if the product has **only one** variant →
  `IllegalArgumentException` ("a product must keep at least one variant"); else `removeVariant` +
  reindex positions 0..n; return detail.
- **reorderVariants:** guard; the id list must equal the product's variant‑id set (else
  `IllegalArgumentException`); assign positions by index; return detail.
- **Combo validation (shared):** every `attributeValueId` resolves via
  `AttributeValueRepository.findById` (else `IllegalArgumentException`); **at most one value per
  attribute** (group by `attributeValue.getAttribute().getId()`, reject duplicates); the resulting
  value‑id **set must be unique** among the product's other variants (compare sets) — else
  `IllegalArgumentException`. An empty combo is allowed but is itself subject to uniqueness (so at most
  one attribute‑less variant).

**`AttributeAdminService`** — `addValue(Long attributeId, String label) -> AttributeView`:
guard; load attribute (404); `label` trimmed non‑blank (else 400); `slug = Slugs.slugify(label)`
(blank result → 400); if `findByAttributeAndSlug(attr, slug)` present → return the existing catalog
entry (**idempotent**); else `AttributeValueRepository.save(AttributeValue.create(attr, slug, label,
nextSortOrder))`; return the attribute's updated `AttributeView`.

### 4. API (`api/admin`)

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/admin/products/{id}/variants` | `CreateVariant` | `ProductDetailView` |
| PUT | `/api/admin/products/{id}/variants/{variantId}` | `UpdateVariant` | `ProductDetailView` |
| DELETE | `/api/admin/products/{id}/variants/{variantId}` | — | `ProductDetailView` |
| POST | `/api/admin/products/{id}/variants/reorder` | `{ variantIds: [..] }` | `ProductDetailView` |
| GET | `/api/admin/attributes` | — | `List<AttributeView>` |
| POST | `/api/admin/attributes/{attributeId}/values` | `{ label }` | `AttributeView` |

`PUT …/variants/{variantId}` (structure) and the existing P2b‑1 `PUT …/variants/{variantId}/price`
are distinct sub‑resources — no conflict. Variant writes + `POST …/values` are flag‑gated + ADMIN +
CSRF; `GET /attributes` is an ungated read (consistent with other product reads). Two thin controllers:
`ProductVariantController` (the four variant endpoints) and `AttributeController` (the two attribute
endpoints).

### 5. Frontend (`admin-ui/src/pages/products/show.tsx`, editable branch only)

The variant table becomes a full editor (read‑only/flag‑off branch unchanged):
- **Per variant row:** the attribute combo (a dropdown per global attribute, pre‑filled from the
  variant's `attributeValues`), SKU input, the **existing P2b‑1 price editor** (unchanged), reorder
  **▲/▼** arrows, and a **delete** button. Save SKU/combo edits via `PUT …/variants/{variantId}`.
- **Add‑variant form:** the same attribute pickers + SKU; submit via `POST …/variants`. New variants
  are created **unpriced** — the admin then prices them with the per‑row price editor.
- **Add‑term:** next to each attribute dropdown, an "új érték" control prompts for a label and calls
  `POST /attributes/{attributeId}/values`, then refreshes the catalog (`GET /attributes`) so the new
  term is selectable.
- After every op: `res.ok` → success toast + `refetch()`; else `message.error(await res.text())` (so
  combo/SKU/last‑variant 400s and 403/404s surface). Same `apiFetch`/`API_BASE`/`App.useApp()` +
  `refetch` wiring as the gallery/price editors.
- `types.ts`: `ProductVariantView` gains `attributeValues: AttributeValueRef[]`; new `AttributeView`,
  `AttributeValueOption`, `CreateVariant`, `UpdateVariant` types. New i18n keys in `hu` + `en`.

## Error handling

- **400** — invalid/unknown attribute‑value id, two values of one attribute, duplicate combo on the
  product, duplicate SKU, blank term label, deleting the last variant, reorder id‑set mismatch.
- **403** — editor flag off, or non‑admin.
- **404** — unknown product, variant not on the product, unknown attribute.

## Testing (combo/uniqueness rules are the risk; none may be skipped)

- **Domain:** `SlugsTest` (lowercase/hyphenate; Hungarian diacritics: "Piros árnyalat" → `piros-arnyalat`,
  "Zöld/Kék" → `zold-kek`); variant‑label combo derivation (sorted by attribute slug, `·`‑joined;
  default → "Alap").
- **Service** (Testcontainers, flag on; seed via `CatalogImporter` incl. an attribute with values):
  create variant with a combo (persisted; label derived; appended position); duplicate SKU → 400;
  duplicate combo → 400; two values of one attribute → 400; unknown value id → 400; update combo;
  delete variant (orphan removed); **delete the only variant → 400**; reorder. Plus a **disabled**
  sibling (flag off) → `EditorDisabledException`. `AttributeAdminServiceTest`: add term (slug derived
  + persisted), **idempotent** on an existing slug (returns existing, no duplicate), unknown attribute
  → 404, flag‑off → `EditorDisabledException`.
- **Controller** (MockMvc + Testcontainers): each endpoint happy path + 400/403/404 + non‑admin + a
  flag‑off endpoint test (403). `GET /attributes` returns the catalog.
- **Frontend:** `npm run build` + existing vitest suite green; a small unit test for the combo‑payload
  builder if it's extracted.
- **Gate:** full `mvn verify` (incl. ArchUnit `ModularityTest`) + admin‑ui build/test.

## Files (anticipated)

- Create: `domain/catalog/Slugs.java`; `application/catalog/ProductVariantService.java`,
  `application/catalog/AttributeAdminService.java`; `api/admin/ProductVariantController.java`,
  `api/admin/AttributeController.java`.
- Modify: `domain/catalog/Product.java` (`removeVariant`); `application/catalog/ProductAdminQueryService.java`
  (`VariantView.attributeValues`, label upgrade, `attributes()` + `AttributeView`/`AttributeValueOption`);
  `admin-ui/src/types.ts`, `admin-ui/src/pages/products/show.tsx`, `admin-ui/src/i18n/{hu,en}/products.json`.
- Tests: `SlugsTest`, `ProductVariantServiceTest` (+ disabled), `AttributeAdminServiceTest` (+ disabled),
  `ProductVariantControllerTest` (+ disabled), `AttributeControllerTest`.

## Notes / decisions

- **`Slugs` utility** is added server‑side (rather than trusting a client‑sent slug) so the term slug
  is derived consistently and validated; it's a small reusable pure helper.
- **`addValue` is idempotent** — re‑adding a label whose slug already exists under the attribute
  returns the existing term (avoids accidental duplicates and 500s from the `(attribute_id, slug)`
  constraint).
- The **primary/default** variant stays derived; converting a simple product to variable is just
  adding attribute‑bearing variants and editing/deleting the old `"Alap"` one like any other.
- Storefront pricing/availability already reads variants + attribute combos; this slice only changes
  the admin write path.
