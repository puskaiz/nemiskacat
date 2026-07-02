# Order Cancel + Refund (Slice 2b-1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin refund a paid, pre-shipment order (whole order) — money back via KHPos, order → `REFUNDED`, audited, with a per-source storno invoice (Billingo credit note for workshop lines, Kulcs for physical lines, gated like the forward invoicing).

**Architecture:** Reuse the forward-invoicing machinery for the storno direction: the `invoice` table gains an `InvoiceType {INVOICE, CREDIT_NOTE}`, and `InvoicingService.creditNote(orderId)` mirrors `invoice(orderId)`. A new `RefundService` (application/order) orchestrates: guard → `PaymentGateway.refund` → `Payment=REVERSED` → `Order→REFUNDED` → audit → credit note. Exposed via `POST /api/admin/orders/{id}/refund` and a button on the order detail page.

**Tech Stack:** Spring Boot 4.1, Java 21, Spring Data JPA, Flyway, Testcontainers + `@SpringBootTest`/`@MockitoBean`; Refine + TypeScript SPA.

**Conventions:**
- Backend tests: `@SpringBootTest @Testcontainers @Transactional`, `PostgreSQLContainer<>("postgres:17")` + `@ServiceConnection`; MockMvc via `webAppContextSetup(context).apply(springSecurity()).build()`.
- Run backend tests: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=<Class> test`, then `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.
- Frontend gate: `cd admin-ui && npm run build`.
- Do NOT stop/restart the app on port 8085.

## Existing pieces (do not recreate)
- `Invoice` entity/table: `@Table(name="invoice", uniqueConstraints=@UniqueConstraint(columnNames={"order_id","source"}))`; fields order, source (`InvoiceSource`), state (`InvoiceState` ISSUED/PUSHED/FAILED), invoiceNumber, publicUrl, externalId, message, timestamps; factory `Invoice.of(order, source)` (state FAILED initial), `recordIssued/recordPushed/recordFailed/isSuccessful`.
- `InvoiceRepository`: `findByOrderAndSource(Order, InvoiceSource)`, `findByState(InvoiceState)`.
- `InvoicingService.invoice(orderId)`: groups `order.getItems()` by `OrderItem::getInvoiceSource`, BILLINGO → `InvoiceIssuer.issue` (→`recordIssued`), KULCS_SOFT → `OrderSink.push` (→`recordPushed`), exception → `recordFailed`; idempotent (skip `isSuccessful`); `retryFailed()` re-runs `invoice` for FAILED rows.
- `InvoiceIssuer` port (`InvoiceResult(invoiceNumber, publicUrl, externalId)`, `isEnabled`, `issue`), `BillingoInvoiceIssuer` (gated), `DisabledInvoiceIssuer` (throws), `OrderSink`/`KulcsSoftOrderSink` (stub), `InvoicingConfig` (`@ConditionalOnProperty webshop.invoicing.billingo-enabled`).
- `PaymentGateway` port (`initPayment`, `queryStatus`, `isEnabled`), `KhposGatewayAdapter` (has `merchantId()`, `khpos` = `KhposPaymentService`), `DisabledPaymentGateway` (`PaymentUnavailableException`). KHPos `KhposPaymentService` exposes `cancelOrReturn(merchantId, payId, Long amount) -> KhposStatusResult`, plus `KhposStatusResult.status()` (`PaymentStatus`) + `.resultMessage()`. `KhposGatewayAdapter.mapStatus` treats CONFIRMED/WAITING_SETTLEMENT/SETTLED/REFUND_PROCESSING/RETURNED as money-moved.
- `Payment` entity: states INITIATED/CONFIRMED/FAILED/REVERSED; `markState(State, message)`, `getPayId()`, `getState()`. `PaymentRepository.findFirstByOrderOrderByIdDesc(Order)`.
- `OrderStatus` + `Order.transitionTo`; `OrderAdminQueryService.detail(id)` + nested `NotFoundException` (404-mapped); `OrderAdminController`; `AdminExceptionHandler`; `AuditService.record(action, entityType, entityId, summary)`.
- SPA: `admin-ui/src/pages/orders/show.tsx`, `admin-ui/src/api/orders.ts` (`STATUS_LABELS`, `STATUS_COLORS`, `NEXT`, `transitionOrder`), `admin-ui/src/api/http.ts` (`apiFetch`).

---

## Task 1: `InvoiceType` + Invoice gains a type (migration V14)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/domain/order/InvoiceType.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/Invoice.java`
- Create: `src/main/resources/db/migration/V14__invoice_type.sql`
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/InvoiceRepository.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/invoicing/InvoicingService.java` (use the type-scoped lookup for INVOICE)
- Test: `src/test/java/hu/deposoft/webshop/domain/order/InvoiceTypeTest.java`

- [ ] **Step 1: Write the migration**

Create `V14__invoice_type.sql`:

```sql
-- A credit note (storno) is a second document per (order, source), so widen the
-- uniqueness to include the document type. Default existing rows to INVOICE.
ALTER TABLE invoice ADD COLUMN type TEXT NOT NULL DEFAULT 'INVOICE'
    CHECK (type IN ('INVOICE', 'CREDIT_NOTE'));

