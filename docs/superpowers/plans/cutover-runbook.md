# Cutover runbook: WooCommerce → local DB

All steps are human-run against the target environment. The Woo side is read-only.

1. **Export from Woo** (reads `wp_db`, writes JSON):
   - `python3 scripts/woo-export/export.py > /tmp/woo-catalog.json`
   - `python3 scripts/woo-export/export-customers.py > /tmp/woo-customers.json`
   - `python3 scripts/woo-export/export-orders.py > /tmp/woo-orders.json`
2. **Reset app data** (destructive — confirm target DB first):
   - `psql "$APP_DB_URL" -f scripts/woo-export/reset-app-data.sql`
3. **Import, in order:**
   - catalog:
     ```
     mvn spring-boot:run -Dspring-boot.run.profiles=local,import \
       -Dspring-boot.run.arguments=--webshop.import.file=/tmp/woo-catalog.json
     ```
   - customers:
     ```
     mvn spring-boot:run -Dspring-boot.run.profiles=local,import-customers \
       -Dspring-boot.run.arguments=--webshop.import.customers-file=/tmp/woo-customers.json
     ```
   - orders:
     ```
     mvn spring-boot:run -Dspring-boot.run.profiles=local,import-orders \
       -Dspring-boot.run.arguments=--webshop.import.orders-file=/tmp/woo-orders.json
     ```
4. **Review the order import report** in the logs: orphan SKUs (lines without a
   live variant) and any unknown statuses. Decide whether orphan coverage is
   acceptable before opening the shop.
