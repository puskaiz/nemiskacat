# Workshop Booking Cancel (2b-2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cancel a single workshop booking (an `OrderItem` line) of a paid order — free its seats, partially refund the line, and issue a line-scoped per-source credit note — without touching the rest of the order.

**Architecture:** A new `cancelledQuantity` column on `order_item` frees seats by changing the availability sum to `SUM(quantity − cancelledQuantity)`. The 2b-1 credit-note record is reworked to link credit notes to an `order_item` via partial unique indexes (NULL = forward/whole-order, set = line-scoped). A new line-generic `BookingCancellationService` orchestrates the partial gateway refund + line cancel + gated credit note, exposed at `POST /api/admin/order-items/{itemId}/cancel` and wired into the workshop attendee table.

**Tech Stack:** Spring Boot 4.1, Java 21, JPA/Hibernate (`ddl-auto: validate`), Flyway (PostgreSQL), Lombok; Refine + TypeScript + Ant Design SPA in `admin-ui/`.

**Build/test commands (this repo):**
- Backend: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=<ClassName>` (single class) or `... verify` (full). After backend runs, clean leftover containers: `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.
- Frontend gate: `cd admin-ui && npm run build`.
- Do **not** stop/restart the app on port 8085.

**Code root:** `/Users/zolika/Work/Claude/website`. Spec: `docs/superpowers/specs/2026-06-15-workshop-booking-cancel-2b2-design.md`.

---

## File Structure

**Create:**
- `src/main/resources/db/migration/V16__order_item_cancelled_quantity.sql` — line-cancel column.
- `src/main/resources/db/migration/V17__invoice_line_scoped_credit_note.sql` — invoice `order_item_id` + partial unique indexes.
- `src/main/java/hu/deposoft/webshop/domain/order/OrderItemRepository.java` — load a single line by id.
- `src/main/java/hu/deposoft/webshop/application/order/BookingCancellationService.java` — orchestrator + two exceptions.
- `src/main/java/hu/deposoft/webshop/api/admin/BookingCancellationController.java` — `POST /api/admin/order-items/{itemId}/cancel` + `BookingCancelResult`.
- `src/test/java/hu/deposoft/webshop/application/order/BookingCancellationServiceTest.java`
- `src/test/java/hu/deposoft/webshop/api/admin/BookingCancellationControllerTest.java`

**Modify:**
- `domain/order/OrderItem.java` — `cancelledQuantity` field + `cancelWholeLine()`.
- `domain/order/OrderRepository.java` — `orderedQuantitySince` query (subtract cancelled); `findWorkshopBookings` unchanged.
- `domain/order/Invoice.java` — nullable `orderItem` + `creditNote(order, source, orderItem)` factory; drop `@Table` uniqueConstraints.
- `domain/order/InvoiceRepository.java` — `findByOrderItemAndType`.
- `application/invoicing/InvoicingService.java` — `creditNoteForLine(OrderItem)` + `retryFailed()` split.
- `api/admin/AdminExceptionHandler.java` — map the two new exceptions.
- `application/workshop/WorkshopBookingView.java` — add `orderItemId`, `cancelledSeats`, `lineGrossHuf`.
- `application/workshop/WorkshopService.java` — `bookings()` maps the new fields.
- `admin-ui/src/types.ts` — `WorkshopBooking` new fields.
- `admin-ui/src/api/orders.ts` — `cancelBooking(itemId)`.
- `admin-ui/src/pages/workshops/edit.tsx` — "Lemondás" column + effective seats.
- `src/test/.../InvoicingServiceTest.java` — extend (line-scoped credit note).

Existing test for cross-checks: `application/order/RefundServiceTest.java`, `application/invoicing/InvoicingServiceTest.java`, `api/admin/OrderAdminControllerTest.java`.

---

## Task 1: `cancelledQuantity` on `OrderItem` + availability