ALTER TABLE invoice DROP CONSTRAINT invoice_order_id_source_key;
ALTER TABLE invoice ADD CONSTRAINT invoice_order_source_type_key UNIQUE (order_id, source, type);
```

- [ ] **Step 2: Write the enum**

Create `InvoiceType.java`:

```java
package hu.deposoft.webshop.domain.order;

/** Whether an invoice row records the original invoice or its storno (credit note). */
public enum InvoiceType {
    INVOICE,
    CREDIT_NOTE
}
```

- [ ] **Step 3: Add `type` to the Invoice entity**

In `Invoice.java`: change the `@Table` annotation's unique constraint and add the field + a `creditNote` factory, and set type in `of`.

Replace the `@Table(...)` line:

```java
@Table(name = "invoice", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "source", "type"}))
```

Add the field after `source`:

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceType type;
```

In the `of(...)` factory set `i.type = InvoiceType.INVOICE;` and add a credit-note factory next to it:

```java
    public static Invoice of(Order order, InvoiceSource source) {
        Invoice i = new Invoice();
        i.order = order;
        i.source = source;
        i.type = InvoiceType.INVOICE;
        i.state = InvoiceState.FAILED;
        return i;
    }

    public static Invoice creditNote(Order order, InvoiceSource source) {
        Invoice i = new Invoice();
        i.order = order;
        i.source = source;
        i.type = InvoiceType.CREDIT_NOTE;
        i.state = InvoiceState.FAILED;
        return i;
    }
```

- [ ] **Step 4: Update the repository**

In `InvoiceRepository.java` replace `findByOrderAndSource` with a type-scoped lookup (keep `findByState`):

```java
package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderAndSourceAndType(Order order, InvoiceSource source, InvoiceType type);

    List<Invoice> findByState(InvoiceState state);
}
```

- [ ] **Step 5: Point `InvoicingService.invoice` at the INVOICE-typed row**

In `InvoicingService.java`, in `invoice(...)`, change the lookup line:

```java
            Invoice invoice = invoices.findByOrderAndSourceAndType(order, source, hu.deposoft.webshop.domain.order.InvoiceType.INVOICE).orElse(null);
```

(`Invoice.of(...)` already sets type INVOICE, so the create path is unchanged.)

- [ ] **Step 6: Write the failing test**

Create `InvoiceTypeTest.java`:

```java
package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** An invoice and a credit note can coexist for the same (order, source). */
@SpringBootTest
@Testcontainers
@Transactional
class InvoiceTypeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    OrderRepository orders;

    @Test
    void invoiceAndCreditNoteCoexistPerSource() {
        // a bare order row is enough for the FK; use the orders repo to persist one via JPA
        Order order = orders.save(Order.place("itype-key", "T", "t@example.com", null,
                "1111", "B", "Fő u. 1.", null, "pickup", "Pickup", 0));

        invoices.save(Invoice.of(order, InvoiceSource.BILLINGO));
        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO));

        assertThat(invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.INVOICE)).isPresent();
        assertThat(invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE)).isPresent();
    }
}
```

> Check-before-use: confirm the `Order.place(...)` signature (clientKey, customerName, email, phone, postcode, city, addressLine, note, shipMethodCode, shipMethodName, shipGrossHuf) — it is the factory used in `Order`. If the parameter list differs, adjust the call to match.

- [ ] **Step 7: Run the test + the existing invoicing test**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=InvoiceTypeTest,InvoicingServiceTest test`
Expected: both green (V14 applies; the existing invoicing flow still records INVOICE rows). Clean containers afterwards.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/InvoiceType.java \
        src/main/java/hu/deposoft/webshop/domain/order/Invoice.java \
        src/main/java/hu/deposoft/webshop/domain/order/InvoiceRepository.java \
        src/main/resources/db/migration/V14__invoice_type.sql \
        src/main/java/hu/deposoft/webshop/application/invoicing/InvoicingService.java \
        src/test/java/hu/deposoft/webshop/domain/order/InvoiceTypeTest.java
git commit -m "Refund 2b-1: invoice gains a type (INVOICE/CREDIT_NOTE), unique per (order,source,type)"
```

---

## Task 2: Credit-note issuance (`InvoicingService.creditNote`)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/invoicing/CreditNoteIssuer.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/invoicing/OrderSink.java` (add `pushCreditNote`)
- Modify: `src/main/java/hu/deposoft/webshop/integrations/invoicing/KulcsSoftOrderSink.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/invoicing/DisabledCreditNoteIssuer.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/invoicing/BillingoCreditNoteIssuer.java`
- Modify: `src/main/java/hu/deposoft/webshop/integrations/invoicing/InvoicingConfig.java`
- Modify: `src/main/java/hu/deposoft/webshop/application/invoicing/InvoicingService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/invoicing/CreditNoteServiceTest.java`

