# P2b‑1 — Per‑variant price editing (net/gross) — Design

Date: 2026-06-19
Status: Approved (brainstormed 2026-06-19)
Builds on: ADR 0005 (product catalog ownership — price owned in this app, stored gross),
the P2 editor spec (`2026-06-18-admin-products-p2-editor-design.md`), P2a content editor,
and P2c (which established the flag‑gated edit service + per‑resource admin endpoint patterns).

## Goal

Let an admin edit a variant's **regular** and **sale** price (with an optional sale window) on
**existing** variants, entering the amount as **net or gross**, behind the product‑editor flag.
Prices are stored **gross** (the canonical form); the net counterpart is derived. This is the first
of two P2b slices; **variant create/edit/delete and attribute‑combination editing are P2b‑2** and
out of scope here.

## Scope

**In scope**
- Edit `regularPriceHuf`, `salePriceHuf`, `saleFrom`, `saleTo` on an existing `Variant`.
- Net‑or‑gross input; server converts to gross using the product's **effective VAT rate**.
- The effective VAT rate is **read‑only** here: `Product.vatRatePercent` when set, else the
  tax‑class default (27% standard / 5% `reduced-rate`). Editing `vatRatePercent` is not part of P2b.
- Flag‑gated (`webshop.admin.product-editor-enabled`, default on) + ADMIN + CSRF.

**Out of scope (P2b‑2 or later)**
- Adding/removing variants; editing SKU, attribute‑value combinations, position, default flag.
- Editing `vatRatePercent` / tax class.
- Stock editing (separate mask per ADR 0005; Kulcs‑Soft authoritative).
- Slugs (immutable, Woo 1:1).

## Money & units

- HUF amounts are `long` **forint** (HUF has no circulating subunit; this matches existing
  `Variant.regularPriceHuf` and `Money`). Stored value is always **gross**.
