# Admin Order Status Machine (Slice 2a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin drive an order's fulfilment flow (PAID→PACKING→SHIPPED→COMPLETED, and cancel of unpaid NEW orders) from the admin SPA, with every transition audited.

**Architecture:** A new `OrderAdminService.transition(orderId, target)` applies an admin policy (no PAID target; cancel only from NEW) then the existing domain gate (`Order.transitionTo`) and records an audit entry. A thin `POST /api/admin/orders/{id}/transition` returns the updated `OrderDetail`. The Refine `OrderShow` page renders action buttons for the allowed next state(s). No money movement (refund/credit-note is slice 2b).

**Tech Stack:** Spring Boot 4.1, Java 21, Spring Data JPA, Testcontainers + `@SpringBootTest`; Refine + TypeScript SPA (`admin-ui/`, antd).

**Conventions:**
- Backend tests: `@SpringBootTest @Testcontainers @Transactional`, `PostgreSQLContainer<>("postgres:17")` + `@ServiceConnection`; MockMvc via `webAppContextSetup(context).apply(springSecurity()).build()`, admin via `.with(user("a@example.com").roles("ADMIN"))`, mutations `.with(csrf())`.
- Run backend tests with Ryuk disabled and the frontend build skipped:
  `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=<Class> test`
  then clean leftover containers: `docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`.
- Frontend gate: `cd admin-ui && npm run build`.
- Do NOT stop/restart the app on port 8085.

## Existing pieces (do not recreate)
- `OrderStatus` (NEW, PAID, PACKING, SHIPPED, COMPLETED, CANCELLED) with `canTransitionTo`; `Order.transitionTo(target)` throws `IllegalStateException` on an illegal hop.
- `OrderAdminQueryService.detail(Long id) -> OrderDetail`; nested `OrderAdminQueryService.NotFoundException` (already mapped to 404 by `AdminExceptionHandler`).
- `OrderAdminController` (`/api/admin/orders`, read-only list + `/{id}`).
- `AuditService.record(action, entityType, entityId, summary)`.
- `OrderRepository extends JpaRepository<Order, Long>`.
- `AdminExceptionHandler` maps SessionHasBookings→409, the two NotFound→404, IllegalArgument→400.
- Frontend: `admin-ui/src/pages/orders/show.tsx` (`OrderShow`, uses `useShow`), `admin-ui/src/api/http.ts` (`apiFetch`), `admin-ui/src/types.ts` (`OrderDetail`, `OrderStatus`).

## File Structure
- Create `src/main/java/hu/deposoft/webshop/application/order/OrderAdminService.java` — transition use case + `TransitionNotAllowedException`.
- Modify `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java` — map the new exception to 409.
- Modify `src/main/java/hu/deposoft/webshop/api/admin/OrderAdminController.java` — add the transition endpoint.
- Modify `admin-ui/src/pages/orders/show.tsx` — action buttons.
- Tests: `src/test/java/hu/deposoft/webshop/application/order/OrderAdminServiceTest.java` (new), `src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java` (extend).

---

## Task 1: `OrderAdminService.transition` (status machine + audit)

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/order/OrderAdminService.java`
- Test: `src/test/java/hu/deposoft/webshop/application/order/OrderAdminServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `OrderAdminServiceTest.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class OrderAdminServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    OrderAdminService service;

    @Autowired
    OrderRepository orders;

    @Autowired
    AuditEntryRepository audit;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    CatalogImporter importer;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint)));
    }

    private Order newOrder(String key) {
        String token = cart.addItem(null, "FES-1", 1).token();
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt Elek", "t@example.com",
                null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
    }

    /** Drive an order to PAID without going through the gateway. */
    private Order paidOrder(String key) {
        Order o = newOrder(key);
        o.transitionTo(OrderStatus.PAID);
        return orders.save(o);
    }

    @Test
    void advancesPaidOrderThroughFulfilment() {
        Long id = paidOrder("t-chain").getId();

        service.transition(id, OrderStatus.PACKING);
        service.transition(id, OrderStatus.SHIPPED);
        service.transition(id, OrderStatus.COMPLETED);

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order", String.valueOf(id)))
                .extracting(e -> e.getAction()).contains("ORDER_STATUS_CHANGE");
    }

    @Test
    void rejectsSettingPaidFromAdmin() {
        Long id = newOrder("t-paid").getId();
        assertThatThrownBy(() -> service.transition(id, OrderStatus.PAID))
                .isInstanceOf(OrderAdminService.TransitionNotAllowedException.class);
    }

    @Test
    void rejectsIllegalHop() {
        Long id = paidOrder("t-hop").getId(); // PAID -> COMPLETED is not allowed by the domain gate
        assertThatThrownBy(() -> service.transition(id, OrderStatus.COMPLETED))
                .isInstanceOf(OrderAdminService.TransitionNotAllowedException.class);
    }

    @Test
    void cancelsUnpaidOrder() {
        Long id = newOrder("t-cancel").getId();
        service.transition(id, OrderStatus.CANCELLED);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void rejectsCancellingPaidOrder() {
        Long id = paidOrder("t-paid-cancel").getId();
        assertThatThrownBy(() -> service.transition(id, OrderStatus.CANCELLED))
                .isInstanceOf(OrderAdminService.TransitionNotAllowedException.class);
    }

    @Test
    void unknownOrderIsNotFound() {
        assertThatThrownBy(() -> service.transition(999999L, OrderStatus.PACKING))
                .isInstanceOf(OrderAdminQueryService.NotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=OrderAdminServiceTest test`