- [ ] **Step 1: Add the `CreditNoteIssuer` port**

Create `CreditNoteIssuer.java`:

```java
package hu.deposoft.webshop.application.invoicing;

import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;

import java.util.List;

/** Port: issues a storno (credit note) for a group of order lines (Billingo for workshops). */
public interface CreditNoteIssuer {

    /** @param originalExternalId the issuer's id of the original invoice document (may be null). */
    InvoiceIssuer.InvoiceResult creditNote(Order order, List<OrderItem> lines, String originalExternalId);

    default boolean isEnabled() {
        return true;
    }
}
```

- [ ] **Step 2: Add `pushCreditNote` to the Kulcs sink port + impl**

In `OrderSink.java` add a method:

```java
    /** Hands a return/credit for these lines to Kulcs-Soft (they issue the credit note). */
    void pushCreditNote(Order order, List<OrderItem> lines);
```

In `KulcsSoftOrderSink.java` implement it (mirror the existing `push` stub):

```java
    @Override
    public void pushCreditNote(Order order, java.util.List<hu.deposoft.webshop.domain.order.OrderItem> lines) {
        log.info("Kulcs-Soft credit-note hand-over (stub) for order {}: {} physical line(s) — wire the real return push when Kulcs access exists",
                order.orderNumber(), lines.size());
    }
```

- [ ] **Step 3: Add the disabled + Billingo credit-note issuers**

Create `DisabledCreditNoteIssuer.java`:

```java
package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.webshop.application.invoicing.CreditNoteIssuer;
import hu.deposoft.webshop.application.invoicing.InvoiceIssuer;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;

import java.util.List;

/** Active until Billingo is configured; issuing throws so the source is recorded FAILED and retried. */
public class DisabledCreditNoteIssuer implements CreditNoteIssuer {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public InvoiceIssuer.InvoiceResult creditNote(Order order, List<OrderItem> lines, String originalExternalId) {
        throw new IllegalStateException("Billingo credit notes are not configured (webshop.invoicing.billingo-enabled=false)");
    }
}
```

Create `BillingoCreditNoteIssuer.java` (storno = cancel the original Billingo document):

```java
package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.billingo.client.DocumentClient;
import hu.deposoft.billingo.model.document.Document;
import hu.deposoft.webshop.application.invoicing.CreditNoteIssuer;
import hu.deposoft.webshop.application.invoicing.InvoiceIssuer;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Issues a Billingo credit note by cancelling the original document (storno).
 * Active only when {@code webshop.invoicing.billingo-enabled=true}; otherwise
 * {@link DisabledCreditNoteIssuer} stands in. Needs the original invoice's
 * Billingo document id (stored as the forward invoice's externalId).
 */
@Slf4j
public class BillingoCreditNoteIssuer implements CreditNoteIssuer {

    private final DocumentClient documents;

    public BillingoCreditNoteIssuer(DocumentClient documents) {
        this.documents = documents;
    }

    @Override
    public InvoiceIssuer.InvoiceResult creditNote(Order order, List<OrderItem> lines, String originalExternalId) {
        if (originalExternalId == null || originalExternalId.isBlank()) {
            throw new IllegalStateException("No original Billingo document id for order " + order.orderNumber());
        }
        Document cancelled = documents.cancel(Long.parseLong(originalExternalId));
        log.info("Billingo credit note {} for order {}", cancelled.invoiceNumber(), order.orderNumber());
        return new InvoiceIssuer.InvoiceResult(
                cancelled.invoiceNumber(), cancelled.publicUrl(), String.valueOf(cancelled.id()));
    }
}
```

> Check-before-use: confirm `DocumentClient.cancel(long)` returns a `Document` with `invoiceNumber()`, `publicUrl()`, `id()` (it does per the starter). This path runs only when Billingo is enabled (not in tests).

- [ ] **Step 4: Wire the issuers in `InvoicingConfig`**

In `InvoicingConfig.java` add two beans mirroring the invoice issuer:

```java
    @Bean
    @ConditionalOnProperty(name = "webshop.invoicing.billingo-enabled", havingValue = "true")
    hu.deposoft.webshop.application.invoicing.CreditNoteIssuer billingoCreditNoteIssuer(DocumentClient documentClient) {
        return new BillingoCreditNoteIssuer(documentClient);
    }

    @Bean
    @ConditionalOnProperty(name = "webshop.invoicing.billingo-enabled", havingValue = "false", matchIfMissing = true)
    hu.deposoft.webshop.application.invoicing.CreditNoteIssuer disabledCreditNoteIssuer() {
        return new DisabledCreditNoteIssuer();
    }
```

