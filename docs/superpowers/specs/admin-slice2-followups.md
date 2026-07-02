# Admin — slice 2 follow-ups

Captured from the final code review of admin slice 1 (merged to `main` as `5a0a3aa`).
None block slice 1; address as part of slice 2 (order management T16) or alongside
related work.

1. **Audit/write transactional atomicity (Important).** Today `WorkshopAdminController`
   calls the service write and `AuditService.record(...)` in two separate transactions,
   so a failed audit insert can leave a committed mutation with no trail (or vice
   versa). Move audit recording inside the service write (same `@Transactional`) before
   audit expands to order/price edits, where atomicity matters legally.

2. **Rename `SessionHasBookingsException`.** The guard now blocks on *any* order line
   (any status), because hard-deleting the seat `Variant` breaks the `order_item→variant`
   FK — not just "bookings". Rename to e.g. `SeatReferencedByOrdersException` to match.

3. **`MeResponse.role` is hardcoded `"ADMIN"`** in `AdminAuthController`. Derive it from
   the authenticated authorities once more roles/granularity exist.

4. **No rate-limiting / lockout on `POST /api/admin/auth/login`.** Add throttling before
   the admin SPA is exposed publicly.

5. **Structured audit diff.** Replace the free-text `summary` with before/after JSON when
   order/price edits land; backfill `SESSION_CANCEL` with seat/SKU context (it logs
   `null` today).

6. **Explicit `@Valid`→400 handler + test.** Bean-validation failures currently produce
   400 via Spring defaults, not via `AdminExceptionHandler`. Add a
   `MethodArgumentNotValidException` handler (consistent SPA error body) and a test so the
   "bad input → 400" contract is owned by this codebase.

7. **CORS note (only if the SPA ever runs cross-origin).** The serve model is same-origin,
   so no CORS config now. If that changes, expose `X-Total-Count` via
   `Access-Control-Expose-Headers` and revisit cookie `SameSite`.

## Next plan: Plan 1B — Refine SPA (`admin-ui/`)

The backend API (`/api/admin/...`) is done. Plan 1B builds the Refine + TypeScript SPA
that consumes it: OpenAPI→TS types from `/v3/api-docs`, the auth provider against the
session-cookie login, the data provider (simple-rest + `X-Total-Count` + `X-XSRF-TOKEN`),
workshop CRUD screens + read-only orders, and same-origin serving under `/admin`.

## From slice 2a (order fulfilment status machine) — for 2b

- **Paid-order cancellation + refund** is the core of 2b: the rejection message in
  `OrderAdminService.transition` (cancel of a non-NEW order) is the placeholder.
  Wire KHPos reversal/refund + Billingo credit note (workshops); this also unlocks
  workshop-booking cancel/reschedule.
