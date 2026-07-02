# T4 — WooCommerce importer (idempotent) design

**Date:** 2026-06-12 · **Task:** B-webshop `TASKS.md` T4 · **Status:** approved (brainstorming)

## Context

First task that puts real product data into Postgres. The catalog model (T3) exists.
The canonical importer targets the Woo REST API, but the REST key is `[EMBER]`-blocked;
the WooCommerce DB runs locally (`wp_db`). Decision: keep the importer **source-agnostic**
behind a `CatalogSource` port; feed it now from a JSON export of `wp_db`, slot the REST
source behind the same port later.

## Approved decisions

- **Source now:** a `scripts/` exporter turns `wp_db` into a JSON catalog (catalog
  fields only — no users/options/keys). `JsonFileCatalogSource` reads it.
- **Images:** object storage is `[EMBER]`-blocked. Import image **metadata** (alt,
  position, featured) with a deterministic `storage_key` (`product/{wooId}/{attachmentId}-{slug}`);
  binary download + content-hash immutable keys come with the storage migration.
- **Scheduling:** a manual trigger now (runner + report); `@Scheduled` later.

## Components

```
integrations/woo/
  SourceCatalog, SourceCategory, SourceAttribute, SourceProduct,
  SourceVariant, SourceImage        # plain DTO records (the neutral import model)
  CatalogSource (port)              # load() -> SourceCatalog
  JsonFileCatalogSource             # reads the JSON export (Jackson)
application/catalog/
  CatalogImporter                   # idempotent upsert; returns ImportReport
  ImportReport                      # created/updated/skipped per entity + errors
domain/catalog/  (repositories)
  ProductRepository, VariantRepository, CategoryRepository,
  AttributeRepository, AttributeValueRepository, ProductImageRepository
```

## Idempotency / upsert keys

- **Category:** by `woo_term_id`. Two-pass: upsert all, then link `parent` by parent
  woo term id.
- **Attribute:** by `woo_attribute_id`; values by `(attribute, slug)`.
- **Product:** by `woo_id`. Update mutable fields; **slug is preserved** on update
  (CLAUDE.md #7 — never rewrite a slug; log if the source differs). Category set and
  product-attribute axes are replaced from source.
- **Variant:** with `woo_id` (variable variations) → by `woo_id`; the synthetic
  **default variant** of a simple product (no `woo_id`) → by `(product, is_default)`.
- **Image:** by `(product, storage_key)`.

A simple product yields exactly one `is_default=true` variant carrying the product's
SKU/price/stock. A variable product yields one variant per source variation.

## Testing (TDD)

`CatalogImporter` is tested against Testcontainers Postgres with hand-built
`SourceCatalog` fixtures (no real JSON needed):

1. fresh import creates product + default variant + category (report counts)
2. re-import of the same catalog creates **no duplicates**, ids stable (idempotency)
3. changed product name in source → updated on re-import
4. new product in source → created on re-import
5. simple product → exactly one default variant with the product SKU
6. variable product → one variant per variation with attribute values
7. category parent linkage resolved

Then the exporter is run against `wp_db` and the real catalog loaded into local
Postgres; numbers reported (sanity vs the T1 PoC: ~430 published products).

## Out of scope

REST `CatalogSource` (needs the `[EMBER]` key); image binary download/upload + content
hashing (needs storage); Kulcs-Soft stock sync (T5); Meilisearch indexing (T7);
category-save webhook (T17).

## Acceptance

`mvn verify` green incl. idempotency tests; running the importer twice produces no
duplicates and propagates a Woo-side change (name/new product) on the next run.