- [ ] **Step 5: Add `creditNote(orderId)` to `InvoicingService`**

In `InvoicingService.java`: inject `CreditNoteIssuer creditNoteIssuer;` (add to the fields). Add the method and update `retryFailed` to dispatch by type. Add imports `hu.deposoft.webshop.domain.order.InvoiceType`.

```java
    private final CreditNoteIssuer creditNoteIssuer;
```

```java
    /** Storno: issue a credit note per source, mirroring {@link #invoice}. Idempotent + retryable. */
    @Transactional
    public void creditNote(Long orderId) {
        Order order = orders.findWithItemsById(orderId).orElse(null);
        if (order == null) {
            log.warn("creditNote: order {} not found", orderId);
            return;
        }
        Map<InvoiceSource, List<OrderItem>> bySource = order.getItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getInvoiceSource));

        bySource.forEach((source, lines) -> {
            Invoice note = invoices.findByOrderAndSourceAndType(order, source, InvoiceType.CREDIT_NOTE).orElse(null);
            if (note != null && note.isSuccessful()) {
                return;
            }
            if (note == null) {
                note = invoices.save(Invoice.creditNote(order, source));
            }
            try {
                switch (source) {
                    case BILLINGO -> {
                        String originalExternalId = invoices
                                .findByOrderAndSourceAndType(order, source, InvoiceType.INVOICE)
                                .map(Invoice::getExternalId).orElse(null);
                        InvoiceIssuer.InvoiceResult r = creditNoteIssuer.creditNote(order, lines, originalExternalId);
                        note.recordIssued(r.invoiceNumber(), r.publicUrl(), r.externalId());
                        log.info("Order {} credit-noted via Billingo: {}", order.orderNumber(), r.invoiceNumber());
                    }
                    case KULCS_SOFT -> {
                        kulcsSink.pushCreditNote(order, lines);
                        note.recordPushed();
                        log.info("Order {} credit pushed to Kulcs-Soft ({} line(s))", order.orderNumber(), lines.size());
                    }
                }
            } catch (RuntimeException e) {
                note.recordFailed(e.getMessage());
                log.error("Credit note failed for order {} source {}: {}", order.orderNumber(), source, e.getMessage());
            }
        });
    }
```

Replace `retryFailed()` to dispatch by type:

```java
    /** Retry any (order, type) left in FAILED state (scheduled). */
    @Transactional
    public int retryFailed() {
        List<Invoice> failed = invoices.findByState(InvoiceState.FAILED);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.INVOICE)
                .map(i -> i.getOrder().getId()).distinct()
                .forEach(this::invoice);
        failed.stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE)
                .map(i -> i.getOrder().getId()).distinct()
                .forEach(this::creditNote);
        return (int) failed.stream().map(i -> i.getOrder().getId() + ":" + i.getType()).distinct().count();
    }
```

(Add `import hu.deposoft.webshop.domain.order.InvoiceState;` if not already imported — the original used a fully-qualified name; either is fine, just be consistent.)

- [ ] **Step 6: Write the failing test**

Create `CreditNoteServiceTest.java`:

```java
package hu.deposoft.webshop.application.invoicing;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.order.InvoiceRepository;
import hu.deposoft.webshop.domain.order.InvoiceState;
import hu.deposoft.webshop.domain.order.InvoiceType;
import hu.deposoft.webshop.domain.order.Order;
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

/**
 * Credit note routes by source like the forward invoicing: workshop lines →
 * Billingo (disabled here → recorded FAILED/pending), physical lines → Kulcs
 * (pushed). Idempotent. Default (test) profile keeps Billingo disabled.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CreditNoteServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @Autowired InvoicingService invoicing;
    @Autowired InvoiceRepository invoices;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;
    @Autowired WorkshopService workshops;

    @BeforeEach
    void seedPhysical() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint)));
    }

    private Order mixedOrder(String key) {
        Product ws = workshops.createWorkshop("WS", "ws-" + key, "x", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WS-" + key);
        String token = cart.addItem(null, "FES-1", 1).token();
        cart.addItem(token, "WS-" + key, 1);
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));
    }

    @Test
    void creditNoteRoutesBySource() {
        Order order = mixedOrder("cn-a");

        invoicing.creditNote(order.getId());

        var billingo = invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE).orElseThrow();
        var kulcs = invoices.findByOrderAndSourceAndType(order, InvoiceSource.KULCS_SOFT, InvoiceType.CREDIT_NOTE).orElseThrow();
        assertThat(billingo.getState()).isEqualTo(InvoiceState.FAILED);   // Billingo disabled → pending
        assertThat(kulcs.getState()).isEqualTo(InvoiceState.PUSHED);      // Kulcs stub → pushed
    }

    @Test
    void creditNoteIsIdempotentForSuccessfulSources() {
        Order order = mixedOrder("cn-b");
        invoicing.creditNote(order.getId());
        invoicing.creditNote(order.getId()); // second call must not duplicate the Kulcs (PUSHED) row

        assertThat(invoices.findByState(InvoiceState.PUSHED).stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE)
                .filter(i -> i.getOrder().getId().equals(order.getId()))).hasSize(1);
    }
}
```

