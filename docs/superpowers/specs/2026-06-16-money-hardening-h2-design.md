# Money-path hardening — H2: gateway-authoritative refund idempotency — design

## Context

H1 closed the *concurrent* double-refund window with pessimistic locking on both money
paths (`RefundService.refund`, `BookingCancellationService.cancelBooking`). It left open the
**retry-after-commit-failure** window: the gateway refund succeeds, then the DB transaction
rolls back (process crash, connection loss) before the order reaches `REFUNDED`. On a later
retry the order is still `PAID`, so the path re-enters and would call the gateway a second
time — a double refund.

The H1 design assumed this had to be closed with a **DB-side marker** because "KHPos exposes
no idempotency key". **That premise was wrong.** Inspecting the starter
(`hu.deposoft.khpos.starter.service.KhposPaymentService` + `hu.deposoft.khpos.core.model.PaymentStatus`):

- `queryStatus(merchantId, payId)` returns a `PaymentStatus` that includes `REVERSED`,
  `REFUND_PROCESSING`, and `RETURNED`, plus predicates `canRefund()`, `canReverse()`,
  `isCancelled()`.
- The starter emits `KhposPaymentRefundedEvent` / `KhposPaymentReversedEvent`.

So **KHPos is authoritative on refund state, keyed by payId.** A status query before the
refund tells us whether a refund already landed — which resolves the "outcome unknown after a
crash/timeout" question that a DB marker can only *record*, never *answer*. The gateway is the
source of truth; H2 uses it directly instead of building a ledger.

## Decision (confirmed)

**Query-before-refund, no ledger.** For the whole-order path, query `refundability(payId)`
before calling refund; if the gateway already shows the payment refunded, skip the gateway
call and heal the DB. No new table, no schema change. The partial line-cancel path is **not**
gateway-authoritable (all lines share one payId) and stays on H1's defenses with the residual
gap documented.

Approaches considered and rejected:

- **Full `refund_attempt` DB ledger (the original H1 plan).** A `REQUESTED` marker committed
  before the gateway call, checked on entry. Rejected for the whole-order path: it duplicates
  state the gateway already owns authoritatively, and a stale `REQUESTED` marker is *itself*
  ambiguous (in-flight vs. orphaned-after-success) — it would still need a `queryStatus` to
  resolve, so the marker adds a table and a reconciliation job without removing the gateway
  query. YAGNI.
- **Per-line marker for both paths.** Same objection for the whole-order path. A line-only
  marker remains a *possible* future step for the residual partial-refund gap (see §3), but is
  out of scope for H2.

## 1. `PaymentGateway` port — expose refundability

The existing `queryStatus` returns `ResultKind {CONFIRMED, REJECTED, REVERSED, PENDING}`, and
`KhposGatewayAdapter.mapStatus` deliberately collapses `RETURNED`/`REFUND_PROCESSING` →
`CONFIRMED` ("money was taken") for the **inbound** payment flow. That mapping cannot answer
"already refunded?", so add an intention-revealing method to the port:

```java
/** Whether a confirmed payment can still be refunded — the authoritative idempotency
 *  check, since KHPos tracks refund state per payId. */
enum Refundability { REFUNDABLE, ALREADY_REFUNDED, NOT_REFUNDABLE }

Refundability refundability(String payId);
```

`KhposGatewayAdapter` implements it from `PaymentStatus` via an explicit switch (not the
inbound `mapStatus`):

| `PaymentStatus`                                  | `Refundability`       |
|--------------------------------------------------|-----------------------|
| `REVERSED`, `RETURNED`                           | `ALREADY_REFUNDED`    |
| `REFUND_PROCESSING`                              | `REFUND_IN_PROGRESS`  |
| `CONFIRMED`, `SETTLED`, `WAITING_SETTLEMENT`     | `REFUNDABLE`          |
| `FAILED`, `REJECTED`, `CREATED`, `INITIATED`     | `NOT_REFUNDABLE`      |

`REFUND_PROCESSING` maps to `REFUND_IN_PROGRESS`: it blocks a second charge (a refund is already
in flight — do not issue another) but does **not** finalize the order. An in-flight refund is not
yet settled, so finalizing on it would mark the order `REFUNDED` and issue a storno credit note on
an unsettled refund. Only the terminal states `REVERSED`/`RETURNED` (`ALREADY_REFUNDED`) heal the
order. `REFUND_IN_PROGRESS` is left for H3 reconciliation / settlement to resolve once the refund
settles. `DisabledPaymentGateway` throws `PaymentUnavailableException` as it does for the other
methods (it is only active when `khpos.enabled=false`, where no refund can occur).

## 2. `RefundService.refund` — query before refunding

After the existing DB guards (status `REFUNDED` → idempotent no-op; `PAID`/`PACKING` policy;
no cancelled lines; `Payment` must be `CONFIRMED`), and **before** `gateway.refund`:

