# Admin order management — slice 2b-3: workshop booking reschedule — design

## Context

After 2b-2 (cancel a workshop booking), 2b-3 is the **áthelyezés**: move an attendee
(an `OrderItem` line) from one workshop session to another session of the **same
workshop**. This was part of the original ask ("lemondásra, áthelyezésre").

Existing pieces this builds on:
- `OrderItem` (variant, sku, variantLabel, unitGrossHuf, taxRatePercent, quantity,
  lineGrossHuf, cancelledQuantity). Currently no setters — mutations go through domain
  methods (`cancelWholeLine`).
- `WorkshopSession` (a seat `Variant` + `startAt` + `capacity`); `WorkshopSessionRepository`.
  A workshop is a `Product`; each session is a seat `Variant` (sku = session sku, price =
  `regularPriceHuf`), created by `WorkshopService.addSession`.
- `AvailabilityService.availableQty(variant, ownCartToken)` — for a workshop seat returns
  `capacity − non-cancelled bookings (orderedQuantitySince) − foreign holds`; and
  `hasStock(variant, ownCartToken, qty)`. `AvailabilityService.NO_CART` is the
  "no own cart" token (every hold counts as foreign).
- Booking counts are **per variant** via `OrderRepository.orderedQuantitySince`, so
  changing `order_item.variant_id` moves the seat between sessions automatically.
- `BookingCancellationService` (application/order) — the sibling pattern; `AuditService`.
- The admin `workshops/edit.tsx` attendee table (per-row actions; "Lemondás" shipped in
  2b-2) — the `Sessions` component already holds the sessions list + per-session booking
  counts.

## Decisions (confirmed)

- **Same-price only** in 2b-3: reschedule is allowed only when the target session's seat
  price equals the line's unit price → **no money moves** (no gateway call, no credit
  note). A differing price is rejected (409). Price-delta (top-up / partial refund) is a
  later slice (2b-3b).
- Reschedule is restricted to another session of the **same workshop** (same product).
- No schema change: the move is a re-point of `order_item.variant_id` + `sku` snapshot.

## 1. Domain change

Add a thin mutator to `OrderItem`:
- `moveToSeat(Variant targetSeat)`: defensively require `targetSeat.getRegularPriceHuf() ==
  this.unitGrossHuf` (else `IllegalStateException`); re-point `variant = targetSeat` and
  update `sku = targetSeat.getSku()`. `unitGrossHuf`, `lineGrossHuf`, `taxRatePercent`,
  `productName`, `variantLabel` are unchanged (same workshop, same price). The policy
  guards (status, same-workshop, capacity, same-price→409) live in the service; this
  method is the last-line invariant check.

## 2. `BookingRescheduleService` (application/order)

`reschedule(Long orderItemId, Long targetSessionId)` (`@Transactional`), deps:
`OrderItemRepository`, `WorkshopSessionRepository`, `AvailabilityService`, `AuditService`.
Two nested public static exceptions (mirroring `BookingCancellationService`):
`RescheduleNotAllowedException` (→ **409**).

Flow:
1. Load `OrderItem` (→ `OrderAdminQueryService.NotFoundException` → **404** if missing).
2. Load target `WorkshopSession` by id (→ `WorkshopService.NotFoundException` → **404** if
   missing). Its seat = `session.getVariant()`.
3. Guards (each → `RescheduleNotAllowedException`):
   - order status ∈ {`NEW`, `PAID`, `PACKING`} (not `CANCELLED`/`REFUNDED`/terminal);
   - line not fully cancelled (`cancelledQuantity < quantity`);
   - target seat's product == the line's current variant's product (same workshop);
   - target seat != current variant (no-op move rejected);
   - **same price:** `targetSeat.getRegularPriceHuf() == line.getUnitGrossHuf()`;
   - **capacity:** `availabilityService.hasStock(targetSeat, AvailabilityService.NO_CART,
     effectiveSeats)` where `effectiveSeats = quantity − cancelledQuantity`.
4. On success: `line.moveToSeat(targetSeat)`; `audit.record("BOOKING_RESCHEDULED",
   "order_item", String.valueOf(orderItemId), "<orderNo> <oldSku>→<newSku>
   (<oldStartAt>→<newStartAt>)")`. Order status, payment, and invoices are untouched.

The capacity check is correct without double-counting: the line currently counts toward
the **source** variant, so `availableQty(targetSeat)` already excludes it.

## 3. Admin API + UI

- `POST /api/admin/order-items/{itemId}/reschedule` (ADMIN-gated, CSRF), body
  `RescheduleRequest{ @NotNull Long targetSessionId }` → `BookingRescheduleService.reschedule`,
  returns `RescheduleResult(long orderItemId, long sessionId)`. `AdminExceptionHandler`
  maps `RescheduleNotAllowedException` → **409** (`NotFoundException`s already mapped to 404).
- `workshops/edit.tsx` attendee table: an **"Áthelyezés"** button next to "Lemondás"
  (hidden when the row is fully cancelled, i.e. `cancelledSeats >= seats`). It opens an
  antd **Modal** with a `Select` of the workshop's **other** sessions (option label:
  `YYYY. MM. DD. HH:mm — foglalt/kapacitás — ár`); options whose price differs from the
  attendee's line or that have no free seat are disabled. Confirm → POST → on success
  `message.success` + `reload()`; on 409/404 show the server message. The picker uses the
  `sessions` + `bookingsBySession` already in the component (no new fetch). The booking
  row needs the seat unit price to filter same-price targets — expose `unitGrossHuf` on the
  `WorkshopBooking` DTO (see §4).

## 4. DTO addition

`WorkshopBookingView` / `WorkshopBooking` gains `long unitGrossHuf` (the line's unit price),
mapped from `oi.getUnitGrossHuf()` in `WorkshopService.bookings`, so the UI can disable
different-price target sessions. (`lineGrossHuf`, `orderItemId`, `cancelledSeats` already
present from 2b-2.) Session price for the picker comes from the existing
`WorkshopSession`/variant price already shown in the sessions table (`priceHuf`).

## 5. Testing

- **Backend (Testcontainers):**
  - happy path: same-price target with capacity → `order_item.variant` re-pointed, `sku`
    updated; `availableQty(source)` increases by the seats and `availableQty(target)`
    decreases by the seats; audit `BOOKING_RESCHEDULED`; order status + payment unchanged;
    no new `invoice` rows created.
  - guards → `RescheduleNotAllowedException`: target price differs; target full
    (capacity already consumed); order `CANCELLED`/`REFUNDED`; line fully cancelled;
    target session of a different workshop; target == source.
  - 404: unknown order item; unknown target session.
- **Controller:** 200 + `RescheduleResult`; 409 for a rejected move; 404 unknown item /
  unknown session; 403 non-admin.
- **Frontend:** `cd admin-ui && npm run build` green.

## Out of scope (later / follow-ups)

- Price-delta reschedule — top-up (new KHPos payment for the surcharge) or partial refund
  (2b-3b).
- Corrective/modifying invoice reflecting the new session on an already-issued Billingo
  invoice (amount is unchanged, so safe to defer until the Billingo key exists).
- Reschedule across different workshops.
- Concurrency lock on the line (same accepted rationale as the 2b-1/2b-2 refund-path
  follow-up: admin-only; recorded in `admin-slice2-followups.md`).
