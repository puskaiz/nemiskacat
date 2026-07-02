# WooCommerce catalog export (T4 interim source)

Exports the catalog from the local `wp_db` container into the JSON snapshot
consumed by `CatalogImporter` (a `SourceCatalog`, see `integrations/woo`).
Catalog tables only — never users/options/keys. Published products only.

The canonical importer source is the Woo REST API (`[EMBER]`-blocked); this
export feeds the same `CatalogSource` port until the key exists.

## Usage

```bash
# 1. export (wp_db container must be running)
python3 scripts/woo-export/export.py > /tmp/woo-catalog.json

# 2. import into the local webshop Postgres (one-shot run, no web server)
mvn spring-boot:run -Dspring-boot.run.profiles=local,import \
  -Dspring-boot.run.arguments=--webshop.import.file=/tmp/woo-catalog.json
```

Re-running the import is safe: upsert by woo id / SKU, no duplicates.

## Workshops (separate source DB)

`export-workshops.py` exports the 5 workshop landing pages (Elementor) from the
**`client7002dbwork`** dump (container `nemiskacat-work-db`) into a `SourceWorkshops`
JSON consumed by `WorkshopImporter`. Per workshop: name, slug, assembled section HTML
(`descriptionHtml`), and the media-carousel slider images. Sessions/dates/prices are
NOT exported (created in admin).

```bash
# 1. export (nemiskacat-work-db container must be running)
python3 scripts/woo-export/export-workshops.py > /tmp/workshops.json

# 2. import (images are downloaded from the live site; point STORAGE_DIR at the
#    same uploads dir the app serves)
STORAGE_DIR=./data/uploads mvn spring-boot:run \
  -Dspring-boot.run.profiles=local,import-workshops \
  -Dspring-boot.run.arguments=--webshop.import.workshops-file=/tmp/workshops.json
```

Re-running is safe: upsert by wp page id (externalId), images by content-hash key.

## Blog

`export-blog.py` exports all `publish` and `draft` posts (plus their categories) from the
**`client7002dbnem`** database (container `wp_db`) into a `SourceBlog` JSON consumed by
`BlogImporter`. Per post: id, slug, title, excerpt, HTML content, status, UTC publish date,
featured-image URL (siteurl + `/wp-content/uploads/` + attachment path), Yoast SEO fields,
and the list of category slugs.

```bash
# 1. export (wp_db container must be running)
python3 scripts/woo-export/export-blog.py > /tmp/blog.json

# 2. import into the local webshop Postgres (one-shot run, no web server)
mvn spring-boot:run -Dspring-boot.run.profiles=local,import-blog \
  -Dspring-boot.run.arguments=--webshop.import.blog-file=/tmp/blog.json
```

Re-running the import is safe: upsert by slug, no duplicates.

## Content pages

`export_pages.py` exports the content pages listed in its `SLUGS` array (Elementor
pages assembled to HTML) from the **`client7002dbnem`** database (container `wp_db`)
into a `SourcePages` JSON consumed by `PageImporter`. Edit `SLUGS` to change the set;
re-runnable before go-live.

```bash
# 1. export (wp_db container must be running)
python3 scripts/woo-export/export_pages.py > /tmp/pages.json

# 2. import into the local webshop Postgres (one-shot run, no web server)
mvn spring-boot:run -Dspring-boot.run.profiles=local,import-pages \
  -Dspring-boot.run.arguments=--webshop.import.pages-file=/tmp/pages.json
```

Re-running the import is safe: upsert by wp page id (externalId); slug never changes.
Images are downloaded from the live site by the importer (point `STORAGE_DIR` at the
uploads dir the app serves).

## Notes / known limitations

- `storageKey` keeps the original `wp/<year>/<month>/<file>` path so the binary
  migration to object storage can locate and re-key files later (content-hash
  immutable keys arrive with the storage task).
- Attribute value labels currently equal their slugs (e.g. `sotet-viasz`); the
  pretty labels live in the Woo `pa_*` term names and will be exported when the
  product page needs them (T6).
- Only global (`attribute_pa_*`) variation attributes are exported; the PoC found
  no custom per-product attributes in use.
- Variations of draft parents are excluded (published parents only), which is why
  the variation count (576) is below the all-statuses total (674).
