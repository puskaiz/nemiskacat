# Money-path hardening — H3: refund reconciliation sweep — design

## Context

H1 closed the *concurrent* double-refund window (pessimistic locking). H2 made the
whole-order refund **gateway-authoritative**: `RefundService.refund` queries
`gateway.refundability(payId)` before charging, so an admin retry after a
commit-failure-post-refund heals the DB without a second charge (`ALREADY_REFUNDED`),
and an in-flight refund (`REFUND_IN_PROGRESS`) or unrefundable payment throws and leaves
the order `PAID`.

H2 deliberately kept **no DB ledger** — the gateway is the source of truth. That leaves two
cases the *manual* path alone does not cover, which H3 closes with a background sweep:

1. **No-retry heal.** A refund landed at the gateway (admin refund whose commit was lost, or
   an out-of-band refund done directly in KHPos) but no admin ever re-triggers the refund, so
   the order sits `PAID`/`PACKING` while the money has actually been returned.
2. **In-flight settlement.** H2 left a `REFUND_IN_PROGRESS` order `PAID` on purpose (an
   in-flight refund is not yet settled). Once it settles to `REVERSED`/`RETURNED`, something
   must finalize the order.

Both are the same whole-order reconciliation: re-query the payId until the gateway shows a
terminal refund, then heal. The job mirrors the existing missing-callback safety net
`PaymentService.recheckPending` / `PaymentRecheckScheduler`.

**Out of scope (own later slice):** the line-cancel partial-refund residual. Partial refunds
share one payId, so reconciling per line means comparing the gateway's *cumulative returned
amount* against cancelled lines — materially harder, narrower (admin-only), and not derivable
from the payId-level status H3 uses. Deferred.

## Decision (confirmed)

- **H3 = whole-order reconciliation sweep only.** Line residual deferred.
- **Auto-heal the terminal case, alert the ambiguous.** When the gateway shows the payId
  terminally refunded (`REVERSED`/`RETURNED` → `ALREADY_REFUNDED`) but the order is still
  `PAID`/`PACKING`, the job heals the DB automatically (mark Payment `REVERSED`, Order
  `REFUNDED`, audit, credit note). This makes **no** payment-gateway call — the money already
  moved; it is pure DB reconciliation to match reality, not an "éles fizetési művelet", so it
  stays within the CLAUDE.md money rules. Anything ambiguous/inconsistent raises an alert for a
  human, no DB change.
