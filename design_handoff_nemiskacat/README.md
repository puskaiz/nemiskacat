# Handoff: Nemiskacat Design System

## Overview

This is the complete brand & UI design system for **nemiskacat** — a Hungarian retailer of premium furniture- and wall-paints (official Annie Sloan distributor; also Fusion, Polyvine, IOD, Zibra). The package contains the brand foundations (color, type, spacing, motion), the bespoke visual assets (logos, brushstroke graphics, hand-drawn icons), and a working marketing-site UI kit.

Use this bundle to implement nemiskacat-branded interfaces — a marketing site, a webshop, e-mails, landing pages — in a production codebase.

## About the Design Files

The files in this bundle are **design references created in HTML/CSS/JSX** — prototypes that show the intended look and behavior. They are **not production code to copy verbatim**.

Your task is to **recreate these designs in the target codebase's environment** using its established patterns and libraries:

- If the project already uses React / Vue / Svelte / SwiftUI / native, port the components and tokens into that stack.
- If no environment exists yet, choose the most appropriate framework for the project and implement there.
- The **design tokens** (`colors_and_type.css`) port cleanly to any CSS-variable-based system, Tailwind config, or design-token pipeline (Style Dictionary, etc.). Start there.
- The **JSX components** in `ui_kits/marketing-site/` are deliberately cosmetic — they show structure and styling, not real data wiring. Re-implement them with your codebase's component conventions, routing, and state.

## Fidelity

**High-fidelity.** Colors, typography, spacing, radii, shadows, and motion are all final and exact, lifted directly from the official brand book (`Nemiskacat brandkép.pdf`). Recreate the UI pixel-accurately. The only deliberate placeholders are **photography** (hero, product, story, workshop imagery) — these use neutral gradient stand-ins and must be replaced with real brand photography.

---

## Design Tokens

All tokens live in **`colors_and_type.css`** as CSS custom properties. Import that file (or port the values) — do not hardcode.

### Colors

The system is deliberately tiny: four primaries + three accent stops + a warm-gray scale.

| Token | Hex | Role |
| --- | --- | --- |
| `--nk-white` | `#FFFFFF` | Dominant surface. White is *the* background. |
| `--nk-black` | `#000000` | All text, outlines, high-contrast stamps. |
| `--nk-gray-10` | `#F4F0ED` | Lightest surface tint. *(inferred — see Caveats)* |
| `--nk-gray-20` | `#EBE6E3` | Warm gray — the only secondary background. Brand canon. |
| `--nk-gray-30` | `#D6CFC9` | Hairlines, dividers, disabled. *(inferred)* |
| `--nk-gray-50` | `#8C857F` | Secondary text. |
| `--nk-gray-70` | `#4A4540` | Tertiary / muted text. |
| `--nk-yellow-40` | `#F5DFA1` | Soft yellow fill (callout backgrounds). |
| `--nk-yellow-50` | `#ECC45D` | **The accent.** Buttons, brushstrokes, badges. |
| `--nk-yellow-60` | `#C39E36` | Pressed / strong yellow, link hovers. |

**Semantic aliases** (also in the CSS): `--nk-bg`, `--nk-bg-tone` (= gray-20), `--nk-bg-accent` (= yellow-50), `--nk-fg`, `--nk-fg-muted`, `--nk-fg-subtle`, `--nk-fg-on-accent` (= black on yellow), `--nk-border` (= gray-30), `--nk-accent`, `--nk-accent-hover`.

> **Hard rule:** one yellow accent does all the work. No multi-hue gradients, no blues/purples/teals as UI color. The only photo-led color is the real paint color of furniture in lifestyle imagery.

### Typography

Two families, both supplied as local fonts in `fonts/`:

- **Lora** (serif) — everything readable: headings, body, captions. Weights 400 / 500 / 600 / 700, with matching italics. Loaded via `@font-face` in `colors_and_type.css`.
- **Caveat** (handwritten script) — the *emotional* layer. **Only** for: slogans on brushstrokes, CTA button labels, and short word-badges ("Újdonság!"). Weights 400 / 500 / 600 / 700. **Never** body copy, never a paragraph, never below ~22px.

Type scale (px, from the brand book):

