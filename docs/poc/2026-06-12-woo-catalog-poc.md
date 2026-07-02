# WooCommerce catalog PoC report (T1)

**Date:** 2026-06-12 · **Source:** legacy nemiskacat.hu WooCommerce DB dump
(`guxdop_` prefix, classic posts+postmeta storage), queried in the local `wp_db`
MySQL container. Queries: `scripts/woo-dump-poc/catalog-report.sql`.

> Done from a **DB snapshot**, not the Woo REST API. The canonical, repeatable
> importer (T4) still targets the REST API per the plan, because WooCommerce stays
> the editing source until cutover. This snapshot is for sizing T3/T4 and for
> seeding dev/staging.

## Headline numbers

| Metric | Value |
|---|---|
| Products | **702** total — 430 published, 272 draft |
| Product types | 566 simple, 136 variable |
| Variations | 674 (across ~137 variable parents) |
| SKU coverage (published) | **429 / 430** have a non-empty SKU; **0 duplicate SKUs** |
| Product categories | 41 (`product_cat`): 16 root + 25 child — shallow, ~2 levels |
| Global attributes | 10 (`select`); main axes: szín (232), kiszerelés (244), szín-2 (158) |
| On sale now | 234 products (sale prices live in Woo) |
| Stock status (products+variations) | 1130 instock, 263 outofstock, 3 null |
| Attachments (all media) | 8304; product gallery avg **4.8** imgs, max 17 |
| SEO (Yoast, all product statuses) | title 608, metadesc 634, focuskw 646 (~600/702) |
| Charset | clean utf8mb4, no double-encoding |
| Price storage | `DECIMAL(_,4)`, whole HUF (e.g. 333, 3700) |

Slugs are clean and SEO-friendly (e.g. `pure-white-chalk-paint-kretafestek-annie-sloan`),
so the URL-preservation requirement (CLAUDE.md rule #7) is straightforward.

## Implications for the plan

- **Variants are required and non-trivial (resolves open question D5 / TERV §11.5).**
  136 variable products, multi-axis (szín × kiszerelés), with a long tail —
  several parents have 50+ variations (max 54). The T3 catalog model must handle
  multi-axis attributes and per-variation SKU/price/stock. This pushes the M1
  estimate toward the **upper** end of 15–25 days.
- **Woo-side SKU matching for Kulcs-Soft (T2) should be clean:** ~100% SKU coverage,
  no duplicates. Remaining matching risk is on the Kulcs-Soft side (not yet seen).
- **Money decision needed (T3):** prices are whole-HUF decimals. CLAUDE.md rule #6
  mandates minor units. Hungary has no circulating fillér, so store integer HUF as
  the minor unit (scale 0) — i.e. amount == forint. Make this explicit in T3.
- **Image migration is sizable:** ~8.3k attachments, avg 4.8 gallery images/product.
  Confirms the object-storage + hash-key approach (TERV §3.8); plan a bulk copy in T4.
- **Drafts (272):** decide whether drafts import at all, or only `publish`. Suggest
  importing `publish` first; drafts on demand.
- **Attribute cleanup opportunity:** some attributes look non-catalog
  (`idopont`=date on 3 products, `osszeg`=amount) — likely workshop/gift-card
  remnants. Flag for the business before T4 so they don't become noise.

## Caveats

- Counts are from a point-in-time snapshot; the live shop drifts. The T4 REST
  importer is what keeps the catalog current.
- `_price` row count (540) exceeds published products (430): variable parents and
  some duplicate meta rows inflate it — expected for Woo, not a data problem.
