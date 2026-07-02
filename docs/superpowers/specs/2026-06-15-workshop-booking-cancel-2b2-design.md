# Admin order management — slice 2b-2: workshop booking (order-line) cancel — design

## Context

After 2b-1 (whole-order cancel + refund + per-source credit note), 2b-2 is the
**line-level** counterpart: cancel a single workshop booking — an `OrderItem`
line of a (possibly multi-line) paid order — freeing its seats, partially
refunding the money, and issuing a **line-scoped** per-source credit note.

Existing pieces this builds on:
- `OrderItem` (variant, quantity, invoiceSource, lineGrossHuf …) — currently **no
  per-line cancellation state**; cancellation is order-level only.
- Availability: `OrderRepository.orderedQuantitySince(variantId, since, excluded)` =
  `SUM(oi.quantity)` where the **order** status ≠ `CANCELLED`. There is no way to
  exclude a single line today. `totalOrderedQuantity(variantId)` is the FK-delete guard.
- `RefundService.refund(orderId)` (2b-1): whole-order, marks `Payment` `REVERSED`,
  moves the order to `REFUNDED`, audits, triggers the per-source credit note.
- `PaymentGateway.refund(payId, amountHuf)` — **already supports a partial amount**
  (`KhposGatewayAdapter` → `cancelOrReturn(merchantId, payId, amount)`).
- `Invoice` + `InvoiceType{INVOICE, CREDIT_NOTE}`, unique `(order_id, source, type)`,
  states ISSUED/PUSHED/FAILED; `InvoicingService.invoice` / `creditNote` group an
  order's lines by `invoice_source` (BILLINGO → gated issuer, KULCS_SOFT → push stub),
  one row per (order, source), idempotent + retried by `retryFailed()`.
- `WorkshopService.bookings(workshopId)` → `WorkshopBookingView` (attendee list);
  `OrderRepository.findWorkshopBookings(workshopId, excluded)`.
- `AuditService`; Refine admin SPA — `workshops/edit.tsx` renders the attendees
  grouped per session, with a placeholder comment where the per-row action goes.

## Decomposition (recap)

- **2b-1** — order cancel + refund + per-source credit note *(done)*.
- **2b-2 — workshop booking (order-line) cancel** *(this spec):* whole-line cancel +
  partial refund + seat release + line-scoped credit note.
- **2b-2b** — partial seat cancel (N of M) — the `cancelledQuantity` field is designed
  to support it; not built now.
- **2b-3** — workshop booking reschedule (move to another session; capacity check).
- **2c** — post-shipment returns / partial refunds.

## Decisions (confirmed)

- Whole-line cancel only in 2b-2 (`cancelledQuantity = quantity`).
- The line-scoped credit note **is built now**, which requires reworking the 2b-1
  credit-note record model (link credit notes to the `order_item`; partial unique
  indexes). Real issuance stays gated exactly like the forward/whole-order flow
  (Billingo disabled → recorded FAILED/pending, retried once a key exists; Kulcs → push stub).
- A partial line refund **does not** reverse the whole `Payment` (it stays `CONFIRMED`)
  and **does not** change the order status. The cancelled line + credit note + audit
  record the event.

## 1. Line-cancellation model

- Add `cancelledQuantity INT NOT NULL DEFAULT 0` to `order_item` (Flyway **V16**). Map it
  on the `OrderItem` entity (default 0).
- `OrderItem.cancelWholeLine()`: sets `cancelledQuantity = quantity`; throws
  `IllegalStateException` if the line is already fully cancelled
  (`cancelledQuantity == quantity`) — callers treat that as the idempotent no-op (§3).
- Availability query: change `orderedQuantitySince` from `SUM(oi.quantity)` to
  `SUM(oi.quantity - oi.cancelledQuantity)` (order status ≠ excluded, createdAt > since
  unchanged). Cancelled seats free up immediately; the original `quantity` (history) is
  preserved. `totalOrderedQuantity` is **unchanged** — the row still references the variant.

## 2. Credit-note record rewrite

The 2b-1 model stores one credit note per `(order, source, type)`. A line-scoped credit
note needs multiple per `(order, source)` — one per cancelled line — so:

