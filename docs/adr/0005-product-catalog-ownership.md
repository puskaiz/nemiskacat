# 5. Product catalog ownership migrates to this app post-Woo-cutover

Date: 2026-06-18

## Status

Accepted

## Context

The catalog's initial source is WooCommerce via a repeatable, read-only import. CLAUDE.md
originally framed Woo as the place product editing happens until cutover, with price and stock
syncing from Kulcs-Soft. The team has now decided the longer-term ownership model.

## Decision

- A final repeatable **Woo sync/migration** runs before go-live. **After cutover, this app is
  the product master** for the webshop-relevant fields: name, descriptions, SEO, categories,
  images, status, **and price**.
- **Price** is owned and editable in this app (supersedes the earlier "ár a Kulcs-Softból"
  assumption for the webshop price). Stored as gross `regularPriceHuf` (current model); the
  admin editor accepts net-or-gross input and computes via the product VAT rate.
- **Stock** is authoritative in **Kulcs-Soft** (synced in). Interim — until that sync is
  reliable — stock is editable in this app, in a **separate mask** from the content/price editor.
- **Slugs** remain immutable / Woo 1:1.
- The admin product editor (content/price + stock) is gated behind a feature flag
  (`webshop.admin.product-editor-enabled`). The team has chosen to **enable editing now**, so the
  flag now **defaults on**; setting env `ADMIN_PRODUCT_EDITOR_ENABLED=false` can still disable it.
  Reads are not gated. The Woo importer stays read-only — admin edits never write to live Woo.

## Consequences

- The admin gains product read endpoints now (P1) and flag-gated write endpoints later (P2 price/
  content, P3 stock); see `docs/superpowers/specs/2026-06-18-admin-products-management-design.md`.
- CLAUDE.md's "termékszerkesztés helye a Woo" / "ár a Kulcs-Softból" notes are interim; this ADR
  records the target state. The storefront still receives derived stock availability, not raw
  counts.
- A future migration effort must reconcile app-owned edits with any late Woo re-imports (the
  importer's idempotent upsert must not clobber app-owned fields after cutover — to be designed
  when the migration is built).
