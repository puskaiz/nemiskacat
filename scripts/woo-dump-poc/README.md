# WooCommerce dump PoC (T1)

Read-only analysis of the legacy nemiskacat.hu WooCommerce catalog, used to size
the catalog model (T3) and importer (T4). **No data from the dump is committed**
— only the queries here and the aggregate report in `docs/poc/`.

## Data source

A full WordPress/WooCommerce MariaDB→MySQL dump runs in a local container,
provisioned outside this repo at
`/Users/zolika/Work/Nemiskacat/website/wordpress/` (`docker-compose.yaml`,
`db-init/dump.sql` + `dump2.sql`). The DB container is `wp_db` (MySQL 8.0,
database `client7002dbnem`, table prefix `guxdop_`).

> The dump contains customer PII (`guxdop_users`/`usermeta`), secrets
> (`guxdop_options`, `guxdop_woocommerce_api_keys`) and order data. The PoC reads
> **only** product/variation/category/attribute/SEO tables. Never copy users,
> options or API keys into this project.

## Run the report

```bash
docker exec -i wp_db mysql -uroot -proot_password \
  --default-character-set=utf8mb4 --table client7002dbnem \
  < scripts/woo-dump-poc/catalog-report.sql
```

`--default-character-set=utf8mb4` is required, otherwise Hungarian text prints as
mojibake (the stored data is clean utf8mb4).