```
switch (gateway.refundability(payment.getPayId())) {
  ALREADY_REFUNDED   -> // terminal: prior refund settled at the gateway; do NOT call refund again
                        // fall through to the heal block below (this is the only outcome that heals)
  REFUNDABLE         -> result = gateway.refund(payId, total);
                        if (!result.success()) throw RefundFailedException;
                        // fall through to the heal block below
  REFUND_IN_PROGRESS -> throw new RefundFailedException(
                            "Refund already in progress at gateway ... awaiting settlement");
                        // in-flight, not yet settled: order stays PAID, NOT finalized
  NOT_REFUNDABLE     -> throw new RefundFailedException(
                            "Gateway reports payment " + payId + " is not refundable");
}
// heal block (unchanged from today):
payment.markState(REVERSED, msg);
order.transitionTo(REFUNDED);
audit.record("ORDER_REFUNDED", ...);
invoicing.creditNote(orderId);
```

Why this closes the window: if a prior refund succeeded but its transaction rolled back, the
order is still `PAID`, so a retry re-enters past the DB guards. The pre-check now returns
`ALREADY_REFUNDED`, the second `gateway.refund` is skipped, and the DB is healed to `REFUNDED`
with no second charge. A gateway **timeout** resolves identically: the next retry asks the
gateway — the source of truth — which reports `ALREADY_REFUNDED` if the refund landed or
`REFUNDABLE` if it did not. No orphan-marker ambiguity, no new table.

The pre-check runs inside the existing `@Transactional` method, under H1's
`findByIdForUpdate` pessimistic lock. The lock is now held across **two** gateway round-trips
(`refundability` then `refund`). Accepted for this admin-only, low-frequency context —
consistent with H1's documented tradeoff (§Tradeoff in the H1 design).

`BookingRescheduleService` is unaffected (moves no money).

## 3. Line cancel (`BookingCancellationService`) — unchanged in H2

All lines of an order share a single payId, and `queryStatus` reports payId-level state, so
after the first partial return the payId flips to `REFUND_PROCESSING`/`RETURNED` for the whole
payment — the gateway cannot say *which* lines were returned or how much remains. The partial
path is therefore **not** gateway-authoritative and keeps H1's defenses:

- pessimistic lock (`findByIdForUpdate`) serializes concurrent cancels of the same line;
- the `cancelledQuantity >= quantity` guard makes a *committed* cancel an idempotent no-op.

**Residual gap (accepted, documented):** a partial gateway refund that succeeds and then has
its commit rolled back can be re-refunded on retry, because neither the DB (`cancelledQuantity`
not persisted *when the commit fails*) nor the gateway (payId-level only) can prove that specific
line was already returned. Accepted for now (admin-only, KHPos sandbox, Billingo gated off). Future options:
a line-scoped refund marker committed before the partial refund, or an H3 reconciliation that
compares the gateway's cumulative returned amount against the order's cancelled lines. **H2 does
not change this path's code.**

## 4. Testing summary

Backend, Testcontainers, mocked `PaymentGateway` (the gateway is a `@MockitoBean` in
`RefundServiceTest`, so the new `refundability` method needs stubbing only on the paths that
reach it):

- `RefundServiceTest` — new:
  - **refundable → refunds:** `refundability` → `REFUNDABLE`; assert `gateway.refund` called
    once, order `REFUNDED`, `Payment` `REVERSED`.
  - **already-refunded heals without re-charging** (the core H2 guarantee / post-crash retry):
    order `PAID`, `Payment` `CONFIRMED`, `refundability` → `ALREADY_REFUNDED`; assert
    `gateway.refund` is **never** called, yet the order reaches `REFUNDED` and the `Payment` is
    `REVERSED`.
  - **not-refundable → failure:** `refundability` → `NOT_REFUNDABLE`; `RefundFailedException`,
    order stays `PAID`, `gateway.refund` never called.
  - Existing tests stay green: the `REFUNDED` no-op short-circuits before the query; the
    workshop-line e2e and the 502 case stub `refundability` → `REFUNDABLE`.
- `KhposGatewayAdapterTest` — new: maps each `PaymentStatus` (mocked
  `KhposPaymentService.queryStatus`) to the expected `Refundability`, locking the
  `RETURNED`/`REFUND_PROCESSING`/`REVERSED` → `ALREADY_REFUNDED` mapping that the inbound
  `mapStatus` deliberately does not make.
- Full `mvn verify` green incl. ArchUnit. No frontend change.

The payment/inventory error-branch tests are not skipped or disabled (CLAUDE.md).

## 5. Impact on H3 (reconciliation)

H3 shrinks and its framing changes. The H1 design described "H2 = refund-attempt ledger,
H3 = reconcile over the ledger." With no ledger, H3 is no longer the *only* net for the
whole-order path — that path self-heals on the next admin retry via the pre-check. H3 becomes
a proactive scheduled sweep (mirroring `PaymentRecheckScheduler`) for:

- the **no-retry** case: orders left `PAID` whose payId `queryStatus` already shows
  refunded/reversed — heal or alert;
- the **line-path residual** (§3): compare the gateway's cumulative returned amount against the
  order's cancelled lines.

H3 stays out of scope here.

## Out of scope (next slices / unchanged)

- **H3** reconciliation sweep (above).
- Line-scoped refund marker for the partial-refund residual (§3) — deferred; only worth it if
  the residual is judged unacceptable before the real-money cutover.
- Real Billingo credit-note issuance / real Kulcs return push stay gated/stubbed.
- `BookingRescheduleService` concurrency lock (moves no money; documented follow-up).
