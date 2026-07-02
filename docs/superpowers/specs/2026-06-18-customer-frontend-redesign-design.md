# Customer frontend redesign — apply the nemiskacat brand design system

Date: 2026-06-18
Status: Approved
Scope: customer-facing Spring Boot + Thymeleaf + htmx site (`src/main/`). The admin SPA (`admin-ui/`) is out of scope.

## Goal

Apply the `design_handoff_nemiskacat` brand design system (Annie Sloan paints
retailer) to the customer-facing webshop, **porting the handoff verbatim** —
adopt its tokens, fonts, and PNG assets as the source of truth and recreate the
marketing components as Thymeleaf fragments, rather than reinterpreting them
through a component library. Full pass: brand foundation, global chrome,
marketing homepage (with real data where cheap), and a restyle of every
functional page.

## Source of truth

`design_handoff_nemiskacat/` (committed into the repo as part of Phase A):
- `colors_and_type.css` — all design tokens (`--nk-*`), type classes, `@font-face`.
- `SYSTEM.md` / `README.md` — visual + voice rules.
- `fonts/` — Lora ×8, Caveat ×4 (`.ttf`).
- `assets/logos/` (6), `assets/brushes/` (10), `assets/icons/` (11 hand-drawn PNGs).
- `ui_kits/marketing-site/*.jsx` + `styles.css` — the components to port:
  `Nav, Hero, CategoryGrid, FeaturedProducts, StoryRow, Testimonial,
  WorkshopCard, Footer, CartDrawer` (cosmetic prototypes; re-wire to real data).

## Current frontend (what we're changing)

- Chrome: `src/main/resources/templates/fragments/layout.html` exposes
  `head(title)`, `header`, `footer`, `island-script` fragments. Pages include
  these (no Thymeleaf layout dialect).
- Pages: `index.html` (placeholder), `product.html`, `category.html`,
  `workshops.html`, `cart.html`, `checkout.html`, `order-confirmation.html`,
  `account-login.html`, `account.html`.
- Styles: `/design/styles.css` → `/design/tokens/*.css` (earlier partial `--ns-*`
  brand pass) + `/css/site.css` (component CSS, 246 lines, plain CSS, no build).
- Cart island: `GET /api/session` (no-store) → `{cartCount, authenticated}`;
  `/js/cart.js` populates the cart badge and does add-to-cart via
  `POST /api/cart/items` (+ `PATCH`/`DELETE`). CSRF: double-submit XSRF cookie.
- Controllers under `src/main/java/hu/deposoft/webshop/web/`. Catalog data via
  `CatalogQueryService`. Product/category/workshop pages are session-independent
  and micro-cached; cart/checkout/account are no-store.

## Constraints (binding — from CLAUDE.md and the handoff)

- **Cacheable HTML carries no session/cart/user data.** Home, product, category,
  workshop, blog HTML stays cacheable. Cart count, auth state, and cart-drawer
  line items come **only** from the client island (`/api/session`, `/api/cart`,
  no-store).
- **No business-logic changes.** Order placement, payment callback, cart
  mutation, stock derivation, and their tests are untouched. This is markup +
  CSS + read-only view wiring.
- **No new framework/library** without approval. Customer site stays plain
  CSS + the existing vanilla-JS island. (htmx is in the documented stack but is
  not currently wired; we do not add it in this pass.)
- **Slugs/URLs preserved 1:1** (WP-imported). Product JSON-LD (`Product` +
  availability) and blog `Article` JSON-LD preserved.
- **Money** in HUF minor units; VAT in the service layer; times UTC stored /
  Europe/Budapest displayed — unchanged.
- **Hungarian copy, informal "Te"** (capitalised), "Mi" voice. Sentence case.
  **No emoji and no decorative Unicode glyphs** — use the hand-drawn PNG icon set;
  any caret/arrow/check is CSS-drawn.
- Code/comments/commits in English.

## Architecture / phases

### Phase A — Brand foundation
- Copy `fonts/*.ttf` → `src/main/resources/static/fonts/`.
- Copy `assets/{logos,brushes,icons}/*.png` →
  `src/main/resources/static/design/assets/{logos,brushes,icons}/`.
- Port `colors_and_type.css` verbatim → `static/design/colors_and_type.css`
  (only change: `@font-face` `url(...)` → `/fonts/...`). `/design/styles.css`
  imports it. Retire the `--ns-*` token files once nothing references them.
- Rebuild `/css/site.css` base layer (resets, container, `.nk-*` type/util
  classes, button/link/card/focus states per the state spec) on `--nk-*`.
- Favicon → `n-mark` logo.

