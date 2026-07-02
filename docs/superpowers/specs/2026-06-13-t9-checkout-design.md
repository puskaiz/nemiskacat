# T9 — Checkout skeleton & fee calculation design

**Date:** 2026-06-13 · **Task:** B-webshop `TASKS.md` T9 · **Status:** approved (brainstorming)

## Context

Checkout flow (address, shipping method, fee table from config, VAT), order domain
with a state machine, idempotent order recording (client key), stock re-check at
checkout start, and the switchable 15-minute reservation. Payment (KHPos) is T10 —
orders end in `NEW` (awaiting payment). Fee table: the business-confirmed values in
`docs/poc/2026-06-13-shipping-vat-config.md`. ÁSZF/checkout copy is still `[EMBER]`
— placeholder text marked `DECISION NEEDED`.

## Decisions

- **Shipping fee calc** from `webshop.shipping` ConfigurationProperties (gross HUF):
  GLS weight bands 850/1500/1700 Ft (≤0.9/≤5/≤500 kg, band edge inclusive), free GLS
  at gross cart ≥ 39 000 Ft, Posta flat 7 900 Ft, pickup 0 Ft. Over 500 kg GLS is not
  offered. Missing weights count 0 (tracked in docs/TODO.md). Pure domain calculator;
  config is injected at the application layer.
- **VAT from gross** (prices are gross): per rate `net = round(gross/(1+r))`,
  `vat = gross − net`; breakdown grouped by rate. Tax classes: `'' → 27%`,
  `reduced-rate → 5%`; shipping 27%.
- **Order state machine:** `NEW → PAID → PACKING → SHIPPED → COMPLETED`;
  `CANCELLED` from NEW/PAID. Invalid transition throws — single domain method.
- **Idempotent placement:** client-generated key (UUID hidden field, generated at
  form render) with a UNIQUE column; replay returns the existing order (no double
  insert). DB constraint is the last line of defence.
- **Price locking:** order items snapshot name/label/sku/unit gross/tax rate at
  placement (cart never stores prices).
- **Availability ledger goes live** (TERV §3.7): available = `last_sync_qty`
  − non-cancelled ordered qty since `last_sync_at` − foreign active reservations.
  Centralized in `AvailabilityService`; CartService and CatalogQueryService are
  refactored onto it. (List pages do per-variant queries — acceptable behind the
  60 s micro-cache; batch later if needed.)
- **Overselling protection:** soft check at add (T8) → re-check at checkout start →
  pessimistic variant-row locks (ordered by id) + final check inside the placement
  transaction.
- **Reservation:** `reservation(variant, cart_token, qty, expires_at)`; checkout
  start creates/extends a 15-min hold for items whose `product.reserve_on_checkout`
  flag is on (new column, default false). Expired rows are simply ignored (no
  scheduler; cleanup is an ops task, T23).
- **Web:** `/penztar` (WP slug) GET form + POST → redirect to
  `/penztar/koszonjuk/{clientKey}` (unguessable key, no auth yet). No-store, noindex.

## Out of scope

KHPos payment + callbacks (T10), invoice/courier (T11), e-mails (T12), digital
product no-shipping path (docs/TODO.md), reservation-row cleanup job (T23).

## Acceptance (tests)

Fee & VAT units (bands incl. edges, free threshold, posta/pickup, 27/5% rounding);
state-machine transitions; integration: double-submit → one order; insufficient
stock at placement rejected (no order row); reservation blocks a foreign cart and
expires correctly; placement reduces availability without a sync change; totals
(items+shipping, VAT lines) correct; /penztar form → order → confirmation flow.
