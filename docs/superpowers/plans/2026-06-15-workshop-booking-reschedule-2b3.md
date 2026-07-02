# Workshop Booking Reschedule (2b-3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move a workshop attendee (an `OrderItem` line) to another session of the same workshop — same price only, no money movement — by re-pointing the line's seat variant with a capacity check.

**Architecture:** Session availability is per-variant (`AvailabilityService.availableQty` = `capacity − orderedQuantitySince − holds`), so a reschedule is a re-point of `order_item.variant_id` + `sku` snapshot via a thin `OrderItem.moveToSeat` domain method, orchestrated by a new `BookingRescheduleService` (sibling of `BookingCancellationService`) that guards status/same-workshop/same-price/capacity, audits, and changes nothing else. Exposed at `POST /api/admin/order-items/{id}/reschedule` and wired into the workshop attendee table as an "Áthelyezés" modal.

**Tech Stack:** Spring Boot 4.1, Java 21, JPA/Hibernate (`ddl-auto: validate`, no schema change here), Lombok; Refine + TypeScript + Ant Design SPA in `admin-ui/`.

**Build/test commands (this repo):**
- Backend single class: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=<ClassName>`; full: `... verify`. After backend runs: `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.
- Frontend gate: `cd admin-ui && npm run build`.
- Do **not** stop/restart the app on :8085.

**Code root:** `/Users/zolika/Work/Claude/website`. Spec: `docs/superpowers/specs/2026-06-15-workshop-booking-reschedule-2b3-design.md`.

---

## File Structure

**Create:**
- `src/main/java/hu/deposoft/webshop/application/order/BookingRescheduleService.java` — orchestrator + `RescheduleNotAllowedException`.
- `src/main/java/hu/deposoft/webshop/api/admin/BookingRescheduleController.java` — `POST /api/admin/order-items/{itemId}/reschedule` + request/result records.
- `src/test/java/hu/deposoft/webshop/application/order/BookingRescheduleServiceTest.java`
- `src/test/java/hu/deposoft/webshop/api/admin/BookingRescheduleControllerTest.java`

**Modify:**
- `domain/order/OrderItem.java` — `moveToSeat(Variant target)`.
- `api/admin/AdminExceptionHandler.java` — map `RescheduleNotAllowedException` → 409.
- `application/workshop/WorkshopBookingView.java` — add `long unitGrossHuf`.
- `application/workshop/WorkshopService.java` — `bookings()` maps `unitGrossHuf`.
- `src/test/java/.../workshop/WorkshopBookingsViewTest.java` — assert the new field.
- `admin-ui/src/types.ts` — `WorkshopBooking.unitGrossHuf`.
- `admin-ui/src/api/orders.ts` — `rescheduleBooking(itemId, targetSessionId)`.
- `admin-ui/src/pages/workshops/edit.tsx` — "Áthelyezés" button + modal.

Reuse: `domain/order/OrderItemRepository` (exists), `domain/workshop/WorkshopSessionRepository` (`findById`, `findByVariantId`), `application/catalog/AvailabilityService` (`availableQty`, `NO_CART`), `application/audit/AuditService`.

---