- [ ] **Step 7: Run the test**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=CreditNoteServiceTest,InvoicingServiceTest test`
Expected: green. Clean containers afterwards.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/invoicing/CreditNoteIssuer.java \
        src/main/java/hu/deposoft/webshop/application/invoicing/OrderSink.java \
        src/main/java/hu/deposoft/webshop/application/invoicing/InvoicingService.java \
        src/main/java/hu/deposoft/webshop/integrations/invoicing/DisabledCreditNoteIssuer.java \
        src/main/java/hu/deposoft/webshop/integrations/invoicing/BillingoCreditNoteIssuer.java \
        src/main/java/hu/deposoft/webshop/integrations/invoicing/KulcsSoftOrderSink.java \
        src/main/java/hu/deposoft/webshop/integrations/invoicing/InvoicingConfig.java \
        src/test/java/hu/deposoft/webshop/application/invoicing/CreditNoteServiceTest.java
git commit -m "Refund 2b-1: per-source credit-note issuance (Billingo gated / Kulcs stub)"
```

---

## Task 3: `PaymentGateway.refund` port + adapters

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/payment/PaymentGateway.java`
- Modify: `src/main/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapter.java`
- Modify: `src/main/java/hu/deposoft/webshop/integrations/khpos/DisabledPaymentGateway.java`

- [ ] **Step 1: Add `refund` to the port**

In `PaymentGateway.java` add a result record + method:

```java
    record RefundResult(boolean success, String message) {
    }

    /** Refund (or reverse) a confirmed payment. Amount in HUF. */
    RefundResult refund(String payId, long amountHuf);
```

- [ ] **Step 2: Implement in `KhposGatewayAdapter`**

In `KhposGatewayAdapter.java` add the method (use `cancelOrReturn`, which auto-selects reverse vs refund):

```java
    @Override
    public RefundResult refund(String payId, long amountHuf) {
        KhposStatusResult result = khpos.cancelOrReturn(merchantId(), payId, amountHuf);
        ResultKind kind = mapStatus(result);
        boolean success = kind == ResultKind.CONFIRMED || kind == ResultKind.REVERSED;
        return new RefundResult(success, result.resultMessage());
    }
```

(`mapStatus` already maps REVERSED→REVERSED and the settled/returned states→CONFIRMED, i.e. money moved. `cancelOrReturn` returns a `KhposStatusResult`; `KhposStatusResult` already imported in this file.)

- [ ] **Step 3: Implement in `DisabledPaymentGateway`**

In `DisabledPaymentGateway.java` add:

```java
    @Override
    public RefundResult refund(String payId, long amountHuf) {
        throw new PaymentUnavailableException();
    }
```

- [ ] **Step 4: Compile**

Run: `mvn -q -Dskip.frontend=true compile`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/payment/PaymentGateway.java \
        src/main/java/hu/deposoft/webshop/integrations/khpos/KhposGatewayAdapter.java \
        src/main/java/hu/deposoft/webshop/integrations/khpos/DisabledPaymentGateway.java
git commit -m "Refund 2b-1: PaymentGateway.refund (KHPos cancelOrReturn) + disabled impl"
```

---

## Task 4: `REFUNDED` status + `RefundService`

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/checkout/OrderStatus.java`
- Modify: `src/main/resources/db/migration` — *no migration needed* (status is `TEXT`, see note) — but the `orders.status` CHECK constraint must allow REFUNDED.
- Create: `src/main/java/hu/deposoft/webshop/application/order/RefundService.java`
- Test: `src/test/java/hu/deposoft/webshop/domain/checkout/OrderStatusTest.java` (extend)
- Test: `src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java`

- [ ] **Step 1: Check the orders.status CHECK constraint**

Run: `grep -rn "status" src/main/resources/db/migration/V5__orders_reservations.sql`
If `status` has a `CHECK (status IN (...))` listing the enum values, add a migration `V15__order_status_refunded.sql`:

```sql
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('NEW','PAID','PACKING','SHIPPED','COMPLETED','CANCELLED','REFUNDED'));
```

If there is **no** CHECK constraint on `status` (it's a plain `TEXT`), skip the migration. (Determine which by reading the migration; do not guess.)

- [ ] **Step 2: Add REFUNDED to the enum + transitions**

In `OrderStatus.java`, add `REFUNDED` and the transitions:

```java
public enum OrderStatus {
    NEW, PAID, PACKING, SHIPPED, COMPLETED, CANCELLED, REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            NEW, Set.of(PAID, CANCELLED),
            PAID, Set.of(PACKING, CANCELLED, REFUNDED),
            PACKING, Set.of(SHIPPED, REFUNDED),
            SHIPPED, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            REFUNDED, Set.of());

    public boolean canTransitionTo(OrderStatus target) {
        return target != null && ALLOWED.get(this).contains(target);
    }
}
```

- [ ] **Step 3: Extend `OrderStatusTest`**

Add to `OrderStatusTest.java`:

```java
    @org.junit.jupiter.api.Test
    void paidAndPackingCanBeRefunded() {
        org.assertj.core.api.Assertions.assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        org.assertj.core.api.Assertions.assertThat(OrderStatus.PACKING.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        org.assertj.core.api.Assertions.assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
        org.assertj.core.api.Assertions.assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.NEW)).isFalse();
    }
