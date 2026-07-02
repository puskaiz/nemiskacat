# 3. Catalog data model

Date: 2026-06-12

## Status

Accepted

## Context

T3 needs the catalog model: products, variants, categories, attributes, images,
pricing, and the derived stock-status rule. The T1 PoC showed multi-axis variants
(szín × kiszerelés, up to 54/parent), ~100% SKU coverage, whole-HUF prices.

## Decision

- **Uniform variant model:** every product has ≥1 variant; a simple product has one
  default variant. Stock and price live on the variant — one code path everywhere.
- **Relational attributes:** `attribute` + `attribute_value` joined to variants
  (supports faceted search later). Multi-price-list (B2B) deferred to M11; price is
  stored on the variant (regular + sale + window).
- **Money** stored as `BIGINT` minor units, HUF = forint (no fillér). Domain `Money`
  value object; entities expose it via accessors.
- **Derived stock status** computed by a pure `StockStatusCalculator`
  (DISCONTINUED > manual flag > unmanaged > qty≤0 > in-stock/low). `availableQty` is
  an input (the ledger `last sync − web orders − POS sales`), keeping the calculator
  persistence-free and exhaustively unit-tested (T3 acceptance).
- **Schema as source of truth:** Flyway `V2__catalog.sql`; `ddl-auto=validate`. JPA
  entities live in `domain/`; enums stored as `TEXT` + `CHECK`; timestamps
  `TIMESTAMPTZ` mapped to `OffsetDateTime`. `woo_id`/`sku` are partial-unique for
  idempotent import (T4).

## Consequences

The default variant for simple products is synthetic (no `woo_id`/`sku` of its own),
created by the importer. Repositories and the product-page query are deferred to
T4/T6. Non-variation display attributes' values and `last_sync_qty` population
(Kulcs-Soft, T5) are not modelled yet.
