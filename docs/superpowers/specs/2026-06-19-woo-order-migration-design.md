# Design: WooCommerce order migration into the local DB

Date: 2026-06-19
Status: Approved for planning (pending spec review)
Branch context: `admin-products-p2c` (migration work to land on its own branch)

## Goal

Bring **all** historical WooCommerce orders into the local app DB as first-class
order rows, as part of go-live cutover. After cutover this app is the source of
truth for orders. Customers ("partners") already have an import path; this work
is **orders only**. The Woo side is **read-only** — we only ever read from it
(CLAUDE.md cardinal rule).

This reverses the earlier `docs/TERV.md` §7 decision ("orders archived in
WordPress, not migrated"). That section is now superseded for the cutover plan;
a short ADR will record the reversal.

## Source of truth (verified against the live `wp_db` container)

Legacy WooCommerce storage (no HPOS):

- Orders: `guxdop_posts` where `post_type = 'shop_order'` — **22,713 rows**,
  spanning 2014-12 → 2026-06.
- Order fields: `guxdop_postmeta` (billing/shipping, `_order_total`,
  `_order_tax`, `_order_shipping`, `_customer_user`, `_date_paid`,
  `_transaction_id`, `_order_currency`, `_order_key`, …).
- Line items: `guxdop_woocommerce_order_items` (+ `_itemmeta`). `line_item`
  rows carry `_product_id`, `_variation_id`, `_qty`, `_line_total` (ex-tax),
  `_line_subtotal`, `_line_tax`, `_tax_class`. 59,868 line items; 44,299 with a
  variation. Also `shipping`, `tax`, `fee`, `coupon` item types.

Status distribution: completed 21,470 · cancelled 1,219 · failed 14 ·
on-hold 5 · processing 4 · awaiting-shipment 1.

## Decisions (from brainstorming)

1. **Scope: import everything.** Every order row, all statuses.
2. **Status mapping is 1:1 and lossless.** No collapsing of distinct Woo states
   now (may merge later). Names may differ from Woo.
3. **Financial depth: orders + items + paid marker.** One `Payment` row per
   order *only when Woo shows it was paid* (`_date_paid` or `_transaction_id`
   present), `CONFIRMED`, amount = `_order_total`. **No invoices** imported
   (already issued externally; stay archived).
4. **Orphan lines kept.** Lines whose product/variant no longer exists in the
   catalog are imported with `variant_id = NULL`, relying on snapshot fields.
5. **Customers → `customer` table** already handled by the existing
   `export-customers.py` + `CustomerImporter` (keyed by `wp_user_id`). Guest
   orders carry their own buyer snapshot on the order row; no `customer` row.

## Status mapping (1:1)

| Woo status            | Local `OrderStatus` | New value? |
|-----------------------|---------------------|------------|
| `wc-completed`        | `COMPLETED`         | existing   |
| `wc-cancelled`        | `CANCELLED`         | existing   |
| `wc-refunded`         | `REFUNDED`          | existing   |
| `wc-processing`       | `PROCESSING`        | **new**    |
| `wc-on-hold`          | `ON_HOLD`           | **new**    |
| `wc-failed`           | `FAILED`            | **new**    |
| `wc-awaiting-shipment`| `AWAITING_SHIPMENT` | **new**    |

Any unseen/unknown Woo status fails the import loudly (no silent drop) and is
listed in the import report for a human to map.

## Architecture

Mirrors the established catalog/customer import pattern end-to-end:

```
scripts/woo-export/export-orders.py   # reads wp_db (read-only) → JSON
        → SourceOrder[] JSON file
        → OrderImporter (application/)  # idempotent upsert by woo_order_id
        → OrderRepository.save(...)     # direct persist, no checkout/stock/events
   triggered by: import-orders profile + OrderImportRunner (one-shot, web off)
```

### 1. Export script — `scripts/woo-export/export-orders.py`

- Same connection mechanism as `export.py` (`docker exec wp_db mysql …`,
  prefix `guxdop_`, db `client7002dbnem`).
- Emits a JSON array of `SourceOrder` objects (schema below). Reads **only**
  order/line/customer-reference tables. Never writes.
- Per line: resolve SKU by joining `_product_id`/`_variation_id` to that post's
  `_sku` meta; carry `wooProductId`, `wooVariationId`, `sku`, name, variant
  label (from `pa_*` item meta), qty, and ex-tax + tax amounts.
- Money: HUF whole forints, mirroring `export.py`'s `to_minor_huf`
  (round to int; no ×100 — consistent with the catalog importer).
- Gross conversion (our model stores gross): line gross = `_line_total +
  _line_tax`; unit gross = line gross / qty; `tax_rate_percent` =
  round(`_line_tax` / `_line_subtotal` × 100), fallback derived from
  `_tax_class` (standard = 27), final fallback 0.
- Order totals: `items_gross_huf` = Σ line gross; `ship_gross_huf` =
  `_order_shipping + _order_shipping_tax`; `total_gross_huf` = `_order_total`.
- Flags non-HUF `_order_currency` orders in output for review (does not abort).

### 2. Source DTOs — `integrations/woo/`

New records mirroring the `SourceCatalog`/`SourceCustomer` style:

- `SourceOrder`: `wooOrderId`, `orderKey`, `wooStatus`, `currency`,
  `createdAt`, `paidAt`, `completedAt`, `wpUserId` (nullable),
  buyer snapshot (`customerName`, `email`, `phone`, `postcode`, `city`,
  `addressLine`, `note`), shipping (`shipMethodName`, `shipGrossHuf`),
  totals (`itemsGrossHuf`, `totalGrossHuf`), payment (`paid` bool,
  `transactionId`), and `List<SourceOrderItem> items`.
- `SourceOrderItem`: `wooProductId`, `wooVariationId`, `sku`, `productName`,
  `variantLabel`, `quantity`, `unitGrossHuf`, `lineGrossHuf`, `taxRatePercent`.
- An `OrderSource` port (interface) with a `JsonFileOrderSource` impl, parallel
  to `JsonFileCatalogSource`.

### 3. Domain changes — `domain/order/`

- **`Order.imported(...)`** — new import-only static factory. Sets the final
  status **directly** (does not call `transitionTo`), accepts `wooOrderId`,
  `source = "WOO_IMPORT"`, and a deterministic `clientKey = "woo-<wooOrderId>"`.
  Tolerates missing Woo fields by substituting safe placeholders for the
  NOT-NULL snapshot columns (e.g. empty string / "—") so a sparse 2014 order
  still imports. Publishes **no** domain events.
- **`OrderItem.create(...)`** — relaxed to accept `variant == null`; when null,
  snapshot fields (`productName`, `sku`, `variantLabel`, prices) must be
  supplied directly by the caller.
- **`OrderStatus`** — add `PROCESSING`, `ON_HOLD`, `FAILED`,
  `AWAITING_SHIPMENT`. These are **import-only / terminal** in the native state
  machine (no outgoing transitions defined), so the live checkout flow is
  unaffected.

### 4. Application service — `OrderImporter`

- Input: `List<SourceOrder>`. For each order:
  1. **Idempotency:** skip/replace if `orders.woo_order_id` already present
     (re-runnable, like `CatalogImporter`).
  2. Build `Order.imported(...)`; map status via the table above.
  3. For each item: resolve `Variant` by `external_id` (variation id, else
     product id), fallback by `sku`, else `null`. Build `OrderItem` with the
     resolved variant or a null-variant snapshot.
  4. If `paid`: attach one `Payment` (`CONFIRMED`, amount `total_gross_huf`,
     `payId = "woo-pay-<wooOrderId>"`).
  5. Persist via `OrderRepository.save(order)` (cascades items). **Never**
     touches reservations, stock ledger, or the invoicing event listener.
- Returns an `OrderImportReport`: counts (imported, skipped, paid),
  orphan-line count + their SKUs, unknown-status list, non-HUF list.

### 5. Runner + profile

- `OrderImportRunner` (`@Profile("import-orders")`, web off), reading
  `webshop.import.orders-file`, mirroring `CatalogImportRunner` /
  `CustomerImportRunner`.
- `application-import-orders.yml` with `web-application-type: none`.

### 6. Schema migrations (Flyway, additive / non-breaking)

- `V19__order_status_woo.sql`: drop+re-add `orders_status_check` to include the
  four new values.
- `V20__order_item_variant_nullable.sql`:
  `ALTER TABLE order_item ALTER COLUMN variant_id DROP NOT NULL;`
- `V21__orders_woo_source.sql`: add `orders.woo_order_id BIGINT UNIQUE` (nullable)
  and `orders.source TEXT` (nullable; native orders leave it null/"NATIVE").

(Migration numbers provisional — use the next free Vnn at implementation time.)

### 7. Test-data reset (human-run)

A documented `scripts/woo-export/reset-app-data.sql` that truncates
`order_item, payment, invoice, orders, customer` (RESTART IDENTITY CASCADE) so
the real import lands in a clean DB. **Executed by a human** on the target env —
never run by automation, per CLAUDE.md (live/staging DB ops are human-only). The
documented cutover order is: reset → import catalog → import customers →
import orders.

## Admin UI impact (scope boundary)

The four new statuses need Hungarian display labels in the admin order list/
filter (`admin-ui/`). **Transitions out of** the new statuses are out of scope
for this work (these are historical states; merging/standardising them is a
deferred decision per the user). The customer-facing order views are unaffected
(historical orders are admin/account-history only).

## Testing

- **OrderImporter unit/integration (Testcontainers Postgres):** idempotent
  re-run produces no duplicates; status mapping for each Woo status; paid vs.
  unpaid → payment row presence; orphan line → `variant_id = NULL` with intact
  snapshot; sparse-field order imports without error; unknown status surfaces in
  report (no silent drop).
- **export-orders.py:** small fixture-based check of the SQL→JSON shape and the
  gross/tax arithmetic (a few representative orders), runnable against the
  container or a captured fixture.
- Money/tax arithmetic asserted explicitly (gross = ex-tax + tax; totals sum).
- Existing checkout/state-machine tests must stay green (new enum values are
  additive and terminal).

## Out of scope / deferred

- Merging or standardising the imported statuses (explicitly deferred).
- Invoice import (already issued externally).
- Refunds/credit-note reconstruction beyond status = `REFUNDED`.
- Coupon and fee line items as separate rows (folded into order/line totals;
  not modelled individually).
- Kulcs-Soft / stock ledger reconciliation (separate track).

## Risks

- **SKU/variant resolution coverage:** some lines reference long-deleted
  products → land as null-variant snapshots. Report surfaces the count so it can
  be reviewed before cutover.
- **Sparse historical data (2014-era):** missing billing fields handled by
  placeholders; flagged in the report if a required snapshot was substituted.
- **Tax-rate derivation** from amounts may be imprecise for odd historical
  rounding; acceptable for archival orders (totals come straight from Woo).