```

- [ ] **Step 4: Write the failing `RefundServiceTest`**

Create `RefundServiceTest.java`:

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class RefundServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired RefundService refunds;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;

    @MockitoBean
    PaymentGateway gateway;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint)));
    }

    /** A NEW order with a CONFIRMED payment, driven to a given status. */
    private Order orderInStatus(String key, OrderStatus status) {
        String token = cart.addItem(null, "FES-1", 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt", "t@example.com",
                null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        if (status != OrderStatus.PAID) {
            order.transitionTo(OrderStatus.PAID);
            if (status == OrderStatus.PACKING) order.transitionTo(OrderStatus.PACKING);
        } else {
            order.transitionTo(OrderStatus.PAID);
        }
        return orders.save(order);
    }

    @Test
    void refundsPaidOrder() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Long id = orderInStatus("r-ok", OrderStatus.PAID).getId();

        refunds.refund(id);

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payments.findFirstByOrderOrderByIdDesc(orders.findById(id).orElseThrow()).orElseThrow().getState())
                .isEqualTo(Payment.State.REVERSED);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order", String.valueOf(id)))
                .anySatisfy(e -> assertThat(e.getAction()).isEqualTo("ORDER_REFUNDED"));
    }

    @Test
    void rejectsRefundOfShippedOrder() {
        Long id = orderInStatus("r-ship", OrderStatus.PACKING).getId();
        Order o = orders.findById(id).orElseThrow();
        o.transitionTo(OrderStatus.SHIPPED);
        orders.save(o);

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundNotAllowedException.class);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void gatewayFailureLeavesOrderUnchanged() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(false, "bank declined"));
        Long id = orderInStatus("r-fail", OrderStatus.PAID).getId();

        assertThatThrownBy(() -> refunds.refund(id))
                .isInstanceOf(RefundService.RefundFailedException.class);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void refundIsIdempotent() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Long id = orderInStatus("r-idem", OrderStatus.PAID).getId();

        refunds.refund(id);
        refunds.refund(id); // already REFUNDED → no-op

        verify(gateway, org.mockito.Mockito.times(1)).refund(anyString(), anyLong());
    }
}
```

> Check-before-use: confirm `Payment.initiate(order, payId, amount)` is the factory (used in `PaymentService.start`). Confirm `Payment.State.CONFIRMED/REVERSED` and `markState`. Adjust if names differ.

- [ ] **Step 5: Run to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=RefundServiceTest test`
Expected: FAIL — `RefundService` does not exist.

- [ ] **Step 6: Write `RefundService`**

Create `RefundService.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.invoicing.InvoicingService;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole-order, pre-shipment refund (T16 slice 2b-1): refund the money via the
 * gateway, mark the payment reversed, move the order to REFUNDED, audit it, and
 * issue the per-source storno invoice. Idempotent; refunds only PAID/PACKING.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefundService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final InvoicingService invoicing;
    private final AuditService audit;

    /** Policy rejection (wrong state / no confirmed payment). */
    public static class RefundNotAllowedException extends RuntimeException {
        public RefundNotAllowedException(String message) {
            super(message);
        }
    }

    /** The gateway refused or failed the refund. */
    public static class RefundFailedException extends RuntimeException {
        public RefundFailedException(String message) {
            super(message);
        }
    }

    @Transactional
    public void refund(Long orderId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException("No order " + orderId));
        OrderStatus current = order.getStatus();
        if (current == OrderStatus.REFUNDED) {
            return; // idempotent — already refunded
        }
        if (current != OrderStatus.PAID && current != OrderStatus.PACKING) {
            throw new RefundNotAllowedException("Only PAID/PACKING orders can be refunded here; this one is " + current);
        }
        Payment payment = payments.findFirstByOrderOrderByIdDesc(order)
                .orElseThrow(() -> new RefundNotAllowedException("No payment to refund for order " + orderId));
        if (payment.getState() != Payment.State.CONFIRMED) {
            throw new RefundNotAllowedException("Payment for order " + orderId + " is " + payment.getState() + ", not CONFIRMED");
        }

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
        order.transitionTo(OrderStatus.REFUNDED);
        audit.record("ORDER_REFUNDED", "order", String.valueOf(orderId), current + "→REFUNDED");
        log.info("Order {} refunded ({} HUF)", order.orderNumber(), order.getTotalGrossHuf());

        invoicing.creditNote(orderId); // per-source storno invoice (gated)
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=RefundServiceTest,OrderStatusTest test`
Expected: green. Clean containers afterwards.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/checkout/OrderStatus.java \
        src/main/java/hu/deposoft/webshop/application/order/RefundService.java \
        src/test/java/hu/deposoft/webshop/domain/checkout/OrderStatusTest.java \
        src/test/java/hu/deposoft/webshop/application/order/RefundServiceTest.java \
        src/main/resources/db/migration/V15__order_status_refunded.sql 2>/dev/null
git add -A
git commit -m "Refund 2b-1: REFUNDED status + RefundService (gateway refund + reversal + audit + credit note)"
```

