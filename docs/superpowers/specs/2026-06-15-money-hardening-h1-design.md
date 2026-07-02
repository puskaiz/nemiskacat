# Money-path hardening — H1: pessimistic locking + test coverage — design

## Context

Three money paths call the payment gateway to refund money:
- `RefundService.refund(orderId)` (2b-1) — whole-order refund; gateway-before-DB,
  then `Payment` → `REVERSED`, `Order` → `REFUNDED`, audit, gated credit note.
- `BookingCancellationService.cancelBooking(orderItemId)` (2b-2) — partial line refund;
  `Payment` stays `CONFIRMED`, line `cancelledQuantity = quantity`, gated credit note.
- (`BookingRescheduleService` moves no money — out of scope here.)

The 2b-1/2b-2 reviews recorded hard gates before a real-money cutover
(`admin-slice2-followups.md`). They are acceptable today (admin-only, KHPos sandbox,
Billingo gated off) but must be closed for production. This spec is **H1**, the first
and smallest slice.

Findings from exploring the code:
- **KHPos has no idempotency-key parameter** — `cancelOrReturn(merchantId, payId, Long amount)`
  and `refund`/`reverse` take no client key. So idempotency must be a **DB-side marker**
  (deferred to H2), not a gateway key.
- **No `@Version`** on `Order` or `Payment`. Optimistic locking would need a migration and
  touches every write path; **pessimistic locking** (`@Lock(PESSIMISTIC_WRITE)` on the
  refund/cancel lookups) needs no schema change and is scoped to exactly these transactions.
- An existing `PaymentRecheckScheduler` is the template a future reconciliation job (H3) mirrors.

## Decomposition (money-hardening)

- **H1 — pessimistic locking + the two missing tests** *(this spec)*: close the concurrent
  double-refund window; no schema change.
- **H2 — refund-attempt ledger**: a new table; a `REQUESTED` marker committed *before* the
  gateway call (separate transaction), checked on entry, so a retry after a
  commit-failure-post-refund is a no-op. Closes the retry-after-commit-failure window.
- **H3 — reconciliation job**: scheduled, over `REQUESTED` refunds via `queryStatus`,
  heal/alert. Needs H2.

## Decision (confirmed)

H1 scope = pessimistic locking on both money paths + the two recorded tests. Lock strategy:
**pessimistic** (no `@Version` migration). Idempotency ledger and reconciliation are H2/H3.

## 1. Pessimistic locking

Add row-locking finders and use them at the **start** of each money path so the idempotency
guard reads the locked, current state:

- `OrderRepository`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdForUpdate(@Param("id") Long id);
  ```
  `RefundService.refund` replaces its initial `orders.findById(orderId)` with
  `orders.findByIdForUpdate(orderId)`. The rest of the method is unchanged (the `REFUNDED`
  idempotency no-op now runs under the lock).
- `OrderItemRepository`:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from OrderItem i where i.id = :id")
  Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);
  ```
  `BookingCancellationService.cancelBooking` replaces its initial
  `orderItems.findById(orderItemId)` with `orderItems.findByIdForUpdate(orderItemId)`. The
  `cancelledQuantity >= quantity` idempotency no-op now runs under the lock.

Both services are `@Transactional`, so the lock is acquired at read and held to commit. The
second of two concurrent refunds **blocks** until the first commits, then re-reads the
now-`REFUNDED` order (or fully-cancelled line) under the lock and the existing idempotency
guard returns a clean no-op — no second gateway call.

**Tradeoff (documented, accepted):** the lock is held across the external gateway call. For
this context (admin-only, infrequent refunds, short sandbox round-trip) this is the correct,
simplest serialization. Under high concurrency a status-guarded check-and-set would be
preferable; out of scope here.

`BookingRescheduleService` is **not** changed (moves no money; its concurrency note remains a
documented follow-up).

## 2. Test coverage (the two recorded gates)

- **502 controller test** (`OrderAdminControllerTest`): `POST /api/admin/orders/{id}/refund`
  with the mocked gateway returning `RefundResult(false, "...")` → HTTP **502**; the order
  stays `PAID` afterward. Add the analogous 502 case to `BookingCancellationControllerTest`
  (`/order-items/{id}/cancel` with `success=false` → 502) since that path can also 502.
- **RefundService workshop-line e2e** (`RefundServiceTest`): seed a **BILLINGO** workshop
  line (via `WorkshopService.createWorkshop` + `addSession`, booked + paid), refund the
  order with the gateway succeeding; assert the order reaches `REFUNDED` and the `Payment` is
  `REVERSED` **even though** the Billingo credit note `Invoice` row is `FAILED`/pending
  (Billingo gated off in tests). Locks the "credit-note failure never rolls back the refund"
  guarantee end-to-end.

## 3. Testing the lock itself

True concurrent serialization is a DB-level guarantee; a two-thread test under the
`@Transactional`/Testcontainers harness is flaky and low-value, so H1 does **not** add one.
H1 instead verifies (a) the locked finders return the entity and the money paths still work
through them, and (b) the existing sequential **idempotency** tests still pass
(`RefundServiceTest.refundIsIdempotent`, `BookingCancellationServiceTest.cancelIsIdempotent`)
— that is the behavior the lock backstops.

## 4. Testing summary

- Backend (Testcontainers, mocked `PaymentGateway` + gated issuers):
  - locked finders wired in; all existing `RefundServiceTest` (incl. idempotency, guard,
    gateway-failure) and `BookingCancellationServiceTest` tests still green;
  - new `OrderAdminControllerTest` 502 case; new `BookingCancellationControllerTest` 502 case;
  - new `RefundServiceTest` workshop-line e2e (REFUNDED with FAILED credit note).
- Full `mvn verify` green (incl. ArchUnit). No frontend change.

## Out of scope (next slices / unchanged)

- **H2** refund-attempt ledger (idempotency marker, migration) — the DB-marker approach,
  since KHPos exposes no idempotency key.
- **H3** reconciliation job (mirrors `PaymentRecheckScheduler`).
- Real Billingo credit-note issuance / real Kulcs return push stay gated/stubbed.
- `BookingRescheduleService` concurrency lock (moves no money; documented follow-up).