Expected: FAIL — `OrderAdminService` does not exist (compile error).

- [ ] **Step 3: Write the service**

Create `OrderAdminService.java`:

```java
package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-driven order status transitions (T16 slice 2a): the daily fulfilment
 * flow. Applies an admin policy on top of the domain state machine and audits
 * every change. No money movement — paid-order cancel + refund is slice 2b.
 */
@Service
@RequiredArgsConstructor
public class OrderAdminService {

    private final OrderRepository orders;
    private final AuditService audit;

    /** Raised when a transition is not permitted (admin policy or domain sequence). */
    public static class TransitionNotAllowedException extends RuntimeException {
        public TransitionNotAllowedException(String message) {
            super(message);
        }
    }

    @Transactional
    public void transition(Long orderId, OrderStatus target) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order " + orderId));
        OrderStatus current = order.getStatus();

        // admin policy (before the domain gate)
        if (target == OrderStatus.PAID) {
            throw new TransitionNotAllowedException("PAID is set by payment, not by the admin");
        }
        if (target == OrderStatus.CANCELLED && current != OrderStatus.NEW) {
            throw new TransitionNotAllowedException(
                    "Cancelling a " + current + " order needs a refund — that is slice 2b");
        }

        try {
            order.transitionTo(target); // domain gate enforces the legal sequence
        } catch (IllegalStateException e) {
            throw new TransitionNotAllowedException(e.getMessage());
        }
        audit.record("ORDER_STATUS_CHANGE", "order", String.valueOf(orderId), current + "→" + target);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=OrderAdminServiceTest test`
Expected: `Tests run: 6, Failures: 0, Errors: 0`, BUILD SUCCESS. Then clean containers:
`docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/order/OrderAdminService.java \
        src/test/java/hu/deposoft/webshop/application/order/OrderAdminServiceTest.java
git commit -m "Admin order 2a: OrderAdminService.transition (status machine + audit)"
```

---