---

## Task 5: Refund endpoint + exception mapping

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/OrderAdminController.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java` (extend)

- [ ] **Step 1: Map the refund exceptions**

In `AdminExceptionHandler.java` add imports + handlers:

```java
import hu.deposoft.webshop.application.order.RefundService;
```

```java
    @ExceptionHandler(RefundService.RefundNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String refundNotAllowed(RefundService.RefundNotAllowedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(RefundService.RefundFailedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String refundFailed(RefundService.RefundFailedException e) {
        return e.getMessage();
    }
```

- [ ] **Step 2: Add the endpoint**

In `OrderAdminController.java` add imports `hu.deposoft.webshop.application.order.RefundService` and (if absent) `org.springframework.web.bind.annotation.PostMapping`. Inject `RefundService` via the constructor:

```java
    private final RefundService refundService;
```

Add the endpoint:

```java
    @PostMapping("/{id}/refund")
    public OrderDetail refund(@PathVariable Long id) {
        refundService.refund(id);
        return query.detail(id);
    }
```

- [ ] **Step 3: Write the failing controller tests**

Add to `OrderAdminControllerTest.java` (it already autowires `orderRepository`, seeds one NEW order, and has `admin()`):

```java
    @Test
    void refundEndpointRefundsPaidOrder() throws Exception {
        Order order = orderRepository.findAll().getFirst();
        // give it a confirmed payment and drive to PAID
        paymentRepository.save(hu.deposoft.webshop.domain.order.Payment.initiate(order, "PAY-REF", order.getTotalGrossHuf()));
        var p = paymentRepository.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(hu.deposoft.webshop.domain.order.Payment.State.CONFIRMED, "ok");
        order.transitionTo(hu.deposoft.webshop.domain.checkout.OrderStatus.PAID);
        orderRepository.save(order);

        mvc.perform(post("/api/admin/orders/" + order.getId() + "/refund")
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REFUNDED")));
    }

    @Test
    void refundEndpointRejectsNewOrderWith409() throws Exception {
        Order order = orderRepository.findAll().getFirst(); // NEW, unpaid
        mvc.perform(post("/api/admin/orders/" + order.getId() + "/refund")
                        .with(admin()).with(csrf()))
                .andExpect(status().isConflict());
    }
```

Add the needed autowires/imports to the test class if missing: `@Autowired PaymentRepository paymentRepository;` (import `hu.deposoft.webshop.domain.order.PaymentRepository`), and `@MockitoBean PaymentGateway gateway;` with `@org.junit.jupiter.api.BeforeEach` stubbing `when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));` (import `org.springframework.test.context.bean.override.mockito.MockitoBean`, `hu.deposoft.webshop.application.payment.PaymentGateway`, and the Mockito statics). The mocked gateway makes the refund deterministic without a real bank.

> Note: in the default test profile `khpos.enabled=false`, so the real bean is `DisabledPaymentGateway`; `@MockitoBean PaymentGateway` replaces it with the stub.

- [ ] **Step 4: Run the tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=OrderAdminControllerTest test`
Expected: all green (prior + 2 new). Clean containers.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/main/java/hu/deposoft/webshop/api/admin/OrderAdminController.java \
        src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java
git commit -m "Refund 2b-1: POST /api/admin/orders/{id}/refund (409 not-allowed, 502 gateway failure)"
```

---

## Task 6: Refund button + REFUNDED label (SPA)

**Files:**
- Modify: `admin-ui/src/api/orders.ts`
- Modify: `admin-ui/src/pages/orders/show.tsx`

- [ ] **Step 1: Add the REFUNDED label/colour + a refund helper**

In `admin-ui/src/api/orders.ts`:
- add `REFUNDED: "Visszatérítve"` to `STATUS_LABELS` and `REFUNDED: "purple"` to `STATUS_COLORS`;
- add a refund call:

```ts
export async function refundOrder(id: number): Promise<Response> {
  return apiFetch(`${API_BASE}/orders/${id}/refund`, { method: "POST" });
}
```

> `OrderStatus` in `admin-ui/src/types.ts` must include `"REFUNDED"`. Add it to the union if absent.

- [ ] **Step 2: Add the refund button to the order detail**

In `admin-ui/src/pages/orders/show.tsx`, import `refundOrder`:

```tsx
import { NEXT, STATUS_COLORS, STATUS_LABELS, refundOrder, transitionOrder } from "../../api/orders";
```

Add a refund handler next to `transition` (reuse the `pending` state):

```tsx
  const refund = async () => {
    setPending(true);
    try {
      const res = await refundOrder(order!.id);
      if (res.ok) {
        message.success("Visszatérítve");
        await queryResult?.refetch();
      } else {
        const text = await res.text().catch(() => "");
        message.error(text || `Nem sikerült (${res.status})`);
      }
    } catch {
      message.error("Hálózati hiba történt.");
    } finally {
      setPending(false);
    }
  };
```

In the action `<Space>` (where the status `<Tag>` + transition button render), add a refund button for PAID/PACKING orders:

```tsx
            {(order.status === "PAID" || order.status === "PACKING") && (
              <Popconfirm
                title={`Sztornó + visszatérítés (${order.totalGrossHuf.toLocaleString("hu-HU")} Ft)?`}
                onConfirm={refund}
              >
                <Button danger loading={pending} disabled={pending}>
                  Sztornó + visszatérítés
                </Button>
              </Popconfirm>
            )}
```

- [ ] **Step 3: Build**

Run: `cd admin-ui && npm run build`
Expected: `✓ built`, no TS errors.

- [ ] **Step 4: Commit**

```bash
git add admin-ui/src/api/orders.ts admin-ui/src/pages/orders/show.tsx admin-ui/src/types.ts
git commit -m "Refund 2b-1: 'Sztornó + visszatérítés' button + Visszatérítve (REFUNDED) label"
```

---

## Task 7: Full verification

- [ ] **Step 1: Backend suite (Ryuk disabled, frontend skipped)**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify`
Expected: BUILD SUCCESS, all tests pass (prior + InvoiceTypeTest, CreditNoteServiceTest, RefundServiceTest, OrderStatusTest additions, 2 controller tests). ArchUnit `ModularityTest` green (RefundService in `application`, endpoint in `api`, issuers in `integrations`). Clean containers afterwards.

- [ ] **Step 2: Frontend build**

Run: `cd admin-ui && npm run build`
Expected: `✓ built`, no TS errors.

- [ ] **Step 3: Commit any fixups**

```bash
git add -A && git commit -m "Refund 2b-1: full verify green" || echo "nothing to commit"
```

---

## Self-Review

**Spec coverage:**
- REFUNDED status, PAID/PACKING→REFUNDED → Task 4 (+ OrderStatusTest). ✓
- `PaymentGateway.refund` (KHPos cancelOrReturn) + disabled impl → Task 3. ✓
- `RefundService` (guard → gateway → Payment REVERSED → REFUNDED → audit → credit note), idempotent → Task 4. ✓
- Per-source credit note (Billingo gated / Kulcs stub), Invoice `type`, V14, idempotent/retry → Tasks 1–2. ✓
- `POST /api/admin/orders/{id}/refund` + 409 / 502 mapping → Task 5. ✓
- SPA refund button (PAID/PACKING) + Visszatérítve label → Task 6. ✓
- Testing: refund happy/guard/gateway-failure/idempotent, credit-note routing, status transitions, controller 200/409/403 → Tasks 1,2,4,5. ✓

**Placeholder scan:** none. (Task 4 Step 1 and Task 5 Step 3 contain explicit conditional instructions — "read the migration to decide", "add autowires if missing" — with the exact code to add, not vague TODOs.)

**Type consistency:** `PaymentGateway.RefundResult(boolean success, String message)` used identically in the adapter, RefundService, and both tests; `RefundService.refund(Long)`, `RefundNotAllowedException`(→409)/`RefundFailedException`(→502) consistent across Tasks 4/5; `InvoicingService.creditNote(Long)` (Task 2) called by RefundService (Task 4); `Invoice.creditNote(order, source)` + `InvoiceType.CREDIT_NOTE` + `findByOrderAndSourceAndType` consistent across Tasks 1/2; SPA `refundOrder(id)` + `REFUNDED` label/colour/type consistent in Task 6.

**Check-before-use items flagged inline:** `Order.place(...)` signature (Task 1 test); `DocumentClient.cancel(long)` return shape (Task 2); `Payment.initiate` + `Payment.State` (Task 4 test); whether `orders.status` has a CHECK constraint needing V15 (Task 4 Step 1 — read, don't guess); `OrderStatus` TS union includes REFUNDED (Task 6).
