# Admin redesign — apply the nemiskacat admin handoff — design

## Context

`design_handoff_nemiskacat_admin/` is a mid-fidelity design handoff for the full admin
back-office (replacing WooCommerce/WordPress admin). The **source of truth** is
`Admin Mid-fi HU.dc.html` (Hungarian; the English `Admin Mid-fi.dc.html` is deprecated and kept
only for EN label reference). `Mid-fi States & Flows.dc.html` and `Type & Components.dc.html` are
the state/component references. The `.dc.html` files are design references (a custom `<x-dc>`
prototype runtime) — **treat their markup as a visual spec, not code to copy**; re-implement
natively in our stack.

We already have an `admin-ui/` SPA (Refine + Ant Design + react-router-v6) with a small set of
screens (dashboard, orders, workshops, login) on an **indigo** theme. The handoff brand is
**near-black + red** and is the source of truth, so the current indigo look is dropped and the
admin is rebuilt to the handoff.

This is a program (~12 modules). It is delivered in **passes**: Pass 1 applies the entire new
design across the whole app; later passes deepen interaction-heavy modules. Each pass after this
spec gets its own implementation plan.

## Decisions (confirmed with the team)

1. **Drop the indigo design; apply the handoff wholesale** (theme, chrome, screens).
2. **Keep the stack: Refine + Ant Design + react-router.** Re-theme antd; build a **custom
   layout shell** replacing `ThemedLayoutV2` (the handoff chrome is too specific to bend the
   stock layout into).
3. **Bilingual HU/EN from the start** via i18n (HU default). EN labels come from the deprecated
   English design file.
4. **Backed modules use the real REST API** (orders, workshops, bookings — backend exists).
   **Everything else renders the design's static example data as-is** — no mock-server/data-
   provider infrastructure. (E.g. Reports stays with its static numbers.)
5. **First pass = the whole new look everywhere**, then depth passes per module.

Out of scope: POS (separate app, deferred), WooCommerce import (already handled by the team —
do not build importers), real third-party provider integrations (Barion, Számlázz.hu/NAV,
carriers, GA/Mailchimp/Meta), and a real passkey/WebAuthn backend (passkey UI is shown but
non-functional for now). The customer-facing Thymeleaf shop and blog are untouched.

## 1. Stack mapping (handoff → Refine + antd)

- **Theme:** drive antd via `ConfigProvider` theme tokens, replacing `admin-ui/src/theme/tokens.ts`.
  The handoff palette maps to antd tokens; a thin global CSS layer (`admin-ui/src/theme/*.css`)
  covers what tokens can't express (squared containers + pill buttons, table header treatment,
  status pills, nav active left-bar). The existing `ThemeProvider`/`mode.ts` (light/dark toggle,
  persistence) is kept and re-tokenized.
- **Font:** Hanken Grotesk (Google Fonts), weights 400/500/600/700/800 — replaces Inter.
- **Shape:** antd `borderRadius` → 2–3px for containers/inputs; Buttons overridden to pills
  (`border-radius: 999px`) via component token + CSS; avatars/status dots circular.
- **Layout:** new `admin-ui/src/components/layout/AdminLayout.tsx` (sidebar + top bar + content)
  replaces `ThemedLayoutV2` in `App.tsx`. The existing `Header.tsx`/`Title.tsx` are folded into
  it or replaced.