| Token | Size | Weight | Use |
| --- | --- | --- | --- |
| `--nk-fs-section` | 40 | 400 | Section eyebrow |
| `--nk-fs-h1` | 64 | 500 | Page title |
| `--nk-fs-h2` | 48 | 500 | Section title |
| `--nk-fs-h3` | 40 | 500 | Subsection |
| `--nk-fs-h4` | 32 | 500 | Card title |
| `--nk-fs-h5` | 25 | 600 | Small heading |
| `--nk-fs-body-l` | 20 | 400 | Lead paragraph (line-height 1.65) |
| `--nk-fs-body` | 16 | 400 | Body (line-height 1.65) |
| `--nk-fs-body-s` | 14 | 400 | Small body (line-height 1.5) |
| `--nk-fs-caption` | 12 | 400 | Caption — uppercase, `letter-spacing: 0.06em` |

Ready-made classes in the CSS: `.nk-h1`…`.nk-h5`, `.nk-body-l/.nk-body/.nk-body-s`, `.nk-caption`, `.nk-eyebrow`, `.nk-script`, `.nk-slogan`.

### Spacing

4-based scale: `--nk-space-1` 4px · `-2` 8 · `-3` 12 · `-4` 16 · `-5` 24 · `-6` 32 · `-7` 48 · `-8` 64 · `-9` 96 · `-10` 128px. **Generous whitespace is a brand element** — desktop sections use ≥64px vertical padding; text columns stay short.

### Radii

`--nk-radius-sm` 6px · `-md` 12px (card default) · `-lg` 20px (large surfaces) · `-xl` 32px · `-pill` 999px (chips, buttons, brush badges). No 0px sharp corners except inside data tables.

### Shadows (warm brown-gray, never cool/blue)

- `--nk-shadow-xs` `0 1px 2px rgba(60,50,40,.06)` — hover lift
- `--nk-shadow-sm` `0 2px 6px rgba(60,50,40,.08)` — card at rest
- `--nk-shadow-md` `0 8px 24px rgba(60,50,40,.10)` — menu / hovered card
- `--nk-shadow-lg` `0 20px 48px rgba(60,50,40,.14)` — modal / drawer

No inner shadows, no neumorphism.

### Motion

- Easing: `--nk-ease` = `cubic-bezier(0.22, 0.61, 0.36, 1)` (gentle quick-out, settle-in). No springs/bounce.
- Durations: `--nk-duration-1` 120ms (hover) · `-2` 220ms (transition) · `-3` 360ms (page-level). Nothing slower than 360ms.

---

## Interactions & Behavior (state spec)

These apply across all components — implement them in the target stack.

