# Money-path Hardening H1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the concurrent double-refund window on both money paths with a pessimistic row lock, and lock in two recorded behaviors with tests (refund 502, refund-succeeds-despite-failed-credit-note).

**Architecture:** Add `@Lock(PESSIMISTIC_WRITE)` "for update" finders on `OrderRepository` and `OrderItemRepository`; `RefundService` and `BookingCancellationService` acquire the row lock at the start of their `@Transactional` method so concurrent refunds serialize and the existing idempotency guards turn the second into a no-op. No schema change. Then add the two recorded coverage tests (these characterize already-shipped behavior — they pass on first run).

**Tech Stack:** Spring Boot 4.1, Java 21, Spring Data JPA, Lombok; PostgreSQL via Testcontainers. No frontend change.

**Build/test commands (this repo):**
- Single class: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=<ClassName>`; full: `... verify`. After backend runs: `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.
- Do **not** stop/restart the app on :8085.

**Code root:** `/Users/zolika/Work/Claude/website`. Spec: `docs/superpowers/specs/2026-06-15-money-hardening-h1-design.md`.

**Note on TDD framing:** Tasks 1–2 are lock-in *refactors* (no new single-threaded behavior — true concurrency isn't unit-tested, per the spec); their verification is "the existing test class stays green". Tasks 3–4 are *characterization tests* for behavior already shipped in 2b-1/2b-2 (the 502 mapping and the no-rollback guarantee), so they are expected to **pass on first run** — that is correct for closing a coverage gap, not a TDD violation.

---

## File Structure

**Modify:**
- `src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java` — add `findByIdForUpdate`.
- `src/main/java/hu/deposoft/webshop/domain/order/OrderItemRepository.java` — add `findByIdForUpdate`.
- `src/main/java/hu/deposoft/webshop/application/order/RefundService.java` — use the locked finder.
- `src/main/java/hu/deposoft/webshop/application/order/BookingCancellationService.java` — use the locked finder.
- `src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java` — add a 502 refund test.
- `src/test/java/hu/deposoft/webshop/api/admin/BookingCancellationControllerTest.java` — add a 502 cancel test.
- `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java` — add the workshop-line e2e test.

---

## Task 1: Pessimistic lock on the whole-order refund path

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/order/RefundService.java`
- Verify: `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java` (existing, stays green)

Context: `OrderRepository` already imports `org.springframework.data.jpa.repository.Query` and `org.springframework.data.repository.query.Param` (used by `orderedQuantitySince`). `RefundService.refund` currently begins with `Order order = orders.findById(orderId).orElseThrow(() -> new ...OrderAdminQueryService.NotFoundException("No order " + orderId));`.

- [ ] **Step 1: Add the locked finder to `OrderRepository`**

Add these imports (if not present): `import jakarta.persistence.LockModeType;` and `import org.springframework.data.jpa.repository.Lock;`. Add the method:

```java
    /** Lock the order row for the duration of a money operation (refund) — serializes concurrent refunds. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
```

(`Optional` is already imported in this interface; if not, add `import java.util.Optional;`.)

- [ ] **Step 2: Use it in `RefundService.refund`**

In `src/main/java/hu/deposoft/webshop/application/order/RefundService.java`, change the first line of `refund(Long orderId)` from:
```java
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException("No order " + orderId));
```
to:
```java
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException("No order " + orderId));
```
(Only the finder name changes; everything else in the method is unchanged.)

- [ ] **Step 3: Run the existing refund tests; verify still GREEN**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=RefundServiceTest`
Expected: all existing tests pass (happy path, guards, gateway-failure, idempotent). The lock is transparent to single-threaded tests. Clean containers afterward.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java src/main/java/hu/deposoft/webshop/application/order/RefundService.java
git commit -m "feat(hardening): pessimistic lock on the whole-order refund path"
```

---

## Task 2: Pessimistic lock on the line-cancel path

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/OrderItemRepository.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/order/BookingCancellationService.java`
- Verify: `src/test/java/hu/deposoft/webshop/application/order/BookingCancellationServiceTest.java` (existing, stays green)

Context: `OrderItemRepository` is currently a bare `interface OrderItemRepository extends JpaRepository<OrderItem, Long> {}`. `BookingCancellationService.cancelBooking` begins with `OrderItem line = orderItems.findById(orderItemId).orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order item " + orderItemId));`.

- [ ] **Step 1: Add the locked finder to `OrderItemRepository`**

Replace the file body with (keeping the package):

```java
package hu.deposoft.webshop.domain.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Lock the line row for the duration of a money operation (line cancel) — serializes concurrent cancels. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderItem i where i.id = :id")
    Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);
}
```

- [ ] **Step 2: Use it in `BookingCancellationService.cancelBooking`**

Change the first line of `cancelBooking(Long orderItemId)` from:
```java
        OrderItem line = orderItems.findById(orderItemId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order item " + orderItemId));
```
to:
```java
        OrderItem line = orderItems.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order item " + orderItemId));
```
(Only the finder name changes.)

- [ ] **Step 3: Run the existing booking-cancel tests; verify still GREEN**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingCancellationServiceTest`
Expected: all existing tests pass (happy path, guards, gateway-failure rollback, idempotent, two-line). Clean containers afterward.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/OrderItemRepository.java src/main/java/hu/deposoft/webshop/application/order/BookingCancellationService.java
git commit -m "feat(hardening): pessimistic lock on the line-cancel path"
```

---

## Task 3: 502 controller tests (refund + cancel)

**Files:**
- Modify: `src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java`
- Modify: `src/test/java/hu/deposoft/webshop/api/admin/BookingCancellationControllerTest.java`

Context: both controller tests `@MockitoBean PaymentGateway gateway`. `OrderAdminControllerTest.setUp()` stubs `when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"))` and seeds one NEW order (helper `admin()` = `user("a@example.com").roles("ADMIN")`; `paymentRepository`, `orderRepository`, `Payment`, `OrderStatus` imported). The existing `refundEndpointRefundsPaidOrder` shows the paid-order setup. `BookingCancellationControllerTest` has `paidOrder(key, confirm)` and stubs the gateway per-test (no global stub). Both map `RefundFailedException`/`BookingRefundFailedException` → 502 (shipped in 2b-1/2b-2). These tests characterize that behavior; they should PASS on first run.

- [ ] **Step 1: Add the 502 refund test to `OrderAdminControllerTest`**

Add this test method (it re-stubs the gateway to fail, overriding the success stub from `setUp`):

```java
    @Test
    void refundEndpointReturns502WhenGatewayDeclines() throws Exception {
        when(gateway.refund(anyString(), anyLong()))
                .thenReturn(new PaymentGateway.RefundResult(false, "bank declined"));
        Order order = orderRepository.findAll().getFirst();
        paymentRepository.save(Payment.initiate(order, "PAY-502", order.getTotalGrossHuf()));
        var p = paymentRepository.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        orderRepository.save(order);

        mvc.perform(post("/api/admin/orders/" + order.getId() + "/refund")
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadGateway());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }
```

(`when`, `anyString`, `anyLong`, `assertThat`, `post`, `csrf`, `status` are already statically imported in this test class — verify; add any missing.)

- [ ] **Step 2: Run it; verify PASS (characterization — the 502 mapping already exists)**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=OrderAdminControllerTest`
Expected: all pass including the new test. If it does NOT return 502, STOP and report (it would mean the mapping regressed). Clean containers afterward.

- [ ] **Step 3: Add the 502 cancel test to `BookingCancellationControllerTest`**

This class has `@MockitoBean PaymentGateway gateway` and a `paidOrder(String key, boolean confirm)` helper that returns a paid order (confirm=true → CONFIRMED payment + PAID). Add:

```java
    @Test
    void cancelReturns502WhenGatewayDeclines() throws Exception {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(false, "declined"));
        Order order = paidOrder("c502", true);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/cancel", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadGateway());
    }
```

(`when`, `anyString`, `anyLong`, `user`, `csrf`, `post`, `status` are already statically imported in this class — verify; add any missing.)

- [ ] **Step 4: Run it; verify PASS**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingCancellationControllerTest`
Expected: all pass including the new test. Clean containers afterward.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java src/test/java/hu/deposoft/webshop/api/admin/BookingCancellationControllerTest.java
git commit -m "test(hardening): 502 coverage for refund + line-cancel endpoints"
```

---

## Task 4: RefundService workshop-line e2e (REFUNDED despite a FAILED credit note)

**Files:**
- Modify: `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java`

Context: `RefundServiceTest` autowires `refunds`, `orders`, `payments`, `audit`, `cart`, `checkout`, `importer`, and `@MockitoBean PaymentGateway gateway`; it seeds a physical product "FES-1" in `@BeforeEach`. Billingo is gated off in tests, so a workshop (BILLINGO) line's credit note records `Invoice(type=CREDIT_NOTE)` in state `FAILED`. This test proves the refund still completes (the credit-note failure is swallowed). It characterizes shipped behavior → expected to PASS.

- [ ] **Step 1: Add the workshop-line e2e test**

Add the imports at the top of the file (if missing):
```java
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.order.InvoiceRepository;
import hu.deposoft.webshop.domain.order.InvoiceType;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
```
Add two autowired fields to the test class:
```java
    @Autowired WorkshopService workshops;
    @Autowired InvoiceRepository invoices;
```
Add the test method:
```java
    @Test
    void refundsWorkshopOrderEvenWhenBillingoCreditNoteFails() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS", "ws-ref", "leiras", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-REF");
        String token = cart.addItem(null, "WS-REF", 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand("ws-refund", "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        payments.save(Payment.initiate(order, "PAY-WSREF", order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        Long id = orders.save(order).getId();

        refunds.refund(id);

        Order refunded = orders.findById(id).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payments.findFirstByOrderOrderByIdDesc(refunded).orElseThrow().getState())
                .isEqualTo(Payment.State.REVERSED);
        // Billingo gated off in tests → the workshop line's credit note is recorded FAILED (pending),
        // but the refund itself still completed (no rollback).
        var note = invoices.findByOrderAndSourceAndTypeAndOrderItemIsNull(refunded, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE)
                .orElseThrow();
        assertThat(note.getState()).isEqualTo(hu.deposoft.webshop.domain.order.InvoiceState.FAILED);
    }
```

(`when`, `anyString`, `anyLong`, `assertThat`, `PlaceOrderCommand`, `OrderStatus`, `Payment`, `Order` are already imported/used in this class — verify; add any missing. `cart`/`checkout`/`payments`/`orders` are existing autowired fields.)

- [ ] **Step 2: Run it; verify PASS**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=RefundServiceTest`
Expected: all pass including the new test. If the credit-note row is not FAILED (e.g., a real Billingo issuer is somehow active in tests), STOP and report. Clean containers afterward.

- [ ] **Step 3: Run the FULL backend suite (slice gate)**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify`
Expected: BUILD SUCCESS (all tests + ArchUnit). Clean containers: `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java
git commit -m "test(hardening): refund completes despite a FAILED Billingo credit note (workshop line)"
```

---

## Final verification

- [ ] **Backend:** `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify` → BUILD SUCCESS (all tests + ArchUnit). Clean containers afterward.
- [ ] Run the whole-slice final code review before finishing the branch.

---

## Self-Review notes (spec coverage)

- Spec §1 (pessimistic locking on both paths) → Tasks 1 + 2. Spec §2 (502 controller tests + RefundService workshop-line e2e) → Tasks 3 + 4. Spec §3 (no flaky concurrency test; rely on existing idempotency tests) → reflected in Tasks 1–2 verification (existing suites stay green).
- No frontend change (spec confirms). No schema change (pessimistic lock, no `@Version`).
- Method names consistent: `findByIdForUpdate` on both repositories; services swap only the finder call.
- The `findByOrderAndSourceAndTypeAndOrderItemIsNull` finder used in Task 4 was added in slice 2b-2 and is the correct whole-order credit-note lookup.
