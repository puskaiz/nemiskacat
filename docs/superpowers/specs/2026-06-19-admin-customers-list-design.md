# Admin customers list — Design

Date: 2026-06-19
Status: Approved (brainstormed 2026-06-19)
Builds on: the admin foundation (`2026-06-14-admin-foundation-workshop-orders-design.md`)
and its order-list slice — this feature copies the `OrderAdminController` /
`OrderAdminQueryService` / `useTable` pattern verbatim.

## Goal

Wire the existing `/admin/customers` page (currently static mock data in
`admin-ui/src/data/customers.ts`) to the real `customer` table. Display a paginated,
searchable list of customers read from the DB.

## Scope

**In scope**
- New read-only admin endpoint `GET /api/admin/customers?page=&size=&q=`.
- Columns: **Name | Email | Role | Status | Registered**, all sourced from the
  `customer` table (no joins):
  - Name = `Customer.fullName()` (first+last, falling back to displayName/email).
  - Email = `email`.
  - Role = `role` (CUSTOMER / SUBSCRIBER / ADMIN), shown as an Ant `Tag` with a
    Hungarian label.
  - Status = `enabled` → "Aktív" / "Letiltva".
  - Registered = `createdAt`, formatted Europe/Budapest.
- Optional case-insensitive search `q` over name + email (wires the page's existing
  search box).
- Default sort: `createdAt` descending (newest first).
- Reuses the existing `/api/admin/** → ROLE_ADMIN` security gate and the existing
  Refine REST dataProvider (`X-Total-Count` envelope). No security or provider changes.

**Out of scope** (user decision, 2026-06-19)
- Order stats (order count, total spent, last-order date) — would need an orders
  aggregation; deferred.
- VIP/tier column — replaced by the real `role`.
- Export, add, edit/detail. List + search only.

## Architecture

Thin REST controller → application query service → repository. Business logic stays in
the service per CLAUDE.md rule 1. Cacheability rule 2 is not in play (admin JSON API,
not cacheable customer-facing HTML).

### Backend (`hu.deposoft.webshop`)

1. `domain/customer/CustomerRepository` — add:
   ```java
   @Query("""
       select c from Customer c
       where :q is null
          or lower(c.email) like lower(concat('%', :q, '%'))
          or lower(coalesce(c.firstName,'')) like lower(concat('%', :q, '%'))
          or lower(coalesce(c.lastName,''))  like lower(concat('%', :q, '%'))
          or lower(coalesce(c.displayName,'')) like lower(concat('%', :q, '%'))
       """)
   Page<Customer> search(@Param("q") String q, Pageable pageable);
   ```
   (blank `q` is normalized to `null` in the service).

2. `application/customer/CustomerAdminQueryService` — new:
   ```java
   record CustomerSummary(Long id, String name, String email,
                          CustomerRole role, boolean enabled, OffsetDateTime createdAt) {}
   record PageResult(List<CustomerSummary> items, long total) {}
   PageResult list(int page, int size, String q);
   ```
   Maps `Customer` → `CustomerSummary` (`name` via `fullName()`); builds
   `PageRequest.of(page, size, Sort.by(DESC, "createdAt"))`; trims/normalizes `q`.

3. `api/admin/CustomerAdminController` — new, thin:
   `GET /api/admin/customers` → body `List<CustomerSummary>`, header
   `X-Total-Count: <total>`. Mirrors `OrderAdminController` exactly.

### Frontend (`admin-ui/`)

- `pages/customers/index.tsx` — replace the static `CUSTOMERS` array with
  `useTable<CustomerSummary>({ syncWithLocation: true })` (pattern from
  `pages/orders/list.tsx`). Columns → Name, Email, Role (Tag), Status, Registered.
  Drop the tier filter chips; wire the search box to `setFilters` (field `q`).
- `types.ts` — add `CustomerSummary`.
- `i18n/hu/customers.json` — role labels (Vásárló / Feliratkozó / Admin), status
  labels, column headers.
- Delete the now-unused `data/customers.ts`.

## Testing

- **Integration (Testcontainers/Postgres)** for `CustomerAdminController`:
  - seed customers → `GET /api/admin/customers` returns them with correct
    `X-Total-Count` and body order (newest first);
  - `q` filters by name and email;
  - **401 without authentication**, **403 for a non-admin** (CUSTOMER) — the
    ROLE_ADMIN gate.
- **Service unit test** for `CustomerAdminQueryService`: pagination params + mapping,
  including `fullName()` fallback when names are null.

## Risks / notes

- `search` does a `LIKE '%q%'` scan; acceptable at current customer volumes. If the
  table grows large, revisit with a trigram index — noted, not built.
- No new Flyway migration: all columns already exist (`V7`–`V9`).