**Files:**
- Create: `src/main/resources/db/migration/V16__order_item_cancelled_quantity.sql`
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/OrderItem.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java:29-37` (the `orderedQuantitySince` query)
- Test: `src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java` (create)

- [ ] **Step 1: Write the failing unit test for `cancelWholeLine`**

Create `src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java`:

```java
package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    private OrderItem line(int qty) {
        return OrderItem.create(null, null, "Workshop", "Alkalom", "WS-1",
                15_000L, 27, qty, InvoiceSource.BILLINGO);
    }

    @Test
    void cancelWholeLineSetsCancelledQuantityToQuantity() {
        OrderItem item = line(3);
        item.cancelWholeLine();
        assertThat(item.getCancelledQuantity()).isEqualTo(3);
    }

    @Test
    void newLineHasZeroCancelledQuantity() {
        assertThat(line(2).getCancelledQuantity()).isZero();
    }

    @Test
    void cancellingAnAlreadyCancelledLineThrows() {
        OrderItem item = line(1);
        item.cancelWholeLine();
        assertThatThrownBy(item::cancelWholeLine).isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run it; verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=OrderItemTest`
Expected: FAIL — `cancelWholeLine()` / `getCancelledQuantity()` do not exist (compile error).

- [ ] **Step 3: Add the field + method to `OrderItem`**

In `src/main/java/hu/deposoft/webshop/domain/order/OrderItem.java`, add the field after `quantity` (keep `@Getter` — it generates `getCancelledQuantity()`):

```java
    @Column(name = "cancelled_quantity", nullable = false)
    private int cancelledQuantity = 0;
```

Add this method to the class body (after the `create` factory):

```java
    /** Cancel the whole line (2b-2). The seat frees up via the availability sum. */
    public void cancelWholeLine() {
        if (cancelledQuantity >= quantity) {
            throw new IllegalStateException("Line " + id + " is already fully cancelled");
        }
        this.cancelledQuantity = quantity;
    }
```

- [ ] **Step 4: Add the migration**

Create `src/main/resources/db/migration/V16__order_item_cancelled_quantity.sql`:

```sql
-- 2b-2: per-line cancellation. A cancelled booking frees its seats by being
-- subtracted from the availability sum, while the original quantity (history) stays.
ALTER TABLE order_item ADD COLUMN cancelled_quantity INT NOT NULL DEFAULT 0
    CHECK (cancelled_quantity >= 0 AND cancelled_quantity <= quantity);
```

- [ ] **Step 5: Run the unit test; verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=OrderItemTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Write the failing availability test**

Add to `src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java` is not suitable (it's a pure unit test). Instead create `src/test/java/hu/deposoft/webshop/domain/order/AvailabilityCancelTest.java`:

```java
package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class AvailabilityCancelTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;
    @Autowired OrderRepository orders;

    private Long variantId;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint)));
    }

    @Test
    void cancelledQuantityIsExcludedFromOrderedSum() {
        String token = cart.addItem(null, "FES-1", 2).token();
        var order = checkout.placeOrder(token, new PlaceOrderCommand("avail", "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        OrderItem line = order.getItems().get(0);
        variantId = line.getVariant().getId();
        OffsetDateTime epoch = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        assertThat(orders.orderedQuantitySince(variantId, epoch, OrderStatus.CANCELLED)).isEqualTo(2);

        line.cancelWholeLine();
        orders.save(order);

        assertThat(orders.orderedQuantitySince(variantId, epoch, OrderStatus.CANCELLED)).isZero();
    }
}
```

- [ ] **Step 7: Run it; verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=AvailabilityCancelTest`
Expected: FAIL — after cancel the sum is still 2 (the query does not subtract cancelled yet).

- [ ] **Step 8: Update the availability query**

In `src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java`, change the `orderedQuantitySince` `@Query` body from `SUM(oi.quantity)` to subtract the cancelled quantity:

```java
    /** Ledger input: quantity ordered for a variant since the last stock sync (cancelled excluded). */
    @Query("""
            SELECT COALESCE(SUM(oi.quantity - oi.cancelledQuantity), 0) FROM OrderItem oi
            WHERE oi.variant.id = :variantId
              AND oi.order.status <> :excluded
              AND oi.order.createdAt > :since
            """)
    int orderedQuantitySince(@Param("variantId") Long variantId,
                             @Param("since") OffsetDateTime since,
                             @Param("excluded") OrderStatus excluded);
```

- [ ] **Step 9: Run it; verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=AvailabilityCancelTest`
Expected: PASS. Then clean containers: `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/OrderItem.java \
        src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java \
        src/main/resources/db/migration/V16__order_item_cancelled_quantity.sql \
        src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java \
        src/test/java/hu/deposoft/webshop/domain/order/AvailabilityCancelTest.java
git commit -m "feat(2b-2): per-line cancelledQuantity frees workshop seats"
```

---

## Task 2: Line-scoped credit-note record model

**Files:**
- Create: `src/main/resources/db/migration/V17__invoice_line_scoped_credit_note.sql`
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/Invoice.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/InvoiceRepository.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/order/InvoiceLineScopedTest.java` (create)

Context: V14 created `ALTER TABLE invoice ADD CONSTRAINT invoice_order_source_type_key UNIQUE (order_id, source, type)`. We drop that named constraint and replace it with two partial unique indexes. JPA's `@Table(uniqueConstraints=...)` cannot express a filtered (`WHERE`) unique index, so we remove it from the entity; `ddl-auto: validate` validates tables/columns, not indexes, so this is safe.

- [ ] **Step 1: Write the failing repository test**

Create `src/test/java/hu/deposoft/webshop/domain/order/InvoiceLineScopedTest.java`:

```java
package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class InvoiceLineScopedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired InvoiceRepository invoices;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;
    @Autowired WorkshopService workshops;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint)));
    }

    @Test
    void twoLineScopedCreditNotesCoexistWithWholeOrderOne() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS", "ws-x", "leiras", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-A");
        workshops.addSession(ws, now.plusDays(8), 5, 15_000L, "WS-B");
        String token = cart.addItem(null, "WS-A", 1).token();
        cart.addItem(token, "WS-B", 1);
        var order = checkout.placeOrder(token, new PlaceOrderCommand("ls", "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));

        OrderItem lineA = order.getItems().get(0);
        OrderItem lineB = order.getItems().get(1);

        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO));            // whole-order, order_item NULL
        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO, lineA));     // line-scoped A
        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO, lineB));     // line-scoped B
        invoices.flush();

        assertThat(invoices.findByOrderItemAndType(lineA, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findByOrderItemAndType(lineB, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE))
                .isPresent(); // the whole-order one (order_item NULL)
    }
}
```

- [ ] **Step 2: Run it; verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=InvoiceLineScopedTest`
Expected: FAIL — `Invoice.creditNote(order, source, line)` and `findByOrderItemAndType` do not exist (compile error). (Also note `findByOrderAndSourceAndType` currently returns a single `Optional`; with three CREDIT_NOTE rows sharing `(order, source, type)` it would break — Step 4 makes the whole-order one unique by `order_item_id IS NULL` and Step 5 narrows the lookup.)

- [ ] **Step 3: Add the `orderItem` association + factory to `Invoice`**

In `src/main/java/hu/deposoft/webshop/domain/order/Invoice.java`:

Remove the `uniqueConstraints` from the `@Table` annotation (replaced by partial indexes in SQL) and the now-unused `UniqueConstraint` import. Change:

```java
@Table(name = "invoice", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "source", "type"}))
```
to:
```java
@Table(name = "invoice")
```
and delete the line `import jakarta.persistence.UniqueConstraint;`.

Add the association after the `order` field:

```java
    @ManyToOne
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;
```

Add the line-scoped factory right after the existing `creditNote(Order, InvoiceSource)`:

```java
    public static Invoice creditNote(Order order, InvoiceSource source, OrderItem orderItem) {
        Invoice i = creditNote(order, source);
        i.orderItem = orderItem;
        return i;
    }
