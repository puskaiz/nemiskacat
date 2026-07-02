# WooCommerce Order Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import all 22,713 historical WooCommerce orders (read-only) into the local Postgres DB as first-class orders, for go-live cutover.

**Architecture:** Mirror the existing catalog/customer import pattern end-to-end: a Python `export-orders.py` reads the `wp_db` MySQL container → JSON → an `OrderImporter` application service upserts `Order`/`OrderItem`/`Payment` aggregates directly via repositories, behind a one-shot `import-orders` Spring profile. Imported orders set their final status directly (no state-machine replay), fire no domain events, and touch no stock/reservations.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, Flyway, Jackson 3 (`tools.jackson.databind`), Lombok, JUnit 5 + Testcontainers (Postgres), Python 3 (export script), TypeScript/Refine (admin-ui).

## Global Constraints

- **Woo is read-only.** Export queries only `SELECT`; never write to `wp_db`. (CLAUDE.md)
- **Money:** HUF whole forints, `long`, scale 0 — mirror `export.py`'s `to_minor_huf` (round to int; no ×100). (CLAUDE.md §6)
- **Time:** stored UTC (`OffsetDateTime`/`TIMESTAMPTZ`), displayed Europe/Budapest. Preserve original Woo order dates.
- **Business logic only in the service layer.** Importer is an `application/` `@Service`. (CLAUDE.md §1)
- **Idempotent:** re-running the import produces no duplicates (key: `woo_order_id`). (CLAUDE.md §4)
- **No side effects on import:** no `OrderPaidEvent`, no invoicing, no stock decrement, no reservations.
- **UI text Hungarian; code/comments English.** (CLAUDE.md §10)
- **Migrations are additive/non-breaking** (relax constraint, add nullable columns). Next free Flyway version is **V19** (highest existing is V18).
- **Status mapping is 1:1 and lossless** — no collapsing of distinct Woo states.

## File Structure

| File | Responsibility |
|------|----------------|
| `src/main/resources/db/migration/V19__order_status_woo.sql` | Add 4 statuses to `orders_status_check` |
| `src/main/resources/db/migration/V20__order_item_variant_nullable.sql` | Drop NOT NULL on `order_item.variant_id` |
| `src/main/resources/db/migration/V21__orders_woo_source.sql` | Add `orders.woo_order_id` (unique) + `orders.source` |
| `src/main/java/.../domain/checkout/OrderStatus.java` | +PROCESSING/ON_HOLD/FAILED/AWAITING_SHIPMENT (terminal) |
| `src/main/java/.../domain/order/OrderItem.java` | Allow null variant |
| `src/main/java/.../domain/order/Order.java` | `imported(...)` factory, `addImportedItem`, woo fields, date-preserving `onCreate` |
| `src/main/java/.../domain/order/OrderRepository.java` | `findByWooOrderId` |
| `src/main/java/.../integrations/woo/SourceOrder.java` | Export DTO (order) |
| `src/main/java/.../integrations/woo/SourceOrderItem.java` | Export DTO (line) |
| `src/main/java/.../integrations/woo/OrderSource.java` | Port |
| `src/main/java/.../integrations/woo/JsonFileOrderSource.java` | JSON-file port impl |
| `src/main/java/.../application/order/OrderImportReport.java` | Run report (counts + orphan/unknown lists) |
| `src/main/java/.../application/order/OrderImporter.java` | Idempotent upsert service |
| `src/main/java/.../config/OrderImportRunner.java` | `import-orders` profile CLI runner |
| `src/main/resources/application-import-orders.yml` | Profile config (web off) |
| `scripts/woo-export/export-orders.py` | Woo→JSON export |
| `scripts/woo-export/test_export_orders.py` | Pure-function arithmetic tests |
| `scripts/woo-export/reset-app-data.sql` | Human-run pre-import wipe |
| `admin-ui/src/types.ts` | `OrderStatus` union +4 values |
| `admin-ui/src/api/orders.ts` | Hungarian labels + colors for the +4 |
| `docs/adr/0011-migrate-woo-orders.md` | ADR reversing TERV.md §7 |
| `docs/superpowers/plans/cutover-runbook.md` | Reset→catalog→customers→orders runbook |

Package root: `hu.deposoft.webshop`. Test sources mirror under `src/test/java/...`.

---

### Task 1: Order status model — add the four Woo statuses

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/checkout/OrderStatus.java`
- Create: `src/main/resources/db/migration/V19__order_status_woo.sql`
- Test: `src/test/java/hu/deposoft/webshop/domain/checkout/OrderStatusTest.java`

**Interfaces:**
- Produces: `OrderStatus.PROCESSING`, `OrderStatus.ON_HOLD`, `OrderStatus.FAILED`, `OrderStatus.AWAITING_SHIPMENT` — each terminal (`canTransitionTo(x) == false` for all `x`).

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.domain.checkout;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void importStatusesExistAndAreTerminal() {
        for (OrderStatus s : new OrderStatus[]{
                OrderStatus.PROCESSING, OrderStatus.ON_HOLD,
                OrderStatus.FAILED, OrderStatus.AWAITING_SHIPMENT}) {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(s.canTransitionTo(target))
                        .as("%s should be terminal", s)
                        .isFalse();
            }
        }
    }

    @Test
    void existingNativeTransitionsUnchanged() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PACKING)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=OrderStatusTest test`
Expected: FAIL — `PROCESSING` (etc.) cannot be resolved / compilation error.

- [ ] **Step 3: Add the enum values (terminal)**

Edit `OrderStatus.java` — extend the constant list and the `ALLOWED` map:

```java
public enum OrderStatus {
    NEW, PAID, PACKING, SHIPPED, COMPLETED, CANCELLED, REFUNDED,
    // Import-only states from WooCommerce (1:1, lossless). Terminal in the
    // native checkout state machine — historical orders only.
    PROCESSING, ON_HOLD, FAILED, AWAITING_SHIPMENT;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.ofEntries(
            Map.entry(NEW, Set.of(PAID, CANCELLED)),
            Map.entry(PAID, Set.of(PACKING, CANCELLED, REFUNDED)),
            Map.entry(PACKING, Set.of(SHIPPED, REFUNDED)),
            Map.entry(SHIPPED, Set.of(COMPLETED)),
            Map.entry(COMPLETED, Set.of()),
            Map.entry(CANCELLED, Set.of()),
            Map.entry(REFUNDED, Set.of()),
            Map.entry(PROCESSING, Set.of()),
            Map.entry(ON_HOLD, Set.of()),
            Map.entry(FAILED, Set.of()),
            Map.entry(AWAITING_SHIPMENT, Set.of()));

    public boolean canTransitionTo(OrderStatus target) {
        return target != null && ALLOWED.get(this).contains(target);
    }
}
```

