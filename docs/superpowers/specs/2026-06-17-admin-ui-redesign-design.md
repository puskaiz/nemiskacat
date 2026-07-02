# Admin UI redesign — clean, modern, dark mode

Date: 2026-06-17
Status: Approved
Scope: `admin-ui/` (Refine + Ant Design SPA). Frontend-only.

## Goal

Make the admin interface cleaner and more modern, and add dark mode. Aesthetic
direction: **refined neutral / SaaS** (Linear/Vercel-style) — grayscale
surfaces, one calm indigo accent, generous whitespace, subtle borders and
shadows, rounded corners. Dark mode is a user-toggleable, persisted choice that
defaults to the OS preference on first visit.

## Constraints

- **No new dependencies / frameworks.** CLAUDE.md requires human approval for new
  libraries; the redesign stays entirely within the existing Ant Design + Refine
  stack, driven through Ant Design design tokens.
- **No new backend endpoints.** The dashboard derives its numbers from the
  existing admin list endpoints (`GET /api/admin/{resource}` with the
  `X-Total-Count` header and status filters). No `/api/admin` changes.
- **No business-logic changes.** The orders state-transition flow, bulk
  transitions, refund flow, and workshop CRUD keep their current behavior. This
  is a presentation-layer change.
- All UI copy stays Hungarian; code/comments English (per CLAUDE.md §10).

## Architecture

### 1. Theming layer (foundation)

- **`src/theme/tokens.ts`** — a single shared Ant Design token set (border
  radius, control heights, font stack, spacing rhythm) plus light- and
  dark-specific color tokens. `colorPrimary` ≈ indigo `#4f46e5`; neutral gray
  surfaces; soft borders; subtle shadows.
- **`src/theme/ThemeProvider.tsx`** — React context owning
  `mode: 'light' | 'dark'`.
  - On mount: read `localStorage('admin-theme')`; if absent, resolve from
    `matchMedia('(prefers-color-scheme: dark)')`.
  - Persists `mode` to `localStorage` on change; exposes `toggle()` / `setMode()`
    via `useThemeMode()`.
  - Wraps Ant Design `ConfigProvider` with
    `algorithm: mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm`
    plus the shared + mode-specific token overrides.
  - Syncs `document.body` background color to the active surface so there is no
    white flash on dark mode.
- Refine's `ThemedLayoutV2` and all Ant Design components inherit the active
  theme automatically, so existing tables/forms adopt dark mode with no
  per-component work.
- Replaces the current hardcoded `<ConfigProvider theme={RefineThemes.Blue}>` in
  `App.tsx`.

### 2. Layout chrome (custom sidebar + header)

Keep `ThemedLayoutV2` and replace its slots — avoids re-implementing
collapse/responsive/routing behavior.

- **`src/components/layout/Title.tsx`** — brand/logo block for the sider top
  (passed as `ThemedLayoutV2`'s `Title`).
- **`src/components/layout/Header.tsx`** — sticky header (passed as
  `ThemedLayoutV2`'s `Header`) containing: breadcrumb, a light/dark toggle
  control, and a user menu showing email + role (from `/api/admin/auth/me`) with
  logout (reuses `authProvider.logout`).
- Sider visuals (active state, grouping, spacing) tuned via tokens.

### 3. Dashboard home

- **`src/pages/dashboard/index.tsx`** — new index route, replacing the current
  redirect to the workshops list (`<NavigateToResource resource="workshops" />`
  at `index`).
- Built with Refine `useList`:
  - **KPI cards:** total orders (`X-Total-Count` of unfiltered orders), orders
    needing action (NEW count + PAID count via status-filtered lists), workshop
    count.
  - **Recent orders table:** first page of orders, status `Tag`s, each row links
    to the order detail page.
  - Responsive card grid; loading + empty states.
- Add a `dashboard` entry to Refine `resources` (or a standalone route) and point
  the index route at it.

### 4. Page polish

- **Tables** (orders list, workshops list, order detail lines): consistent
  density, refined status `Tag` styling, proper empty states. **No logic
  changes** — orders bulk-transition, single-row transitions, and refund button
  stay exactly as-is.
- **Forms** (workshops create/edit): grouped in cleaner cards with improved field
  spacing. (Refine `Create`/`Edit` already wrap in a card; refine the layout.)
- **Custom login** — `src/pages/login.tsx`: branded split-layout login screen
  replacing the stock `AuthPage`, reusing the same `authProvider.login` call and
  error handling. Wired into the `/login` route in `App.tsx`.

## Component boundaries

| Unit | Purpose | Depends on |
| --- | --- | --- |
| `theme/tokens.ts` | Token/color source of truth | Ant Design `theme` |
| `theme/ThemeProvider.tsx` | Mode state + ConfigProvider | tokens, localStorage, matchMedia |
| `components/layout/Header.tsx` | Header chrome + toggle + user menu | `useThemeMode`, authProvider, `/auth/me` |
| `components/layout/Title.tsx` | Brand block | — |
| `pages/dashboard/index.tsx` | KPIs + recent orders | `useList` (orders, workshops) |
| `pages/login.tsx` | Branded login | authProvider |

## Testing

- **Unit test** for `ThemeProvider` mode resolution: system default when no
  stored value, localStorage persistence, and `toggle()` flips the mode. (Vitest;
  `matchMedia` mocked.) This is the only piece with real logic.
- `npm run build` (`tsc --noEmit && vite build`) green; existing
  `src/providers/providers.test.ts` stays green.
- No new E2E needed — pure presentation, no money/stock/payment paths touched.

## Out of scope

- Backend/API changes; new stats endpoint.
- New navigation resources beyond the dashboard.
- Business-logic or data-model changes.
- Adding chart libraries or other new dependencies.