```

- [ ] **Step 4: Add the migration**

Create `src/main/resources/db/migration/V17__invoice_line_scoped_credit_note.sql`:

```sql
-- 2b-2: a line-scoped credit note links to the cancelled order_item. The 2b-1
-- one-row-per-(order,source,type) rule only applies to whole-order documents
-- (order_item_id NULL); line-scoped credit notes are unique per (order_item, type).
ALTER TABLE invoice ADD COLUMN order_item_id BIGINT REFERENCES order_item (id);

ALTER TABLE invoice DROP CONSTRAINT invoice_order_source_type_key;

CREATE UNIQUE INDEX ux_invoice_order_source_type
    ON invoice (order_id, source, type) WHERE order_item_id IS NULL;

CREATE UNIQUE INDEX ux_invoice_order_item_type
    ON invoice (order_item_id, type) WHERE order_item_id IS NOT NULL;
```

- [ ] **Step 5: Add the repository lookup**

In `src/main/java/hu/deposoft/webshop/domain/order/InvoiceRepository.java`, add (and keep the existing methods):

```java
    Optional<Invoice> findByOrderItemAndType(OrderItem orderItem, InvoiceType type);
```

Also change `findByOrderAndSourceAndType` so the whole-order lookup ignores line-scoped rows:

```java
    Optional<Invoice> findByOrderAndSourceAndTypeAndOrderItemIsNull(Order order, InvoiceSource source, InvoiceType type);
```

Keep the original `findByOrderAndSourceAndType` too (still used by `invoice()` for forward INVOICE rows, which are always `order_item_id NULL` — but to be unambiguous, callers in Task 3 use the `...AndOrderItemIsNull` variant for credit-note/original lookups).

- [ ] **Step 6: Fix the test's whole-order lookup**

In `InvoiceLineScopedTest`, change the final assertion to the null-scoped finder:

```java
        assertThat(invoices.findByOrderAndSourceAndTypeAndOrderItemIsNull(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE))
                .isPresent(); // the whole-order one (order_item NULL)
```

- [ ] **Step 7: Run it; verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=InvoiceLineScopedTest`
Expected: PASS. Clean containers afterward.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/Invoice.java \
        src/main/java/hu/deposoft/webshop/domain/order/InvoiceRepository.java \
        src/main/resources/db/migration/V17__invoice_line_scoped_credit_note.sql \
        src/test/java/hu/deposoft/webshop/domain/order/InvoiceLineScopedTest.java
git commit -m "feat(2b-2): line-scoped credit-note record model (order_item link + partial indexes)"
```

---

## Task 3: `InvoicingService.creditNoteForLine` + `retryFailed` split

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/invoicing/InvoicingService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/invoicing/InvoicingServiceTest.java` (extend)

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/hu/deposoft/webshop/application/invoicing/InvoicingServiceTest.java` (the class already autowires `invoicing`, `invoices`, has `mixedOrder(key)`, and mocks `issuer`, `kulcsSink`; add the `CreditNoteIssuer` mock and `OrderItem`/`InvoiceSource` imports if missing):

```java
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    CreditNoteIssuer creditNoteIssuer;

    @Test
    void creditNoteForLineRecordsLineScopedRowForBillingoWorkshopLine() {
        when(creditNoteIssuer.creditNote(any(), any(), any()))
                .thenReturn(new InvoiceIssuer.InvoiceResult("ST-1", "https://billingo/st/1", "99"));
        Order order = mixedOrder("cn-line");
        hu.deposoft.webshop.domain.order.OrderItem wsLine = order.getItems().stream()
                .filter(i -> i.getInvoiceSource() == InvoiceSource.BILLINGO).findFirst().orElseThrow();

        invoicing.creditNoteForLine(wsLine);

        var note = invoices.findByOrderItemAndType(wsLine, InvoiceType.CREDIT_NOTE).orElseThrow();
        assertThat(note.getState()).isEqualTo(InvoiceState.ISSUED);
        assertThat(note.getOrderItem().getId()).isEqualTo(wsLine.getId());
        verify(creditNoteIssuer, times(1)).creditNote(any(), any(), any());
    }

    @Test
    void creditNoteForLineIsIdempotent() {
        when(creditNoteIssuer.creditNote(any(), any(), any()))
                .thenReturn(new InvoiceIssuer.InvoiceResult("ST-2", "u", "1"));
        Order order = mixedOrder("cn-idem");
        hu.deposoft.webshop.domain.order.OrderItem wsLine = order.getItems().stream()
                .filter(i -> i.getInvoiceSource() == InvoiceSource.BILLINGO).findFirst().orElseThrow();

        invoicing.creditNoteForLine(wsLine);
        invoicing.creditNoteForLine(wsLine);

        verify(creditNoteIssuer, times(1)).creditNote(any(), any(), any());
    }