- **No schema change, no marker.** The candidate set is derived from order/payment state; the
  gateway remains authoritative (consistent with H2's no-ledger decision).

## 1. Candidate set & scheduling

Refunds apply only to `PAID`/`PACKING` orders with a `CONFIRMED` payment (per `RefundService`
guards), and orders leave that window once shipped — so the candidate set is naturally small
and bounded.

- **New finder** on `PaymentRepository`:
  ```java
  /** Reconciliation candidates: CONFIRMED payments on still-open (PAID/PACKING) orders,
   *  not re-checked since the cutoff. */
  @Query("""
          select p from Payment p
          where p.state = hu.deposoft.webshop.domain.order.Payment$State.CONFIRMED
            and p.order.status in (
                hu.deposoft.webshop.domain.checkout.OrderStatus.PAID,
                hu.deposoft.webshop.domain.checkout.OrderStatus.PACKING)
            and (p.lastCheckedAt is null or p.lastCheckedAt < :cutoff)
          """)
  List<Payment> findReconcilable(@Param("cutoff") OffsetDateTime cutoff);
  ```
  (Final enum-reference form to be settled in the plan — JPQL enum literals vs. bound
  parameters; bound `:state`/`:statuses` parameters are the simpler equivalent and preferred if
  cleaner.)
- **New `RefundReconciliationScheduler`** (mirrors `PaymentRecheckScheduler`): `@Scheduled`
  `fixedDelay` **1 hour**, re-check each payment at most once per **24h** (the cutoff passed to
  `findReconcilable` is `now − 24h`). Rationale: a missed refund is not customer-facing (unlike
  the INITIATED payments `recheckPending` sweeps every 5 min), so a daily per-payment cadence is
  ample. The scheduler is thin — it calls the service and logs a one-line summary when anything
  was healed or alerted.

`lastCheckedAt` is shared with `recheckPending`, but the two never contend: `recheckPending`
only touches `INITIATED` payments, H3 only `CONFIRMED` ones, and a payment is in one state at a
time.

## 2. `RefundReconciliationService`

A new application service so the scheduler stays thin (mirroring `recheckPending`). It exposes
`int reconcile(Duration maxAge)` returning the number of candidates processed (for the log line).
For each candidate it runs an **idempotent, per-payment** unit; one bad payId must not abort the
batch. Each payment is reconciled in its own transaction, under the same pessimistic lock as the
manual path (`orders.findByIdForUpdate`) so it cannot race a concurrent admin refund.

Per-candidate logic:

```
touchChecked(now)                          // throttle, like recheckPending
order = orders.findByIdForUpdate(orderId)  // same lock as RefundService
if order.status == REFUNDED -> return       // already reconciled (idempotent)
if order.status not in (PAID, PACKING) -> return   // moved on (e.g. SHIPPED) — not our case
try:
  switch gateway.refundability(payId):
    ALREADY_REFUNDED   -> heal(order, payment)     // terminal: money moved, DB missed it
    REFUND_IN_PROGRESS -> log.info, leave           // in flight; next sweep re-checks
    REFUNDABLE         -> no-op                      // normal active order
    NOT_REFUNDABLE     -> raiseAlert(...)            // CONFIRMED payment gateway won't refund → anomaly
catch RuntimeException e:                            // gateway unreachable, etc.
  log.warn(...)                                      // caught per-payment; batch continues
```

This path **never calls `gateway.refund`** — it only heals what the gateway already reports as
refunded, so the broad sweep cannot accidentally initiate a refund on a normal order.

`touchChecked(now)` is committed even when the per-payment transaction otherwise does nothing,
so the throttle advances. (If the lookup/heal rolls back, the next sweep simply retries — the
operation is idempotent.)

### Shared `heal()` (refactor)

The heal effect currently lives inline in `RefundService.refund` (the block after the switch:
mark Payment `REVERSED`, `order.transitionTo(REFUNDED)`, `audit.record("ORDER_REFUNDED", ...)`,
`invoicing.creditNote(orderId)`). Extract it into a method reused by **both** the manual
`ALREADY_REFUNDED`/`REFUNDABLE` branches and the reconciliation path, so the two stay identical.

- The extracted method takes the resolved `Order` (+ `Payment`) and the reversal message, and
  performs the four effects. It is the single definition of "finalize a refund in the DB".
- **Audit traceability:** the audit detail distinguishes the actor. The manual path keeps
  `<old>→REFUNDED`; the reconciliation path records a detail noting it was healed by the sweep
  (e.g. `<old>→REFUNDED (reconciled)`), so it is clear a background job, not an admin, finalized
  it. (Exact mechanism — a parameter on the shared method — settled in the plan.)

`raiseAlert` is the existing `Payment.raiseAlert(msg)` (the flag T23 ops alerting hooks into)
plus a loud `log.error`, exactly as `PaymentService.confirm` does for the "paid but order
recording failed" branch.

## 3. Alerting & anomalies

Reuses the existing ops path — no new alerting infrastructure. The job raises an alert when:

- `refundability` → `NOT_REFUNDABLE` on a `CONFIRMED` payment (a payment the gateway claims is
  not refundable — a genuine inconsistency worth a human's eyes); and
- a `heal()` attempt throws (e.g. the order refuses the `REFUNDED` transition) — a real refund
  was found but could not be recorded, so it must be escalated rather than silently dropped.

The scheduler logs a one-line summary (count healed / alerted) only when non-zero, like
`PaymentRecheckScheduler`.

## 4. Testing summary

Backend, Testcontainers (Postgres), mocked `PaymentGateway` (a `@MockitoBean`, as in
`RefundServiceTest`). New `RefundReconciliationServiceTest`:

- **auto-heal:** order `PAID`, payment `CONFIRMED`, `refundability` → `ALREADY_REFUNDED` ⇒ order
  `REFUNDED`, payment `REVERSED`, an `ORDER_REFUNDED` audit row marked reconciled, and
  `gateway.refund` **never** called.
- **in-flight:** `REFUND_IN_PROGRESS` ⇒ no state change, order stays `PAID`.
- **normal:** `REFUNDABLE` ⇒ no change, `gateway.refund` never called.
- **anomaly:** `NOT_REFUNDABLE` ⇒ alert flag set on the payment, no state change.
- **idempotent:** order already `REFUNDED` ⇒ returns before querying the gateway.
- **moved on:** order `SHIPPED` ⇒ skipped, no change.
- **query failure:** `queryStatus` throws ⇒ caught, the batch continues (a second healthy
  candidate in the same sweep still heals).
- **candidate finder:** `findReconcilable` selects only `CONFIRMED` payments on `PAID`/`PACKING`,
  respects the `lastCheckedAt` cutoff (excludes recently-checked), and excludes
  `INITIATED`/`SHIPPED`/`COMPLETED`/`REFUNDED`.
- Existing `RefundServiceTest` stays green after the `heal()` extraction (the manual path is
  behaviorally unchanged).
- Full `mvn verify` green incl. ArchUnit `ModularityTest`. No frontend change.

The payment/inventory error-branch tests are not skipped or disabled (CLAUDE.md).

## Files (anticipated)

- `domain/order/PaymentRepository.java` — add `findReconcilable(cutoff)`.
- `application/order/RefundReconciliationService.java` — new sweep service.
- `application/order/RefundService.java` — extract the shared `heal()` (used by both paths).
- `config/RefundReconciliationScheduler.java` — new `@Scheduled` wiring (mirrors
  `PaymentRecheckScheduler`).
- `application/order/RefundReconciliationServiceTest.java` — new tests.

## Out of scope (next slices / unchanged)

- **Line-cancel partial-refund residual** — its own later slice (cumulative-amount logic).
- Real Billingo credit-note issuance / real Kulcs return push stay gated/stubbed.
- `BookingRescheduleService` (moves no money).
- Webhook/event-driven refund reconciliation (`KhposPaymentRefundedEvent`) — a possible future
  complement to the poll, not a replacement (we cannot rely on callback delivery; that is why
  this is a sweep).
