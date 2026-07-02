# Admin order management — slice 2a: fulfilment status machine — design

## Context

T16 (admin order management) is the second admin slice. The customer side and
the read-only admin order views (list + detail) already exist; the order state
machine lives in the domain (`OrderStatus` + `Order.transitionTo`), and an
`AuditService` records staff actions. The admin SPA (Refine) is in place.

T16 as a whole spans several parts, some `[EMBER]`/dependency-blocked. This spec
covers **only slice 2a**: letting an admin drive the **fulfilment** flow.

## Decomposition (T16)

- **2a — Fulfilment status machine** *(this spec, unblocked):* admin advances a
  paid order PACKING → SHIPPED → COMPLETED, and cancels **unpaid (NEW)** orders;
  every transition audited. No money movement.
- **2b — Cancel + refund:** cancelling a PAID order → KHPos refund/reversal +
  credit note (Billingo for workshops). `[EMBER]`: Billingo key, KHPos refund
  wiring. Also unlocks workshop-booking cancellation.
- **2c — Returns / partial refunds.**
- **2d — Courier label** (depends on T11).

## Scope of slice 2a

- Admin transitions on the order detail page: **PAID→PACKING, PACKING→SHIPPED,
  SHIPPED→COMPLETED**, and **NEW→CANCELLED** (unpaid only).
- `PAID` is never an admin-settable target (payment sets it).
- Cancelling a **PAID** order is rejected here (refund flow is 2b).
- Every successful transition writes an audit entry.

## 1. Backend — `OrderAdminService.transition(orderId, target)`

New service in `application/order`:

1. Load the order (`orders.findById`); missing → `NotFoundException` (→404).
2. **Admin policy** (before the domain gate):
   - `target == PAID` → reject (payment-only).
   - `target == CANCELLED && current != NEW` → reject ("paid-order cancel +
     refund is slice 2b").
3. **Domain gate:** `order.transitionTo(target)` — enforces the legal sequence
   (e.g. `COMPLETED` only from `SHIPPED`), throwing `IllegalStateException` on an
   illegal hop.
4. On success: `audit.record("ORDER_STATUS_CHANGE", "order", id, "<old>→<new>")`.

Policy/sequence violations surface as a dedicated
`OrderAdminService.TransitionNotAllowedException` (the service wraps the domain
`IllegalStateException` too), mapped to **409 CONFLICT** by `AdminExceptionHandler`.

## 2. API

`POST /api/admin/orders/{id}/transition`, body `{ "status": "PACKING" }`
(values: PACKING | SHIPPED | COMPLETED | CANCELLED). ADMIN-gated + CSRF. Returns
the updated `OrderDetail` (reusing `OrderAdminQueryService.detail`). The existing
read endpoints are unchanged.

## 3. Admin UI (order detail)

On `OrderShow`, next to the current status, render **action buttons only for the
allowed next state(s)**, derived from the current status:

- `NEW` → **Lemondás** (CANCELLED, Popconfirm)
- `PAID` → **Csomagolásba** (PACKING)
- `PACKING` → **Feladva** (SHIPPED)
- `SHIPPED` → **Teljesítve** (COMPLETED)
- `COMPLETED` / `CANCELLED` → no actions

Each button calls the transition endpoint (CSRF header via the existing
`apiFetch`), then refetches the order. A 409 shows a readable error message.

## 4. Audit

Each transition records `ORDER_STATUS_CHANGE` with the order id and `old→new`
summary — satisfying the T15 audit requirement for order changes. (Structured
diff stays a slice-2-followups item.)

## 5. Testing

- **Backend (TDD):** valid chain PAID→PACKING→SHIPPED→COMPLETED; `PAID` target
  rejected; illegal hop (e.g. PAID→COMPLETED) → `TransitionNotAllowedException`;
  `NEW`→CANCELLED allowed; `PAID`→CANCELLED rejected (slice 2b); an audit entry
  is written per successful transition. Controller test: 200 + status changed,
  409 on illegal transition, 403 for non-admin.
- **Frontend:** `npm run build` green; the button-visibility logic is thin (the
  policy is enforced and tested in the service).

## Out of scope (later slices)

PAID-order cancel + refund + credit note (2b) · returns / partial refunds (2c) ·
courier label (2d) · status-change history shown in the UI · bulk status actions.