```

- [ ] **Step 2: Run it; verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=InvoicingServiceTest`
Expected: FAIL — `creditNoteForLine` does not exist (compile error).

- [ ] **Step 3: Implement `creditNoteForLine`**

In `src/main/java/hu/deposoft/webshop/application/invoicing/InvoicingService.java`, add this method (after `creditNote(Long orderId)`). Note it uses the null-scoped finder for the original external id:

```java
    /** Storno of a single cancelled line (2b-2), scoped to that order_item. Idempotent + retryable. */
    @Transactional
    public void creditNoteForLine(OrderItem line) {
        Order order = line.getOrder();
        InvoiceSource source = line.getInvoiceSource();
        Invoice note = invoices.findByOrderItemAndType(line, InvoiceType.CREDIT_NOTE).orElse(null);
        if (note != null && note.isSuccessful()) {
            return;
        }
        if (note == null) {
            note = invoices.save(Invoice.creditNote(order, source, line));
        }
        try {
            switch (source) {
                case BILLINGO -> {
                    String originalExternalId = invoices
                            .findByOrderAndSourceAndTypeAndOrderItemIsNull(order, source, InvoiceType.INVOICE)
                            .map(Invoice::getExternalId).orElse(null);
                    InvoiceIssuer.InvoiceResult r = creditNoteIssuer.creditNote(order, List.of(line), originalExternalId);
                    note.recordIssued(r.invoiceNumber(), r.publicUrl(), r.externalId());
                    log.info("Order {} line {} credit-noted via Billingo: {}", order.orderNumber(), line.getId(), r.invoiceNumber());
                }
                case KULCS_SOFT -> {
                    kulcsSink.pushCreditNote(order, List.of(line));
                    note.recordPushed();
                    log.info("Order {} line {} credit pushed to Kulcs-Soft", order.orderNumber(), line.getId());
                }
                default -> throw new IllegalStateException("Unhandled invoice source: " + source);
            }
        } catch (RuntimeException e) {
            note.recordFailed(e.getMessage());
            log.error("Line credit note failed for order {} line {}: {}", order.orderNumber(), line.getId(), e.getMessage());
        }
    }
```

Add the import if not present: `import hu.deposoft.webshop.domain.order.OrderItem;` (already imported in this file).

- [ ] **Step 3b: Fix the whole-order `creditNote(Long)` lookup**

After V17, a `(order, BILLINGO, CREDIT_NOTE)` query matches the whole-order row **and** any line-scoped rows → `IncorrectResultSizeDataAccessException`. In `creditNote(Long orderId)`, change the existing CREDIT_NOTE lookup to the null-scoped finder. Find:

```java
            Invoice note = invoices.findByOrderAndSourceAndType(order, source, InvoiceType.CREDIT_NOTE).orElse(null);
```
and replace with:
```java
            Invoice note = invoices.findByOrderAndSourceAndTypeAndOrderItemIsNull(order, source, InvoiceType.CREDIT_NOTE).orElse(null);
```

(The `originalExternalId` lookup in `creditNote` uses `INVOICE`, whose rows are always `order_item_id NULL`, so it is already unambiguous — leave it.)

- [ ] **Step 4: Split `retryFailed` for line-scoped credit notes**

Replace the body of `retryFailed()` in `InvoicingService.java` with:

```java
    /** Retry any (order, type) left in FAILED state (scheduled). Line-scoped credit notes retry per line. */
    @Transactional
    public int retryFailed() {
        List<Invoice> failed = invoices.findByState(InvoiceState.FAILED);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.INVOICE)
                .map(i -> i.getOrder().getId()).distinct()
                .forEach(this::invoice);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE && i.getOrderItem() == null)
                .map(i -> i.getOrder().getId()).distinct()
                .forEach(this::creditNote);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE && i.getOrderItem() != null)
                .forEach(i -> creditNoteForLine(i.getOrderItem()));
        return (int) failed.stream()
                .map(i -> i.getOrder().getId() + ":" + i.getType()
                        + ":" + (i.getOrderItem() == null ? "-" : i.getOrderItem().getId()))
                .distinct().count();
    }
```