- **Consistent 400 bodies:** add a `MethodArgumentNotValidException` handler to
  `AdminExceptionHandler` so a missing `status` returns the same plain-string shape
  as the bogus-enum case (currently it falls through to Spring's default error JSON).
- **Shared not-found type:** promote `OrderAdminQueryService.NotFoundException` to a
  package-level `order` exception once a second command service needs it (the command
  `OrderAdminService` currently reuses the query service's type).
- **Tests:** add a negative-audit test (no audit row on a rejected transition) and
  SPA component tests for the order-detail action buttons.

## Order status mapping (old WooCommerce → ours) — for the order import + labels

Our order statuses (display label in the admin SPA, `api/orders.ts` STATUS_LABELS):
NEW="Fizetésre vár", PAID="Fizetve", PACKING="Feldolgozás alatt",
SHIPPED="Futárnak átadva", COMPLETED="Teljesítve", CANCELLED="Visszamondva".
Payment state is separate (`Payment`: INITIATED/CONFIRMED/FAILED/REVERSED).

| Old (Woo) | Ours |
|---|---|
| Fizetés függőben | NEW |
| Fizetés folyamatban | NEW + Payment INITIATED (payment-level, not an order status) |
| Sikertelen | NEW + Payment FAILED (payment-level) |
| Feldolgozás alatt | PAID / PACKING |
| Futárra vár | PACKING (done) / SHIPPED |
| Teljesítve | COMPLETED |
| Visszamondva | CANCELLED |
| Visszatérítve | REFUNDED — not yet (arrives with 2b refund) |
| Vázlat | no order (cart is separate) |

Decision: keep the lean 6-status model with Hungarian labels; do NOT add ON_HOLD/FAILED
as order statuses (they're payment-level). A future order import maps old→new per this table.

## Money-path hardening status (slice H1 — done, merged `606b6f0`)

- ✅ **Locking** (gate 2 below + the 2b-2 line-cancel lock): pessimistic `findByIdForUpdate`
  (`@Lock(PESSIMISTIC_WRITE)`) on `OrderRepository`/`OrderItemRepository`; `RefundService`
  and `BookingCancellationService` acquire it first → concurrent double-refund window closed.
- ✅ **Test coverage** (gate 4): 502 controller tests (refund + line-cancel) + RefundService
  workshop-line e2e (REFUNDED despite a FAILED/gated Billingo credit note).
- ✅ **H2 — gateway-authoritative idempotency** (gate 1, this branch): the original "DB ledger
  because KHPos has no idempotency key" premise was wrong — `queryStatus(payId)` returns
  `PaymentStatus` incl. `REVERSED`/`RETURNED`/`REFUND_PROCESSING`, so KHPos is authoritative
  on refund state per payId. `PaymentGateway.refundability(payId)` + a pre-check in
  `RefundService.refund`: `ALREADY_REFUNDED` heals the DB without a second charge → the
  retry-after-commit-failure window is closed for the whole-order path. No new table.
  Design: `2026-06-16-money-hardening-h2-design.md`.
- ⏳ **Line-cancel partial-refund residual** (NOT closed): a partial refund shares the
  order's single payId, so the gateway can't answer per-line; `BookingCancellationService`
  keeps H1's lock + `cancelledQuantity` guard. A partial refund that commits-fails can be
  re-refunded on retry. Accepted now (admin-only, sandbox); future: line-scoped marker or H3.
- ✅ **H3 — reconciliation sweep** (gate 3, this branch): hourly `RefundReconciliationScheduler`
  + `RefundReconciliationService` re-check CONFIRMED payments on PAID/PACKING orders via
  `gateway.refundability`; a terminal `ALREADY_REFUNDED` heals the order (no `gateway.refund`
  call — pure DB reconcile, shared `RefundFinalizer`), `NOT_REFUNDABLE`/heal-failure alerts,
  `REFUND_IN_PROGRESS` is left for the next sweep. Index `ix_payment_reconcile` backs the
  candidate query. Design: `2026-06-18-money-hardening-h3-design.md`.
- ⏳ **Line-cancel partial-refund residual** (still its own deferred slice): per-line
  reconciliation needs the gateway's cumulative returned amount vs. cancelled lines.

## From slice 2b-1 (order refund) — hard gates before real-money production

These are bounded/acceptable now (admin-only, KHPos sandbox, Billingo credit note GATED off → no real issuance), but MUST be closed before a real-money cutover:

1. **Idempotency key on the gateway refund** — pass a stable key (payId + order id) to `cancelOrReturn`, or persist a "refund requested" marker before the gateway call, so a retry after a commit-failure-post-refund is a bank-side no-op (closes the "money refunded but order still PAID → operator retries → double refund" window).
2. **Lock the refund path** — pessimistic lock or optimistic `@Version` on `Order` to close the concurrent double-refund window (two simultaneous refund calls both pass the PAID guard).
3. **Reconciliation job** — detect bank-refunded-but-not-REFUNDED orders (or move the gateway call to an after-commit pattern with a pending state).
4. **Test coverage (minor):** add a 502 controller test (gateway `success=false`) and a RefundService end-to-end test seeding a workshop line that asserts the order is REFUNDED even when the Billingo credit note is FAILED/pending (locks the no-rollback guarantee).
5. Real Billingo credit-note issuance + real Kulcs return push stay gated/stubbed until the Billingo key / Kulcs access exist (per spec).

## From slice 2b-2 (line cancel / partial refund) — hard gates before real-money production

- **2b-2 line cancel — concurrency lock:** `BookingCancellationService.cancelBooking` reads `cancelledQuantity` then calls the gateway then sets it; two concurrent identical requests could each pass the idempotency guard and double-refund a line. Same un-locked-refund-path rationale as the 2b-1 refund (admin-only, KHPos sandbox). Mitigation when hardening: pessimistic lock on the `order_item` row (`@Lock(PESSIMISTIC_WRITE)` on the lookup) or a check-and-set UPDATE.

## Next slices (2b)
- **2b-2** workshop booking cancel (order-line partial refund + seat release) — builds on RefundService.
- **2b-3** workshop booking reschedule (move to another session; capacity check).
- **2c** returns / partial refunds post-shipment.