- **Icons:** Lucide/Feather-style stroke icons (matches the handoff's inline SVGs); use the
  icon set already available to the SPA (antd icons or `lucide-react`).
- **Logos:** `uploads/logo.png` (wordmark) + `uploads/logo-small.png` (collapsed mark), copied
  into `admin-ui/public/`; auto-invert in dark mode (`filter: invert(1) brightness(2)` on a
  `[data-brandlogo]` wrapper).

## 2. Design tokens

Per the handoff README. Light is default; dark fully supported.

**Light:** `--bg #F7F8FA` · `--surface #fff` · `--input #F8FAFC` · `--th #FBFCFD` ·
`--fill #F1F5F9` · `--border #E5E8EC` · `--border-soft #EEF0F3` · `--text #0F172A` ·
`--muted #475569` · `--faint #94A3B8` · `--accent #16181D` (near-black: primary buttons, active
nav, logo) · `--accent-fg #16181D` · `--accent-soft #ECECEE`.

**Dark:** `--bg #0E1525` · `--surface #161E2E` · `--input #1B2436` · `--th #1B2436` ·
`--fill #222C40` · `--border #2A3650` · `--border-soft #222C40` · `--text #E7ECF3` ·
`--muted #9AA7BD` · `--faint #6B7A93` · `--accent #3A4658` · `--accent-fg #C9D3E2` ·
`--accent-soft #232C3C`.

**Semantic (not theme-bound):** secondary buttons (Export, date filters) solid red `#C20E1A`
white text; danger/refund `#C0392B` (`#DC4040` for sign-out / validation / error icon).
**Status pill tones (light):** paid/active green `#047857` on `#ECFDF3`; pending/warn amber
`#B45309` on `#FEF6E7`; shipped/info blue `#1D4ED8` on `#EAF0FE`; neutral/refunded gray
`#64748B` on `#F1F5F9`. Dark uses muted equivalents (a `tone` map keyed by status).

**Type scale:** page title 24/700 (dashboard greeting 25); section header 22/700; card title
13–15/700; body 13–14; label 12/600 `--muted`; table header 12/600 `--faint`. Letter-spacing
-0.3px on big headings.

**Shape/space/shadow:** containers/cards/inputs radius 2–3px; buttons pill 999px; card shadow
`0 1px 2px rgba(16,24,40,.04)`; modal `0 24px 60px rgba(16,24,40,.3)`; primary-button glow
`0 2px 6px rgba(22,24,29,.3)`. Page padding 24–26px; card padding 16–18px; cell padding 12–18px;
gap 16px; content max-width 1760px centered.

## 3. Internationalization

- `react-i18next` + `i18next`, wired through Refine's `i18nProvider`.
- Two locales: `hu` (default) and `en`. Resource JSON under `admin-ui/src/i18n/{hu,en}/`,
  split into namespaces by module (`common`, `nav`, `orders`, `products`, …).
- All admin strings externalized to keys; HU values from the primary design, EN values from the
  deprecated English design file.
- The top-bar **HU/EN switch** changes the i18next language (and persists it), not "navigates
  between builds" as the prototype faked. Locale persisted to `localStorage`.
- Shop content remains Hungarian-only (this is the **admin UI** language only).

## 4. Chrome (layout shell)

**Sidebar** (`AdminLayout` + a `Sidebar` unit): 234px; collapses to a 68px icon rail via «/»;
becomes an overlay drawer + hamburger under 880px. Full wordmark (small mark when collapsed) +
"admin". **Grouped nav** with section headers, stroke line-icons, active item = `--accent-soft`
bg + a 3px inset accent left-bar, count badges on Orders/Products/Shipping/Bookings. Submenus
are accordions (one open at a time; close when navigating elsewhere); active submenu item
highlighted. Nav order:

- **Irányítópult** (Dashboard)
- *Értékesítés:* Rendelések · Termékek (▸ Összes termék, Kategóriák) · Kuponok · Vásárlók · Szállítás
- *Workshopok:* Workshopok (▸ Összes workshop, Oktatók) · Foglalások
- *Elemzések:* Riportok
- *Tartalom:* Oldalak
- **Beállítások** (pinned bottom)

**Top bar** (62px): global search, HU/EN switch, dark-mode ☾/☀ toggle, notifications bell,
profile dropdown (name+avatar → Profilbeállítások, Jelszó módosítása, Passkey hozzáadása,
Értesítések, red Kijelentkezés). Sign-out hits the existing `authProvider.logout`.

## 5. Routing & resources (full section inventory)

Register every section as a Refine resource + route now, so the nav is complete in Pass 1.
Detail/edit open **on row click**; create/edit are **inline pages** (not modals), except the
three popups in §7. Real routes (`/orders`, `/orders/:id`, `/orders/new`, `/settings/payments`,
…) replace the prototype's state-flag navigation.

| Section | Route base | Data source (now) |
|---|---|---|
| Dashboard | `/` | static design data (live KPIs deferred to a depth pass) |
| Orders (+ detail, new) | `/orders` | **real REST** |
| Products (+ Categories) | `/products`, `/products/categories` | static design data |
| Coupons | `/coupons` | static design data |
| Customers | `/customers` | static design data |
| Shipping (Fulfilment) | `/shipping` | static design data |
| Workshops (+ Instructors) | `/workshops`, `/workshops/instructors` | **real REST** (workshops); instructors static |
| Bookings | `/bookings` | **real REST** (cancel/reschedule exist) |
| Reports | `/reports` | static design data |
| Pages (CMS) | `/pages` | static design data |
| Settings (sub-tabs) | `/settings/*` | static design data |
| Auth (login/reset/passkey) | `/login`, `/reset`, `/passkey` | login real; reset/passkey UI-only |

Backed resources use the existing `dataProvider`; static ones render fixtures co-located with
the page (clearly marked placeholder). Swapping a static module to REST later = wire its Refine
hooks to the data provider; the screens stay.

## 6. Shared primitives

A small kit under `admin-ui/src/components/ui/` (and `theme/*.css`), reused by every screen:

- **StatusPill** — status → `{label, tone}` map (paid/pending/shipped/refunded/neutral),
  light+dark tones per §2.
- **DataTable states** — every list supports loading (skeleton rows), empty (icon + message +
  primary action), and error (icon + message + retry). A shared wrapper drives these from the
  Refine query state; the Orders **demo state switcher** (Data/Loading/Empty/Error) exercises all
  three.
- **Toast** — bottom-center confirmation on save/create/refund/invite, auto-dismiss ~2.6s (via
  antd `App` message/notification, themed).
- **PillButton / SecondaryButton** — primary (near-black, glow), secondary (red), danger.
- **InlinePageScaffold** — header (title + back-link) + form layout for the inline create/edit
  pattern; required fields marked red `*`, invalid field = red border + helper text.
- **PageHeader / Filters / Pagination / Tabs** — consistent list-screen furniture.

## 7. The three popups (modals)

Everything else is an inline page; only these are modals over a dimmed backdrop:

1. **Prepare for packaging** (Orders bulk action) — lists each selected non-workshop order's
   customer name, phone, purchased items + qty, with a pack checkbox; workshop orders
   auto-excluded with a "skipped" note; Print action.
2. **Refund** — amount, reason, restock toggle, red confirm. (Backed by the real refund API on
   the Orders depth pass.)
3. Both confirm via toast.

## 8. Delivery passes

- **Pass 1 — whole new look across the app** *(first implementation plan)*: re-theme + font +
  shape; `AdminLayout` chrome (sidebar + top bar) with full grouped nav; i18n (HU/EN); routes/
  resources for every section; the shared primitives kit; and every screen present — backed ones
  rendering basic real data, the rest rendering the design's static example data. Outcome: the
  new design is visibly applied everywhere; navigation works end-to-end.
- **Pass 2 — Orders depth:** tab/channel/date/fulfilment filters, row select + bulk bar, inline
  per-row status dropdown, the Prepare-for-packaging + Refund popups, order detail
  (items/totals/timeline), new-order form with validation, demo state switcher.
- **Pass 3 — Workshops/Instructors/Bookings depth:** calendar, sessions w/ seat progress,
  new-workshop (recurrence + waitlist), bookings filters + detail actions (resend/move/cancel).
- **Pass 4 — Products/Categories depth** (variants, pricing & stock, gallery; categories CRUD).
- **Pass 5 — remaining mocked modules polish** (Customers, Coupons, Shipping, Reports, Pages,
  Settings sub-tabs) — interactions on static data, ready to wire to REST when backends land.
- **Pass 6 — Auth polish** (password reset, passkey UI).

Each pass after Pass 1 gets its own spec/plan as needed; this document is the umbrella.

## 9. Testing

- **Frontend:** `vitest` unit tests for logic — theme mode resolve/persist, i18n language
  switch + key resolution, nav active-state + accordion (one-open) logic, StatusPill tone map,
  DataTable state selection (loading/empty/error), and the static-data fixtures shape. Type-check
  + production build (`npm run build` = `tsc --noEmit && vite build`) green. Existing
  `providers.test.ts` / `mode.test.ts` kept (re-pointed at new tokens where needed).
- **Backend:** untouched by Pass 1; the Java suite stays green (`mvn verify`). Backed screens use
  the existing admin REST endpoints; no new backend in this pass.
- Manual: light/dark, collapse/drawer at <880px, HU/EN switch, every nav route resolves.

## 10. File structure (Pass 1)

```
admin-ui/src/
  theme/        tokens.ts (re-tokenized), themeVars.css, mode.ts (kept)
  i18n/         index.ts (i18next + Refine i18nProvider), hu/*.json, en/*.json
  components/
    layout/     AdminLayout.tsx, Sidebar.tsx, Topbar.tsx, ProfileMenu.tsx, nav.config.ts
    ui/         StatusPill, DataTable(states), Toast helpers, PillButton, InlinePageScaffold,
                PageHeader, Filters, Pagination, Tabs
  pages/        dashboard, orders, products(+categories), coupons, customers, shipping,
                workshops(+instructors), bookings, reports, pages, settings/*, auth/*
  data/         static example fixtures for backend-less modules
  App.tsx       resources + routes for every section; AdminLayout replaces ThemedLayoutV2
```

Backed pages (orders, workshops, bookings) keep their existing real-API wiring, re-skinned.
```
