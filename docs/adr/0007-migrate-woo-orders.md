# 7. Migrate WooCommerce orders into the local DB

Date: 2026-06-19

## Status

Accepted

## Context

TERV.md §7 originally stated that historical WooCommerce orders would not be
migrated — they would stay archived read-only in WordPress. For go-live cutover
the decision was reversed: the new app becomes the single source of truth for
orders, so all 22,713 historical orders are imported into the local DB.

## Decision

Import all Woo orders (every status) read-only into `orders`/`order_item`/
`payment`, mirroring the catalog/customer import pipeline. Status is mapped 1:1
(lossless): four import-only statuses (PROCESSING, ON_HOLD, FAILED,
AWAITING_SHIPMENT) are added. Order lines for discontinued products are kept
with a null variant and their snapshot. A single confirmed Payment is created
only when Woo recorded payment. No invoices are imported (issued externally).
Import fires no events and touches no stock.

## Consequences

- TERV.md §7 is superseded for cutover.
- The order state machine gains terminal import-only states (native checkout
  unaffected).
- `order_item.variant_id` is now nullable.
- Merging/standardising the imported statuses is deferred.