- [ ] **Step 5: Run the test; verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=InvoicingServiceTest`
Expected: PASS (existing tests + the two new ones). Clean containers afterward.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/invoicing/InvoicingService.java \
        src/test/java/hu/deposoft/webshop/application/invoicing/InvoicingServiceTest.java
git commit -m "feat(2b-2): InvoicingService.creditNoteForLine + line-aware retryFailed"
```

---

## Task 4: `BookingCancellationService`

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/domain/order/OrderItemRepository.java`
- Create: `src/main/java/hu/deposoft/webshop/application/order/BookingCancellationService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/order/BookingCancellationServiceTest.java`

- [ ] **Step 1: Create the `OrderItemRepository`**

Create `src/main/java/hu/deposoft/webshop/domain/order/OrderItemRepository.java`:

```java
package hu.deposoft.webshop.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
```

- [ ] **Step 2: Write the failing service test**

Create `src/test/java/hu/deposoft/webshop/application/order/BookingCancellationServiceTest.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.Invoice;
import hu.deposoft.webshop.domain.order.InvoiceRepository;
import hu.deposoft.webshop.domain.order.InvoiceType;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class BookingCancellationServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BookingCancellationService bookings;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired InvoiceRepository invoices;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;

    @MockitoBean PaymentGateway gateway;

    /** A paid order: 1 workshop (BILLINGO) line + 1 second workshop line, CONFIRMED payment, driven to status. */
    private Order paidWorkshopOrder(String key, OrderStatus status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "leiras", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WSA-" + key);
        workshops.addSession(ws, now.plusDays(8), 5, 12_000L, "WSB-" + key);
        String token = cart.addItem(null, "WSA-" + key, 1).token();
        cart.addItem(token, "WSB-" + key, 1);
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        if (status == OrderStatus.PACKING) order.transitionTo(OrderStatus.PACKING);
        return orders.save(order);
    }

    private OrderItem firstLine(Order order) {
        return order.getItems().get(0);
    }

    @Test
    void cancelsLinePartialRefundFreesSeatKeepsOrder() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidWorkshopOrder("ok", OrderStatus.PAID);
        OrderItem line = firstLine(order);
        long lineGross = line.getLineGrossHuf();

        bookings.cancelBooking(line.getId());

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        OrderItem cancelled = reloaded.getItems().stream().filter(i -> i.getId().equals(line.getId())).findFirst().orElseThrow();
        OrderItem other = reloaded.getItems().stream().filter(i -> !i.getId().equals(line.getId())).findFirst().orElseThrow();

        verify(gateway, times(1)).refund(eq("PAY-ok"), eq(lineGross));     // partial: the line gross
        assertThat(cancelled.getCancelledQuantity()).isEqualTo(cancelled.getQuantity());
        assertThat(other.getCancelledQuantity()).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);       // order unchanged
        assertThat(payments.findFirstByOrderOrderByIdDesc(reloaded).orElseThrow().getState())
                .isEqualTo(Payment.State.CONFIRMED);                        // not REVERSED
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order_item", String.valueOf(line.getId())))
                .anySatisfy(e -> assertThat(e.getAction()).isEqualTo("BOOKING_CANCELLED"));
        assertThat(invoices.findByOrderItemAndType(cancelled, InvoiceType.CREDIT_NOTE)).isPresent();
    }

    @Test
    void rejectsCancelOnNewOrder() {
        Order order = paidWorkshopOrder("new", OrderStatus.PAID);
        // drive back is not possible; instead use a fresh NEW order
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS new2", "ws-new2", "l", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WSN");
        String token = cart.addItem(null, "WSN", 1).token();
        Order fresh = checkout.placeOrder(token, new PlaceOrderCommand("new2", "T",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        Long lineId = fresh.getItems().get(0).getId();

        assertThatThrownBy(() -> bookings.cancelBooking(lineId))
                .isInstanceOf(BookingCancellationService.BookingCancelNotAllowedException.class);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void gatewayFailureRollsBack() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(false, "declined"));
        Order order = paidWorkshopOrder("fail", OrderStatus.PAID);
        OrderItem line = firstLine(order);

        assertThatThrownBy(() -> bookings.cancelBooking(line.getId()))
                .isInstanceOf(BookingCancellationService.BookingRefundFailedException.class);

        // re-read in a fresh lookup: nothing committed
        assertThat(orders.findById(order.getId()).orElseThrow().getItems().stream()
                .filter(i -> i.getId().equals(line.getId())).findFirst().orElseThrow()
                .getCancelledQuantity()).isZero();
    }

    @Test
    void cancelIsIdempotent() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidWorkshopOrder("idem", OrderStatus.PAID);
        OrderItem line = firstLine(order);

        bookings.cancelBooking(line.getId());
        bookings.cancelBooking(line.getId()); // already fully cancelled → no-op

        verify(gateway, times(1)).refund(anyString(), anyLong());
    }

    @Test
    void twoLineCancelsProduceTwoCreditNoteRows() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidWorkshopOrder("two", OrderStatus.PAID);
        OrderItem a = order.getItems().get(0);
        OrderItem b = order.getItems().get(1);

        bookings.cancelBooking(a.getId());
        bookings.cancelBooking(b.getId());

        assertThat(invoices.findByOrderItemAndType(a, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findByOrderItemAndType(b, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findAll().stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE && i.getOrderItem() != null)
                .count()).isEqualTo(2);
    }
}
```

- [ ] **Step 3: Run it; verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingCancellationServiceTest`
Expected: FAIL — `BookingCancellationService` does not exist (compile error).