- Add nullable `order_item_id BIGINT REFERENCES order_item(id)` to `invoice`. **NULL** =
  forward invoice or **whole-order** credit note (the `RefundService` path); **set** = a
  **line-scoped** credit note.
- Replace the table-level `UNIQUE(order_id, source, type)` (added in V14) with two
  **partial** unique indexes in SQL (JPA `@Table` cannot express filtered uniqueness;
  remove the `uniqueConstraints` from the entity — Hibernate `ddl-auto: validate` does
  not validate indexes, only tables/columns):
  - `CREATE UNIQUE INDEX ux_invoice_order_source_type ON invoice(order_id, source, type) WHERE order_item_id IS NULL;`
    — preserves one forward invoice / one whole-order credit note per (order, source).
  - `CREATE UNIQUE INDEX ux_invoice_order_item_type ON invoice(order_item_id, type) WHERE order_item_id IS NOT NULL;`
    — one credit note per cancelled line (idempotent line cancel; two cancellations on the
    same order+source coexist).
  - The implementer must look up the exact existing constraint name in `V14__*.sql` to
    drop it (`ALTER TABLE invoice DROP CONSTRAINT <name>`).
- `Invoice` entity: add a nullable `@ManyToOne` `orderItem`; add factory
  `Invoice.creditNote(Order order, InvoiceSource source, OrderItem orderItem)`. The existing
  `creditNote(order, source)` stays (orderItem = null, whole-order path).
- `InvoiceRepository`: add `findByOrderItemAndType(OrderItem, InvoiceType)` (line-scoped
  idempotency lookup).
- `InvoicingService.creditNoteForLine(OrderItem line)`:
  - `source = line.getInvoiceSource()`; load any existing line-scoped CREDIT_NOTE
    (`findByOrderItemAndType`) — successful → skip (idempotent); else create
    `Invoice.creditNote(order, source, line)`.
  - **BILLINGO** → `creditNoteIssuer.creditNote(order, List.of(line), originalExternalId)`
    where `originalExternalId` comes from the order's forward INVOICE row for that source
    (`findByOrderAndSourceAndType(order, source, INVOICE)`); gated/disabled → recorded FAILED.
  - **KULCS_SOFT** → `kulcsSink.pushCreditNote(order, List.of(line))`; recorded PUSHED.
  - `default -> throw new IllegalStateException(...)`. Failures recorded FAILED (never thrown).
- `InvoicingService.retryFailed()`: split FAILED CREDIT_NOTE rows — rows with a non-null
  `orderItem` retry via `creditNoteForLine(line)`; rows with null `orderItem` retry via
  `creditNote(order)` (existing). INVOICE rows unchanged. The dedup count key becomes
  `orderId:type:orderItemId`.

## 3. Orchestration — `BookingCancellationService` (application/order)

`cancelBooking(Long orderItemId)` (`@Transactional`), depends on `OrderRepository`/
`OrderItem` lookup, `PaymentRepository`, `PaymentGateway`, `InvoicingService`, `AuditService`:

1. Load the `OrderItem` (with its order); not found → `OrderAdminQueryService.NotFoundException`
   → **404**.
2. Idempotent: if the line is already fully cancelled (`cancelledQuantity == quantity`) →
   no-op return (no gateway call).
3. Guard: order status `PAID` or `PACKING`, and a `CONFIRMED` `Payment`
   (`payments.findFirstByOrderOrderByIdDesc`); else `BookingCancelNotAllowedException` → **409**.
4. `gateway.refund(payment.getPayId(), line.getLineGrossHuf())`. A thrown exception or
   `!result.success()` → `BookingRefundFailedException` → **502**; the transaction rolls back
   (nothing changes). Both exceptions carry a readable message.
5. On success: `line.cancelWholeLine()`. **`Payment` stays `CONFIRMED`** (partial refund — not
   `REVERSED`); **order status unchanged**. `audit.record("BOOKING_CANCELLED", "order_item",
   String.valueOf(orderItemId), "<orderNumber> <sku> x<qty> refunded <lineGrossHuf> HUF")`.
6. `invoicing.creditNoteForLine(line)` — gated; a failure is swallowed (the credit note never
   rolls back the money refund), exactly like 2b-1.

