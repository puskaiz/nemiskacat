# Money-path hardening — H2: gateway-authoritative refund idempotency — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the retry-after-commit-failure double-refund window on the whole-order refund path by asking KHPos whether a payment is still refundable *before* calling refund, instead of building a DB ledger.

**Architecture:** KHPos is authoritative on refund state per payId (`PaymentStatus` exposes `REVERSED`/`RETURNED`/`REFUND_PROCESSING`). Add a `Refundability` query to the `PaymentGateway` port; `RefundService` pre-checks it and, when the gateway already shows the payment refunded, heals the DB to `REFUNDED` without a second gateway charge. The partial line-cancel path is unchanged (its payId is shared across lines, so the gateway can't answer per-line) — its residual gap is documented in the spec.

**Tech Stack:** Java 21, Spring Boot 3, JUnit 5, Mockito, Testcontainers (Postgres), Maven (`mvn`, no wrapper). Design: `docs/superpowers/specs/2026-06-16-money-hardening-h2-design.md`.

---

## File Structure

- **Modify** `src/main/java/hu/deposoft/webshop/application/payment/PaymentGateway.java` — add the `Refundability` enum and the `refundability(String payId)` port method.
- **Modify** `src/main/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapter.java` — implement `refundability` from `PaymentStatus`.
- **Modify** `src/main/java/hu/deposoft/webshop/integrations/khpos/DisabledPaymentGateway.java` — implement `refundability` (throws, like the other methods).
- **Create** `src/test/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapterTest.java` — plain Mockito unit test of the status→`Refundability` mapping.
- **Modify** `src/main/java/hu/deposoft/webshop/application/order/RefundService.java` — query-before-refund.
- **Modify** `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java` — 2 new tests + stub `refundability` on the 4 existing tests that reach the pre-check.
- **Modify** `docs/superpowers/specs/admin-slice2-followups.md` — update the H2/H3 status to the gateway-authoritative decision.

`BookingCancellationService` and the `refund_attempt`-style schema are intentionally **not** touched (see spec §3).

---

## Task 1: Add `Refundability` to the `PaymentGateway` port

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/payment/PaymentGateway.java`
- Modify: `src/main/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapter.java`
- Modify: `src/main/java/hu/deposoft/webshop/integrations/khpos/DisabledPaymentGateway.java`
- Test: `src/test/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapterTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapterTest.java`:

```java
package hu.deposoft.webshop.integrations.khpos;

import hu.deposoft.khpos.core.dto.KhposStatusResult;
import hu.deposoft.khpos.core.model.Currency;
import hu.deposoft.khpos.core.model.PaymentStatus;
import hu.deposoft.khpos.starter.autoconfig.KhposProperties;
import hu.deposoft.khpos.starter.service.KhposPaymentService;
import hu.deposoft.webshop.application.payment.PaymentGateway.Refundability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KhposGatewayAdapterTest {

    private final KhposPaymentService khpos = mock(KhposPaymentService.class);
    private final KhposProperties properties = mock(KhposProperties.class);
    private KhposGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        when(properties.merchants()).thenReturn(
                Map.of(Currency.HUF, new KhposProperties.Merchant("M-1", null, null)));
        adapter = new KhposGatewayAdapter(khpos, properties);
    }

    private void gatewayReports(PaymentStatus status) {
        when(khpos.queryStatus("M-1", "PAY-1"))
                .thenReturn(new KhposStatusResult("PAY-1", status, 0, "ok", null, null));
    }

    @Test
    void alreadyRefundedWhenReversedReturnedOrProcessing() {
        for (PaymentStatus s : new PaymentStatus[]{
                PaymentStatus.REVERSED, PaymentStatus.RETURNED, PaymentStatus.REFUND_PROCESSING}) {
            gatewayReports(s);
            assertThat(adapter.refundability("PAY-1")).as("%s", s)
                    .isEqualTo(Refundability.ALREADY_REFUNDED);
        }
    }

    @Test
    void refundableWhenConfirmedSettledOrWaiting() {
        for (PaymentStatus s : new PaymentStatus[]{
                PaymentStatus.CONFIRMED, PaymentStatus.SETTLED, PaymentStatus.WAITING_SETTLEMENT}) {
            gatewayReports(s);
            assertThat(adapter.refundability("PAY-1")).as("%s", s)
                    .isEqualTo(Refundability.REFUNDABLE);
        }
    }

    @Test
    void notRefundableWhenFailedRejectedOrUnpaid() {
        for (PaymentStatus s : new PaymentStatus[]{
                PaymentStatus.FAILED, PaymentStatus.REJECTED, PaymentStatus.CREATED, PaymentStatus.INITIATED}) {
            gatewayReports(s);
            assertThat(adapter.refundability("PAY-1")).as("%s", s)
                    .isEqualTo(Refundability.NOT_REFUNDABLE);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `mvn -q -Dtest=KhposGatewayAdapterTest test`
Expected: COMPILE FAILURE — `Refundability` and `adapter.refundability(...)` do not exist yet.

- [ ] **Step 3: Add the enum + method to the port**

In `PaymentGateway.java`, add the enum next to the other result types (after the `RefundResult` record) and declare the method after `refund`:

```java
    /**
     * Whether a confirmed payment can still be refunded — the authoritative
     * idempotency check, since KHPos tracks refund state per payId.
     */
    enum Refundability { REFUNDABLE, ALREADY_REFUNDED, NOT_REFUNDABLE }

    /** Ask the gateway whether {@code payId} can still be refunded (vs. already refunded). */
    Refundability refundability(String payId);
```

- [ ] **Step 4: Implement it in `KhposGatewayAdapter`**

Add this method to `KhposGatewayAdapter` (it does NOT reuse `mapStatus`, which collapses `RETURNED`/`REFUND_PROCESSING` to `CONFIRMED` for the inbound flow):

```java
    @Override
    public Refundability refundability(String payId) {
        KhposStatusResult result = khpos.queryStatus(merchantId(), payId);
        return switch (result.status()) {
            case REVERSED, RETURNED, REFUND_PROCESSING -> Refundability.ALREADY_REFUNDED;
            case CONFIRMED, SETTLED, WAITING_SETTLEMENT -> Refundability.REFUNDABLE;
            case FAILED, REJECTED, CREATED, INITIATED -> Refundability.NOT_REFUNDABLE;
        };
    }
```

The switch is exhaustive over all 10 `PaymentStatus` constants, so no `default` is needed (and adding a new status upstream would break the build loudly — intended).

- [ ] **Step 5: Implement it in `DisabledPaymentGateway`**

Add to `DisabledPaymentGateway` (consistent with its other methods — it is only active when `khpos.enabled=false`, where no refund can occur):

```java
    @Override
    public Refundability refundability(String payId) {
        throw new PaymentUnavailableException();
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -Dtest=KhposGatewayAdapterTest test`
Expected: PASS (3 tests). No Docker needed — plain Mockito unit test.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/payment/PaymentGateway.java \
        src/main/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapter.java \
        src/main/java/hu/deposoft/webshop/integrations/khpos/DisabledPaymentGateway.java \
        src/test/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapterTest.java
git commit -m "feat(hardening): PaymentGateway.refundability — KHPos-authoritative refund state

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `RefundService` queries refundability before refunding

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/order/RefundService.java:67-75`
- Test: `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add these two tests to `RefundServiceTest` (imports already present: `when`, `never`, `verify`, `anyString`, `anyLong`, `assertThatThrownBy`, `assertThat`):

```java
    @Test
    void refundsAlreadyRefundedAtGatewayWithoutRecharging() {
        when(gateway.refundability(anyString()))
                .thenReturn(PaymentGateway.Refundability.ALREADY_REFUNDED);
        Long id = orderInStatus("r-already", OrderStatus.PAID).getId();

        refunds.refund(id); // a prior refund landed but the commit rolled back; this is the retry

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payments.findFirstByOrderOrderByIdDesc(orders.findById(id).orElseThrow()).orElseThrow().getState())
                .isEqualTo(Payment.State.REVERSED);
        verify(gateway, never()).refund(anyString(), anyLong()); // no second charge
    }

    @Test
    void rejectsRefundWhenGatewayReportsNotRefundable() {
        when(gateway.refundability(anyString()))
                .thenReturn(PaymentGateway.Refundability.NOT_REFUNDABLE);
        Long id = orderInStatus("r-notref", OrderStatus.PAID).getId();

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundFailedException.class);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(gateway, never()).refund(anyString(), anyLong());
    }
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `mvn -q -Dtest=RefundServiceTest#refundsAlreadyRefundedAtGatewayWithoutRecharging+rejectsRefundWhenGatewayReportsNotRefundable test`
Expected: FAIL — `refundsAlreadyRefundedAtGatewayWithoutRecharging` fails (current code calls `gateway.refund`, which is unstubbed → returns `null` → NPE on `result.success()`); `rejectsRefundWhenGatewayReportsNotRefundable` fails the same way (it reaches `gateway.refund` instead of throwing). (Requires Docker for Testcontainers.)

- [ ] **Step 3: Implement query-before-refund**

In `RefundService.refund`, replace the gateway block (currently lines 67-75):

```java
        PaymentGateway.RefundResult result;
        try {
            result = gateway.refund(payment.getPayId(), order.getTotalGrossHuf());
        } catch (RuntimeException e) {
            throw new RefundFailedException("Refund call failed: " + e.getMessage());
        }
        if (!result.success()) {
            throw new RefundFailedException("Refund declined: " + result.message());
        }

        payment.markState(Payment.State.REVERSED, result.message());
```

with:

```java
        // KHPos is authoritative on refund state per payId, so ask before charging:
        // a prior refund that succeeded then lost its commit (crash/timeout) leaves the
        // order PAID, so we re-enter here — and now skip the second gateway call.
        String resultMessage = switch (gateway.refundability(payment.getPayId())) {
            case ALREADY_REFUNDED -> "already refunded at gateway";
            case REFUNDABLE -> {
                PaymentGateway.RefundResult result;
                try {
                    result = gateway.refund(payment.getPayId(), order.getTotalGrossHuf());
                } catch (RuntimeException e) {
                    throw new RefundFailedException("Refund call failed: " + e.getMessage());
                }
                if (!result.success()) {
                    throw new RefundFailedException("Refund declined: " + result.message());
                }
                yield result.message();
            }
            case NOT_REFUNDABLE -> throw new RefundFailedException(
                    "Gateway reports payment " + payment.getPayId() + " is not refundable");
        };

        payment.markState(Payment.State.REVERSED, resultMessage);
```

The rest of the method (`order.transitionTo(REFUNDED)`, audit, `invoicing.creditNote`) is unchanged. Note: a thrown `refundability(...)` (gateway unreachable) is intentionally *not* caught — it rolls back the transaction with the order still `PAID`, which is the safe outcome (no money moved), and the admin can retry.

- [ ] **Step 4: Stub `refundability` on the four existing tests that reach the pre-check**

These tests previously stubbed only `gateway.refund`; they now also pass through the pre-check, so add the `REFUNDABLE` stub. Add this line immediately after each existing `when(gateway.refund(...))` line in:

- `refundsPaidOrder`
- `gatewayFailureLeavesOrderUnchanged`
- `refundIsIdempotent`
- `refundsWorkshopOrderEvenWhenBillingoCreditNoteFails`

```java
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
```

(`rejectsRefundOfShippedOrder` and `rejectsWholeOrderRefundWhenALineIsCancelled` need **no** stub — they throw on the policy/state guards, before the pre-check.)

- [ ] **Step 5: Run the whole `RefundServiceTest` to verify all pass**

Run: `mvn -q -Dtest=RefundServiceTest test`
Expected: PASS (8 tests: 6 existing + 2 new). (Requires Docker.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/order/RefundService.java \
        src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java
git commit -m "feat(hardening): query KHPos refundability before refunding (idempotent retry, no double charge)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Update the hardening-status doc + full verify

**Files:**
- Modify: `docs/superpowers/specs/admin-slice2-followups.md:87-92`

- [ ] **Step 1: Update the H2/H3 status block**

In `admin-slice2-followups.md`, replace the two `⏳` bullets (the `H2 — idempotency ledger` and `H3 — reconciliation job` lines, 87-92) with:

```markdown
- ✅ **H2 — gateway-authoritative idempotency** (gate 1, merged): the original "DB ledger
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
- ⏳ **H3 — reconciliation job** (gate 3): NOT yet; a proactive sweep (mirrors
  `PaymentRecheckScheduler`) for orders left PAID whose payId `queryStatus` shows refunded,
  and the line-path residual above.
```

- [ ] **Step 2: Run the full build to verify nothing regressed**

Run: `mvn -q verify`
Expected: BUILD SUCCESS — all tests green incl. ArchUnit. (Requires Docker for Testcontainers.)

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/admin-slice2-followups.md
git commit -m "docs(hardening): mark H2 done (gateway-authoritative refund idempotency); note line residual + H3

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review notes

- **Spec coverage:** §1 port → Task 1; §2 RefundService → Task 2; §3 line path unchanged → no task (correct, by design); §4 tests → `KhposGatewayAdapterTest` (Task 1) + the 3 `RefundServiceTest` cases incl. existing-stay-green (Task 2); §5 H3 impact → doc update (Task 3).
- **No placeholders:** every code/command/expected-output is concrete.
- **Type consistency:** `Refundability {REFUNDABLE, ALREADY_REFUNDED, NOT_REFUNDABLE}` and `refundability(String)` are used identically across the port, adapter, disabled gateway, both test files, and the service switch.
