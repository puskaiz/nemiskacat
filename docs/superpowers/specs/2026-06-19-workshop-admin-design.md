# Workshop admin (complete the management UI) — Design

Date: 2026-06-19
Status: Approved (brainstormed 2026-06-19)
Builds on: existing `WorkshopAdminController` / `WorkshopService` (create/update/sessions/bookings
already wired) and the Refine admin SPA (`admin-ui/`, `workshops` resource). Reuses the product
image endpoints (`ProductImageController`) and the product image gallery service.

## Goal

Finish the workshop admin so the 5 imported workshops (and future ones) can be fully managed:
see them in a real list, manage the imported image gallery, edit sessions, delete a workshop, and
publish/unpublish. Most CRUD already exists; this fills the gaps.

## Current state (audit)

Already working: create workshop, edit name/slug/desc/VAT, add/delete session, bookings
(view/cancel/reschedule). Gaps this spec closes:

1. **List page is a static mock** (`admin-ui/src/pages/workshops/list.tsx` uses `data/workshops.ts`)
   — the 5 imported workshops aren't visible in admin.
2. **No gallery editor** — imported `ProductImage` rows can't be managed.
3. **Session edit** — backend `PUT /api/admin/sessions/{id}` exists, but no UI form (add/delete work).
4. **No delete workshop** endpoint/UI.
5. **No publish/unpublish** — workshops are hardcoded `PUBLISHED`.

## Backend changes

Thin controllers; logic in `WorkshopService` (CLAUDE.md #1). Existing `/api/admin/**` ROLE_ADMIN +
CSRF gate applies. No new Flyway migration (reuses Product/ProductImage/WorkshopSession).

- **Extend `WorkshopView`** (in `WorkshopAdminController`) with `status` (PUBLISHED/DRAFT),
  `sessionCount` (for the list), and `images` (the gallery: list of {id, url, alt, position,
  featured}, reusing the product image view shape). Populate in `WorkshopView.of(...)` /
  the query that backs the list + get.
- **Publish/unpublish:** add `status` to `WorkshopRequest`; `WorkshopService.updateWorkshop(...)`
  applies it (DRAFT/PUBLISHED). Default on create stays PUBLISHED.
- **Delete workshop:** `WorkshopService.deleteWorkshop(id)` + `DELETE /api/admin/workshops/{id}`.
  Guard: reject (problem+json 409) if any session has bookings/orders referencing its variants
  (mirror `cancelSession`'s `SessionHasBookingsException`); otherwise delete the workshop's
  sessions, variants, images, and the product. Idempotent-safe.
- **Gallery:** REUSE the existing `/api/admin/products/{id}/images` endpoints (upload / delete /
  cover / reorder) with the workshop's product id — a workshop *is* a Product. **Verify** these
  aren't blocked for workshops by the product-editor flag; if they are, allow them for
  type=WORKSHOP (or add thin `/api/admin/workshops/{id}/images` aliases that call
  `ProductImageService`). No duplicate gallery logic.
- **Session edit:** already present (`PUT /api/admin/sessions/{id}`) — no backend change.

## Frontend changes (`admin-ui/`)

- **List page** (`pages/workshops/list.tsx`): replace the mock calendar with `useTable<Workshop>`
  on the `workshops` resource (`GET /api/admin/workshops`, `X-Total-Count`). Table columns: name,
  slug, status (tag), session count, image count, actions (edit, delete). Keep "+ Új workshop".
  Delete via `useDelete` (DELETE workshop) with confirm. Delete `data/workshops.ts` mock.
- **Edit page** (`pages/workshops/edit.tsx`):
  - Add a **status** control (PUBLISHED/DRAFT) to the workshop form.
  - Add a **gallery editor** section: show current images (thumbnails, cover badge), upload
    (multipart), delete, set cover, drag/▲▼ reorder — calling the product image endpoints with the
    workshop id; refetch the workshop after each mutation. Mirror any existing product image UI;
    otherwise build a simple gallery grid.
  - Add a **session edit** form: an "edit" action on each session row opens an inline/modal form
    (startAt, capacity, priceHuf, sku) → `PUT /api/admin/sessions/{id}`. Add/delete already wired.

## Testing

- **Backend (Testcontainers):** `WorkshopService`/controller tests for: `updateWorkshop` toggles
  status; `deleteWorkshop` removes a workshop with no bookings; `deleteWorkshop` is **rejected**
  when a session has a booking (the no-skip rule for booking/stock error paths, CLAUDE.md);
  `WorkshopView` exposes status + sessionCount + images. Reuse the existing workshop test harness.
- **Frontend:** type-check + build green (`tsc --noEmit`, `vite build`). (SPA has no unit-test
  harness; verification is the build + the wired API shape.)

## Out of scope

Customer-facing workshop page (already renders); booking flow changes; the calendar view (the list
becomes a table — a calendar can come later); bulk actions.