### Phase B — Global chrome (`fragments/layout.html` + island)
- **Nav** (port `Nav.jsx`): sticky; white 96% + 6px blur on scroll; 1px
  `gray-30` bottom border; wordmark `nemiskacat-logo-black.png`; links (Webshop,
  Workshopok, Tudástár, Színek) with yellow hover underline; 38px circular
  search + cart icon-buttons; yellow cart-count badge fed by the island. Keep a
  slim announcement topbar (free-shipping threshold) restyled to tokens.
- **Footer** (port `Footer.jsx`): warm-gray (`gray-20`) 4-col grid; brand column
  + link columns + contact column with hand-drawn `phone/email/house/delivery`
  icons; bottom divider + copyright.
- **Cart drawer** (port `CartDrawer.jsx`): fixed-right, 420px, `shadow-lg`,
  backdrop fade, 360ms `--nk-ease` slide. Rendered as an **empty shell** in the
  layout (no cart data in cacheable HTML). `cart.js` hydrates it and opens it on
  cart-icon click and on add-to-cart; backdrop/✕ closes.
- **`GET /api/cart`** (new, no-store): returns current cart lines as JSON
  (name, brand/variant, unit price minor units, qty, line total, image, swatch
  if available) for the drawer. Read-only; mirrors what `cart.html` already
  renders server-side. Unit/web test for empty + populated cart.

### Phase C — Marketing homepage (`index.html`)
Compose the ported sections; all cacheable (no session data):
- **Hero** (static): eyebrow → Caveat slogan on `brush-short-yellow.png` →
  Lora lead → primary pill CTA (`Megyek a webshopba`) + ghost link; right
  lifestyle photo placeholder + circular "Újdonság!" brush badge.
- **CategoryGrid** (real): top-level shop categories from `CatalogQueryService`,
  hand-drawn icons + name + product count, on tone background.
- **FeaturedProducts** (real): product cards (reused component) from a featured/
  recent-products query; add-to-cart wired to the island.
- **StoryRow** (real): latest blog post teaser (title, excerpt, link) from the
  blog content source.
- **Testimonial** (static): Annie Sloan founder quote, soft-yellow card.
- **WorkshopCard** (real): next upcoming workshop session; falls back to a
  "Hamarosan" state when none.
- Backend: a `HomePageView` view-model + minimal `CatalogQueryService`/blog
  query methods (top categories, featured products, next workshop, latest post);
  `HomeController` populates it. Tests for the new query methods / view-model.

### Phase D — Functional pages
Restyle to the new system; **logic untouched**:
- `product.html`, `category.html`, `workshops.html` — product/workshop cards use
  the FeaturedProducts card style; galleries, variant pickers, qty steppers,
  pagination, breadcrumbs restyled; JSON-LD preserved.
- `cart.html`, `checkout.html`, `order-confirmation.html` — tables, summary
  sidebars, forms, validation states restyled (no-store unchanged).
- `account-login.html`, `account.html` — branded auth/account layouts.

## Component boundaries (new/changed files)

| Unit | Responsibility |
| --- | --- |
| `static/design/colors_and_type.css` | Verbatim token layer (`--nk-*`), fonts |
| `static/fonts/`, `static/design/assets/` | Brand fonts + PNG assets |
| `static/css/site.css` | Component + base CSS on `--nk-*` |
| `fragments/layout.html` | Nav, footer, cart-drawer shell, head |
| `static/js/cart.js` | Badge + drawer hydration/open-close (extended) |
| `web/SessionController` or new `CartApiController` | `GET /api/cart` (no-store) |
| `web/HomeController` + `HomePageView` + query methods | Homepage real data |
| `index.html` + per-section fragments | Marketing homepage |
| `product/category/workshops/cart/checkout/account*` templates | Restyle |

## Testing

- **Backend (JUnit + Testcontainers where DB):** new `GET /api/cart` (empty +
  populated, no-store header); new homepage query methods / `HomePageView`
  (categories, featured products, next workshop, latest post). Existing
  order/payment/stock/cart-mutation tests must stay green and are not modified.
- **Build:** `mvn -q -DskipTests=false test` green for touched modules;
  full app compiles and starts.
- **Visual / manual:** run the app, verify homepage, chrome, drawer open/close,
  and each restyled page in light view; check focus rings and that no
  session-specific data leaks into cacheable HTML.
- **E2E:** the existing Playwright checkout-path test must still pass; extend
  only if a selector changed.

## Out of scope

- Admin SPA. Real photography (gradient/swatch placeholders remain). Adding
  htmx. New payment/shipping integrations. Per-field content translation.
  Dark mode (the customer brand system is light-only).
