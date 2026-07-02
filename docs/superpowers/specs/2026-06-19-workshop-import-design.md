# Workshop import (from WooCommerce/Elementor) — Design

Date: 2026-06-19
Status: Approved (brainstormed 2026-06-19)
Builds on: the catalog import slice (ADR 0004 source-agnostic importer, `CatalogImporter` /
`CatalogImportRunner` / `export.py`) and the workshop domain (`WorkshopService`, `Product`
type=WORKSHOP). Mirrors the catalog importer's structure and idempotency.

## Goal

Import the **5 base workshops** from the legacy WooCommerce site into the new app as
`Product type=WORKSHOP`, including each workshop's **image slider (gallery)** and its
**structured content sections**. Sessions/dates/prices are **out of scope** — the user creates
upcoming sessions in the admin.

## Source of truth

The legacy data lives in a local MariaDB dump (`client7002dbwork`, container
`nemiskacat-work-db`, prefix `lxoplw_`). The 5 workshops are WordPress **Pages** built with
Elementor; their content + slider live in `lxoplw_postmeta._elementor_data` (JSON). The Events
Calendar ticket-products (dated occurrences) are **not** imported.

The 5 pages (post_type=page), keyed by slug (immutable, taken 1:1 from Woo — CLAUDE.md #7):

| wp page id | slug |
|---|---|
| 38573 | annie-sloan-alaptechnikak-butorfesto-workshop |
| 40301 | annie-sloan-kisbutorfesto-workshop |
| 43799 | annie-sloan-kretafestek-dekoracios-technikak |
| 43532 | butorfestes-alapok-workshop |
| 42806 | dekoracios-technikak-targykeszito-workshop-i |

## Two-stage pipeline (mirrors catalog import)

### Stage 1 — Export (`scripts/woo-export/export-workshops.py`)

Reads the work DB (via `docker exec nemiskacat-work-db mariadb -uroot -proot_password
client7002dbwork -N --raw -e "..."`; **`--raw` is required** — batch mode otherwise escapes
newlines and corrupts the Elementor JSON). For each of the 5 pages it parses `_elementor_data`
and walks the widget tree **in document order**, extracting:

- **name** = the page's first `heading` (the workshop title) / `post_title`.
- **descriptionHtml** = the ordered content sections assembled into HTML. For each `heading`
  emit `<h2>…</h2>`; for each `text-editor` emit its `editor` HTML; for `toggle`/`accordion`
  emit each tab as `<h3>tab_title</h3>` + content; keep inline `image` widgets as `<img>` in
  place. **Skip** the dynamic "Következő időpontok" heading (sessions block) and the trailing
  newsletter CTA heading ("…Kérj értesítést…").
- **images** = the `media-carousel` (or `image-carousel`) widget's ordered slide image URLs —
  this is the slider/gallery.

Output JSON to stdout (the contract below). Mirror `export.py` conventions (UTF-8, JSON arrays).
Reference extraction logic already validated in `/tmp/extract_ws.py`.

### Stage 2 — Import (Java, `@Profile("import-workshops")`)

- `integrations/woo/SourceWorkshop` (DTO record) matching the contract.
- `application/workshop/WorkshopImporter` — upsert each workshop **by `externalId`** (the wp
  page id; fallback slug). Create via the workshop path (type=WORKSHOP, invoiceSource=BILLINGO,
  fulfilmentType=EVENT) with `vatRatePercent = 27` (Woo tickets were standard-rate taxable;
  editable in admin). On re-run, update name + description; **do not** create duplicates and
  **do not** change the slug. No sessions/variants created.
- **Gallery images:** for each image URL, fetch bytes via an injectable `WorkshopImageFetcher`
  (HTTP impl in prod; stubbed in tests — no network in tests), store them through the **same
  image-storage mechanism product images use** (hashed immutable storage key; the app does no
  image processing — CLAUDE.md #8), and upsert a `product_image(product, storageKey, position)`
  row. Idempotent by `(product, storageKey)` like `CatalogImporter`.
- `config/WorkshopImportRunner` — `CommandLineRunner`, arg
  `--webshop.import.workshops-file=<path>`, `application-import-workshops.yml`
  (web-application-type=none), exits after run. Mirrors `CatalogImportRunner`.

The implementer MUST read `CatalogImporter`, `CatalogImportRunner`, the product-image storage
service (how bytes get a storageKey — see `ProductImageController` upload path), `WorkshopService`,
and `Product`/`ProductImage` before writing, and follow those patterns exactly.

## JSON contract (`workshops.json`)

```json
{
  "workshops": [
    {
      "externalId": 43532,
      "slug": "butorfestes-alapok-workshop",
      "name": "Bútorfestés alapok workshop",
      "descriptionHtml": "<p>Szint: Kezdő</p><h2>Miért ajánlom a kurzust?</h2>…",
      "images": [
        { "url": "https://workshop.nemiskacat.hu/wp-content/uploads/2026/04/Nevtelen-terv-1-scaled.jpg", "position": 0 },
        { "url": "https://workshop.nemiskacat.hu/wp-content/uploads/2026/04/Nevtelen-terv-11.png", "position": 1 }
      ]
    }
  ]
}
```

## Idempotency & conventions (CLAUDE.md)

- Upsert key: `externalId` (wp page id) for the workshop; `(product, storageKey)` for images (#4).
- Slug taken 1:1 from Woo, immutable on re-import (#7).
- Money: not applicable here (no prices imported). VAT stored as percent on the product.
- Timestamps UTC (#6). Images stored with hashed immutable keys; no in-app processing (#8).
- Business logic in the service layer; runner/DTO are thin (#1).

## Testing

- **WorkshopImporter integration test** (Testcontainers/Postgres): given a fixture `workshops.json`
  with 2 workshops (each with a description + 2 image URLs) and a **stub** `WorkshopImageFetcher`
  returning fixed bytes: asserts 2 workshops created (type=WORKSHOP, vat=27, correct name/slug/
  description), gallery `product_image` rows created with positions, and **re-running is
  idempotent** (still 2 workshops, no duplicate images, name/description updated, slug unchanged).
- **Export script:** a lightweight check (e.g. a Python unit test or a documented sample run)
  that the extractor turns a sample `_elementor_data` into the expected sections + image list.

## Out of scope

Sessions/dates/prices (created in admin); the dated ticket-products; the Events Calendar tables;
admin UI changes (the existing workshop admin already lists/edits workshops).