The service is line-generic (works for any `OrderItem`); the workshop attendee list is its
entry point. The two new exceptions are nested static classes (mirroring `RefundService`).

## 4. Admin API + UI

- `POST /api/admin/order-items/{itemId}/cancel` (ADMIN-gated, CSRF) →
  `BookingCancellationService.cancelBooking(itemId)`, returns
  `BookingCancelResult(long orderItemId, long refundedHuf)`. `AdminExceptionHandler` maps the
  two new exceptions: `BookingCancelNotAllowedException` → **409**,
  `BookingRefundFailedException` → **502** (NotFoundException → 404 already mapped).
- DTO + query: `WorkshopBookingView` / `WorkshopBooking` and `findWorkshopBookings` gain
  `orderItemId`, `cancelledSeats` (= the line's `cancelledQuantity`), and `lineGrossHuf`.
  `findWorkshopBookings` continues to exclude `CANCELLED` orders; line-cancelled bookings of a
  non-cancelled order are still returned (shown as cancelled in the UI).
- `workshops/edit.tsx` attendee table (replacing the `{/* később ... */}` placeholder):
  - A **"Lemondás"** column — a danger `Popconfirm` button per row, title showing the seats and
    the refund amount (`lineGrossHuf`). On confirm → POST cancel → on success `message.success`
    + `reload()` (refetch sessions + bookings); on 409/502 show the server message.
  - A row that is fully cancelled (`cancelledSeats === seats`) shows a **"Lemondva"** tag instead
    of the button.
  - `bookedSeats(sessionId)` sums **effective** seats (`seats − cancelledSeats`) so
    "Foglalt / kapacitás" reflects the freed seat. The `WorkshopBooking` type gains the three new
    fields.

## 5. Testing

- **Backend (TDD, mocked `PaymentGateway` + gated issuers):**
  - `OrderItem.cancelWholeLine` sets `cancelledQuantity = quantity`; second call throws.
  - `orderedQuantitySince` excludes cancelled quantity (repository/integration test: a line with
    `cancelledQuantity = quantity` contributes 0).
  - `BookingCancellationService` happy path: `PAID` order, workshop line → `gateway.refund` called
    with `payId` + the **line** gross; line `cancelledQuantity == quantity`; `Payment` stays
    `CONFIRMED`; order status unchanged; audit `BOOKING_CANCELLED`; a line-scoped
    `Invoice(type=CREDIT_NOTE, orderItem set)` row (BILLINGO → FAILED/pending while disabled).
  - guards: cancel on `NEW`/`SHIPPED`/`COMPLETED`/`CANCELLED`/`REFUNDED` → 
    `BookingCancelNotAllowedException`; no `CONFIRMED` payment → same.
  - gateway failure → `BookingRefundFailedException`, line `cancelledQuantity` stays 0, no audit,
    no credit-note row (rolled back).
  - idempotent: cancelling an already-fully-cancelled line → no-op (no second gateway call).
  - multi-line order: cancelling one line leaves the order status unchanged and the other line's
    `cancelledQuantity` at 0.
  - **two line cancels on the same (order, BILLINGO source) → two distinct CREDIT_NOTE rows**
    (proves the partial-unique-index rewrite); a whole-order `creditNote(order)` row
    (`order_item_id` null) coexists with line-scoped rows.
  - `retryFailed()` retries a FAILED line-scoped credit note via `creditNoteForLine`.
- **Controller:** 200 + `BookingCancelResult`; 409 for a non-cancellable order/line; 404 for an
  unknown item; 403 for a non-admin.
- **Frontend:** `cd admin-ui && npm run build` green.

## Out of scope (later / follow-ups)

- Partial seat cancel — N of M (2b-2b); reschedule (2b-3); post-shipment returns (2c).
- Auto-transitioning an order to `REFUNDED` when its last remaining line is cancelled
  (Payment isn't fully reversed in 2b-2 — deliberately left to a follow-up).
- Tracking a cumulative refunded amount on `Payment` (the cancelled line + credit note + audit
  are the record of truth for now).
- Real Billingo credit-note issuance (needs the key — gated path records pending) and real Kulcs
  return push (stub).