## Task 1: `OrderItem.moveToSeat`

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/OrderItem.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java` (exists — add to it)

Context: `OrderItem` is a JPA `@Entity` with Lombok `@Getter`, fields `variant` (`@ManyToOne Variant`), `sku`, `unitGrossHuf` (long), `quantity`, `cancelledQuantity`. `Variant` exposes `getSku()`, `getRegularPriceHuf()` (long), `getProduct()`. The existing `OrderItemTest` builds lines via `OrderItem.create(order, variant, productName, variantLabel, sku, unitGrossHuf, taxRatePercent, quantity, invoiceSource)`.

- [ ] **Step 1: Add failing unit tests to `OrderItemTest.java`**

The existing `line(int qty)` helper passes `variant = null`, which is fine for the price-guard test but not for the happy re-point. Add a tiny `Variant` builder using the real factory. Append these imports if missing: `import hu.deposoft.webshop.domain.catalog.Variant;` `import hu.deposoft.webshop.domain.catalog.Product;` `import hu.deposoft.webshop.domain.catalog.ProductType;` `import hu.deposoft.webshop.domain.catalog.ProductStatus;`. Add:

```java
    private Variant seat(String sku, long priceHuf) {
        Product ws = Product.create(null, "ws-" + sku, "WS", ProductType.WORKSHOP, ProductStatus.PUBLISHED);
        Variant v = Variant.create(ws, null, false);
        v.setSku(sku);
        v.setRegularPriceHuf(priceHuf);
        return v;
    }

    @Test
    void moveToSeatRepointsVariantAndSku() {
        OrderItem item = OrderItem.create(null, seat("WS-A", 15_000L), "WS", "Alkalom",
                "WS-A", 15_000L, 27, 1, InvoiceSource.BILLINGO);
        Variant target = seat("WS-B", 15_000L);

        item.moveToSeat(target);

        assertThat(item.getVariant()).isSameAs(target);
        assertThat(item.getSku()).isEqualTo("WS-B");
        assertThat(item.getUnitGrossHuf()).isEqualTo(15_000L); // unchanged (same price)
    }

    @Test
    void moveToSeatRejectsDifferentPrice() {
        OrderItem item = OrderItem.create(null, seat("WS-A", 15_000L), "WS", "Alkalom",
                "WS-A", 15_000L, 27, 1, InvoiceSource.BILLINGO);
        Variant pricier = seat("WS-B", 18_000L);

        assertThatThrownBy(() -> item.moveToSeat(pricier)).isInstanceOf(IllegalStateException.class);
    }
```

(`assertThat`, `assertThatThrownBy`, `InvoiceSource` are already imported in `OrderItemTest`.)

- [ ] **Step 2: Run; verify FAIL** — `moveToSeat` doesn't exist (compile error).

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=OrderItemTest`

- [ ] **Step 3: Implement `moveToSeat` in `OrderItem.java`** (after `cancelWholeLine()`):

```java
    /** Move this line to another seat variant of the same price (2b-3 reschedule). */
    public void moveToSeat(hu.deposoft.webshop.domain.catalog.Variant targetSeat) {
        if (targetSeat.getRegularPriceHuf() != unitGrossHuf) {
            throw new IllegalStateException(
                    "Target seat price " + targetSeat.getRegularPriceHuf()
                            + " != line unit price " + unitGrossHuf);
        }
        this.variant = targetSeat;
        this.sku = targetSeat.getSku();
    }
```

(`Variant` is already imported in `OrderItem.java` as `hu.deposoft.webshop.domain.catalog.Variant`; you may use the short name `Variant` in the signature.)

- [ ] **Step 4: Run; verify PASS** (5 tests in `OrderItemTest`: 3 existing + 2 new).

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=OrderItemTest`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/OrderItem.java src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java
git commit -m "feat(2b-3): OrderItem.moveToSeat re-points a same-price seat"
```

---

