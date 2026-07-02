# Workshops + mixed cart with per-line invoicing — design

**Date:** 2026-06-14 · **Status:** draft for review (brainstorming) · proposed task **T24**

## Problem

Workshops (today on `workshop.nemiskacat.hu`) move into the single new webshop.
A cart may mix **physical products** and **workshop seats** and is paid once
(KHPos), but the two need **different invoicing**:

- physical products → **Kulcs-Soft** (Kulcs issues the invoice; our app only
  hands the order over),
- workshops → **Billingo** (our app issues the invoice via the in-house
  `billingo-spring-boot-starter`).

## Confirmed decisions

- Workshops are real **events**: a workshop type has concrete **sessions**
  (start datetime) with a **capacity**. ~5 workshop types today, entered **by
  hand in the new admin** (no import from the old site).
- **Mixed cart is required** → one order, one payment, **multiple invoices**
  partitioned by line invoice-source.
- **No attendee data** — only the seat **quantity** matters.
- Physical invoices: **Kulcs issues them**; our app only **pushes the order**.
- Workshop VAT: **27% default, but configurable** per product.

## Core idea

"Which invoicer" is a **post-payment, per-line** concern, and payment is already
decoupled from invoicing. So the mixed cart is invisible to cart/checkout/payment;
the only new complexity is the invoicing step, which **groups order lines by
`invoiceSource` and issues one invoice per group**.

## Model

- **Reuse the catalog pipeline.** A workshop session is sold as a catalog
  `Variant` (its product has `type = WORKSHOP`), so cart, checkout, KHPos payment
  and order recording are unchanged and mixed carts "just work".
- **New event domain, linked to the catalog:**
  - `Workshop` — event type (title, slug, description, location, instructor),
    `invoice_source = BILLINGO`, configurable `vat_rate`.
  - `WorkshopSession` — a concrete occurrence (start datetime, **capacity**),
    1:1 with a `Variant` (the sellable; sku = session code, price on the variant).
- **Per-line invoice source:** add `product.invoice_source`
  (`KULCS_SOFT` default, `BILLINGO`) and `product.fulfilment_type` (`SHIP` /
  `EVENT`); `order_item` snapshots `invoice_source` at placement. Add a
  configurable `vat_rate_percent` (workshops 27% by default, overridable) instead
  of inferring everything from the Woo tax class.
- **Capacity availability (pluggable stock strategy):** `AvailabilityService`
  branches by product type — physical = ledger (last sync − orders − reservations);
  workshop = **capacity − paid seats − active checkout holds**. The existing
  15-minute reservation is reused (seat held during checkout; "two buyers, one
  seat" is the main case).

## Invoicing

- `InvoiceSource` enum on the line; `InvoicingService.invoice(order)` runs **after
  payment is CONFIRMED**, idempotent, retried on failure (like the payment
  re-check):
  - groups lines by source,
  - **BILLINGO group → `BillingoInvoiceIssuer`** (port): builds a `DocumentInsert`
    (partner = customer, items = workshop lines with VAT, HUF, invoice
    `blockId` from config) via the starter's `DocumentClient.create(...)`, stores
    the returned invoice number + public URL;
  - **KULCS_SOFT group → `KulcsSoftOrderSink`** (port): pushes the physical lines /
    order to Kulcs-Soft (no invoice issued by us — status "handed to Kulcs").
- Two ports, two adapters (mirrors the `PaymentGateway` design). Per-source
  invoice/push status stored on the order. A new `invoice` table records issued
  documents (source, external id/number, public URL, state).

## Checkout / shipping

- `ShippingFeeCalculator` runs only on **SHIP** lines; workshop lines
  (`fulfilment_type = EVENT`) carry no weight/shipping. Workshop-only cart → no
  shipping step. Mixed cart → ship the physical part only.

## Admin

- Manual CRUD for workshops + sessions (the ~5 types) in the admin (T15/T16
  scope): create a workshop, add sessions (date + capacity), set price + VAT.

## Phasing (proposed)

1. **Foundations:** `invoice_source` + `fulfilment_type` + configurable
   `vat_rate` on product; order-line snapshot; shipping calc on SHIP lines only.
2. **Workshop domain:** `Workshop` + `WorkshopSession`, session-as-Variant,
   capacity stock-strategy in `AvailabilityService` (+ reservation reuse).
3. **Invoicing split:** `InvoiceSource`, `InvoicingService`, `BillingoInvoiceIssuer`
   (+ `KulcsSoftOrderSink` stub), `invoice` table — TDD with mocked ports.
4. **Workshop UI:** listing + event page with a session picker; mixed-cart
   checkout/shipping adjustments.
5. **Routing/migration:** `/workshopok/...` in the app + 301 from the subdomain.

## `[EMBER]` / open

- Billingo API key + invoice `blockId` (test account) for live issuing.
- Kulcs-Soft push mechanism + access (ties into D9 / T2–T5).
- The Számlázz.hu integration named in the original TERV is superseded here by
  **Billingo** for workshops ("egyelőre").
- Subdomain cutover (301 map) — coordinate with the A-track.

## Acceptance (high level)

A mixed cart (paint + workshop seat) → one KHPos payment → on confirmation, the
workshop lines get a Billingo invoice (number + URL stored) and the physical
lines are pushed to Kulcs; shipping is charged only on the paint; the workshop
session's remaining capacity drops by the seats bought.
