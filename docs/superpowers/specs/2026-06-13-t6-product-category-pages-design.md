# T6 — Product & category page design

**Date:** 2026-06-13 · **Task:** B-webshop `TASKS.md` T6 · **Status:** approved (brainstorming)

## Context

Server-rendered product and category pages over the imported catalog (T4).
Acceptance: product page HTML is byte-identical for two cookie-less visitors;
`Product` JSON-LD validates; out-of-stock state renders correctly.

## URLs (verified from the live WP config — 1:1 preservation, CLAUDE.md #7)

WP permalinks: `product_base=/product`, `category_base=termekkategoria`,
`permalink_structure=/%postname%/` →

- product page: **`/product/{slug}`** (and `/product/{slug}/` — both served,
  canonical = the trailing-slash WP form)
- category page: **`/termekkategoria/{slug}`** (same trailing-slash handling)
- category pagination: `?page=N` for now; the WP `/page/N/` form goes on the
  cutover URL map (T22) — listing pagination is SEO-light.

## Decisions

- **Design:** the `shared/` design system comes from the A track (K1,
  `[EMBER]`-blocked). Until then: clean, semantic, minimally styled templates;
  swapping in the shared fragments/CSS later is a template-only change.
- **Attribute value labels fixed now:** the export gains the `pa_*` term names
  (`SourceAttribute.values`), the importer upserts `AttributeValue.label` — the
  product page must show "2,5 l", not `25-l`.
- **Effective price** is domain logic: `PriceCalculator` — sale price applies iff
  within its `[saleFrom, saleTo]` window (null = open end). Pure, unit-tested.
- **Availability:** `availableQty = lastSyncQty` until the order/POS ledger
  arrives (T5/T9); status via the existing `StockStatusCalculator`. Raw counts
  never reach the view — only `StockStatus` + lowStock flag (CLAUDE.md #5).
- **JSON-LD:** built server-side (Jackson, stable field order) and emitted raw in
  a `<script type="application/ld+json">`. Simple product → `Offer`; variable →
  `AggregateOffer` (lowPrice/highPrice/offerCount). Availability mapping:
  IN_STOCK→InStock (lowStock→LimitedAvailability), OUT_OF_STOCK→OutOfStock,
  TEMPORARILY_UNAVAILABLE→OutOfStock, DISCONTINUED→Discontinued, PREORDER→PreOrder.
- **Cache headers:** `Cache-Control: public, max-age=0, s-maxage=60` — CDN
  micro-cache ready (TERV §3.3), browser revalidates. No session is touched;
  no Set-Cookie may appear (asserted by test).
- **Images:** no object storage yet; `webshop.images.base-url` config maps the
  interim `wp/<path>` storage keys to a URL (locally the running WP serves
  `wp-content/uploads`). Swapped when storage arrives.

## Components

```
domain/catalog/PriceCalculator        # pure effective-price rule (+ EffectivePrice)
application/catalog/CatalogQueryService  # ProductPageView / CategoryPageView records
web/ProductController                 # GET /product/{slug}[/]
web/CategoryController                # GET /termekkategoria/{slug}[/] (?page=N, 24/page)
templates/product.html, category.html
```

Controllers stay thin: load view from the query service, set headers, render.
404 for unknown slug or non-published product.

## Tests (TDD)

- PriceCalculator: no sale / active sale / before window / after window / open ends.
- Importer: attribute value labels created and updated from source values.
- Web (MockMvc + Testcontainers, data seeded through the importer):
  product 200 + JSON-LD content; byte-identical two requests; no Set-Cookie +
  correct Cache-Control; unknown slug 404; draft product 404; out-of-stock state
  text; category page lists product with price; trailing-slash variant serves.

## Out of scope

Cart/htmx interactivity (T8), Meilisearch (T7), shared/ design integration (K1),
image binaries (storage task), Caffeine cache (optimization, micro-cache suffices).