- [ ] **Step 4: Implement `BookingCancellationService`**

Create `src/main/java/hu/deposoft/webshop/application/order/BookingCancellationService.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.invoicing.InvoicingService;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderItemRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancel a single order line (T16 slice 2b-2) — the workshop-booking case: partially
 * refund the line via the gateway, mark the line cancelled (freeing its seats), audit
 * it, and issue a line-scoped per-source credit note. The Payment stays CONFIRMED and
 * the order status is unchanged (other lines may remain). Line-generic; the workshop
 * attendee list is the entry point. Pre-shipment only (PAID/PACKING).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingCancellationService {

    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final InvoicingService invoicing;
    private final AuditService audit;

    /** Policy rejection (wrong order state / no confirmed payment). */
    public static class BookingCancelNotAllowedException extends RuntimeException {
        public BookingCancelNotAllowedException(String message) {
            super(message);
        }
    }

    /** The gateway refused or failed the partial refund. */
    public static class BookingRefundFailedException extends RuntimeException {
        public BookingRefundFailedException(String message) {
            super(message);
        }
    }

    @Transactional
    public void cancelBooking(Long orderItemId) {
        OrderItem line = orderItems.findById(orderItemId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order item " + orderItemId));
        if (line.getCancelledQuantity() >= line.getQuantity()) {
            return; // idempotent — already fully cancelled
        }
        Order order = line.getOrder();
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.PACKING) {
            throw new BookingCancelNotAllowedException(
                    "Only PAID/PACKING orders can have a booking cancelled here; this one is " + status);
        }
        Payment payment = payments.findFirstByOrderOrderByIdDesc(order)
                .orElseThrow(() -> new BookingCancelNotAllowedException("No payment to refund for order " + order.getId()));
        if (payment.getState() != Payment.State.CONFIRMED) {
            throw new BookingCancelNotAllowedException(
                    "Payment for order " + order.getId() + " is " + payment.getState() + ", not CONFIRMED");
        }

        long amount = line.getLineGrossHuf();
        PaymentGateway.RefundResult result;
        try {
            result = gateway.refund(payment.getPayId(), amount);
        } catch (RuntimeException e) {
            throw new BookingRefundFailedException("Refund call failed: " + e.getMessage());
        }
        if (!result.success()) {
            throw new BookingRefundFailedException("Refund declined: " + result.message());
        }

        line.cancelWholeLine();
        audit.record("BOOKING_CANCELLED", "order_item", String.valueOf(orderItemId),
                order.orderNumber() + " " + line.getSku() + " x" + line.getQuantity()
                        + " refunded " + amount + " HUF");
        log.info("Booking line {} of order {} cancelled ({} HUF)", orderItemId, order.orderNumber(), amount);

        invoicing.creditNoteForLine(line); // line-scoped per-source storno (gated; never rolls back the refund)
    }
}
```

- [ ] **Step 5: Run the test; verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingCancellationServiceTest`
Expected: PASS (5 tests). Clean containers afterward.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/OrderItemRepository.java \
        src/main/java/hu/deposoft/webshop/application/order/BookingCancellationService.java \
        src/test/java/hu/deposoft/webshop/application/order/BookingCancellationServiceTest.java
git commit -m "feat(2b-2): BookingCancellationService — line cancel + partial refund + credit note"
```

---

## Task 5: Admin API endpoint + exception mapping

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/api/admin/BookingCancellationController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/BookingCancellationControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `src/test/java/hu/deposoft/webshop/api/admin/BookingCancellationControllerTest.java`:

```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
class BookingCancellationControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;

    @MockitoBean PaymentGateway gateway;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Order paidOrder(String key, boolean confirm) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "l", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-" + key);
        String token = cart.addItem(null, "WS-" + key, 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "T",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        if (confirm) {
            p.markState(Payment.State.CONFIRMED, "ok");
            order.transitionTo(OrderStatus.PAID);
        }
        return orders.save(order);
    }

    @Test
    void cancelReturns200WithResult() throws Exception {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidOrder("c200", true);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/cancel", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(lineId))
                .andExpect(jsonPath("$.refundedHuf").value(15_000));
    }

    @Test
    void cancelOnUnpaidOrderReturns409() throws Exception {
        Order order = paidOrder("c409", false); // still NEW, no confirmed payment
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/cancel", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelUnknownItemReturns404() throws Exception {
        mvc.perform(post("/api/admin/order-items/{id}/cancel", 999999L)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelWithoutAdminRoleReturns403() throws Exception {
        Order order = paidOrder("c403", true);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/cancel", lineId)
                        .with(user("user").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run it; verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingCancellationControllerTest`
