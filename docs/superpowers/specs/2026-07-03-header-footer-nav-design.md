# Header/footer navigation — design

**Date:** 2026-07-03
**Status:** approved (defaults accepted)

## Goal

Rebuild the customer-side header and footer navigation menus to match the live
nemiskacat.hu structure, using the already-migrated content pages. Keep the
existing **Workshop** menu item (the one intentional deviation from live).

## Context / current state

- Nav is **hardcoded** in `src/main/resources/templates/fragments/layout.html`
  (`header` and `footer` fragments). Current header is a flat list; several links
  are placeholders pointing at `/`.
- Routing:
  - Content pages: `RootSlugController` → `/{slug}` renders `page` (slug = content_page.slug).
  - Categories: `/termekkategoria/{slug}`.
  - Blog: `/blog`. Workshops: `/workshopok`. Account: `/fiokom`. Cart: `/kosar`.
- Migrated `content_page` slugs (all PUBLISHED): `adatvedelem`,
  `altalanos-szerzodesi-feltetelek`, `butorfestes-kezdoknek`, `butorfestes-otletek`,
  `csapatunk`, `gyakran-ismetelt-kerdesek`, `itt-hallottal-meg-rolunk`, `kapcsolat`,
  `latvanymuhely`, `rolunk`.

## Approach

Keep the menu **hardcoded in the `layout.html` fragment** (rarely changes; no need
for DB-driven or admin-editable menus now — YAGNI). Add a lightweight, accessible
**dropdown** for the two info menus (Rólunk, Tudástár): CSS hover on desktop, a
small JS click/tap toggle for mobile/keyboard. "Webshop" stays a single top-level
link (no product mega-menu). The nav stays static inside the cacheable layout
fragment — no session/user data (CLAUDE.md #2).

Rejected: faithful product mega-menu (large CSS/JS + full category-tree mapping,
not requested now); DB/admin-driven menu (unnecessary complexity).

## Header menu

| Item | Target |
|---|---|
| Webshop | `/termekkategoria/kretafestek-annie-sloan-chalk-paint/` |
| Bútorfestés kezdőknek | `/butorfestes-kezdoknek` |
| Rólunk ▾ | Bolt → `/latvanymuhely` · Kapcsolat → `/kapcsolat` · Kik vagyunk mi? → `/rolunk` · Itt hallottál még rólunk → `/itt-hallottal-meg-rolunk` |
| Tudástár ▾ | Blog → `/blog` · Bútorfestés ötletek → `/butorfestes-otletek` · Gyakran Ismételt Kérdések → `/gyakran-ismetelt-kerdesek` |
| Workshop | `/workshopok` (kept — deviation from live) |

## Footer menu (3 columns, mirrors live)

- **Oldaltérkép:** Bútorfestés kezdőknek `/butorfestes-kezdoknek` · Webshop
  `/termekkategoria/kretafestek-annie-sloan-chalk-paint/` · Rólunk `/rolunk` ·
  Workshop `/workshopok` · Bolt `/latvanymuhely` · Tudástár `/blog` · Kapcsolat `/kapcsolat`
- **Kiemelt termékeink:** Krétafesték `/termekkategoria/kretafestek-annie-sloan-chalk-paint/` ·
  Szatén festék `/termekkategoria/szaten-festek-annie-sloan-satin-paint/` ·
  Annie Sloan falfesték `/termekkategoria/annie-sloan-falfestek/` ·
  IOD `/termekkategoria/iron-orchid-designs-termekek/`
- **Jogi:** ÁSZF `/altalanos-szerzodesi-feltetelek` · Adatvédelem `/adatvedelem`

Keep the existing footer "Elérhetőség" (contact) block and bottom copyright bar.

## Decisions (defaults accepted by user)

1. "Bolt" → `/latvanymuhely` (showroom).
2. "Kik vagyunk mi?" → `/rolunk`; `/csapatunk` stays out of the menu.
3. Items with no migrated page are **omitted**: "Ingyenesen letölthető
   tudásanyagok" (header Tudástár), and footer legal "Online elállás",
   "Nyereményjáték szabályzat", "Piackutatás adatvédelmi tájékoztatás",
   "Szállítási feltételek".
4. "Webshop" top link → the flagship chalk-paint category (no dedicated shop-index route).

## Scope / files

- `src/main/resources/templates/fragments/layout.html` — header + footer markup.
- `src/main/resources/static/css/site.css` — dropdown styles (desktop hover +
  mobile expanded state); reuse existing design tokens/classes where possible.
- Small JS for accessible dropdown toggle (mobile/keyboard) — extend the existing
  client island (`/js/*`), no new framework.

## Verification

Template-only behaviour; verify by rendering, not unit tests:
- Run the app locally; load `/` and confirm header dropdowns open on desktop
  (hover) and mobile (tap), and keyboard focus works.
- Confirm every href resolves: content-page slugs exist (listed above), category
  slugs exist, `/workshopok` and `/blog` route.
- Confirm cacheable HTML unchanged in principle (no session data added).

## Non-goals

Product mega-menu; DB/admin-editable menus; migrating the not-yet-present legal/
download pages; restyling the topbar or brand.
