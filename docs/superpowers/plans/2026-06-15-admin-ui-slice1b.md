# Admin UI (Slice 1B) — Refine SPA Implementation Plan

**Goal:** A Refine + TypeScript SPA in `admin-ui/`, consuming the `/api/admin` backend, served same-origin under `/admin` by Spring Boot. Workshop CRUD + read-only orders, session-cookie + CSRF auth.

**Decisions (from brainstorming):** minimal hand-rolled Vite + React + TS + Refine (Ant Design UI); hand-written TS types (no OpenAPI codegen); built into `src/main/resources/static/admin/` and served same-origin under `/admin`.

**Verification:** `npm run build` (type-check + bundle) is the gate; a few `vitest` tests for the data/auth provider logic; manual on 8085 after a restart. NOT `mvn verify`.

## Architecture
- `admin-ui/` Vite project, `base: '/admin/'`, `build.outDir: ../src/main/resources/static/admin`, dev `server.proxy` for `/api` → `:8085`.
- **Auth (cookie session):** `authProvider` → `POST/GET/POST /api/admin/auth/{login,me,logout}`. All `fetch` use `credentials: 'include'`. Login + writes send `X-XSRF-TOKEN` read from the `XSRF-TOKEN` cookie; the SPA primes that cookie via a GET at startup.
- **Data provider:** custom (Refine `DataProvider`) mapping Refine pagination/sort/filter → our `page/size/q/status/from/to`, reading `X-Total-Count`; mutations attach the CSRF header.
- **Resources:** `workshops` (list/create/edit + nested sessions editor), `orders` (list/show, read-only).
- **Serve + security:** Boot serves the built bundle under `/admin`; a forwarding controller maps `/admin/**` (non-asset) → `/admin/index.html` for client-side routing. SecurityConfig: `/admin/**` static becomes `permitAll` (the bundle is public; data is protected at `/api/admin/**`), `/api/admin/**` stays ADMIN-gated. Add CSRF-cookie materialization so the SPA can obtain the token.

## Milestones (commit each)
1. **Scaffold + build green** — package.json, vite/ts config, index.html, minimal `<Refine>` app shell (antd), `npm install`, `npm run build` outputs to `static/admin/`.
2. **Serve + security** — `AdminSpaController` forward for `/admin/**`; SecurityConfig `/admin/**` permitAll + CSRF token endpoint/materialization; build, restart, `/admin` loads.
3. **Auth** — `authProvider`, login page, authenticated layout; unauthenticated → login, admin → app.
4. **Data provider** — custom provider with param mapping + `X-Total-Count` + CSRF; `vitest` test for the mapping.
5. **Workshops** — list/create/edit + sessions (add/edit/cancel via the session endpoints).
6. **Orders** — list (filters) + show (read-only); final build + manual-test guide.

## Backend touch-ups (in this slice)
- `SecurityConfig`: `/admin/**` → `permitAll` (keep `/api/admin/**` gated); ensure the `XSRF-TOKEN` cookie is issued for the SPA (a lightweight `GET /api/admin/csrf` that materializes the token, permitAll).
- `AdminSpaController`: `@GetMapping` forward of `/admin` and client-routes to `/admin/index.html`.
- `.gitignore`: `admin-ui/node_modules`, `admin-ui/dist` (we commit the built bundle under `static/admin/`? No — build is reproducible; ignore `static/admin/` generated output OR commit it. Decision: ignore `node_modules`; commit the built `static/admin/` so the jar serves it without a node build. Revisit if the bundle is large.)
