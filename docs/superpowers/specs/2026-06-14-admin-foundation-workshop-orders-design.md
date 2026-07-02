# Admin (T15–T17): foundation + workshop CRUD + orders read-only — design

## Context

The webshop needs a staff admin (TASKS.md T15–T17). The customer side is Spring
Boot + Thymeleaf; per `SPEC.md`/`CLAUDE.md` the **admin is a Refine + TypeScript
SPA** (`admin-ui/`) over a REST API, authenticated with an **HttpOnly session
cookie + CSRF** (browser JWT is forbidden), with **roles + an audit log** on
price/order changes.

What already exists and shapes this design:

- **Roles:** `CustomerRole { CUSTOMER, SUBSCRIBER, ADMIN }`; 14 admin accounts
  migrated. Spring Security form login (`/fiokom`), session cookie, and CSRF
  (`CookieCsrfTokenRepository.withHttpOnlyFalse`) are in place.
- **API layer:** `api/` package with `/api/...` REST controllers; **springdoc**
  is active (`/v3/api-docs`).
- **No audit infrastructure yet.**
- **No static-build webhook (K5) yet** (needed later in T17).

## Decomposition (the admin is multi-subsystem; one slice per spec)

1. **Admin foundation + Workshop CRUD + Orders read-only** — *this spec*.
2. **Order management (T16, write):** status machine (új → fizetve → csomagolás →
   feladva → teljesítve), sztornó + sztornószámla, visszáru/részvisszatérítés,
   címkenyomtatás. Partly depends on **T11** (invoice + courier), not yet built.
3. **Catalog editing (T17):** product/image editing **behind a feature flag**
   until cutover, category management + **static-build webhook (K5)**.
4. **Coupon admin (T18).**

Cross-cutting **audit log** starts minimal here (workshop writes) and expands in
slices 2–3 to order/price changes.

## Scope of this slice

Foundation (Refine SPA + auth + API + OpenAPI→TS + audit) plus:

- **Workshop CRUD (write):** create/edit a workshop (name, slug, description,
  VAT); add/edit/cancel sessions (start datetime, capacity, price, SKU).
- **Orders (read-only):** list with filters (status, date range, search) +
  pagination, and an order detail page. No status changes/refunds/labels here.

## 1. Architecture

- **Module boundaries (ArchUnit-friendly):** admin REST controllers in
  `api/admin/...`; business logic in the existing `application/workshop` service
  (extended with update/cancel/list/read) and a new read-only order query service
  in `application`. Audit in `application/audit` + a `domain` entity. **`admin-ui/`
  lives outside the Maven module** as a separate Vite/Refine front-end build.
- **Serve model (same-origin):** in production Spring Boot serves the built SPA
  as static assets under `/admin/**`, so the session cookie + CSRF work with no
  CORS. In development the Vite dev server (`:5173`) proxies `/api` and the auth
  endpoints to `:8085`, keeping the browser same-origin (cookie-friendly). This
  matches the cookie-only / no-JWT rule and avoids `SameSite=None` + CORS.

## 2. Auth + access control

- Reuse the existing Spring Security session + CSRF. Thin JSON auth surface for
  the SPA:
  - `POST /api/admin/auth/login` (email + password) → authenticate via
    `AuthenticationManager`, **require the `ADMIN` role**, establish the session;
    200 + minimal user info on success, 401/403 otherwise.
  - `GET /api/admin/auth/me` → current admin (Refine `authProvider.check`).
  - `POST /api/admin/auth/logout`.
- **Authorization:** `/api/admin/**` and `/admin/**` require `hasRole('ADMIN')`
  (authority `ROLE_ADMIN`), added **before** the existing `anyRequest().permitAll()`.
- **CSRF:** the existing `XSRF-TOKEN` cookie (already `withHttpOnlyFalse`) is read
  by the Refine data provider, which sends the `X-XSRF-TOKEN` header on mutations.
  `/khpos/return` stays the only CSRF-exempt path.

## 3. API (this slice)

REST under `/api/admin`, JSON, Refine `simple-rest` conventions (list → array +
`X-Total-Count` header; filter/sort/pagination via query params).

- **Workshops:** `GET/POST /api/admin/workshops`, `GET/PUT /api/admin/workshops/{id}`.
- **Sessions:** `POST /api/admin/workshops/{id}/sessions`,
  `PUT /api/admin/sessions/{id}`, `DELETE /api/admin/sessions/{id}` (cancel).
  Cancel is allowed **only when the session has no non-cancelled bookings**
  (otherwise rejected with a clear error); cancelling a sold session involves
  refunds and belongs to slice 2. This keeps slice 1 free of the refund path.
- **Orders (read-only):** `GET /api/admin/orders` (filters: status, date range,
  search; pagination), `GET /api/admin/orders/{id}`.

## 4. OpenAPI → TypeScript

springdoc already serves `/v3/api-docs`. The `admin-ui` build step runs
`openapi-typescript` to generate types (types-only + a thin fetch wrapper, rather
than full client codegen — lighter, fewer moving parts).

## 5. Audit log

- `audit_log` table: `actor`, `action`, `entity_type`, `entity_id`,
  `summary`/diff JSON, `created_at`; plus an `AuditService`.
- This slice records **workshop writes** (create/update, session add/edit/cancel).
- Orders are read-only here → no audit writes yet; the table/service are built so
  slice 2 can record order/price changes without retrofitting.

## 6. Testing

- **Backend (TDD):** `@SpringBootTest` over `/api/admin/**`:
  - ADMIN gate (anonymous/non-admin → 401/403, admin → 200),
  - workshop CRUD + session cancel and its effect on availability,
  - order list filters + detail read model,
  - an audit entry is written on each workshop mutation.
- **Front-end:** smoke-level only in this slice (the deep checkout E2E is T14 /
  Playwright, separate).

## Out of scope (later slices)

Order status machine / sztornó / refunds / courier labels (slice 2, partly
T11-blocked); product/image/category editing + K5 webhook (slice 3); coupons
(slice 4); bulk operations and statistics (deliberately second-round per SPEC.md).

## `[EMBER]` / open

- Admin SPA deployment/hosting under `/admin` (build artifact wiring into the
  Boot static resources) — confirm at implementation.
- Where admins log in from (a dedicated `/admin` login screen in the SPA;
  customer `/fiokom` login stays separate).
