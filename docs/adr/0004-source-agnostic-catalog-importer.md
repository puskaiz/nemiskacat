# 4. Source-agnostic catalog importer

Date: 2026-06-12

## Status

Accepted

## Context

T4 requires the idempotent Woo importer, but the canonical source (Woo REST API)
is blocked on the `[EMBER]` REST key, while the full WooCommerce DB runs locally
(`wp_db` container).

## Decision

The importer (`application/catalog/CatalogImporter`) consumes a neutral
`SourceCatalog` snapshot behind the `CatalogSource` port (`integrations/woo`).
Sources plug in behind the port:

- **now:** `scripts/woo-export/export.py` (reads `wp_db`, catalog tables only,
  published products only) → JSON → `JsonFileCatalogSource`;
- **later:** a Woo REST source, same port, no importer changes.

Upsert keys: category by `woo_term_id`; attribute by `woo_attribute_id`; product
by `woo_id` (slug immutable on update, CLAUDE.md #7); variation by `woo_id`;
simple products get one synthetic default variant keyed by `(product, is_default)`;
image by `(product, storage_key)`. Image `storage_key` keeps the original
`wp/<path>` until object storage exists, then files are re-keyed to content-hash
keys. Import stock becomes the ledger baseline (`recordSyncedStock`) until the
Kulcs-Soft sync (T5) takes over.

Trigger: manual one-shot run (`import` profile, no web server); scheduling comes
with the REST source.

## Consequences

Deletion/deactivation of products that disappear from the source is not handled
yet (revisit with the REST source — the dump snapshot is always complete).
Jackson 3 (`tools.jackson`) is the JSON mapper, per Spring Boot 4 default.
Verified on real data: 430 products / 887 variants / 2067 images imported; second
run is pure update (0 created, 0 errors).
