# Money-path hardening — H3: refund reconciliation sweep — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A scheduled background sweep that finds whole-order refunds the DB missed — querying KHPos for refund state on still-open paid orders — and heals the terminal case (no second gateway charge), alerting anomalies.

**Architecture:** Mirrors the existing missing-callback safety net (`PaymentService.recheckPending` / `PaymentRecheckScheduler`). A new `RefundReconciliationService.reconcileOne(paymentId)` runs per-payment under the same pessimistic lock as the manual refund and reuses a shared `RefundFinalizer` (extracted from `RefundService`) to apply the refund to the DB. A thin `RefundReconciliationScheduler` loops candidates hourly. No schema change; the gateway stays authoritative (H2's no-ledger decision holds).

**Tech Stack:** Java 21, Spring Boot 3, Spring Scheduling, JUnit 5, Mockito, Testcontainers (Postgres), Maven (`mvn`, no wrapper). Design: `docs/superpowers/specs/2026-06-18-money-hardening-h3-design.md`.

---

## File Structure

- **Create** `src/main/java/hu/deposoft/webshop/application/order/RefundFinalizer.java` — the single definition of "finalize a refund in the DB" (order→REFUNDED, payment→REVERSED, audit, storno). Used by both the manual refund and the sweep.
- **Modify** `src/main/java/hu/deposoft/webshop/application/order/RefundService.java` — delegate its heal block to `RefundFinalizer` (behaviour unchanged).
- **Create** `src/main/java/hu/deposoft/webshop/application/order/RefundReconciliationService.java` — `findCandidatePaymentIds` + `reconcileOne` + the `Outcome` enum.
- **Modify** `src/main/java/hu/deposoft/webshop/domain/order/PaymentRepository.java` — add `findReconcilable`.
- **Create** `src/main/java/hu/deposoft/webshop/config/RefundReconciliationScheduler.java` — thin hourly `@Scheduled` wiring.
- **Create** `src/test/java/hu/deposoft/webshop/application/order/RefundReconciliationServiceTest.java` — branch + finder tests.
- **Modify** `docs/superpowers/specs/admin-slice2-followups.md` — mark H3 done.

`BookingCancellationService` and any line-residual logic are intentionally **not** touched (deferred slice).

---

## Task 1: Extract `RefundFinalizer` from `RefundService`

A pure refactor: move the heal block into a shared component so the sweep can reuse it. The existing `RefundServiceTest` (8 tests) is the regression net — behaviour must stay identical.

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/order/RefundFinalizer.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/order/RefundService.java`
- Test (regression): `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java`

- [ ] **Step 1: Create `RefundFinalizer`**

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.invoicing.InvoicingService;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The single definition of "finalize a refund in the DB" — shared by the manual refund
 * ({@link RefundService}) and the reconciliation sweep ({@code RefundReconciliationService}):
 * move the order to REFUNDED, mark the payment reversed, audit it, and issue the per-source
 * storno invoice. {@code transitionTo} runs FIRST so an illegal transition throws before any
 * other state changes — a caller that catches it then sees a clean, unmutated aggregate.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefundFinalizer {

    private final AuditService audit;
    private final InvoicingService invoicing;

    /** @param reconciled true when a background reconciliation healed it (audit-trail marker). */
    public void finalizeRefund(Order order, Payment payment, String reversalMessage, boolean reconciled) {
        OrderStatus previous = order.getStatus();
        order.transitionTo(OrderStatus.REFUNDED);
        payment.markState(Payment.State.REVERSED, reversalMessage);
        String suffix = reconciled ? " (reconciled)" : "";
        audit.record("ORDER_REFUNDED", "order", String.valueOf(order.getId()), previous + "→REFUNDED" + suffix);
        log.info("Order {} refunded ({} HUF){}", order.orderNumber(), order.getTotalGrossHuf(),
                reconciled ? " [reconciled]" : "");
        invoicing.creditNote(order.getId());
    }
}
```

Note the operation order differs from today's `RefundService` (transition before markState). This is intentional and behaviour-neutral for the manual path (all in one transaction; either everything commits or nothing does), and it makes the sweep's catch-and-alert path leave a clean state.

- [ ] **Step 2: Delegate from `RefundService`**

In `RefundService.java`:

1. Remove these imports:
```java
import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.invoicing.InvoicingService;
import lombok.extern.slf4j.Slf4j;
```
2. Remove the `@Slf4j` class annotation (the `log` field is no longer used here).
3. Replace the two injected fields:
```java
    private final InvoicingService invoicing;
    private final AuditService audit;
```
with:
```java
    private final RefundFinalizer finalizer;
```
   (Keep `orders`, `payments`, `gateway`.)
4. Replace the heal block — currently:
```java
        payment.markState(Payment.State.REVERSED, resultMessage);
        order.transitionTo(OrderStatus.REFUNDED);
        audit.record("ORDER_REFUNDED", "order", String.valueOf(orderId), current + "→REFUNDED");
        log.info("Order {} refunded ({} HUF)", order.orderNumber(), order.getTotalGrossHuf());

        invoicing.creditNote(orderId); // per-source storno invoice (gated)
```
with:
```java
        finalizer.finalizeRefund(order, payment, resultMessage, false);
```
   (`current` is still used by the guard checks above, so it stays.)

- [ ] **Step 3: Run the regression suite**

Run: `mvn -q -Dtest=RefundServiceTest test`
Expected: PASS, 8 tests. (Requires Docker.) The `ORDER_REFUNDED` audit row and the `<old>→REFUNDED` summary are unchanged for the manual path (suffix is empty when `reconciled=false`), so `refundsPaidOrder` and the others stay green.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/order/RefundFinalizer.java \
        src/main/java/hu/deposoft/webshop/application/order/RefundService.java
git commit -m "refactor(hardening): extract RefundFinalizer (shared refund-to-DB step)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `RefundReconciliationService` + candidate finder

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/PaymentRepository.java`
- Create: `src/main/java/hu/deposoft/webshop/application/order/RefundReconciliationService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/order/RefundReconciliationServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/hu/deposoft/webshop/application/order/RefundReconciliationServiceTest.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class RefundReconciliationServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired RefundReconciliationService reconciliation;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;

    @MockitoBean PaymentGateway gateway;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint)));
    }

    /** A PAID order with a CONFIRMED payment; returns the payment id. */
    private Long paidOrder(String key) {
        return paidOrder(key, OrderStatus.PAID);
    }

    private Long paidOrder(String key, OrderStatus status) {
        String token = cart.addItem(null, "FES-1", 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt", "t@example.com",
                null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        if (status == OrderStatus.PACKING) order.transitionTo(OrderStatus.PACKING);
        if (status == OrderStatus.SHIPPED) { order.transitionTo(OrderStatus.PACKING); order.transitionTo(OrderStatus.SHIPPED); }
        orders.save(order);
        return p.getId();
    }

    @Test
    void healsWhenGatewayShowsAlreadyRefunded() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.ALREADY_REFUNDED);
        Long payId = paidOrder("rec-heal");

        RefundReconciliationService.Outcome outcome = reconciliation.reconcileOne(payId);

        assertThat(outcome).isEqualTo(RefundReconciliationService.Outcome.HEALED);
        Payment p = payments.findById(payId).orElseThrow();
        assertThat(p.getState()).isEqualTo(Payment.State.REVERSED);
        assertThat(orders.findById(p.getOrder().getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order", String.valueOf(p.getOrder().getId())))
                .anySatisfy(e -> {
                    assertThat(e.getAction()).isEqualTo("ORDER_REFUNDED");
                    assertThat(e.getSummary()).contains("(reconciled)");
                });
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void leavesOrderWhenRefundInProgress() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUND_IN_PROGRESS);
        Long payId = paidOrder("rec-inprog");

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.NONE);
        assertThat(payments.findById(payId).orElseThrow().getState()).isEqualTo(Payment.State.CONFIRMED);
        assertThat(orders.findById(payments.findById(payId).orElseThrow().getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void doesNothingWhenStillRefundable() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
        Long payId = paidOrder("rec-normal");

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.NONE);
        assertThat(orders.findById(payments.findById(payId).orElseThrow().getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void alertsWhenGatewaySaysNotRefundable() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.NOT_REFUNDABLE);
        Long payId = paidOrder("rec-notref");

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.ALERTED);
        Payment p = payments.findById(payId).orElseThrow();
        assertThat(p.isAlert()).isTrue();
        assertThat(orders.findById(p.getOrder().getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void skipsOrderThatMovedPastPacking() {
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.ALREADY_REFUNDED);
        Long payId = paidOrder("rec-shipped", OrderStatus.SHIPPED);

        assertThat(reconciliation.reconcileOne(payId)).isEqualTo(RefundReconciliationService.Outcome.NONE);
        assertThat(orders.findById(payments.findById(payId).orElseThrow().getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
        verify(gateway, never()).refundability(anyString());
    }

    @Test
    void candidateFinderSelectsOnlyOpenConfirmedNotRecentlyChecked() {
        Long open = paidOrder("rec-cand-open");                 // CONFIRMED + PAID, never checked → match
        Long shipped = paidOrder("rec-cand-shipped", OrderStatus.SHIPPED); // CONFIRMED but SHIPPED → no
        Long checked = paidOrder("rec-cand-checked");           // CONFIRMED + PAID but checked just now → no
        payments.findById(checked).orElseThrow().touchChecked(OffsetDateTime.now(ZoneOffset.UTC));

        List<Long> ids = reconciliation.findCandidatePaymentIds(Duration.ofHours(24));

        assertThat(ids).contains(open).doesNotContain(shipped, checked);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (does not compile)**

Run: `mvn -q -Dtest=RefundReconciliationServiceTest test`
Expected: COMPILE FAILURE — `RefundReconciliationService` and its `Outcome` enum / methods do not exist yet.

- [ ] **Step 3: Add the candidate finder to `PaymentRepository`**

In `src/main/java/hu/deposoft/webshop/domain/order/PaymentRepository.java`, add imports and the method:

```java
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

```java
    /**
     * Reconciliation candidates (H3): payments in the given state on orders in the given
     * statuses, not re-checked since the cutoff. Used to find open paid orders whose
     * gateway-side refund state may have changed.
     */
    @Query("""
            select p from Payment p
            where p.state = :state
              and p.order.status in :statuses
              and (p.lastCheckedAt is null or p.lastCheckedAt < :cutoff)
            """)
    List<Payment> findReconcilable(@Param("state") Payment.State state,
                                   @Param("statuses") Collection<OrderStatus> statuses,
                                   @Param("cutoff") OffsetDateTime cutoff);
```

- [ ] **Step 4: Create `RefundReconciliationService`**

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refund reconciliation safety net (H3): for CONFIRMED payments on still-open (PAID/PACKING)
 * orders, asks the gateway whether the payment was already refunded out-of-band (or by a
 * refund whose commit was lost) and heals the DB to match — WITHOUT ever calling
 * {@code gateway.refund}, so it can never initiate a refund. Mirrors
 * {@code PaymentService.recheckPending}; the scheduled loop lives in
 * {@code RefundReconciliationScheduler}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefundReconciliationService {

    public enum Outcome { HEALED, ALERTED, NONE }

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final RefundFinalizer finalizer;

    /** Payment ids worth re-checking now (CONFIRMED, on PAID/PACKING orders, stale enough). */
    @Transactional(readOnly = true)
    public List<Long> findCandidatePaymentIds(Duration maxAge) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(maxAge);
        return payments.findReconcilable(
                        Payment.State.CONFIRMED, List.of(OrderStatus.PAID, OrderStatus.PACKING), cutoff)
                .stream().map(Payment::getId).toList();
    }

    /** Reconcile one payment against the gateway. Own transaction; never throws for the caller. */
    @Transactional
    public Outcome reconcileOne(Long paymentId) {
        Payment payment = payments.findById(paymentId).orElse(null);
        if (payment == null) {
            return Outcome.NONE;
        }
        payment.touchChecked(OffsetDateTime.now(ZoneOffset.UTC));
        if (payment.getState() != Payment.State.CONFIRMED) {
            return Outcome.NONE; // state changed since it was listed
        }
        Order order = orders.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.PACKING) {
            return Outcome.NONE; // already REFUNDED, or moved on to SHIPPED/etc.
        }

        PaymentGateway.Refundability refundability;
        try {
            refundability = gateway.refundability(payment.getPayId());
        } catch (RuntimeException e) {
            log.warn("Reconcile status query failed for payId={}: {}", payment.getPayId(), e.getMessage());
            return Outcome.NONE; // transient; re-checked next sweep
        }

        return switch (refundability) {
            case ALREADY_REFUNDED -> heal(order, payment);
            case REFUND_IN_PROGRESS -> {
                log.info("Reconcile: refund in progress for order {}, will re-check", order.orderNumber());
                yield Outcome.NONE;
            }
            case REFUNDABLE -> Outcome.NONE; // normal active order — nothing to reconcile
            case NOT_REFUNDABLE -> {
                payment.raiseAlert("Reconcile: CONFIRMED payment " + payment.getPayId() + " for order "
                        + order.orderNumber() + " is NOT_REFUNDABLE at gateway — inconsistency");
                log.error("ALERT: CONFIRMED payment {} reported NOT_REFUNDABLE by gateway — manual review needed",
                        payment.getPayId());
                yield Outcome.ALERTED;
            }
        };
    }

    private Outcome heal(Order order, Payment payment) {
        try {
            finalizer.finalizeRefund(order, payment, "reconciled: already refunded at gateway", true);
            return Outcome.HEALED;
        } catch (RuntimeException e) {
            payment.raiseAlert("Reconcile: gateway shows payment " + payment.getPayId() + " refunded but order "
                    + order.orderNumber() + " could not be finalized: " + e.getMessage());
            log.error("ALERT: reconcile could not finalize refunded order {} — manual intervention needed",
                    order.orderNumber());
            return Outcome.ALERTED;
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=RefundReconciliationServiceTest test`
Expected: PASS, 6 tests. (Requires Docker.)

Note (documented gap): the `heal()` catch-and-alert branch is defensive and not unit-tested — forcing `order.transitionTo(REFUNDED)` to throw on a genuinely PAID order is not reproducible without mocking the entity, which would test the mock, not behaviour. The `transitionTo`-first ordering in `RefundFinalizer` guarantees no partial mutation if it ever does throw.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/PaymentRepository.java \
        src/main/java/hu/deposoft/webshop/application/order/RefundReconciliationService.java \
        src/test/java/hu/deposoft/webshop/application/order/RefundReconciliationServiceTest.java
git commit -m "feat(hardening): refund reconciliation service (heal gateway-refunded orders, alert anomalies)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Scheduler wiring + doc + full verify

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/config/RefundReconciliationScheduler.java`
- Modify: `docs/superpowers/specs/admin-slice2-followups.md`

- [ ] **Step 1: Create the scheduler**

```java
package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.order.RefundReconciliationService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refund reconciliation safety net (H3): hourly, re-checks CONFIRMED payments on still-open
 * (PAID/PACKING) orders against the gateway's refund state and heals any whose money was
 * refunded out-of-band or whose refund commit was lost. Mirrors {@link PaymentRecheckScheduler}
 * (scheduling is already enabled there). Each payment runs in its own transaction in the
 * service, so one failure cannot abort the batch.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefundReconciliationScheduler {

    /** Re-check each payment at most once per 24h. */
    private static final Duration MAX_AGE = Duration.ofHours(24);

    private final RefundReconciliationService reconciliation;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void reconcileRefunds() {
        List<Long> ids = reconciliation.findCandidatePaymentIds(MAX_AGE);
        int healed = 0;
        int alerted = 0;
        for (Long id : ids) {
            try {
                switch (reconciliation.reconcileOne(id)) {
                    case HEALED -> healed++;
                    case ALERTED -> alerted++;
                    case NONE -> { }
                }
            } catch (RuntimeException e) {
                log.warn("Refund reconciliation failed for payment {}: {}", id, e.getMessage());
            }
        }
        if (healed > 0 || alerted > 0) {
            log.info("Refund reconciliation: {} healed, {} alerted ({} checked)", healed, alerted, ids.size());
        }
    }
}
```

Do NOT add `@EnableScheduling` — it is already declared on `PaymentRecheckScheduler`, and `@Scheduled` methods are picked up globally.

- [ ] **Step 2: Update the hardening-status doc**

In `docs/superpowers/specs/admin-slice2-followups.md`, the H3 bullet currently reads:

```
- ⏳ **H3 — reconciliation job** (gate 3): NOT yet; a proactive sweep (mirrors
  `PaymentRecheckScheduler`) for orders left PAID whose payId `queryStatus` shows refunded,
  and the line-path residual above.
```

Replace it with:

```
- ✅ **H3 — reconciliation sweep** (gate 3, merged): hourly `RefundReconciliationScheduler` +
  `RefundReconciliationService` re-check CONFIRMED payments on PAID/PACKING orders via
  `gateway.refundability`; a terminal `ALREADY_REFUNDED` heals the order (no `gateway.refund`
  call — pure DB reconcile, shared `RefundFinalizer`), `NOT_REFUNDABLE`/heal-failure alerts,
  `REFUND_IN_PROGRESS` is left for the next sweep. Design: `2026-06-18-money-hardening-h3-design.md`.
- ⏳ **Line-cancel partial-refund residual** (still its own deferred slice): per-line
  reconciliation needs the gateway's cumulative returned amount vs. cancelled lines.
```

(Use Read to find the exact current lines first; leave the surrounding bullets untouched. If the text differs, STOP and report NEEDS_CONTEXT.)

- [ ] **Step 3: Full build**

Run: `mvn verify`
Expected: `BUILD SUCCESS`, all tests green incl. ArchUnit `ModularityTest`. (Requires Docker.) Paste the `Tests run:` total + `BUILD SUCCESS`. If a test fails, report it — do not fix unrelated failures.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/config/RefundReconciliationScheduler.java \
        docs/superpowers/specs/admin-slice2-followups.md
git commit -m "feat(hardening): hourly refund reconciliation scheduler + mark H3 done

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage:** §1 candidate set + scheduling → Task 2 finder + Task 3 scheduler (1h/24h). §2 `RefundReconciliationService` + shared `heal()` → Task 1 (`RefundFinalizer`) + Task 2 (`reconcileOne`, lock via `findByIdForUpdate`, never calls `gateway.refund`, reconciled audit marker). §3 alerting → `NOT_REFUNDABLE` + heal-failure `raiseAlert` in Task 2. §4 testing → Task 2's six tests (auto-heal, in-flight, normal, anomaly, moved-on, finder) + Task 1's `RefundServiceTest` regression; the "idempotent already-REFUNDED" case is covered by the moved-on guard (`status != PAID/PACKING → NONE`) — note the explicit `REFUNDED` short-circuit is the same branch.
- **Placeholder scan:** no TBD/TODO; all code and commands concrete.
- **Type consistency:** `Refundability {REFUNDABLE, ALREADY_REFUNDED, REFUND_IN_PROGRESS, NOT_REFUNDABLE}` (from H2) and `Outcome {HEALED, ALERTED, NONE}`, `finalizeRefund(Order, Payment, String, boolean)`, `findReconcilable(State, Collection<OrderStatus>, OffsetDateTime)`, `findCandidatePaymentIds(Duration)`, `reconcileOne(Long)` are used identically across service, scheduler, repository, and tests. `Payment.isAlert()` is the Lombok getter for the existing `alert` boolean field.