Expected: FAIL — no controller mapped at `/api/admin/order-items/{id}/cancel` (404 for the 200 test).

- [ ] **Step 3: Create the controller**

Create `src/main/java/hu/deposoft/webshop/api/admin/BookingCancellationController.java`:

```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.order.BookingCancellationService;
import hu.deposoft.webshop.domain.order.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Line-level booking cancel for the admin SPA (2b-2). ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/order-items")
@RequiredArgsConstructor
public class BookingCancellationController {

    private final BookingCancellationService bookings;
    private final OrderItemRepository orderItems;

    public record BookingCancelResult(long orderItemId, long refundedHuf) {
    }

    @PostMapping("/{itemId}/cancel")
    public BookingCancelResult cancel(@PathVariable Long itemId) {
        long refunded = orderItems.findById(itemId).map(i -> i.getLineGrossHuf()).orElse(0L);
        bookings.cancelBooking(itemId);
        return new BookingCancelResult(itemId, refunded);
    }
}
```

Note: the refund amount is read before the cancel (the line gross is unchanged by cancellation — `cancelWholeLine` only sets `cancelledQuantity`). If the item is missing, `cancelBooking` throws `NotFoundException` → 404 before the result is used.

- [ ] **Step 4: Map the two new exceptions**

In `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`, add the import:

```java
import hu.deposoft.webshop.application.order.BookingCancellationService;
```

and add these two handlers (after the `RefundService` handlers):

```java
    @ExceptionHandler(BookingCancellationService.BookingCancelNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String bookingCancelNotAllowed(BookingCancellationService.BookingCancelNotAllowedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(BookingCancellationService.BookingRefundFailedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String bookingRefundFailed(BookingCancellationService.BookingRefundFailedException e) {
        return e.getMessage();
    }
```

- [ ] **Step 5: Run the test; verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=BookingCancellationControllerTest`
Expected: PASS (4 tests). Clean containers afterward.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/BookingCancellationController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/test/java/hu/deposoft/webshop/api/admin/BookingCancellationControllerTest.java
git commit -m "feat(2b-2): POST /api/admin/order-items/{id}/cancel + 409/502 mapping"
```

---

## Task 6: Expose booking line id + cancellation in the attendee view

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopBookingView.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopService.java:119-140` (the `bookings` mapping)
- Test: `src/test/java/hu/deposoft/webshop/application/workshop/WorkshopBookingsViewTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/hu/deposoft/webshop/application/workshop/WorkshopBookingsViewTest.java`:

```java
package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
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

@SpringBootTest
@Testcontainers
@Transactional
class WorkshopBookingsViewTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WorkshopService workshops;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired OrderRepository orders;

    @Test
    void bookingViewExposesLineIdGrossAndCancelledSeats() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS", "ws-v", "l", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-V");
        String token = cart.addItem(null, "WS-V", 2).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand("v", "T",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        OrderItem line = order.getItems().get(0);
        line.cancelWholeLine();
        orders.save(order);

        var view = workshops.bookings(ws.getId()).get(0);
        assertThat(view.orderItemId()).isEqualTo(line.getId());
        assertThat(view.seats()).isEqualTo(2);
        assertThat(view.cancelledSeats()).isEqualTo(2);
        assertThat(view.lineGrossHuf()).isEqualTo(line.getLineGrossHuf());
    }
}
```

- [ ] **Step 2: Run it; verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=WorkshopBookingsViewTest`
Expected: FAIL — `orderItemId()`, `cancelledSeats()`, `lineGrossHuf()` do not exist (compile error).

- [ ] **Step 3: Add the fields to `WorkshopBookingView`**

Replace the record in `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopBookingView.java` with (keep the package + javadoc + imports):

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
        long lineGrossHuf) {
}
```

- [ ] **Step 4: Map the new fields in `WorkshopService.bookings`**

In `src/main/java/hu/deposoft/webshop/application/workshop/WorkshopService.java`, in the `bookings` method, extend the `new WorkshopBookingView(...)` constructor call to pass the three new values:

```java
                    return new WorkshopBookingView(
                            session != null ? session.getId() : null,
                            session != null ? session.getStartAt() : null,
                            oi.getSku(),
                            order.orderNumber(),
                            order.getStatus(),
                            order.getCustomerName(),
                            order.getEmail(),
                            order.getPhone(),
                            oi.getQuantity(),
                            oi.getId(),
                            oi.getCancelledQuantity(),
                            oi.getLineGrossHuf());
```

- [ ] **Step 5: Run the test; verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true test -Dtest=WorkshopBookingsViewTest`
Expected: PASS. Clean containers afterward.

- [ ] **Step 6: Run the full backend suite once**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify`
Expected: BUILD SUCCESS — all tests + ArchUnit pass. Clean containers afterward:
`docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/workshop/WorkshopBookingView.java \
        src/main/java/hu/deposoft/webshop/application/workshop/WorkshopService.java \
        src/test/java/hu/deposoft/webshop/application/workshop/WorkshopBookingsViewTest.java
