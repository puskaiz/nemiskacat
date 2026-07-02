# Admin order management — slice 2b-1: order cancel + refund (+ per-source credit note) — design

## Context

After 2a (fulfilment status machine), 2b is **sztornó + visszatérítés**. This spec
is its first buildable slice, **2b-1: order-level cancel + refund** of a paid,
pre-shipment order, with a per-source storno-invoice (credit note) step.

Existing pieces this builds on:
- `OrderStatus` (NEW, PAID, PACKING, SHIPPED, COMPLETED, CANCELLED) + `Order.transitionTo`
  domain gate; `OrderAdminService.transition` (admin policy) — currently rejects
  paid-order cancel with "refund is slice 2b".
- `PaymentGateway` port (initPayment/queryStatus, no refund yet) + `KhposGatewayAdapter`
  on the KHPos starter, which **does** expose `refund`, `reverse`, and
  `cancelOrReturn(merchantId, payId, amount)` (auto-selects reverse vs refund by
  settlement state). Sandbox keys exist in the `local` profile.
- `Payment` entity with states INITIATED/CONFIRMED/FAILED/REVERSED.
- `InvoicingService.invoice(orderId)` groups order lines by `invoice_source`
  (BILLINGO → `InvoiceIssuer`/`BillingoInvoiceIssuer`, gated by
  `webshop.invoicing.billingo-enabled`; KULCS_SOFT → `OrderSink`/`KulcsSoftOrderSink`
  stub), recording one `Invoice` row per (order, source) with state ISSUED/PUSHED/FAILED.
- `AuditService`; Refine admin SPA (order detail + list).

## Decomposition (2b)

- **2b-1 — order cancel + refund + per-source credit note** *(this spec):* whole-order
  refund of a paid pre-shipment order.
- **2b-2 — workshop booking cancel** (order-line-level partial refund + seat release).
- **2b-3 — workshop booking reschedule** (move to another session; capacity check).
- **2c — returns / partial refunds** post-shipment.

## Decision (confirmed)

Credit notes (sztornószámla) route **by `invoice_source`**, mirroring the forward
invoicing: **workshop lines → Billingo credit note**, **physical lines → Kulcs**.
Real issuance stays gated exactly like the forward flow (Billingo disabled → recorded
FAILED/pending and retried once a key exists; Kulcs → push stub). Built in 2b-1 as
one piece. Refunds are **whole-order, pre-shipment only** (`PAID`/`PACKING`).

## 1. State machine

Add **`REFUNDED`** (distinct from `CANCELLED`, like the old "Visszatérítve" vs
"Visszamondva"). Allowed: `PAID → REFUNDED`, `PACKING → REFUNDED`; `REFUNDED` is
terminal. Post-shipment refunds (SHIPPED/COMPLETED) are out of scope (2c).
`OrderStatus.canTransitionTo` updated; existing transitions unchanged.

## 2. Money refund

- Extend the `PaymentGateway` port: `RefundResult refund(String payId, long amountHuf)`
  (`RefundResult` = normalized outcome/message). `KhposGatewayAdapter.refund` calls
  `khpos.cancelOrReturn(merchantId, payId, amountHuf)`. `DisabledPaymentGateway.refund`
  throws (mirrors its other methods).
- `RefundService.refund(orderId)` in **`application/order`** (an order-management
  operation; depends on the `PaymentGateway` port, `PaymentRepository`,
  `OrderRepository`, `InvoicingService`, `AuditService`):
  1. Load the order; require status `PAID` or `PACKING`, and a `CONFIRMED` `Payment`
     (else `RefundNotAllowedException` → **409**).
  2. Call `gateway.refund(payId, order.totalGrossHuf)`. A gateway/execution failure
     throws `RefundFailedException` → **502** (the transaction rolls back, nothing
     changes); both exceptions carry a readable message for the SPA.
  3. On success: `payment.markState(REVERSED, ...)`, `order.transitionTo(REFUNDED)`,
     `audit.record("ORDER_REFUNDED", "order", id, "<old>→REFUNDED")`.
  4. Trigger the credit-note step (§3).
- Idempotent: if the order is already `REFUNDED`, the call is a no-op (no double refund).

## 3. Per-source credit note (storno invoice)

Mirror `InvoicingService` for the storno direction:
- Reuse the `Invoice` entity/table, adding an **`InvoiceType { INVOICE, CREDIT_NOTE }`**
  column (default `INVOICE`); change the unique constraint from `(order_id, source)`
  to **`(order_id, source, type)`** so a credit note can coexist with the original
  invoice. Flyway **V14**. Reuse `InvoiceState` (ISSUED/PUSHED/FAILED).
- `InvoicingService.creditNote(orderId)` groups the order's lines by `invoice_source`:
  - **BILLINGO** → a gated `CreditNoteIssuer` (Billingo). Disabled (no key) → records
    `FAILED` (pending) and is retried by the existing `retryFailed` path; enabled →
    issues a real Billingo credit note (storno of the original document).
  - **KULCS_SOFT** → a Kulcs credit sink (push stub, like `KulcsSoftOrderSink`): we
    hand the return to Kulcs; they issue. Recorded `PUSHED`.
  - One `Invoice` row per (order, source) with `type = CREDIT_NOTE`. Idempotent
    (skip a source already successfully credited).
- The refund (§2) does not depend on the credit note succeeding; a gated/pending
  credit note never blocks the money refund or the `REFUNDED` transition.

## 4. Admin API + UI

- `POST /api/admin/orders/{id}/refund` (ADMIN-gated, CSRF) → `RefundService.refund(id)`,
  returns the updated `OrderDetail`. `RefundNotAllowedException` → **409** and
  `RefundFailedException` → **502** via `AdminExceptionHandler`.
- Order detail page: a **"Sztornó + visszatérítés"** button shown only for `PAID`/`PACKING`
  orders (danger + Popconfirm showing the amount). On success refetch; on error show the
  server message. `REFUNDED` gets a Hungarian label ("Visszatérítve") + colour in
  `STATUS_LABELS`/`STATUS_COLORS`.

## 5. Testing

- **Backend (TDD, mocked `PaymentGateway` + gated issuers):**
  - refund happy path → order `REFUNDED`, `Payment` `REVERSED`, audit `ORDER_REFUNDED`,
    and per-source credit-note `Invoice(type=CREDIT_NOTE)` rows (workshop → BILLINGO
    pending/FAILED while disabled; physical → KULCS_SOFT PUSHED);
  - guard: refund of `NEW`/`SHIPPED`/`COMPLETED` → `RefundNotAllowedException`;
  - gateway failure → exception, order stays `PAID`, no audit, no status change;
  - idempotent: second refund call is a no-op (no second gateway call).
  - `OrderStatusTest`: `PAID→REFUNDED`, `PACKING→REFUNDED` allowed; illegal hops rejected.
- **Controller:** 200 + status REFUNDED, 409 for a non-refundable order, 403 non-admin.
- **Frontend:** `npm run build` green.

## Out of scope (later)

Workshop booking-level cancel (2b-2) & reschedule (2b-3); post-shipment returns /
partial refunds (2c); real Billingo credit-note issuance (needs the key — gated path
records pending until then); real Kulcs return push (needs Kulcs access — stub now).