- **Button hover:** yellow primary darkens `yellow-50 → yellow-60`, lifts `translateY(-1px)`, gains `shadow-sm` (220ms). Secondary (outline) inverts to black fill / white text.
- **Button press (`:active`):** `translateY(1px)` + slight darken. *Never* shrink-to-scale.
- **Link / ghost button:** 2px underline in `yellow-50`, hover → `yellow-60`.
- **Card hover (interactive only):** `translateY(-2px)` + bump to `shadow-md`. Static cards do not move.
- **Nav links:** transparent 2px bottom border → `yellow-50` on hover.
- **Focus (all interactive):** 3px `yellow-50` ring, 2px offset. Always visible — accessibility is non-negotiable.
- **Cart drawer:** slides in from the right (`translateX(100%) → 0`, 360ms `--nk-ease`); backdrop fades to `rgba(0,0,0,0.3)`. Opens on cart-icon click and on add-to-cart.
- **No** frosted glass / backdrop-blur (except the nav's subtle 6px blur-on-scroll), no parallax, no scroll-jacking.

---

## Screens / Views — Marketing Site UI Kit

Source: `ui_kits/marketing-site/`. Entry point: `index.html` (React 18 + Babel, components in sibling `.jsx` files). See `ui_kits/marketing-site/README.md` for the per-file map.

### Global: Nav (`Nav.jsx`)
- **Purpose:** primary navigation + cart access.
- **Layout:** sticky top, full-width, white at 96% opacity with 6px backdrop blur, 1px `gray-30` bottom border. Flex row, 32px gap, max-width 1400px, 16px vertical / 32px horizontal padding.
- **Components:** wordmark (`assets/logos/nemiskacat-logo-black.png`, height 30px) · nav links (Lora 15px, hover yellow underline) · search + cart icon-buttons (38px circular, hover `gray-10` fill). Cart shows a yellow count badge (16px circle, top-right).

### Hero (`Hero.jsx`)
- **Purpose:** brand promise + primary CTA.
- **Layout:** 2-col grid `1.05fr / 1fr`, 64px gap, 80px top / 96px bottom padding.
- **Left:** eyebrow (uppercase, 13px, `letter-spacing 0.18em`, `gray-50`) → **Caveat slogan rendered on a brushstroke** (the slogan text sits centered inside `assets/brushes/brush-short-yellow.png` used as a stretched background; Caveat 64px/600) → lead sub (Lora 20px, `gray-70`, max-width 480px) → CTA row (primary pill button + ghost link).
- **Right:** 4:5 rounded card (`radius-lg`), gradient photo placeholder, a circular "Újdonság!" brush badge top-right (`brush-circle-yellow.png`, Caveat rotated −9°), a pill photo-tag bottom-left.

### Category Grid (`CategoryGrid.jsx`)
- **Purpose:** top-level shop categories.
- **Layout:** section on **tone background** (`gray-20`), 96px padding. Section header (eyebrow + 48px title + 18px sub). 4-col grid, 20px gap.
- **Tile:** white, 1px `gray-30` border, `radius-md`, 28px padding, hand-drawn icon (64px) + name (Lora 18px/500) + count (13px `gray-50`). Hover: lift + `shadow-md`, border → transparent.

### Featured Products (`FeaturedProducts.jsx`)
- **Purpose:** product showcase; demonstrates add-to-cart.
- **Layout:** white section, 96px padding, 4-col grid, 24px gap.
- **Product card:** white, 1px border, `radius-md`. Square image area (`gray-10`) with a circular paint-swatch (the product's color) inset; optional "Új" brush badge top-left. Body: brand eyebrow (11px uppercase `gray-50`) + name (Lora 16px/500) + price in **Caveat 26px**. Click → adds to cart + opens drawer. Hover: lift + `shadow-md`.

### Story Row (`StoryRow.jsx`)
- **Purpose:** editorial / blog teaser.
- **Layout:** tone background, 2-col `1fr/1fr`, 64px gap. Left: 5:4 rounded photo placeholder with pill tag. Right: eyebrow + 40px headline + two 17px `gray-70` paragraphs + secondary (outline) CTA.

### Testimonial (`Testimonial.jsx`)
- **Layout:** centered card, **`yellow-40` background**, `radius-lg`, 64×72px padding, max-width 920px. Giant decorative `"` in `rgba(0,0,0,0.12)`. Quote in **Lora italic 24px**; attribution in 14px uppercase `gray-70`.

### Workshop Card (`WorkshopCard.jsx`)
- **Layout:** white section. Card = 2-col grid `1fr/1.2fr`, 1px border, `radius-lg`. Left: gradient photo placeholder. Right: 48×56px padding, 32px title, 16px body, a meta row (3 items, each = 28px hand-drawn icon + 13px label), primary CTA.

### Footer (`Footer.jsx`)
- **Layout:** **tone background** (`gray-20`) — the only section that breaks white. 80px top padding. 4-col grid `1.4fr/1fr/1fr/1fr`, 48px gap. Brand column (wordmark + 15px description). Three link columns (uppercase 13px headings + Lora 15px links, hover yellow underline). Contact column uses hand-drawn `phone`/`email`/`house`/`delivery` icons (22px) inline with each row. Bottom bar: 1px top divider, copyright + locale.

### Cart Drawer (`CartDrawer.jsx`)
- **Layout:** fixed right, 420px wide, full height, `shadow-lg`. Header (22px title + ✕ close). Body: cart lines (64px thumb with circular swatch + name/brand + Caveat price). Footer: subtotal (label + Caveat 32px total) + full-width primary checkout button. Backdrop fades behind.

---

## State Management (marketing kit reference)

The kit's `App.jsx` holds minimal demo state — re-implement with your stack's patterns:

- `cartOpen: boolean` — drawer visibility. Toggled by nav cart icon, add-to-cart, backdrop/✕.
- `cart: Array<{ brand, name, price, priceNum, color }>` — seeded with two items.
- `handleAdd(item)` — pushes a product (parsing `priceNum` from the price string) and opens the drawer.
- Subtotal is derived (sum of `priceNum`), formatted with `toLocaleString('hu-HU')`.

A real implementation needs: product catalog fetching, real cart persistence, checkout/routing, i18n (copy is Hungarian), and form validation for the newsletter/workshop signup.

---

## Content & Voice (so copy stays on-brand)

- **Language:** Hungarian only, informal second person — "Te" (capitalised), never formal "Ön".
- **Speaker:** "Mi" (we), a small team/community.
- **Buttons** are first-person verb phrases in Caveat: *Elolvasom*, *Megyek a webshopba*, *Regisztrálj*.
- **No emoji.** The hand-drawn icon set fills that role. **No** urgency/scarcity tactics, no all-caps shouting, no marketing clichés.
- Brand attributes to keep in mind: *Időtálló · Rendezett · Letisztult* (timeless · composed · uncluttered). The most common mistake is being too loud.

---

## Assets

All under `assets/` (copy into the target project; reference the real files — **do not** redraw as inline SVG, the illustrator's hand is the brand).

- `assets/logos/` — `nemiskacat-logo-{black,white,yellow}.png` (wordmark) + `n-mark-{black,white,yellow}.png` (square "n" stamp for favicon/avatar/watermark).
- `assets/brushes/` — brushstroke graphics: `brush-{long,short,circle}-{yellow,gray,white}.png` + `brush-square-black.png`. The brushstroke is the signature graphic; Caveat slogans always sit *on* one. Pairing: light bg → yellow/gray brush; tone/dark bg → white/yellow brush. Never tint to a non-system color, never stretch non-uniformly.
- `assets/icons/` — 11 bespoke hand-drawn PNGs: `brush, paint-can, cart, house, warehouse, delivery, workshop, email, phone, eye, idea`. Always pair with a text label. **Do not** substitute Lucide/Heroicons/Material — the hand-drawn style breaks instantly. If you need an icon that's missing, commission one in the same hand.
- `assets/examples/` — real production artwork (e-mail headers, market-research promos) for visual reference.
- `fonts/` — Lora (8 cuts) + Caveat (4 cuts) `.ttf` files, wired up in `colors_and_type.css`.

**Photography is NOT included** — hero/product/story/workshop blocks use gradient placeholders. Replace with real brand photography (warm, bright, side-lit; two families: Annie Sloan campaign interiors + nemiskacat's own close-up hands-and-materials shots).

---

## Files in this bundle

| Path | What it is |
| --- | --- |
| `colors_and_type.css` | **Start here.** All design tokens as CSS custom properties + ready-made type classes + `@font-face` declarations. |
| `SYSTEM.md` | Full design-system documentation: content fundamentals, visual foundations, iconography rules, caveats. |
| `fonts/` | Lora + Caveat `.ttf` files. |
| `assets/` | Logos, brushstrokes, hand-drawn icons, example artwork. |
| `ui_kits/marketing-site/` | Working React/JSX recreation of the homepage. `index.html` is the entry point; `README.md` maps the components. |
| `preview/` | Small static HTML "spec cards" for each token group and component — handy visual references when building. |

## Caveats

- **`gray-10` and `gray-30` hexes are inferred.** The brand book's printed values for these collide with other tokens (likely a typo); a sensible warm-gray scale was used. Confirm with the brand owner.
- **Lora** is loaded locally but is also freely available (Google Fonts / OFL); **Caveat** likewise (OFL). Both ship in `fonts/`.
- **No production codebase or Figma** was provided when this system was built — the UI kit is a faithful interpretation of the brand book, not a 1:1 component copy. If a Figma library or repo exists, reconcile against it.
- **Icon coverage is small** (11 icons). A full product surface needs more (search, menu, close, chevrons, settings…). Commission them in the same hand rather than mixing in a vector set.
