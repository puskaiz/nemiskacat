# T3 — Catalog data model design

**Date:** 2026-06-12 · **Task:** B-webshop `TASKS.md` T3 · **Status:** approved (brainstorming)

## Context

Catalog core: entities + Flyway migrations for product, variant, category, image
metadata, pricing, and the derived **stock-status** computation in the domain
layer. Shaped by the T1 PoC (`docs/poc/2026-06-12-woo-catalog-poc.md`): 566 simple
+ 136 variable products, multi-axis variants (szín × kiszerelés, up to 54/parent),
~100% SKU coverage, whole-HUF prices, ~8.3k images.

## Approved decisions

- **Uniform variant model:** every product has ≥1 variant; a simple product has one
  default variant. Stock and price always live on the variant — one code path for
  cart/checkout/stock.
- **Relational attributes:** `attribute` + `attribute_value` entities, joined to
  variants. Supports faceted search/filtering later.
- **Price on the variant** (regular + sale + sale window); the multi-price-list
  abstraction is deferred to B2B (M11).
- **Money:** stored as `BIGINT` minor units, HUF. No circulating fillér → minor unit
  = forint (scale 0). Domain `Money` value object wraps `long amount` + currency.
- **Slugs** copied 1:1 from Woo and immutable (CLAUDE.md #7); `woo_id`/`sku` carried
  for idempotent upsert (T4).

## Schema (Flyway `V2__catalog.sql`)

```
category(id, woo_term_id UQ, slug UQ, name, parent_id FK→category, sort_order,
         description, seo_title, meta_description, created_at, updated_at)

product(id, woo_id UQ, slug UQ, name, type[SIMPLE|VARIABLE],
        status[PUBLISHED|DRAFT], lifecycle[ACTIVE|DISCONTINUED],
        short_description, description, tax_class,
        seo_title, meta_description, created_at, updated_at)

product_category(product_id FK, category_id FK)  -- PK(product_id, category_id)

attribute(id, woo_attribute_id UQ, slug UQ, label, type)
attribute_value(id, attribute_id FK, slug, label, sort_order)  -- UQ(attribute_id, slug)
product_attribute(product_id FK, attribute_id FK, position, for_variation)
                                                  -- PK(product_id, attribute_id)

variant(id, product_id FK, woo_id UQ NULL, sku UQ NULL, is_default,
        regular_price_huf, sale_price_huf, sale_from, sale_to, weight_grams,
        position,
        -- stock fields (raw qty never leaves the service layer):
        manage_stock, last_sync_qty, last_sync_at,
        manual_availability[NULL|TEMPORARILY_UNAVAILABLE|PREORDER],
        low_stock_threshold,
        created_at, updated_at)

variant_attribute_value(variant_id FK, attribute_value_id FK)
                                                  -- PK(variant_id, attribute_value_id)

product_image(id, product_id FK, variant_id FK NULL, storage_key, alt,
              position, is_featured, created_at)
```

Timestamps `TIMESTAMPTZ` (UTC). `sku`/`woo_id` use partial-unique indexes (unique
when not null). Enums stored as `TEXT` with a `CHECK` constraint (portable, readable).

JPA entities live in `domain/`, mapped to these tables; `spring.jpa.hibernate.ddl-auto`
stays `validate`, so the migration is the single source of schema truth. The
`Money` value object and the stock calculator are plain domain classes (no JPA).

## Stock-status computation (the tested core — T3 acceptance)

A **pure** domain function `StockStatusCalculator.evaluate(input) → StockAvailability`.
`StockAvailability` = `{ status: StockStatus, lowStock: boolean }`.

`StockStatus` ∈ { `IN_STOCK`, `OUT_OF_STOCK`, `TEMPORARILY_UNAVAILABLE`,
`DISCONTINUED`, `PREORDER` }.

Inputs: `lifecycle` (ACTIVE/DISCONTINUED), `manualAvailability`
(null/TEMPORARILY_UNAVAILABLE/PREORDER), `manageStock`, `availableQty`,
`lowStockThreshold`.

`availableQty` is computed elsewhere as the availability ledger
`last_sync_qty − web orders since sync − POS sales since sync` (TERV §3.7); T3
takes it as an input so the calculator stays pure and fully unit-testable. The
order/POS wiring lands in T5/checkout.

Precedence (first match wins):

1. `lifecycle == DISCONTINUED` → **DISCONTINUED**
2. `manualAvailability == TEMPORARILY_UNAVAILABLE` → **TEMPORARILY_UNAVAILABLE**
3. `manualAvailability == PREORDER` → **PREORDER** (sellable even at qty 0)
4. `!manageStock` → **IN_STOCK** (lowStock=false)
5. `availableQty <= 0` → **OUT_OF_STOCK**
6. else → **IN_STOCK**, `lowStock = availableQty <= lowStockThreshold`

Unit tests cover every branch and the boundaries (qty 0, qty == threshold,
threshold+1, discontinued-overrides-everything, preorder-overrides-zero).

## Out of scope (later tasks)

- Repositories/queries beyond what the model needs (T4 importer, T6 product page).
- Multi-price-list / B2B pricing (M11), non-variation display attributes' values,
  Kulcs-Soft sync of `last_sync_qty` (T5), image upload to storage (T4).

## Acceptance (verifiable now)

`mvn verify` green: `StockStatusCalculator` unit tests pass for every transition;
the existing boot test validates the JPA entities against the `V2` migration.