(`Map.of` caps at 10 entries; switch to `Map.ofEntries`.)

- [ ] **Step 4: Create the migration**

`V19__order_status_woo.sql`:

```sql
-- Order migration (cutover): WooCommerce statuses mapped 1:1 into our enum.
-- PROCESSING/ON_HOLD/FAILED/AWAITING_SHIPMENT are import-only historical states.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('NEW','PAID','PACKING','SHIPPED','COMPLETED','CANCELLED','REFUNDED',
                      'PROCESSING','ON_HOLD','FAILED','AWAITING_SHIPMENT'));
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q -Dtest=OrderStatusTest test`
Expected: PASS (the DB constraint is exercised later in Task 5's integration test).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/checkout/OrderStatus.java \
        src/main/resources/db/migration/V19__order_status_woo.sql \
        src/test/java/hu/deposoft/webshop/domain/checkout/OrderStatusTest.java
git commit -m "feat(orders): add WooCommerce import-only order statuses"
```

---

### Task 2: Allow order lines without a live variant

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/OrderItem.java:31-33`
- Create: `src/main/resources/db/migration/V20__order_item_variant_nullable.sql`
- Test: `src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java`

**Interfaces:**
- Produces: `OrderItem.create(order, null, productName, variantLabel, sku, unitGrossHuf, taxRatePercent, quantity, invoiceSource)` succeeds; `getVariant()` returns `null`, snapshot fields intact.

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void createsOrphanLineWithoutVariant() {
        OrderItem item = OrderItem.create(
                null, null, "Régi termék", "1 kg", "OLD-SKU",
                3700, 27, 2, InvoiceSource.KULCS_SOFT);

        assertThat(item.getVariant()).isNull();
        assertThat(item.getProductName()).isEqualTo("Régi termék");
        assertThat(item.getSku()).isEqualTo("OLD-SKU");
        assertThat(item.getLineGrossHuf()).isEqualTo(7400);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=OrderItemTest test`
Expected: PASS at the Java level may already occur (factory accepts null); the JPA mapping is what blocks persistence. If it passes, that's fine — the persistence guarantee is enforced by Step 3 + Task 5. Proceed to relax the mapping anyway.

- [ ] **Step 3: Relax the JPA mapping**

In `OrderItem.java`, change the variant association (currently lines 31-33):

```java
    @ManyToOne
    @JoinColumn(name = "variant_id")
    private Variant variant;
```

(Remove `optional = false` and `nullable = false`.)

- [ ] **Step 4: Create the migration**

`V20__order_item_variant_nullable.sql`:

```sql
-- Imported historical orders may reference products no longer in the catalog.
-- The line keeps its snapshot (product_name, sku, prices); the live FK is optional.
ALTER TABLE order_item ALTER COLUMN variant_id DROP NOT NULL;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -q -Dtest=OrderItemTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/OrderItem.java \
        src/main/resources/db/migration/V20__order_item_variant_nullable.sql \
        src/test/java/hu/deposoft/webshop/domain/order/OrderItemTest.java
git commit -m "feat(orders): allow order lines without a live variant (orphan snapshot)"
```

---

### Task 3: `Order.imported(...)` factory + Woo provenance columns

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/Order.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java`
- Create: `src/main/resources/db/migration/V21__orders_woo_source.sql`
- Test: `src/test/java/hu/deposoft/webshop/domain/order/OrderImportedTest.java`

**Interfaces:**
- Produces:
  - `Order.imported(long wooOrderId, OrderStatus status, String customerName, String email, String phone, String postcode, String city, String addressLine, String note, String shipMethodName, long shipGrossHuf, long itemsGrossHuf, long totalGrossHuf, OffsetDateTime createdAt) -> Order` — sets final status directly, `clientKey = "woo-" + wooOrderId`, `source = "WOO_IMPORT"`, preserves `createdAt`, substitutes `"—"` for blank required snapshot fields.
  - `Order.addImportedItem(OrderItem item)` — appends without recomputing totals.
  - `Order.getWooOrderId() : Long`, `Order.getSource() : String` (Lombok getters).
  - `OrderRepository.findByWooOrderId(Long) : Optional<Order>`.

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
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
class OrderImportedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    OrderRepository orders;

    @Test
    void importedOrderKeepsFinalStatusDateAndProvenance() {
        OffsetDateTime placed = OffsetDateTime.of(2015, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        Order o = Order.imported(1024L, OrderStatus.COMPLETED, "Nagy Ágnes", "agi@example.com",
                "+36301234567", "1011", "Budapest", "Fő utca 1.", null,
                "Foxpost", 990, 7400, 8390, placed);

        Order saved = orders.save(o);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(saved.getClientKey()).isEqualTo("woo-1024");
        assertThat(saved.getWooOrderId()).isEqualTo(1024L);
        assertThat(saved.getSource()).isEqualTo("WOO_IMPORT");
        assertThat(saved.getCreatedAt()).isEqualTo(placed); // NOT now()
        assertThat(saved.getTotalGrossHuf()).isEqualTo(8390);
        assertThat(orders.findByWooOrderId(1024L)).isPresent();
    }

    @Test
    void blankRequiredSnapshotFieldsBecomePlaceholders() {
        Order o = Order.imported(7L, OrderStatus.CANCELLED, "  ", "buyer@example.com",
                null, "", "", "", null, null, 0, 0, 0,
                OffsetDateTime.now(ZoneOffset.UTC));
        Order saved = orders.save(o);
        assertThat(saved.getCustomerName()).isEqualTo("—");
        assertThat(saved.getCity()).isEqualTo("—");
        assertThat(saved.getShipMethodName()).isEqualTo("—");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=OrderImportedTest test`
Expected: FAIL — `imported`, `getWooOrderId`, `getSource`, `findByWooOrderId` undefined.

- [ ] **Step 3: Add columns to `Order` and the date-preserving guard**

In `Order.java`, add fields after `clientKey`:

```java
    /** WooCommerce order id for imported orders; null for native orders. */
    @Column(name = "woo_order_id", unique = true)
    private Long wooOrderId;

    /** Provenance marker: "WOO_IMPORT" for imported orders, null for native. */
    private String source;
```

Add the factory + appender (after `place(...)`):

```java
    private static String orPlaceholder(String v) {
        return (v == null || v.isBlank()) ? "—" : v;
    }

    /**
     * Build a historical order imported from WooCommerce. Sets the FINAL status
     * directly (no state-machine replay), preserves the original placement date,
     * and stores Woo totals verbatim (coupons/fees make total != items + ship).
     * Fires no events and touches no stock.
     */
    public static Order imported(long wooOrderId, OrderStatus status, String customerName, String email,
                                 String phone, String postcode, String city, String addressLine, String note,
                                 String shipMethodName, long shipGrossHuf, long itemsGrossHuf,
                                 long totalGrossHuf, OffsetDateTime createdAt) {
        Order o = new Order();
        o.clientKey = "woo-" + wooOrderId;
        o.wooOrderId = wooOrderId;
        o.source = "WOO_IMPORT";
        o.status = status;
        o.customerName = orPlaceholder(customerName);
        o.email = orPlaceholder(email);
        o.phone = phone;
        o.postcode = orPlaceholder(postcode);
        o.city = orPlaceholder(city);
        o.addressLine = orPlaceholder(addressLine);
        o.note = note;
        o.shipMethodCode = "woo-import";
        o.shipMethodName = orPlaceholder(shipMethodName);
        o.shipGrossHuf = shipGrossHuf;
        o.itemsGrossHuf = itemsGrossHuf;
        o.totalGrossHuf = totalGrossHuf;
        o.createdAt = createdAt;
        return o;
    }

    /** Append an imported line without recomputing totals (Woo totals are authoritative). */
    public void addImportedItem(OrderItem item) {
        items.add(item);
    }
```

Change `onCreate()` so a preset date is not clobbered:

```java
    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
```

- [ ] **Step 4: Add the repository finder**

In `OrderRepository.java`, add:

```java
    Optional<Order> findByWooOrderId(Long wooOrderId);
```

- [ ] **Step 5: Create the migration**

`V21__orders_woo_source.sql`:

```sql
-- Provenance for imported WooCommerce orders. Native orders leave these null.
-- woo_order_id is the idempotency key for re-running the order import.
ALTER TABLE orders ADD COLUMN woo_order_id BIGINT;
ALTER TABLE orders ADD COLUMN source TEXT;
ALTER TABLE orders ADD CONSTRAINT orders_woo_order_id_key UNIQUE (woo_order_id);
```

(Postgres treats NULLs as distinct, so many native orders with `woo_order_id = NULL` are allowed.)

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw -q -Dtest=OrderImportedTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/domain/order/Order.java \
        src/main/java/hu/deposoft/webshop/domain/order/OrderRepository.java \
        src/main/resources/db/migration/V21__orders_woo_source.sql \
        src/test/java/hu/deposoft/webshop/domain/order/OrderImportedTest.java
git commit -m "feat(orders): Order.imported factory + woo provenance columns"
```

---

### Task 4: Source DTOs and JSON order source

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/integrations/woo/SourceOrder.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/woo/SourceOrderItem.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/woo/OrderSource.java`
- Create: `src/main/java/hu/deposoft/webshop/integrations/woo/JsonFileOrderSource.java`
- Test: `src/test/java/hu/deposoft/webshop/integrations/woo/JsonFileOrderSourceTest.java`

**Interfaces:**
- Produces:
  - `record SourceOrder(long wooOrderId, String orderKey, String wooStatus, String currency, String createdAt, String paidAt, Long wpUserId, String customerName, String email, String phone, String postcode, String city, String addressLine, String note, String shipMethodName, long shipGrossHuf, long itemsGrossHuf, long totalGrossHuf, boolean paid, String transactionId, List<SourceOrderItem> items)`
  - `record SourceOrderItem(Long wooProductId, Long wooVariationId, String sku, String productName, String variantLabel, int quantity, long unitGrossHuf, int taxRatePercent, long lineGrossHuf)`
  - `interface OrderSource { List<SourceOrder> load(); }`
  - `JsonFileOrderSource(ObjectMapper, Path)` implementing `OrderSource`.

- [ ] **Step 1: Write the failing test**

```java
package hu.deposoft.webshop.integrations.woo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFileOrderSourceTest {

    @Test
    void parsesOrdersFromJsonFile(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("orders.json");
        Files.writeString(file, """
            [{
              "wooOrderId": 1024, "orderKey": "wc_order_x", "wooStatus": "wc-completed",
              "currency": "HUF", "createdAt": "2015-03-01T10:00:00Z", "paidAt": "2015-03-01T10:05:00Z",
              "wpUserId": 3768, "customerName": "Nagy Ágnes", "email": "agi@example.com",
              "phone": "+36301234567", "postcode": "1011", "city": "Budapest",
              "addressLine": "Fő utca 1.", "note": null, "shipMethodName": "Foxpost",
              "shipGrossHuf": 990, "itemsGrossHuf": 7400, "totalGrossHuf": 8390,
              "paid": true, "transactionId": "txn-1",
              "items": [{
                "wooProductId": 55, "wooVariationId": 56, "sku": "ASFPW",
                "productName": "Pure White Chalk Paint", "variantLabel": "1 kg",
                "quantity": 2, "unitGrossHuf": 3700, "taxRatePercent": 27, "lineGrossHuf": 7400
              }]
            }]
            """);

        List<SourceOrder> result = new JsonFileOrderSource(new ObjectMapper(), file).load();

        assertThat(result).hasSize(1);
        SourceOrder o = result.getFirst();
        assertThat(o.wooOrderId()).isEqualTo(1024L);
        assertThat(o.paid()).isTrue();
        assertThat(o.items()).hasSize(1);
        assertThat(o.items().getFirst().sku()).isEqualTo("ASFPW");
        assertThat(o.items().getFirst().lineGrossHuf()).isEqualTo(7400);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=JsonFileOrderSourceTest test`
Expected: FAIL — types undefined.

- [ ] **Step 3: Create the DTOs and source**

`SourceOrderItem.java`:

```java
package hu.deposoft.webshop.integrations.woo;

/** One WooCommerce order line as exported from the WP DB. */
public record SourceOrderItem(
        Long wooProductId,
        Long wooVariationId,
        String sku,
        String productName,
        String variantLabel,
        int quantity,
        long unitGrossHuf,
        int taxRatePercent,
        long lineGrossHuf) {
}
```

`SourceOrder.java`:

```java
package hu.deposoft.webshop.integrations.woo;

import java.util.List;

/** A WooCommerce order (legacy shop_order) as exported from the WP DB. wooStatus is the raw wc-* key. */
public record SourceOrder(
        long wooOrderId,
        String orderKey,
        String wooStatus,
        String currency,
        String createdAt,
        String paidAt,
        Long wpUserId,
        String customerName,
        String email,
        String phone,
        String postcode,
        String city,
        String addressLine,
        String note,
        String shipMethodName,
        long shipGrossHuf,
        long itemsGrossHuf,
        long totalGrossHuf,
        boolean paid,
        String transactionId,
        List<SourceOrderItem> items) {
}
```

`OrderSource.java`:

```java
package hu.deposoft.webshop.integrations.woo;

import java.util.List;

/** Port for a source of historical orders (one implementation per source). */
public interface OrderSource {
    List<SourceOrder> load();
}
```

`JsonFileOrderSource.java`:

```java
package hu.deposoft.webshop.integrations.woo;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

/**
 * Reads a SourceOrder[] snapshot from a JSON file produced by
 * scripts/woo-export/export-orders.py. Mirrors JsonFileCatalogSource.
 */
public class JsonFileOrderSource implements OrderSource {

    private final ObjectMapper objectMapper;
    private final Path file;

    public JsonFileOrderSource(ObjectMapper objectMapper, Path file) {
        this.objectMapper = objectMapper;
        this.file = file;
    }

    @Override
    public List<SourceOrder> load() {
        SourceOrder[] orders = objectMapper.readValue(file.toFile(), SourceOrder[].class);
        return List.of(orders);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=JsonFileOrderSourceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/integrations/woo/SourceOrder.java \
        src/main/java/hu/deposoft/webshop/integrations/woo/SourceOrderItem.java \
        src/main/java/hu/deposoft/webshop/integrations/woo/OrderSource.java \
        src/main/java/hu/deposoft/webshop/integrations/woo/JsonFileOrderSource.java \
        src/test/java/hu/deposoft/webshop/integrations/woo/JsonFileOrderSourceTest.java
git commit -m "feat(orders): source DTOs and JSON order source"
```

---

### Task 5: `OrderImporter` service + report

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/order/OrderImportReport.java`
- Create: `src/main/java/hu/deposoft/webshop/application/order/OrderImporter.java`
- Test: `src/test/java/hu/deposoft/webshop/application/order/OrderImporterResolveTest.java` (unit, Mockito)
- Test: `src/test/java/hu/deposoft/webshop/application/order/OrderImporterTest.java` (integration, Testcontainers)

**Interfaces:**
- Consumes: `VariantRepository.findByExternalId(Long)`, `VariantRepository.findBySku(String)`, `OrderRepository.findByWooOrderId(Long)`, `OrderRepository.save(Order)`, `PaymentRepository.save(Payment)`, `Order.imported(...)`, `Order.addImportedItem(...)`, `OrderItem.create(...)`, `Payment.initiate(...)` + `markState(...)`, `SourceOrder`/`SourceOrderItem`.
- Produces:
  - `OrderImporter.run(List<SourceOrder>) : OrderImportReport`
  - `OrderImporter.mapStatus(String wooStatus) : OrderStatus` (package-private; returns null for unknown)
  - `OrderImporter.resolveVariant(SourceOrderItem) : Variant` (package-private; null when unresolved)
  - `OrderImportReport` getters: `imported()`, `skipped()`, `payments()`, `orphanLines()`, `orphanSkus()`, `unknownStatuses()`, `nonHuf()`.

- [ ] **Step 1: Write the failing unit test (resolution ordering)**

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceOrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderImporterResolveTest {

    @Mock VariantRepository variants;
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock Variant byVariation;
    @Mock Variant bySku;

    @Test
    void prefersVariationIdOverProductIdAndSku() {
        OrderImporter importer = new OrderImporter(variants, orders, payments);
        lenient().when(variants.findByExternalId(56L)).thenReturn(Optional.of(byVariation));
        lenient().when(variants.findBySku("ASFPW")).thenReturn(Optional.of(bySku));

        Variant resolved = importer.resolveVariant(
                new SourceOrderItem(55L, 56L, "ASFPW", "p", "v", 1, 3700, 27, 3700));

        assertThat(resolved).isSameAs(byVariation);
    }

    @Test
    void fallsBackToSkuThenNull() {
        OrderImporter importer = new OrderImporter(variants, orders, payments);
        lenient().when(variants.findByExternalId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(variants.findBySku("ASFPW")).thenReturn(Optional.of(bySku));

        assertThat(importer.resolveVariant(
                new SourceOrderItem(55L, 56L, "ASFPW", "p", "v", 1, 3700, 27, 3700)))
                .isSameAs(bySku);
        assertThat(importer.resolveVariant(
                new SourceOrderItem(55L, 56L, "GONE", "p", "v", 1, 3700, 27, 3700)))
                .isNull();
    }
}
```

- [ ] **Step 2: Run unit test to verify it fails**

Run: `./mvnw -q -Dtest=OrderImporterResolveTest test`
Expected: FAIL — `OrderImporter` undefined.

- [ ] **Step 3: Create the report**

`OrderImportReport.java`:

```java
package hu.deposoft.webshop.application.order;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Mutable run report for the order import: counters plus diagnostic lists. */
public class OrderImportReport {

    private int imported;
    private int skipped;
    private int payments;
    private int orphanLines;
    private final Set<String> orphanSkus = new LinkedHashSet<>();
    private final Set<String> unknownStatuses = new LinkedHashSet<>();
    private final List<Long> nonHuf = new ArrayList<>();

    void importedOrder() { imported++; }
    void skippedOrder() { skipped++; }
    void payment() { payments++; }
    void orphanLine(String sku) { orphanLines++; orphanSkus.add(sku == null ? "(no sku)" : sku); }
    void unknownStatus(String wooStatus) { unknownStatuses.add(wooStatus); }
    void nonHufOrder(long wooOrderId) { nonHuf.add(wooOrderId); }

    public int imported() { return imported; }
    public int skipped() { return skipped; }
    public int payments() { return payments; }
    public int orphanLines() { return orphanLines; }
    public Set<String> orphanSkus() { return Set.copyOf(orphanSkus); }
    public Set<String> unknownStatuses() { return Set.copyOf(unknownStatuses); }
    public List<Long> nonHuf() { return List.copyOf(nonHuf); }

    @Override
    public String toString() {
        return "OrderImportReport{imported=%d, skipped=%d, payments=%d, orphanLines=%d, unknownStatuses=%s, nonHuf=%d}"
                .formatted(imported, skipped, payments, orphanLines, unknownStatuses, nonHuf.size());
    }
}
```

- [ ] **Step 4: Create the importer**

`OrderImporter.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceOrder;
import hu.deposoft.webshop.integrations.woo.SourceOrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Idempotent historical-order migration (cutover). Upserts by woo_order_id;
 * sets the final status directly; creates a single confirmed Payment only when
 * Woo recorded payment. Lines whose product/variant no longer exists are kept
 * with a null variant and their snapshot. No events, no stock, no invoicing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderImporter {

    private final VariantRepository variants;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    /** 1:1 WooCommerce status mapping (lossless). Keys are the wc-* values without the wc- prefix. */
    private static final Map<String, OrderStatus> STATUS = Map.of(
            "completed", OrderStatus.COMPLETED,
            "processing", OrderStatus.PROCESSING,
            "on-hold", OrderStatus.ON_HOLD,
            "cancelled", OrderStatus.CANCELLED,
            "failed", OrderStatus.FAILED,
            "refunded", OrderStatus.REFUNDED,
            "awaiting-shipment", OrderStatus.AWAITING_SHIPMENT);

    @Transactional
    public OrderImportReport run(List<SourceOrder> sources) {
        OrderImportReport report = new OrderImportReport();
        for (SourceOrder src : sources) {
            if (orders.findByWooOrderId(src.wooOrderId()).isPresent()) {
                report.skippedOrder();
                continue;
            }
            OrderStatus status = mapStatus(src.wooStatus());
            if (status == null) {
                report.unknownStatus(src.wooStatus());
                log.warn("Unknown Woo status '{}' on order {} — skipped", src.wooStatus(), src.wooOrderId());
                report.skippedOrder();
                continue;
            }
            if (src.currency() != null && !"HUF".equalsIgnoreCase(src.currency())) {
                report.nonHufOrder(src.wooOrderId());
            }

            Order order = Order.imported(src.wooOrderId(), status, src.customerName(), src.email(),
                    src.phone(), src.postcode(), src.city(), src.addressLine(), src.note(),
                    src.shipMethodName(), src.shipGrossHuf(), src.itemsGrossHuf(), src.totalGrossHuf(),
                    OffsetDateTime.parse(src.createdAt()));

            for (SourceOrderItem line : src.items()) {
                Variant variant = resolveVariant(line);
                if (variant == null) {
                    report.orphanLine(line.sku());
                }
                order.addImportedItem(OrderItem.create(order, variant, line.productName(),
                        line.variantLabel(), line.sku(), line.unitGrossHuf(), line.taxRatePercent(),
                        line.quantity(), InvoiceSource.KULCS_SOFT));
            }
            orders.save(order);
            report.importedOrder();

            if (src.paid()) {
                Payment payment = Payment.initiate(order, "woo-pay-" + src.wooOrderId(), src.totalGrossHuf());
                payment.markState(Payment.State.CONFIRMED, "Imported from WooCommerce");
                payments.save(payment);
                report.payment();
            }
        }
        log.info("Order import finished: {}", report);
        return report;
    }

    OrderStatus mapStatus(String wooStatus) {
        if (wooStatus == null) return null;
        String key = wooStatus.startsWith("wc-") ? wooStatus.substring(3) : wooStatus;
        return STATUS.get(key);
    }

    Variant resolveVariant(SourceOrderItem line) {
        if (line.wooVariationId() != null && line.wooVariationId() != 0) {
            Variant v = variants.findByExternalId(line.wooVariationId()).orElse(null);
            if (v != null) return v;
        }
        if (line.wooProductId() != null) {
            Variant v = variants.findByExternalId(line.wooProductId()).orElse(null);
            if (v != null) return v;
        }
        if (line.sku() != null && !line.sku().isBlank()) {
            return variants.findBySku(line.sku()).orElse(null);
        }
        return null;
    }
}
```

- [ ] **Step 5: Run unit test to verify it passes**

Run: `./mvnw -q -Dtest=OrderImporterResolveTest test`
Expected: PASS

- [ ] **Step 6: Write the failing integration test**

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.integrations.woo.SourceOrder;
import hu.deposoft.webshop.integrations.woo.SourceOrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderImporterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired OrderImporter importer;
    @Autowired OrderRepository orders;

    private SourceOrder order(long id, String status, boolean paid, String sku) {
        return new SourceOrder(id, "wc_order_" + id, status, "HUF",
                "2015-03-01T10:00:00Z", paid ? "2015-03-01T10:05:00Z" : null, 3768L,
                "Nagy Ágnes", "agi@example.com", "+36301", "1011", "Budapest", "Fő utca 1.", null,
                "Foxpost", 990, 7400, 8390, paid, paid ? "txn-" + id : null,
                List.of(new SourceOrderItem(55L, 56L, sku, "Régi termék", "1 kg", 2, 3700, 27, 7400)));
    }

    @Test
    void importsOrphanLineWithNullVariantAndStatus() {
        OrderImportReport report = importer.run(List.of(order(2001, "wc-completed", true, "GONE-SKU")));

        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.orphanLines()).isEqualTo(1);
        assertThat(report.payments()).isEqualTo(1);
        Order saved = orders.findWithItemsByClientKey("woo-2001").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(saved.getItems().getFirst().getVariant()).isNull();
        assertThat(saved.getItems().getFirst().getSku()).isEqualTo("GONE-SKU");
    }

    @Test
    void isIdempotentOnRerun() {
        importer.run(List.of(order(2002, "wc-completed", true, "GONE-SKU")));
        OrderImportReport second = importer.run(List.of(order(2002, "wc-completed", true, "GONE-SKU")));
        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(orders.findByWooOrderId(2002L)).isPresent();
    }

    @Test
    void cancelledUnpaidGetsNoPayment() {
        OrderImportReport report = importer.run(List.of(order(2003, "wc-cancelled", false, "GONE-SKU")));
        assertThat(report.payments()).isZero();
        assertThat(orders.findByWooOrderId(2003L).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void unknownStatusIsReportedNotPersisted() {
        OrderImportReport report = importer.run(List.of(order(2004, "wc-bogus", false, "GONE-SKU")));
        assertThat(report.unknownStatuses()).contains("wc-bogus");
        assertThat(orders.findByWooOrderId(2004L)).isEmpty();
    }
}
```

- [ ] **Step 7: Run the integration test to verify it passes**

Run: `./mvnw -q -Dtest=OrderImporterTest test`
Expected: PASS (this is where V19–V21 migrations are exercised against a real Postgres: new status persists, null `variant_id` accepted, `woo_order_id` unique).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/order/ \
        src/test/java/hu/deposoft/webshop/application/order/
git commit -m "feat(orders): idempotent WooCommerce OrderImporter with report"
```

---

### Task 6: Import runner + profile

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/config/OrderImportRunner.java`
- Create: `src/main/resources/application-import-orders.yml`

**Interfaces:**
- Consumes: `OrderImporter.run(...)`, `JsonFileOrderSource`, property `webshop.import.orders-file`.
- Produces: runnable `import-orders` profile that loads the JSON, imports, logs the report, and exits (0 always; orphan/unknown are diagnostics, not failures).

> **Note on testing:** this is thin CLI glue that calls `System.exit`, exactly like the existing `CatalogImportRunner` / `CustomerImportRunner`, which have no unit tests. Its logic is already covered by Task 4 (JSON parsing) and Task 5 (import). Verification here is the manual dry-run in Step 3.

- [ ] **Step 1: Create the runner**

`OrderImportRunner.java`:

```java
package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.order.OrderImportReport;
import hu.deposoft.webshop.application.order.OrderImporter;
import hu.deposoft.webshop.integrations.woo.JsonFileOrderSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * Manual order-migration trigger (cutover): run with the {@code import-orders}
 * profile and {@code --webshop.import.orders-file=<orders.json>} (produced by
 * scripts/woo-export/export-orders.py), then exit. Idempotent.
 */
@Configuration
@Profile("import-orders")
public class OrderImportRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderImportRunner.class);

    @Bean
    CommandLineRunner importOrders(OrderImporter importer, ObjectMapper objectMapper,
                                   Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.orders-file");
            log.info("Importing orders from {}", file);
            OrderImportReport report =
                    importer.run(new JsonFileOrderSource(objectMapper, Path.of(file)).load());
            log.info("Order import report: {}", report);
            if (!report.orphanSkus().isEmpty()) {
                log.warn("Orphan SKUs (lines kept without a live variant): {}", report.orphanSkus());
            }
            if (!report.unknownStatuses().isEmpty()) {
                log.warn("Unknown Woo statuses (orders skipped): {}", report.unknownStatuses());
            }
            System.exit(SpringApplication.exit(ctx, () -> 0));
        };
    }
}
```

- [ ] **Step 2: Create the profile config**

`application-import-orders.yml`:

```yaml
# Order-migration profile: one-shot import (OrderImportRunner), no web server.
spring:
  main:
    web-application-type: none
```

- [ ] **Step 3: Verify the app still compiles and the context loads**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS. (Full dry-run against real data happens at cutover — Task 9 runbook.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/config/OrderImportRunner.java \
        src/main/resources/application-import-orders.yml
git commit -m "feat(orders): import-orders profile + CLI runner"
```

---

### Task 7: Woo export script

**Files:**
- Create: `scripts/woo-export/export-orders.py`
- Test: `scripts/woo-export/test_export_orders.py`

**Interfaces:**
- Produces: a JSON array of `SourceOrder` objects on stdout (schema = Task 4 DTOs), read by `JsonFileOrderSource`.
- Pure helpers (importable, unit-tested): `to_huf(value)`, `line_gross(line_total, line_tax)`, `unit_gross(line_gross_huf, qty)`, `tax_rate(line_tax, line_subtotal)`.

- [ ] **Step 1: Write the failing test**

`scripts/woo-export/test_export_orders.py`:

```python
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "export_orders", Path(__file__).parent / "export-orders.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def test_line_gross_adds_tax():
    assert m.line_gross("3700", "999") == 4699


def test_unit_gross_divides_by_qty():
    assert m.unit_gross(7400, 2) == 3700


def test_tax_rate_from_amounts():
    assert m.tax_rate("999", "3700") == 27
    assert m.tax_rate("0", "0") == 0


def test_to_huf_rounds():
    assert m.to_huf("8390.0") == 8390
    assert m.to_huf("") is None
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd scripts/woo-export && python3 -m pytest test_export_orders.py -q`
Expected: FAIL — `export-orders.py` missing.

- [ ] **Step 3: Create the export script**

`scripts/woo-export/export-orders.py`:

```python
#!/usr/bin/env python3
"""Export ALL WooCommerce orders (legacy shop_order) from the local wp_db
container to JSON for the OrderImporter. Reads ONLY order/line tables — never
writes. Mirrors export.py / export-customers.py.

Usage:
    python3 scripts/woo-export/export-orders.py > /tmp/woo-orders.json
"""

import json
import subprocess
import sys
from collections import defaultdict
from datetime import datetime, timezone

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"


def rows(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]


def to_huf(value):
    if value is None or value == "":
        return None
    return int(round(float(value)))


def line_gross(line_total, line_tax):
    return int(round(float(line_total or 0) + float(line_tax or 0)))


def unit_gross(line_gross_huf, qty):
    q = int(qty) if qty else 1
    return int(round(line_gross_huf / q)) if q else line_gross_huf


def tax_rate(line_tax, line_subtotal):
    sub = float(line_subtotal or 0)
    if sub <= 0:
        return 0
    return int(round(float(line_tax or 0) / sub * 100))


def iso(dt_str):
    if not dt_str or dt_str.startswith("0000"):
        return None
    return datetime.strptime(dt_str, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc).isoformat()


def main():
    t = lambda name: PREFIX + name

    # 1) Orders with the billing/total meta pulled via correlated subqueries.
    order_rows = rows(f"""
        SELECT JSON_OBJECT(
          'wooOrderId', p.ID,
          'orderKey',   meta('_order_key'),
          'wooStatus',  p.post_status,
          'currency',   meta('_order_currency'),
          'createdAt',  DATE_FORMAT(p.post_date_gmt, '%Y-%m-%d %H:%i:%s'),
          'paidAt',     meta('_date_paid'),
          'wpUserId',   NULLIF(meta('_customer_user'), '0'),
          'customerName', TRIM(CONCAT(COALESCE(meta('_billing_first_name'),''),' ',
                                       COALESCE(meta('_billing_last_name'),''))),
          'email',      meta('_billing_email'),
          'phone',      meta('_billing_phone'),
          'postcode',   meta('_billing_postcode'),
          'city',       meta('_billing_city'),
          'addressLine',TRIM(CONCAT(COALESCE(meta('_billing_address_1'),''),' ',
                                     COALESCE(meta('_billing_address_2'),''))),
          'orderShipping', meta('_order_shipping'),
          'orderShippingTax', meta('_order_shipping_tax'),
          'orderTotal', meta('_order_total'),
          'transactionId', meta('_transaction_id')
        )
        FROM {t('posts')} p
        WHERE p.post_type = 'shop_order'
        ORDER BY p.ID
    """.replace("meta(", f"(SELECT meta_value FROM {t('postmeta')} pm "
                         f"WHERE pm.post_id = p.ID AND pm.meta_key = "))
    # NB: the meta() shorthand above expands to a correlated subquery; the closing
    # paren of each meta('_key') call closes the subquery's WHERE comparison.

    # 2) Shipping method name per order (first shipping line item).
    ship_rows = rows(f"""
        SELECT JSON_OBJECT('orderId', i.order_id, 'name', i.order_item_name)
        FROM {t('woocommerce_order_items')} i
        WHERE i.order_item_type = 'shipping'
        GROUP BY i.order_id
    """)
    ship_name = {int(r["orderId"]): r["name"] for r in ship_rows}

    # 3) Line items with product/variation ids, qty, totals, and resolved SKU.
    line_rows = rows(f"""
        SELECT JSON_OBJECT(
          'orderId', i.order_id,
          'productName', i.order_item_name,
          'wooProductId', im('_product_id'),
          'wooVariationId', im('_variation_id'),
          'qty', im('_qty'),
          'lineTotal', im('_line_total'),
          'lineTax', im('_line_tax'),
          'lineSubtotal', im('_line_subtotal'),
          'sku', (SELECT pm.meta_value FROM {t('postmeta')} pm
                  WHERE pm.meta_key = '_sku' AND pm.post_id =
                        COALESCE(NULLIF(im('_variation_id'),'0'), im('_product_id')) LIMIT 1)
        )
        FROM {t('woocommerce_order_items')} i
        WHERE i.order_item_type = 'line_item'
        ORDER BY i.order_item_id
    """.replace("im(", f"(SELECT meta_value FROM {t('woocommerce_order_itemmeta')} m "
                       f"WHERE m.order_item_id = i.order_item_id AND m.meta_key = "))

    items_by_order = defaultdict(list)
    for r in line_rows:
        lg = line_gross(r.get("lineTotal"), r.get("lineTax"))
        qty = int(float(r.get("qty") or 1))
        items_by_order[int(r["orderId"])].append({
            "wooProductId": to_huf(r.get("wooProductId")),
            "wooVariationId": to_huf(r.get("wooVariationId")),
            "sku": r.get("sku"),
            "productName": r.get("productName") or "—",
            "variantLabel": None,
            "quantity": qty,
            "unitGrossHuf": unit_gross(lg, qty),
            "taxRatePercent": tax_rate(r.get("lineTax"), r.get("lineSubtotal")),
            "lineGrossHuf": lg,
        })

    orders = []
    for o in order_rows:
        oid = int(o["wooOrderId"])
        items = items_by_order.get(oid, [])
        ship_gross = (to_huf(o.get("orderShipping")) or 0) + (to_huf(o.get("orderShippingTax")) or 0)
        paid = bool(o.get("paidAt")) or bool(o.get("transactionId"))
        orders.append({
            "wooOrderId": oid,
            "orderKey": o.get("orderKey"),
            "wooStatus": o.get("wooStatus"),
            "currency": o.get("currency"),
            "createdAt": iso(o.get("createdAt")),
            "paidAt": None,  # timestamp not preserved on payment; presence drives `paid`
            "wpUserId": to_huf(o.get("wpUserId")),
            "customerName": (o.get("customerName") or "").strip() or "—",
            "email": o.get("email") or "—",
            "phone": o.get("phone"),
            "postcode": o.get("postcode") or "—",
            "city": o.get("city") or "—",
            "addressLine": (o.get("addressLine") or "").strip() or "—",
            "note": None,
            "shipMethodName": ship_name.get(oid),
            "shipGrossHuf": ship_gross,
            "itemsGrossHuf": sum(i["lineGrossHuf"] for i in items),
            "totalGrossHuf": to_huf(o.get("orderTotal")) or 0,
            "paid": paid,
            "transactionId": o.get("transactionId"),
            "items": items,
        })

    json.dump(orders, sys.stdout, ensure_ascii=False, indent=1)
    print(f"\nexported {len(orders)} orders", file=sys.stderr)


if __name__ == "__main__":
    main()
```

> **Implementer note:** the `meta(...)`/`im(...)` `.replace(...)` shorthands expand to correlated subqueries before the query is sent. After writing the file, run the dry-run in Step 5 and eyeball one order's JSON; if the generated SQL is hard to follow, inline the subqueries by hand instead — the shorthand is a convenience, not a requirement.

- [ ] **Step 4: Run the helper unit tests to verify they pass**

Run: `cd scripts/woo-export && python3 -m pytest test_export_orders.py -q`
Expected: PASS (4 tests).

- [ ] **Step 5: Dry-run against the container (sanity check)**

Run: `python3 scripts/woo-export/export-orders.py > /tmp/woo-orders.json && python3 -c "import json;d=json.load(open('/tmp/woo-orders.json'));print(len(d), d[0]['wooStatus'], d[0]['totalGrossHuf'], len(d[0]['items']))"`
Expected: prints `~22713` and a sample order's status/total/line count. Spot-check that `totalGrossHuf` matches the Woo order and statuses look like `wc-completed`.

- [ ] **Step 6: Commit**

```bash
git add scripts/woo-export/export-orders.py scripts/woo-export/test_export_orders.py
git commit -m "feat(orders): export-orders.py Woo→JSON export with tested arithmetic"
```

---

### Task 8: Admin UI — Hungarian labels for the new statuses

**Files:**
- Modify: `admin-ui/src/types.ts`
- Modify: `admin-ui/src/api/orders.ts`

**Interfaces:**
- Consumes: `OrderStatus` union.
- Produces: `STATUS_LABELS` and `STATUS_COLORS` cover all 11 statuses (TypeScript `Record<OrderStatus, …>` compiles only when exhaustive). `NEXT`/`TARGET_STATUSES` unchanged — the new statuses are historical/terminal and admin-non-transitionable.

- [ ] **Step 1: Extend the `OrderStatus` union**

In `admin-ui/src/types.ts`:

```typescript
export type OrderStatus =
  | "NEW"
  | "PAID"
  | "PACKING"
  | "SHIPPED"
  | "COMPLETED"
  | "CANCELLED"
  | "REFUNDED"
  | "PROCESSING"
  | "ON_HOLD"
  | "FAILED"
  | "AWAITING_SHIPMENT";
```

- [ ] **Step 2: Verify the typecheck now fails (non-exhaustive Records)**

Run: `cd admin-ui && npx tsc --noEmit`
Expected: FAIL — `STATUS_COLORS`/`STATUS_LABELS` missing the 4 new keys (`Record<OrderStatus,…>` is now incomplete).

- [ ] **Step 3: Add labels and colors**

In `admin-ui/src/api/orders.ts`, extend `STATUS_COLORS`:

```typescript
  REFUNDED: "purple",
  PROCESSING: "gold",
  ON_HOLD: "orange",
  FAILED: "red",
  AWAITING_SHIPMENT: "geekblue",
```

and `STATUS_LABELS`:

```typescript
  REFUNDED: "Visszatérítve",
  PROCESSING: "Feldolgozás alatt (Woo)",
  ON_HOLD: "Fizetésre vár (Woo)",
  FAILED: "Sikertelen (Woo)",
  AWAITING_SHIPMENT: "Szállításra vár (Woo)",
```

(`NEXT` and `TARGET_STATUSES` are left unchanged — imported statuses are not admin-transitionable.)

- [ ] **Step 4: Verify the typecheck passes**

Run: `cd admin-ui && npx tsc --noEmit`
Expected: PASS (no errors).

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/types.ts admin-ui/src/api/orders.ts
git commit -m "feat(admin-ui): Hungarian labels for imported Woo order statuses"
```

---

### Task 9: ADR + reset script + cutover runbook

**Files:**
- Create: `docs/adr/0011-migrate-woo-orders.md`
- Create: `scripts/woo-export/reset-app-data.sql`
- Create: `docs/superpowers/plans/cutover-runbook.md`

**Interfaces:** none (documentation + human-run SQL).

- [ ] **Step 1: Write the ADR**

`docs/adr/0011-migrate-woo-orders.md`:

```markdown
# 11. Migrate WooCommerce orders into the local DB

Date: 2026-06-19
Status: Accepted

## Context

TERV.md §7 originally stated that historical WooCommerce orders would not be
migrated — they would stay archived read-only in WordPress. For go-live cutover
the decision was reversed: the new app becomes the single source of truth for
orders, so all 22,713 historical orders are imported into the local DB.

## Decision

Import all Woo orders (every status) read-only into `orders`/`order_item`/
`payment`, mirroring the catalog/customer import pipeline. Status is mapped 1:1
(lossless): four import-only statuses (PROCESSING, ON_HOLD, FAILED,
AWAITING_SHIPMENT) are added. Order lines for discontinued products are kept
with a null variant and their snapshot. A single confirmed Payment is created
only when Woo recorded payment. No invoices are imported (issued externally).
Import fires no events and touches no stock.

## Consequences

- TERV.md §7 is superseded for cutover.
- The order state machine gains terminal import-only states (native checkout
  unaffected).
- `order_item.variant_id` is now nullable.
- Merging/standardising the imported statuses is deferred.
```

- [ ] **Step 2: Write the reset script**

`scripts/woo-export/reset-app-data.sql`:

```sql
-- HUMAN-RUN ONLY. Wipes test data before the real cutover import.
-- Run against the TARGET app DB (never automated; never against production
-- without sign-off). Order of truncation respects FKs via CASCADE.
-- Cutover sequence: this script -> import catalog -> import customers -> import orders.
BEGIN;
TRUNCATE TABLE payment, invoice, order_item, orders, reservation, customer
    RESTART IDENTITY CASCADE;
COMMIT;
```

- [ ] **Step 3: Write the cutover runbook**

`docs/superpowers/plans/cutover-runbook.md`:

```markdown
# Cutover runbook: WooCommerce → local DB

All steps are human-run against the target environment. The Woo side is read-only.

1. **Export from Woo** (reads `wp_db`, writes JSON):
   - `python3 scripts/woo-export/export.py > /tmp/woo-catalog.json`
   - `python3 scripts/woo-export/export-customers.py > /tmp/woo-customers.json`
   - `python3 scripts/woo-export/export-orders.py > /tmp/woo-orders.json`
2. **Reset app data** (destructive — confirm target DB first):
   - `psql "$APP_DB_URL" -f scripts/woo-export/reset-app-data.sql`
3. **Import, in order:**
   - catalog: `--spring.profiles.active=import --webshop.import.file=/tmp/woo-catalog.json`
   - customers: `--spring.profiles.active=import-customers --webshop.import.customers-file=/tmp/woo-customers.json`
   - orders: `--spring.profiles.active=import-orders --webshop.import.orders-file=/tmp/woo-orders.json`
4. **Review the order import report** in the logs: orphan SKUs (lines without a
   live variant) and any unknown statuses. Decide whether orphan coverage is
   acceptable before opening the shop.
```

- [ ] **Step 4: Commit**

```bash
git add docs/adr/0011-migrate-woo-orders.md \
        scripts/woo-export/reset-app-data.sql \
        docs/superpowers/plans/cutover-runbook.md
git commit -m "docs(orders): ADR, human-run reset script, and cutover runbook"
```

---

## Final verification

- [ ] Run the full backend suite: `./mvnw -q test` — Expected: all green (existing 109+ tests plus the new order tests).
- [ ] Admin UI typecheck: `cd admin-ui && npx tsc --noEmit` — Expected: clean.
- [ ] Confirm migration ordering: `ls src/main/resources/db/migration/ | sort -V | tail -4` shows V18…V21.
- [ ] Confirm ADR number `0011` is unused: `ls docs/adr/` (renumber if a 0011 already exists).