## Task 2: `BookingRescheduleService`

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/order/BookingRescheduleService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/order/BookingRescheduleServiceTest.java`

Context / APIs:
- `OrderItemRepository` (`JpaRepository<OrderItem, Long>`), `WorkshopSessionRepository.findById(Long)` → `Optional<WorkshopSession>`, `findByVariantId(Long)` → `Optional<WorkshopSession>`. `WorkshopSession.getVariant()`, `getStartAt()`, `getCapacity()`.
- `AvailabilityService.availableQty(Variant variant, String ownCartToken)` returns the seat balance and is **0 for an already-started session** (so a past target is rejected by the capacity check). Use `AvailabilityService.NO_CART`.
- `OrderItem.getOrder()`, `getVariant()`, `getQuantity()`, `getCancelledQuantity()`, `getUnitGrossHuf()`, `getSku()`, `moveToSeat(Variant)`. `Variant.getProduct().getId()`, `getSku()`, `getRegularPriceHuf()`.
- `Order.getStatus()`, `orderNumber()`. `OrderStatus` enum: NEW, PAID, PACKING, SHIPPED, COMPLETED, CANCELLED, REFUNDED.
- 404 exceptions already mapped by `AdminExceptionHandler`: `OrderAdminQueryService.NotFoundException` (use for the order item) and `WorkshopService.NotFoundException` (use for the target session).
- `AuditService.record(String action, String entityType, String entityId, String summary)`.

- [ ] **Step 1: Write the failing service test**

Create `src/test/java/hu/deposoft/webshop/application/order/BookingRescheduleServiceTest.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.AvailabilityService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class BookingRescheduleServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BookingRescheduleService reschedules;
    @Autowired OrderRepository orders;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;
    @Autowired WorkshopSessionRepository sessions;
    @Autowired AvailabilityService availability;

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    /** A workshop with sessions A (price 15000) and B; returns the product. */
    private Product workshopWithTwoSessions(String key, long priceB, int capB) {
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "leiras", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WSA-" + key);
        workshops.addSession(ws, NOW.plusDays(8), capB, priceB, "WSB-" + key);
        return ws;
    }

    /** An order booking one seat of session A (sku WSA-key). */
    private Order bookSessionA(String key) {
        String token = cart.addItem(null, "WSA-" + key, 1).token();
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
    }

    private Long sessionIdBySku(String sku) {
        return sessions.findAll().stream().filter(s -> sku.equals(s.getVariant().getSku()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void reschedulesToSamePriceSessionFreesSourceConsumesTarget() {
        Product ws = workshopWithTwoSessions("ok", 15_000L, 5);
        Order order = bookSessionA("ok");
        OrderItem line = order.getItems().get(0);
        Variant sourceSeat = line.getVariant();
        Long targetSessionId = sessionIdBySku("WSB-ok");
        Variant targetSeat = sessions.findById(targetSessionId).orElseThrow().getVariant();

        int srcBefore = availability.availableQty(sourceSeat, AvailabilityService.NO_CART);
        int tgtBefore = availability.availableQty(targetSeat, AvailabilityService.NO_CART);

        reschedules.reschedule(line.getId(), targetSessionId);

        OrderItem moved = orders.findById(order.getId()).orElseThrow().getItems().get(0);
        assertThat(moved.getSku()).isEqualTo("WSB-ok");
        assertThat(moved.getVariant().getId()).isEqualTo(targetSeat.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(availability.availableQty(sourceSeat, AvailabilityService.NO_CART)).isEqualTo(srcBefore + 1);
        assertThat(availability.availableQty(targetSeat, AvailabilityService.NO_CART)).isEqualTo(tgtBefore - 1);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order_item", String.valueOf(line.getId())))
                .anySatisfy(e -> assertThat(e.getAction()).isEqualTo("BOOKING_RESCHEDULED"));
    }

    @Test
    void rejectsDifferentPrice() {
        workshopWithTwoSessions("price", 18_000L, 5);
        Order order = bookSessionA("price");
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-price");

        assertThatThrownBy(() -> reschedules.reschedule(lineId, targetSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsFullTarget() {
        Product ws = workshopWithTwoSessions("full", 15_000L, 1);
        // fill session B (capacity 1) with another order
        String fillTok = cart.addItem(null, "WSB-full", 1).token();
        checkout.placeOrder(fillTok, new PlaceOrderCommand("fill", "X", "x@e.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "gls"));
        Order order = bookSessionA("full");
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-full");

        assertThatThrownBy(() -> reschedules.reschedule(lineId, targetSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsTargetEqualsSource() {
        workshopWithTwoSessions("same", 15_000L, 5);
        Order order = bookSessionA("same");
        Long lineId = order.getItems().get(0).getId();
        Long sourceSessionId = sessionIdBySku("WSA-same");

        assertThatThrownBy(() -> reschedules.reschedule(lineId, sourceSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsDifferentWorkshop() {
        workshopWithTwoSessions("wsone", 15_000L, 5);
        Product other = workshops.createWorkshop("Other", "ws-other", "l", 27);
        workshops.addSession(other, NOW.plusDays(9), 5, 15_000L, "OTHER-1");
        Order order = bookSessionA("wsone");
        Long lineId = order.getItems().get(0).getId();
        Long foreignSessionId = sessionIdBySku("OTHER-1");

        assertThatThrownBy(() -> reschedules.reschedule(lineId, foreignSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsCancelledOrder() {
        workshopWithTwoSessions("canc", 15_000L, 5);
        Order order = bookSessionA("canc");
        order.transitionTo(OrderStatus.CANCELLED);
        orders.save(order);
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-canc");

        assertThatThrownBy(() -> reschedules.reschedule(lineId, targetSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void notFoundForUnknownItemOrSession() {
        workshopWithTwoSessions("nf", 15_000L, 5);
        Order order = bookSessionA("nf");
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-nf");

        assertThatThrownBy(() -> reschedules.reschedule(999999L, targetSessionId))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> reschedules.reschedule(lineId, 999999L))
                .isInstanceOf(RuntimeException.class);
    }
}
```

NOTE: `NEW` order is used (no payment needed — reschedule moves no money). Confirm `OrderStatus.NEW → CANCELLED` is an allowed transition (it is, per `OrderStatus.ALLOWED`).

- [ ] **Step 2: Run; verify FAIL** — `BookingRescheduleService` doesn't exist (compile error).

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingRescheduleServiceTest`

- [ ] **Step 3: Implement `src/main/java/hu/deposoft/webshop/application/order/BookingRescheduleService.java`:**

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.catalog.AvailabilityService;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderItemRepository;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reschedule a workshop booking (T16 slice 2b-3): move an order line to another
 * session of the SAME workshop at the SAME price. No money moves — the seat frees
 * on the source variant and is consumed on the target automatically (availability
 * is per-variant). Order status, payment, and invoices are untouched.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingRescheduleService {

    private final OrderItemRepository orderItems;
    private final WorkshopSessionRepository sessions;
    private final AvailabilityService availability;
    private final AuditService audit;

    /** Policy rejection (wrong state / different workshop / different price / no capacity / no-op). */
    public static class RescheduleNotAllowedException extends RuntimeException {
        public RescheduleNotAllowedException(String message) {
            super(message);
        }
    }

    @Transactional
    public void reschedule(Long orderItemId, Long targetSessionId) {
        OrderItem line = orderItems.findById(orderItemId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order item " + orderItemId));
        WorkshopSession target = sessions.findById(targetSessionId)
                .orElseThrow(() -> new WorkshopService.NotFoundException("No session " + targetSessionId));
        Variant targetSeat = target.getVariant();
        Variant sourceSeat = line.getVariant();

        Order order = line.getOrder();
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED
                || status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            throw new RescheduleNotAllowedException("Order " + order.getId() + " is " + status + "; cannot reschedule");
        }
        if (line.getCancelledQuantity() >= line.getQuantity()) {
            throw new RescheduleNotAllowedException("Line " + orderItemId + " is cancelled");
        }
        if (!targetSeat.getProduct().getId().equals(sourceSeat.getProduct().getId())) {
            throw new RescheduleNotAllowedException("Target session belongs to a different workshop");
        }
        if (targetSeat.getId().equals(sourceSeat.getId())) {
            throw new RescheduleNotAllowedException("Target session is the current one");
        }
        if (targetSeat.getRegularPriceHuf() != line.getUnitGrossHuf()) {
            throw new RescheduleNotAllowedException("Target session price differs; price-delta reschedule is not supported");
        }
        int needed = line.getQuantity() - line.getCancelledQuantity();
        if (availability.availableQty(targetSeat, AvailabilityService.NO_CART) < needed) {
            throw new RescheduleNotAllowedException("Target session has no free seat");
        }

        String oldSku = line.getSku();
        String oldDate = sessions.findByVariantId(sourceSeat.getId())
                .map(s -> s.getStartAt().toString()).orElse("?");
        line.moveToSeat(targetSeat);
        audit.record("BOOKING_RESCHEDULED", "order_item", String.valueOf(orderItemId),
                order.orderNumber() + " " + oldSku + "→" + targetSeat.getSku()
                        + " (" + oldDate + "→" + target.getStartAt() + ")");
        log.info("Booking line {} of order {} rescheduled {} → {}", orderItemId, order.orderNumber(),
                oldSku, targetSeat.getSku());
    }
}
```

- [ ] **Step 4: Run; verify all 7 PASS.** Clean containers.

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingRescheduleServiceTest`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/order/BookingRescheduleService.java src/test/java/hu/deposoft/webshop/application/order/BookingRescheduleServiceTest.java
git commit -m "feat(2b-3): BookingRescheduleService — same-price seat move with guards"
```

---

## Task 3: Admin API endpoint + exception mapping

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/api/admin/BookingRescheduleController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/BookingRescheduleControllerTest.java`

Context: `BookingCancellationController` is the sibling pattern (`@RestController @RequestMapping("/api/admin/order-items") @RequiredArgsConstructor`, returns a record). Validation uses `jakarta.validation` (`@Valid`, `@NotNull`); `AdminExceptionHandler` already maps `HttpMessageNotReadableException` → 400 and the two `NotFoundException`s → 404.

- [ ] **Step 1: Write the failing controller test**

Create `src/test/java/hu/deposoft/webshop/api/admin/BookingRescheduleControllerTest.java`:

```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class BookingRescheduleControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;
    @Autowired WorkshopSessionRepository sessions;

    MockMvc mvc;
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Order bookA(String key, long priceB) {
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "l", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WSA-" + key);
        workshops.addSession(ws, NOW.plusDays(8), 5, priceB, "WSB-" + key);
        String token = cart.addItem(null, "WSA-" + key, 1).token();
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "T", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "gls"));
    }

    private Long sessionIdBySku(String sku) {
        return sessions.findAll().stream().filter(s -> sku.equals(s.getVariant().getSku()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void rescheduleReturns200() throws Exception {
        Order order = bookA("r2", 15_000L);
        Long lineId = order.getItems().get(0).getId();
        Long target = sessionIdBySku("WSB-r2");

        mvc.perform(post("/api/admin/order-items/{id}/reschedule", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":" + target + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(lineId))
                .andExpect(jsonPath("$.sessionId").value(target));
    }

    @Test
    void differentPriceReturns409() throws Exception {
        Order order = bookA("r409", 18_000L);
        Long lineId = order.getItems().get(0).getId();
        Long target = sessionIdBySku("WSB-r409");

        mvc.perform(post("/api/admin/order-items/{id}/reschedule", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":" + target + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownSessionReturns404() throws Exception {
        Order order = bookA("r404", 15_000L);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/reschedule", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminReturns403() throws Exception {
        mvc.perform(post("/api/admin/order-items/{id}/reschedule", 12345L)
                        .with(user("user").roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":1}"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run; verify FAIL** (no endpoint → 200 test gets 404/500).

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingRescheduleControllerTest`

- [ ] **Step 3: Create `src/main/java/hu/deposoft/webshop/api/admin/BookingRescheduleController.java`:**

```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.order.BookingRescheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Line-level booking reschedule for the admin SPA (2b-3). ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/order-items")
@RequiredArgsConstructor
public class BookingRescheduleController {

    private final BookingRescheduleService reschedules;

    public record RescheduleRequest(@NotNull Long targetSessionId) {
    }

    public record RescheduleResult(long orderItemId, long sessionId) {
    }

    @PostMapping("/{itemId}/reschedule")
    public RescheduleResult reschedule(@PathVariable Long itemId, @RequestBody @Valid RescheduleRequest req) {
        reschedules.reschedule(itemId, req.targetSessionId());
        return new RescheduleResult(itemId, req.targetSessionId());
    }
}
```

- [ ] **Step 4: Map the exception in `AdminExceptionHandler.java`**

Add the import `import hu.deposoft.webshop.application.order.BookingRescheduleService;` and this handler (after the booking-cancel handlers):

```java
    @ExceptionHandler(BookingRescheduleService.RescheduleNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String rescheduleNotAllowed(BookingRescheduleService.RescheduleNotAllowedException e) {
        return e.getMessage();
    }
```

- [ ] **Step 5: Run; verify all 4 PASS.** Clean containers.

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingRescheduleControllerTest`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/BookingRescheduleController.java src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java src/test/java/hu/deposoft/webshop/api/admin/BookingRescheduleControllerTest.java
git commit -m "feat(2b-3): POST /api/admin/order-items/{id}/reschedule + 409 mapping"
```

---

## Task 4: Expose `unitGrossHuf` in the attendee view

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopBookingView.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopService.java` (the `bookings` mapping)
- Modify: `src/test/java/hu/deposoft/webshop/application/workshop/WorkshopBookingsViewTest.java`

Context: `WorkshopBookingView` currently ends with `int seats, long orderItemId, int cancelledSeats, long lineGrossHuf`. `WorkshopService.bookings` builds it from `oi` (`OrderItem`); `oi.getUnitGrossHuf()` returns long.

- [ ] **Step 1: Add the failing assertion to `WorkshopBookingsViewTest.java`**

In the existing test `bookingViewExposesLineIdGrossAndCancelledSeats`, add after the existing asserts:

```java
        assertThat(view.unitGrossHuf()).isEqualTo(line.getUnitGrossHuf());
```

- [ ] **Step 2: Run; verify FAIL** — `unitGrossHuf()` doesn't exist (compile error).

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=WorkshopBookingsViewTest`

- [ ] **Step 3: Add `long unitGrossHuf` to `WorkshopBookingView`** (append as the last record component):

```java
public record WorkshopBookingView(
        Long sessionId,
        OffsetDateTime sessionStartAt,
        String sessionSku,
        String orderNumber,
        OrderStatus orderStatus,
        String customerName,
        String email,
        String phone,
        int seats,
        long orderItemId,
        int cancelledSeats,
        long lineGrossHuf,
        long unitGrossHuf) {
}
```

- [ ] **Step 4: Map it in `WorkshopService.bookings`** — append `oi.getUnitGrossHuf()` as the last constructor argument (after `oi.getLineGrossHuf()`):

```java
                            oi.getQuantity(),
                            oi.getId(),
                            oi.getCancelledQuantity(),
                            oi.getLineGrossHuf(),
                            oi.getUnitGrossHuf());
```

- [ ] **Step 5: Run; verify PASS.** Then run the FULL backend suite (slice gate before the SPA):

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify`
Expected: BUILD SUCCESS (all tests + ArchUnit). Clean containers: `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/workshop/WorkshopBookingView.java src/main/java/hu/deposoft/webshop/application/workshop/WorkshopService.java src/test/java/hu/deposoft/webshop/application/workshop/WorkshopBookingsViewTest.java
git commit -m "feat(2b-3): expose unitGrossHuf in attendee view for same-price targets"
```

---

## Task 5: Admin SPA — "Áthelyezés" modal

**Files:**
- Modify: `admin-ui/src/types.ts` (`WorkshopBooking`)
- Modify: `admin-ui/src/api/orders.ts` (add `rescheduleBooking`)
- Modify: `admin-ui/src/pages/workshops/edit.tsx`

Gate: `cd admin-ui && npm run build`.

Context (`edit.tsx`): the `Sessions` component holds `sessions` (`WorkshopSession[]` with `id`, `startAt`, `capacity`, `priceHuf`, `sku`), `bookingsBySession` (Map<number, WorkshopBooking[]>), `bookedSeats(sessionId)` (effective seats), `reload()`, `message` from `App.useApp()`, and renders the attendee sub-table where 2b-2 added a "Művelet" column with the "Lemondás" Popconfirm / "Lemondva" tag. `WorkshopSession` TS type has `id, startAt, capacity, priceHuf, sku`. `huf` is a module-level formatter. antd `Modal`, `Select` are available from the `antd` package.

- [ ] **Step 1: Add `unitGrossHuf` to `WorkshopBooking` in `admin-ui/src/types.ts`**

Append inside the interface (after `lineGrossHuf: number;`):

```typescript
  unitGrossHuf: number;
```

- [ ] **Step 2: Add `rescheduleBooking` to `admin-ui/src/api/orders.ts`** (append at end):

```typescript
export async function rescheduleBooking(
  itemId: number,
  targetSessionId: number,
): Promise<Response> {
  return apiFetch(`${API_BASE}/order-items/${itemId}/reschedule`, {
    method: "POST",
    body: JSON.stringify({ targetSessionId }),
  });
}
```

- [ ] **Step 3: Wire the "Áthelyezés" action in `admin-ui/src/pages/workshops/edit.tsx`**

(a) Extend the antd import to include `Modal` and `Select` (add them to the existing `import { ... } from "antd";` list).

(b) Extend the orders import:
```typescript
import { STATUS_COLORS, STATUS_LABELS, cancelBooking, rescheduleBooking } from "../../api/orders";
```

(c) Inside `Sessions`, add modal state + a handler (near `cancelBookingLine`):
```typescript
  const [moving, setMoving] = useState<WorkshopBooking | null>(null);
  const [moveTarget, setMoveTarget] = useState<number | null>(null);

  const submitReschedule = async () => {
    if (moving == null || moveTarget == null) return;
    const res = await rescheduleBooking(moving.orderItemId, moveTarget);
    if (res.ok) {
      message.success("Jelentkező áthelyezve");
      setMoving(null);
      setMoveTarget(null);
      await reload();
    } else {
      const text = await res.text();
      message.error(text || `Nem sikerült (${res.status})`);
    }
  };
```
(`useState` is already imported in this file.)

(d) In the "Művelet" column's non-cancelled branch (currently just the "Lemondás" Popconfirm), wrap the two actions in a `<Space>` (import `Space` from antd) and add an "Áthelyezés" button before/after the Lemondás one:
```tsx
                        <Button size="small" onClick={() => { setMoving(r); setMoveTarget(null); }}>
                          Áthelyezés
                        </Button>
```
So the non-cancelled render returns `<Space>{athelyezesButton}{lemondasPopconfirm}</Space>`. Keep the "Lemondva" tag branch unchanged.

(e) Add the reschedule Modal once, after the sessions `<Table>` (inside the `Card`, sibling to the add-session `<Form>`). It lists the workshop's OTHER sessions; same-price + has-free-seat sessions are selectable, others disabled:
```tsx
      <Modal
        title="Jelentkező áthelyezése"
        open={moving != null}
        onCancel={() => setMoving(null)}
        onOk={submitReschedule}
        okText="Áthelyezés"
        cancelText="Mégse"
        okButtonProps={{ disabled: moveTarget == null }}
      >
        {moving && (
          <Select
            style={{ width: "100%" }}
            placeholder="Válassz cél-alkalmat"
            value={moveTarget ?? undefined}
            onChange={(v) => setMoveTarget(v)}
            options={sessions
              .filter((s) => s.id !== moving.sessionId)
              .map((s) => {
                const free = s.capacity - bookedSeats(s.id);
                const samePrice = (s.priceHuf ?? 0) === moving.unitGrossHuf;
                return {
                  value: s.id,
                  disabled: !samePrice || free < moving.seats - moving.cancelledSeats,
                  label: `${dayjs(s.startAt).format("YYYY. MM. DD. HH:mm")} — ${bookedSeats(
                    s.id,
                  )}/${s.capacity} — ${huf(s.priceHuf)}${samePrice ? "" : " (eltérő ár)"}`,
                };
              })}
          />
        )}
      </Modal>
```
(`dayjs` is already imported in this file.)

- [ ] **Step 4: Build** — `cd /Users/zolika/Work/Claude/website/admin-ui && npm run build` → clean (no TS errors). Fix any missing import (`Modal`, `Select`, `Space`) and rebuild until green.

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/types.ts admin-ui/src/api/orders.ts admin-ui/src/pages/workshops/edit.tsx
git commit -m "feat(2b-3): Áthelyezés modal in attendee table (same-price targets)"
```

(Only these 3 source files; the `static/admin` build output is gitignored — confirm with `git status`.)

---

## Final verification

- [ ] **Backend:** `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify` → BUILD SUCCESS (all tests + ArchUnit). Clean containers afterward.
- [ ] **Frontend:** `cd admin-ui && npm run build` → success.
- [ ] Run the whole-slice final code review before finishing the branch.

---

## Self-Review notes (spec coverage)

- Spec §1 (domain `moveToSeat`) → Task 1. §2 (`BookingRescheduleService` + guards) → Task 2. §3 (API + UI) → Task 3 (endpoint) + Task 5 (UI). §4 (`unitGrossHuf` DTO) → Task 4. §5 (testing) → tests across Tasks 1–4 + SPA build in Task 5.
- Capacity check uses `availability.availableQty(targetSeat, NO_CART) >= needed` rather than `canFulfil` — deliberate: `canFulfil` rejects `LOW_STOCK`, which would wrongly block a session with 1–3 free seats. `availableQty` returns 0 for an already-started session, so a past target is rejected too.
- Names consistent across tasks: `moveToSeat(Variant)`, `BookingRescheduleService.reschedule(Long, Long)`, `RescheduleNotAllowedException`, `RescheduleRequest{targetSessionId}`, `RescheduleResult{orderItemId, sessionId}`, `rescheduleBooking(itemId, targetSessionId)`, view field `unitGrossHuf`.
- No money path: no gateway, no `InvoicingService`, no `Payment`/`Order` status change — matches the same-price decision.