git commit -m "feat(2b-2): expose orderItemId/cancelledSeats/lineGross in attendee view"
```

---

## Task 7: Admin SPA — "Lemondás" in the attendee table

**Files:**
- Modify: `admin-ui/src/types.ts:19-29` (`WorkshopBooking`)
- Modify: `admin-ui/src/api/orders.ts` (add `cancelBooking`)
- Modify: `admin-ui/src/pages/workshops/edit.tsx`

No backend test here; the gate is the SPA build.

- [ ] **Step 1: Add the new fields to the `WorkshopBooking` type**

In `admin-ui/src/types.ts`, extend the interface:

```typescript
export interface WorkshopBooking {
  sessionId: number | null;
  sessionStartAt: string | null;
  sessionSku: string;
  orderNumber: string;
  orderStatus: OrderStatus;
  customerName: string;
  email: string;
  phone: string | null;
  seats: number;
  orderItemId: number;
  cancelledSeats: number;
  lineGrossHuf: number;
}
```

- [ ] **Step 2: Add the `cancelBooking` API call**

In `admin-ui/src/api/orders.ts`, add at the end:

```typescript
export async function cancelBooking(itemId: number): Promise<Response> {
  return apiFetch(`${API_BASE}/order-items/${itemId}/cancel`, { method: "POST" });
}
```

- [ ] **Step 3: Wire the attendee table — cancel action + effective seats**

In `admin-ui/src/pages/workshops/edit.tsx`:

(a) Update the import on line 18 to include `cancelBooking`:

```typescript
import { STATUS_COLORS, STATUS_LABELS, cancelBooking } from "../../api/orders";
```

(b) Change `bookedSeats` to count effective seats (line ~57):

```typescript
  const bookedSeats = (sessionId: number) =>
    (bookingsBySession.get(sessionId) ?? []).reduce(
      (sum, b) => sum + (b.seats - b.cancelledSeats),
      0,
    );
```

(c) Add a cancel handler inside `Sessions` (after the `cancel` session handler, ~line 98):

```typescript
  const cancelBookingLine = async (b: WorkshopBooking) => {
    const res = await cancelBooking(b.orderItemId);
    if (res.ok) {
      message.success("Jelentkező lemondva, visszatérítés elindítva");
      await reload();
    } else {
      const text = await res.text();
      message.error(text || `Nem sikerült (${res.status})`);
    }
  };
```

(d) Replace the placeholder comment `{/* később ide kerül a soronkénti Lemondás / Áthelyezés művelet */}` (line ~137) with a Lemondás column:

```tsx
                <Table.Column<WorkshopBooking>
                  title="Művelet"
                  render={(_, r) =>
                    r.cancelledSeats >= r.seats ? (
                      <Tag color="red">Lemondva</Tag>
                    ) : (
                      <Popconfirm
                        title="Jelentkező lemondása"
                        description={`${r.seats} hely lemondása és ${huf(
                          r.lineGrossHuf,
                        )} visszatérítése?`}
                        okText="Lemondás"
                        cancelText="Mégse"
                        onConfirm={() => cancelBookingLine(r)}
                      >
                        <Button danger size="small">
                          Lemondás
                        </Button>
                      </Popconfirm>
                    )
                  }
                />
```

- [ ] **Step 4: Build the SPA**

Run: `cd admin-ui && npm run build`
Expected: build succeeds (TypeScript compiles, Vite bundles into `../src/main/resources/static/admin`). No type errors.

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/types.ts admin-ui/src/api/orders.ts admin-ui/src/pages/workshops/edit.tsx
git commit -m "feat(2b-2): Lemondás action + effective seat count in attendee table"
```

---

## Final verification

- [ ] **Backend full suite:** `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify` → BUILD SUCCESS (all tests + ArchUnit). Then `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.
- [ ] **Frontend:** `cd admin-ui && npm run build` → success.
- [ ] Then run the whole-slice final code review (subagent-driven-development's final reviewer) before finishing the branch.

---

## Self-Review notes (spec coverage)

- Spec §1 (line model) → Task 1. Spec §2 (credit-note rewrite) → Task 2 (model) + Task 3 (service). Spec §3 (orchestration) → Task 4. Spec §4 (API) → Task 5; (DTO/view) → Task 6; (UI) → Task 7. Spec §5 (testing) → tests distributed across Tasks 1–6 + the SPA build in Task 7.
- The spec described a single Flyway **V16**; the plan splits it into **V16** (order_item) + **V17** (invoice) so each task is independently committable and `ddl-auto: validate` passes after every task. Net schema is identical.
- `findByOrderAndSourceAndType` is preserved; a new `findByOrderAndSourceAndTypeAndOrderItemIsNull` is added for unambiguous whole-order lookups (the existing `invoice()`/`creditNote()` whole-order paths still compile against the original method — no signature change to existing callers).
- Method/field names are consistent across tasks: `cancelledQuantity`/`getCancelledQuantity()`, `cancelWholeLine()`, `creditNoteForLine(OrderItem)`, `findByOrderItemAndType`, `Invoice.creditNote(order, source, orderItem)`, `BookingCancellationService.cancelBooking(Long)`, `BookingCancelNotAllowedException`/`BookingRefundFailedException`, `BookingCancelResult(orderItemId, refundedHuf)`, view fields `orderItemId`/`cancelledSeats`/`lineGrossHuf`.