## Task 2: Map `TransitionNotAllowedException` → 409

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`

- [ ] **Step 1: Add the handler**

Add the import:

```java
import hu.deposoft.webshop.application.order.OrderAdminService;
```

Add this method inside the class (after `orderNotFound`):

```java
    @ExceptionHandler(OrderAdminService.TransitionNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String transitionNotAllowed(OrderAdminService.TransitionNotAllowedException e) {
        return e.getMessage();
    }
```

- [ ] **Step 2: Compile**

Run: `mvn -q -Dskip.frontend=true compile`
Expected: exit 0 (no output).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java
git commit -m "Admin order 2a: map TransitionNotAllowedException to 409"
```

---

## Task 3: Transition endpoint on `OrderAdminController`

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/OrderAdminController.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java`

- [ ] **Step 1: Write the failing test (extend the controller test)**

Add to `OrderAdminControllerTest.java`. First add imports (if absent):

```java
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```

Add `@Autowired OrderRepository orders;` to the fields, and these tests:

```java
    @Test
    void transitionAdvancesStatusForAdmin() throws Exception {
        // the @BeforeEach seeds one NEW order; drive it to PAID first
        Order order = orders.findAll().getFirst();
        order.transitionTo(OrderStatus.PAID);
        orders.save(order);

        mvc.perform(post("/api/admin/orders/" + order.getId() + "/transition")
                        .with(user("a@example.com").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"PACKING\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PACKING")));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PACKING);
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        Order order = orders.findAll().getFirst(); // still NEW
        mvc.perform(post("/api/admin/orders/" + order.getId() + "/transition")
                        .with(user("a@example.com").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void transitionRejectsNonAdmin() throws Exception {
        Order order = orders.findAll().getFirst();
        mvc.perform(post("/api/admin/orders/" + order.getId() + "/transition")
                        .with(user("c@example.com").roles("CUSTOMER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"PACKING\"}"))
                .andExpect(status().isForbidden());
    }
```

> Note: the existing `OrderAdminControllerTest` is `@Transactional` and seeds exactly one order in `@BeforeEach` (customer "Kovács Béla"). `orders.findAll().getFirst()` returns it. Confirm `OrderRepository` import is present.

- [ ] **Step 2: Run the test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=OrderAdminControllerTest test`
Expected: FAIL — no `/transition` mapping (404/405), so the assertions fail.

- [ ] **Step 3: Add the endpoint**

In `OrderAdminController.java`, add imports:

```java
import hu.deposoft.webshop.application.order.OrderAdminService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

Add `OrderAdminService` to the constructor dependencies (the class is `@RequiredArgsConstructor`):

```java
    private final OrderAdminService orderAdmin;
```

Add a request record and the endpoint (inside the class):

```java
    public record TransitionRequest(OrderStatus status) {
    }

    @PostMapping("/{id}/transition")
    public OrderDetail transition(@PathVariable Long id, @RequestBody TransitionRequest req) {
        orderAdmin.transition(id, req.status());
        return query.detail(id);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true -Dtest=OrderAdminControllerTest test`
Expected: all tests green (the prior read tests + the 3 new ones). Then clean containers as above.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/OrderAdminController.java \
        src/test/java/hu/deposoft/webshop/api/admin/OrderAdminControllerTest.java
git commit -m "Admin order 2a: POST /api/admin/orders/{id}/transition"
```

---

## Task 4: Order detail action buttons (Refine SPA)

**Files:**
- Modify: `admin-ui/src/pages/orders/show.tsx`

- [ ] **Step 1: Replace `show.tsx` with the action-enabled version**

Replace the whole file with:

```tsx
import { Show } from "@refinedev/antd";
import { useShow } from "@refinedev/core";
import { App, Button, Descriptions, Popconfirm, Space, Table, Tag, Typography } from "antd";
import { apiFetch, API_BASE } from "../../api/http";
import type { OrderDetail, OrderLine, OrderStatus } from "../../types";

const huf = (n: number) => `${n.toLocaleString("hu-HU")} Ft`;

// the single allowed next state per status for the daily fulfilment flow (2a)
const NEXT: Partial<Record<OrderStatus, { target: OrderStatus; label: string; confirm?: boolean }>> = {
  NEW: { target: "CANCELLED", label: "Lemondás", confirm: true },
  PAID: { target: "PACKING", label: "Csomagolásba" },
  PACKING: { target: "SHIPPED", label: "Feladva" },
  SHIPPED: { target: "COMPLETED", label: "Teljesítve" },
};

export const OrderShow = () => {
  const { queryResult } = useShow<OrderDetail>();
  const { message } = App.useApp();
  const order = queryResult?.data?.data;

  const transition = async (target: OrderStatus) => {
    const res = await apiFetch(`${API_BASE}/orders/${order!.id}/transition`, {
      method: "POST",
      body: JSON.stringify({ status: target }),
    });
    if (res.ok) {
      message.success("Státusz frissítve");
      queryResult?.refetch();
    } else {
      const text = await res.text().catch(() => "");
      message.error(text || `Nem sikerült (${res.status})`);
    }
  };

  const next = order ? NEXT[order.status] : undefined;

  return (
    <Show isLoading={queryResult?.isLoading} title="Rendelés">
      {order && (
        <>
          <Space style={{ marginBottom: 16 }}>
            <Tag>{order.status}</Tag>
            {next &&
              (next.confirm ? (
                <Popconfirm title={`${next.label}?`} onConfirm={() => transition(next.target)}>
                  <Button danger>{next.label}</Button>
                </Popconfirm>
              ) : (
                <Button type="primary" onClick={() => transition(next.target)}>
                  {next.label}
                </Button>
              ))}
          </Space>

          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="Rendelésszám">{order.orderNumber}</Descriptions.Item>
            <Descriptions.Item label="Státusz">
              <Tag>{order.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Vásárló">{order.customerName}</Descriptions.Item>
            <Descriptions.Item label="Email">{order.email}</Descriptions.Item>
            <Descriptions.Item label="Telefon">{order.phone ?? "—"}</Descriptions.Item>
            <Descriptions.Item label="Létrehozva">
              {new Date(order.createdAt).toLocaleString("hu-HU")}
            </Descriptions.Item>
            <Descriptions.Item label="Szállítás" span={2}>
              {order.shipMethodName} — {huf(order.shipGrossHuf)}
            </Descriptions.Item>
            <Descriptions.Item label="Cím" span={2}>
              {order.postcode} {order.city}, {order.addressLine}
            </Descriptions.Item>
          </Descriptions>

          <Typography.Title level={5} style={{ marginTop: 24 }}>
            Tételek
          </Typography.Title>
          <Table dataSource={order.lines} rowKey={(_, i) => String(i)} pagination={false} size="small">
            <Table.Column title="Termék" dataIndex="productName" />
            <Table.Column
              title="Változat"
              dataIndex="variantLabel"
              render={(v: string | null) => v ?? "—"}
            />
            <Table.Column title="Cikkszám" dataIndex="sku" render={(v: string | null) => v ?? "—"} />
            <Table.Column title="Db" dataIndex="quantity" />
            <Table.Column<OrderLine>
              title="Sor összege"
              dataIndex="lineGrossHuf"
              render={(n: number) => huf(n)}
            />
          </Table>

          <Typography.Paragraph style={{ marginTop: 16, textAlign: "right" }}>
            <strong>Termékek: {huf(order.itemsGrossHuf)}</strong>
            <br />
            <strong>Összesen: {huf(order.totalGrossHuf)}</strong>
          </Typography.Paragraph>
        </>
      )}
    </Show>
  );
};
```

- [ ] **Step 2: Build**

Run: `cd admin-ui && npm run build`
Expected: `✓ built` (no TS errors); bundle emitted under `src/main/resources/static/admin`.

- [ ] **Step 3: Commit**

```bash
git add admin-ui/src/pages/orders/show.tsx
git commit -m "Admin order 2a: status action buttons on the order detail page"
```

---

## Task 5: Full verification

- [ ] **Step 1: Backend suite (Ryuk disabled, frontend skipped)**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn -Dskip.frontend=true verify`
Expected: BUILD SUCCESS, all tests pass (prior count + new `OrderAdminServiceTest` (6) + 3 new controller tests). ArchUnit `ModularityTest` green (the new service is `application`, the endpoint `api`). Then clean leftover containers:
`docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f`

- [ ] **Step 2: Frontend build**

Run: `cd admin-ui && npm run build`
Expected: `✓ built`, no TS errors.

- [ ] **Step 3: Commit any fixups**

```bash
git add -A && git commit -m "Admin order 2a: full verify green" || echo "nothing to commit"
```

---

## Self-Review

**Spec coverage:**
- Backend `OrderAdminService.transition` (policy → domain gate → audit) → Task 1. ✓
- Admin policy: PAID target rejected, cancel only from NEW → Task 1 tests `rejectsSettingPaidFromAdmin`, `rejectsCancellingPaidOrder`, `cancelsUnpaidOrder`. ✓
- Domain sequence enforced (illegal hop) → Task 1 `rejectsIllegalHop`. ✓
- `TransitionNotAllowedException` → 409 → Task 2 + Task 3 `illegalTransitionReturns409`. ✓
- `POST /api/admin/orders/{id}/transition` returning OrderDetail, ADMIN-gated → Task 3. ✓
- Audit `ORDER_STATUS_CHANGE` old→new → Task 1 (`advancesPaidOrderThroughFulfilment` asserts the audit entry). ✓
- UI action buttons for allowed next state + 409 error message → Task 4. ✓
- Testing (backend chain/policy/409/403, frontend build) → Tasks 1, 3, 5. ✓

**Placeholder scan:** none.

**Type consistency:** `OrderAdminService.transition(Long, OrderStatus)` and `TransitionNotAllowedException` used identically in Tasks 1–3; `OrderAdminQueryService.NotFoundException` (404, existing) used for the missing-order case; controller `TransitionRequest(OrderStatus status)` matches the SPA body `{ "status": "PACKING" }`; SPA `NEXT` map targets match `OrderStatus` values.

**Check-before-use:** confirm `OrderRepository` import is added to `OrderAdminControllerTest` (Task 3 Step 1); confirm `useShow` in this Refine version exposes `queryResult.refetch()` (it does in @refinedev/core v4) — if named `query` instead, use that.
