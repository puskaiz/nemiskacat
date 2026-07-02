# Admin products management — design (umbrella + P1)

## Context

The admin Products screens currently render the design's **static example data**. The real
catalog already lives in our Postgres (imported from WooCommerce): `Product` (name, slug,
status PUBLISHED/DRAFT, short/long description, SEO title + meta, `vatRatePercent`, categories,
images, variants) and `Variant` (sku, `regularPriceHuf`/`salePriceHuf` (gross HUF),
`lastSyncQty` stock, `lowStockThreshold`, `manualAvailability`). There is a `CatalogQueryService`
(storefront) and `ProductRepository`, but **no admin REST endpoint** over the catalog yet.

This spec wires the admin Products screens to the real catalog and adds editing.

## Long-term direction (product ownership) — confirmed with the team

- The WooCommerce catalog is the *initial* source via a **repeatable import**. Before go-live a
  final Woo **sync/migration** runs; **after cutover this app is the product master** for the
  **webshop-relevant fields** (name, descriptions, SEO, categories, images, status, price).
- **Stock** becomes authoritative in **Kulcs-Soft** (synced in). **Interim** (until the
  Kulcs-Soft sync is reliable) stock is editable **in this app**, but in a **separate mask** from
  the webshop-content editor — never mixed with content/price.
- **Price** is editable **in this app** (this supersedes the earlier "ár a Kulcs-Softból"
  assumption for the webshop price; see ADR `docs/adr/0005-product-catalog-ownership.md`).
- **Slug** stays immutable / Woo 1:1 (CLAUDE.md rule unchanged).
- The admin editor stays behind a **feature flag** (`admin.product-editor.enabled`, default off)
  until the cutover. Reads are not flag-gated. **No writes ever go to the live Woo** — the Woo
  importer remains read-only; admin edits write only to our DB.

## Decomposition (build order: P1 → P2 → P3; each its own plan)

- **P1 — Read** *(this spec, detailed below)*: admin GET products (list + detail) over the real
  catalog; wire the faithful Products grid/list + category chips + a read-only detail view to it.
- **P2 — Content + price editor (flag-gated)**: edit name, short/long description, SEO title +
  meta, status, categories, images, **and price** with **net-or-gross** entry (VAT computed,
  unit-tested); write endpoint behind `admin.product-editor.enabled`. Outlined in §P2.
- **P3 — Stock editor (separate mask, flag-gated, interim)**: edit variant stock in its own
  screen, marked interim until Kulcs-Soft is authoritative. Outlined in §P3.

---

## P1 — Read: admin products list + detail (this slice)

### Backend

New `application/catalog/ProductAdminQueryService` (read-only; mirrors `OrderAdminQueryService`'s
shape) + `api/admin/ProductAdminController` under `/api/admin/products`, ADMIN-gated by
SecurityConfig (same as orders). DTOs:

```java
record ProductSummary(Long id, String name, String slug, String primaryCategory,
                      Long priceGrossHuf, int stockQty, ProductStatus status, int variantCount) {}
record VariantView(Long id, String label, String sku, Long regularPriceHuf, Long salePriceHuf,
                   int stockQty, int lowStockThreshold) {}
record ImageView(String url, String alt) {}
record CategoryRef(String name, String slug) {}
record ProductDetailView(Long id, String name, String slug, ProductStatus status,
                         String shortDescription, String description, String seoTitle,
                         String metaDescription, Integer vatRatePercent,
                         List<CategoryRef> categories, List<ImageView> images,
                         List<VariantView> variants) {}
record PageResult(List<ProductSummary> items, long total) {}
```

- `priceGrossHuf` / `stockQty` for the summary come from the **default variant** (`isDefault`,
  else first by `position`); `stockQty` = that variant's `lastSyncQty` (treat null as 0);
  `variantCount` = variants size; `primaryCategory` = first category name (or "—").