- Sale‑window timestamps are `OffsetDateTime` stored **UTC**, displayed Europe/Budapest (CLAUDE.md #6).

## Architecture

Thin REST controller → flag‑gated application service → pure domain VAT math; mirrors the P2c
shape (each mutation returns the updated `ProductDetailView`; reads stay in `ProductAdminQueryService`).

### 1. Domain — `VatPricing` (pure, unit‑tested) — `domain/catalog`

A new pure class owning the single source of truth for the VAT rate rule and net↔gross conversion:

```java
public final class VatPricing {
    public static final int STANDARD_RATE = 27;
    public static final int REDUCED_RATE = 5;

    /** Rate from an explicit product rate, else the tax‑class default. */
    public static int effectiveRatePercent(Integer vatRatePercent, String taxClass) { ... }
    /** Tax‑class default only: "reduced-rate" → 5, everything else (incl. null/blank) → 27. */
    public static int rateForTaxClass(String taxClass) { ... }

    public static long toGross(long net, int ratePercent)  { return Math.round(net * (100 + ratePercent) / 100.0); }
    public static long toNet(long gross, int ratePercent)   { return Math.round(gross * 100.0 / (100 + ratePercent)); }
}
```

- `effectiveRatePercent(rate, taxClass)` = `rate != null ? rate : rateForTaxClass(taxClass)`.
- **De‑duplication:** `domain/checkout/VatCalculator.ratePercentFor(taxClass)` is refactored to delegate
  to `VatPricing.rateForTaxClass(taxClass)`, and `VatCalculator`'s `STANDARD_RATE`/`REDUCED_RATE`
  reference `VatPricing`'s. The dependency direction is fine: `domain/checkout` already depends on
  `domain/catalog` (it uses `Variant`/`Money`). The existing `VatCalculatorTest` must stay green.
- Gross is canonical; `toNet` is for display. Round‑trip net→gross→net is **not** guaranteed identity
  (rounding) — acceptable because gross is what we store.

### 2. Application — `ProductVariantPriceService` (`application/catalog`)

```java
public enum PriceBasis { NET, GROSS }
public record PriceInput(long amount, PriceBasis basis) {}     // amount in HUF‑forint, ≥ 0
public record PriceUpdate(PriceInput regular,                  // null → clear regular (and sale)
                          PriceInput sale,                     // null → clear sale + window
                          OffsetDateTime saleFrom,             // nullable
                          OffsetDateTime saleTo) {}            // nullable

@Transactional
public ProductAdminQueryService.ProductDetailView updatePrice(Long productId, Long variantId, PriceUpdate cmd);
```

Behaviour:
1. `guard()` — `EditorDisabledException` (→403) when the flag is off (reuse the P2a/P2c guard).
2. Load product (`NotFoundException` →404); find the variant **on that product** (else `NotFoundException`
   →404 — never edit a variant that belongs to another product).
3. `rate = VatPricing.effectiveRatePercent(product.getVatRatePercent(), product.getTaxClass())`.
4. Convert each present `PriceInput` to gross: `basis == GROSS ? amount : VatPricing.toGross(amount, rate)`.
5. Write `regularPriceHuf`, `salePriceHuf`, `saleFrom`, `saleTo` via the existing `Variant` setters.
6. Return `query.detail(productId)`.

Validation (all → `IllegalArgumentException`, mapped to 400):
- any `amount < 0`;
- `sale != null` requires `regular != null` and `saleGross ≤ regularGross`;
- if both `saleFrom` and `saleTo` are set, `saleFrom ≤ saleTo`;
- `sale == null` ⇒ `salePriceHuf`, `saleFrom`, `saleTo` are all cleared;
- `regular == null` ⇒ `regularPriceHuf = null` and sale + window cleared (an unpriced variant can't be on sale).

### 3. API — `ProductVariantPriceController` (`api/admin`)

```
PUT /api/admin/products/{id}/variants/{variantId}/price   body: PriceUpdate (JSON)   → ProductDetailView
```
Thin: delegates to `ProductVariantPriceService.updatePrice`. Errors via the existing
`AdminExceptionHandler`: `EditorDisabledException`→403, `NotFoundException`→404,
`IllegalArgumentException`→400; non‑admin→403 (security config unchanged: `/api/admin/**` is `ADMIN`).

`ProductAdminQueryService.ProductDetailView` gains a read‑only field **`Integer effectiveVatRatePercent`**
(computed via `VatPricing.effectiveRatePercent`) so the SPA can preview net↔gross with the same rate the
server uses. `VariantView` is unchanged (it already exposes `regularPriceHuf`/`salePriceHuf`).

### 4. Frontend — price editing in `admin-ui/src/pages/products/show.tsx` (editable branch only)

- In the editable branch, the per‑variant rows become price‑editable; the **read‑only (flag‑off)
  branch is unchanged**.
- Per variant: **regular** and (optional) **sale** each as an `amount` input + a **Nettó / Bruttó**
  basis toggle; optional **sale from/to** datetime inputs. The other (computed) value shows as a live
  hint computed client‑side with the same `toNet`/`toGross` formula and the product's
  `effectiveVatRatePercent` (shown once, read‑only, e.g. "Áfa: 27%").
- A per‑row **Mentés** button calls `PUT …/variants/{variantId}/price` with the `PriceUpdate` payload,
  then `refetch()` + a success/error toast — the gallery‑ops pattern (`apiFetch`, `API_BASE`,
  `App.useApp()` message; error body surfaced via `message.error(await res.text())`).
- SKU and attribute combinations remain read‑only here (P2b‑2).
- `types.ts`: add `effectiveVatRatePercent: number | null` to `ProductDetail`; add the
  `PriceUpdate`/`PriceInput`/`PriceBasis` request types.

## Error handling

- 400 — validation (`IllegalArgumentException`): negative amount, `sale > regular`, `saleFrom > saleTo`.
- 403 — flag off (`EditorDisabledException`) or non‑admin.
- 404 — unknown product, or variant not belonging to the product (`NotFoundException`).
- The SPA surfaces the server's message body on non‑2xx and keeps the row's inputs intact.

## Testing (CLAUDE.md #6 — money/VAT rules unit‑tested, never skipped/disabled)

- **`VatPricingTest`** (pure): `toGross`/`toNet` incl. rounding edges (net 1000 @27% → 1270;
  gross 1270 @27% → 1000; gross 1000 @27% → 787; a `reduced-rate` 5% case);
  `effectiveRatePercent` for explicit rate, null+standard, null+`reduced-rate`, null+null(→27).
- **`VatCalculatorTest`** stays green after the delegation refactor (regression guard).
- **`ProductVariantPriceServiceTest`** (Testcontainers, flag on; seed via `CatalogImporter`,
  `@Transactional`): NET input → stored gross correct; GROSS input → stored as‑is; sale + window
  set then cleared; validation branches (negative, sale>regular, from>to) throw
  `IllegalArgumentException`; variant‑not‑on‑product → `NotFoundException`. Plus a **disabled**
  sibling (flag off) → `EditorDisabledException`.
- **`ProductVariantPriceControllerTest`** (MockMvc + Testcontainers): PUT 200 + body reflects the new
  gross; 400 (negative, sale>regular); 403 non‑admin; 404 unknown variant. Plus a flag‑off endpoint
  test → 403.
- **Frontend:** `npm run build` + existing vitest suite green; if the net/gross preview is extracted
  into a tiny helper, add a unit test for it.
- Full gate: `mvn verify` (+ ArchUnit `ModularityTest` — confirms `VatPricing` placement keeps module
  boundaries) and admin‑ui build/test.

## Files (anticipated)

- Create `src/main/java/hu/deposoft/webshop/domain/catalog/VatPricing.java`
- Modify `src/main/java/hu/deposoft/webshop/domain/checkout/VatCalculator.java` (delegate rate rule)
- Create `src/main/java/hu/deposoft/webshop/application/catalog/ProductVariantPriceService.java`
- Create `src/main/java/hu/deposoft/webshop/api/admin/ProductVariantPriceController.java`
- Modify `src/main/java/hu/deposoft/webshop/application/catalog/ProductAdminQueryService.java`
  (`ProductDetailView` gains `effectiveVatRatePercent`; `detail()` computes it)
- Modify `admin-ui/src/pages/products/show.tsx`, `admin-ui/src/types.ts`, and the
  `admin-ui/src/i18n/{hu,en}/products.json` files (new labels/toasts)
- Tests: `VatPricingTest`, `ProductVariantPriceServiceTest` (+ disabled sibling),
  `ProductVariantPriceControllerTest` (+ disabled sibling)

## Notes / decisions

- **Default‑variant & combo concerns don't arise here** — P2b‑1 only mutates price fields on an
  existing variant; it never adds/removes variants or touches attribute combinations.
- A `reduced-rate` product whose `vatRatePercent` is null correctly converts at 5% via the tax‑class
  default; an explicit `vatRatePercent` always wins.
- Storefront pricing (`PriceCalculator`/`Money`) is untouched; it already reads the gross fields and
  the sale window this editor writes.