- Endpoints:
  - `GET /api/admin/products?page&size&category&q` → `PageResult`; sets `X-Total-Count`
    (matches the orders list + the SPA data provider). `category` filters by category slug; `q`
    matches product name (substring, case-insensitive). New `ProductRepository` paginated finder
    (e.g. a `@Query` with nullable `:category`/`:q`, mirroring the orders `search`).
  - `GET /api/admin/products/{id}` → `ProductDetailView` (404 via the shared NotFoundException).
  - `GET /api/admin/categories` → `List<CategoryRef>` for the filter chips (from
    `CategoryRepository`, name + slug + product-count optional).

**Stock display rule:** admin sees the numeric synced quantity (`lastSyncQty`). This is the
*admin* context; the storefront still receives only derived availability (CLAUDE.md §5 unchanged).

### Frontend

Wire the already-ported faithful Products screens (no visual change) to the real API via the
Refine data provider:
- `pages/products/index.tsx`: replace the static fixture with `useTable`/`useList` on resource
  `products` (`/api/admin/products`); grid + list views, category filter chips driven by
  `GET /api/admin/categories`, pagination via the shared `Pagination` (real total). Status pill
  from `status` (PUBLISHED→"Közzétéve" green / DRAFT→"Vázlat" gray). Price + stock from the
  summary.
- `pages/categories/index.tsx`: wire to `GET /api/admin/categories` (name/slug/count/status).
- **New read-only detail** `pages/products/show.tsx` (route `/products/show/:id`, registered in
  `App.tsx` + the products resource `show`): the prototype product-detail layout (lines 270-295)
  rendered read-only — gallery, name, description, the variants table (variant/SKU/price/stock),
  Pricing & stock (price, 27% ÁFA, total stock), Organization (category, status). No edit
  controls in P1 (those arrive in P2/P3 behind the flag). Row click in the list → this route.
- Register a `products` data resource on `<Refine>` mapping to `/api/admin/products`.

### Testing (P1)

- Backend (Testcontainers): `ProductAdminQueryServiceTest` / `ProductAdminControllerTest` — seed
  catalog via the existing importer (as `RefundServiceTest` does), assert list (pagination +
  `X-Total-Count`, category filter, q filter), detail (variants/categories/images mapping,
  default-variant price/stock, 404). Read-only — no money/stock writes in P1.
- Frontend: `npm run build` green; the products/categories pages compile against the real
  resource; a small pure mapping test if any non-trivial transform is added. No new unit-test
  framework.
- `mvn verify` green incl. ArchUnit (the new `application/catalog` + `api/admin` classes follow
  the existing module dependencies).

---

## P2 — Content + price editor (flag-gated) — outline (own spec/plan later)

- Feature flag `admin.product-editor.enabled` (config; default off). Gates the write endpoints
  **and** the edit UI (when off, the detail view from P1 stays read-only).
- `POST/PUT /api/admin/products/{id}` writes webshop fields (name, short/long description, SEO
  title + meta, status, categories, images) — to our DB only, never Woo.
- **Price (net/gross):** the editor accepts either a **net** or a **gross** amount per variant;
  the service computes the counterpart from the product `vatRatePercent` and stores **gross**
  `regularPriceHuf` (current model; no schema change). VAT math in the service, **unit-tested**
  (CLAUDE.md money rule — minor units, scale 0, rounding defined and covered).
- Validation: required name; price ≥ 0; status enum. RFC 9457 problem+json errors (existing
  `AdminExceptionHandler`).
- UI: the prototype product-edit form (name/description/images/variants/pricing/organization)
  becomes editable behind the flag, with a net/gross price toggle.

## P3 — Stock editor (separate mask, flag-gated, interim) — outline

- A distinct screen (not the content editor) to edit `Variant.lastSyncQty` (interim manual
  stock), behind the same flag. `POST /api/admin/products/{id}/stock` (or per-variant).
- Clearly labelled interim ("Kulcs-Soft szinkronig"); made read-only once the Kulcs-Soft
  stock sync is authoritative. Stock-write tests (the inventory error branches are not skipped —
  CLAUDE.md).

## Out of scope / unchanged

- The Woo importer stays read-only (no writes to Woo, ever).
- Storefront availability remains derived status, not raw counts (CLAUDE.md §5).
- The final Woo migration/sync-before-go-live is a separate effort (noted in the ADR).
