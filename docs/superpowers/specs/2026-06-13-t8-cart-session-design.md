# T8 — Cart & session design

**Date:** 2026-06-13 · **Task:** B-webshop `TASKS.md` T8 · **Status:** approved (brainstorming)

## Context

Server-side guest cart with a cookie identifier, cart API (also called by the
static pages' JS island), `/api/session` (no-store), soft stock check on add,
CSRF protection. Acceptance: an item added "from a static page" (= via the API)
shows up on the webshop cart page; the session endpoint is cheap/skippable
without a cart cookie. Login-merge is prepared (user_id column) but lands with
Spring Security login in T13.

## Decisions

- **Identity:** dedicated `nk_cart` cookie (128-bit random token), HttpOnly,
  SameSite=Lax, Path=/, Max-Age 30 days, Secure flag from config. NOT the servlet
  session — the cart must survive browser restarts. Cart rows live in Postgres.
- **Prices are not stored on cart items** — always computed at read time via
  `PriceCalculator` (price changes show immediately; checkout locks prices in T9).
- **Soft stock check on add** (overselling protection point 1 of 3): the add is
  rejected with 409 if the variant's derived status is not orderable
  (IN_STOCK/PREORDER). Quantity is not capped here — checkout re-validates (T9).
- **CSRF:** Spring Security added now (permitAll, no login yet): cookie-based
  double-submit — `XSRF-TOKEN` cookie (non-HttpOnly, readable by the JS island)
  + `X-XSRF-TOKEN` header on modifying requests; plain (non-XOR) request handler,
  the standard JS-client pattern. `/api/session` touches the token so clients
  receive the cookie there. Cacheable pages must NOT gain Set-Cookie — the
  existing byte-identical product test guards this.
- **URLs:** cart page `/kosar` (the WP slug, verified; checkout `/penztar` in T9).
  Cart page is session-dependent by design (allowed exception), `no-store`.
- **API (api/ layer, JSON):**
  - `GET /api/cart` → cart view (no cookie → empty view, no cart created)
  - `POST /api/cart/items` `{sku | variantId, quantity}` → 200 + cart view,
    creates cart + sets cookie if absent; 409 if not orderable; 404 unknown sku
  - `PATCH /api/cart/items/{itemId}` `{quantity}` (0 = remove) → cart view
  - `DELETE /api/cart/items/{itemId}` → cart view
  - `GET /api/session` → `{cartCount, authenticated}`, `Cache-Control: no-store`;
    without a cart cookie returns zeros with no DB access
- **Static-page island seed:** `static/js/cart.js` — fills the cart-count badge
  from `/api/session` (skipped if no cookie), wires `data-add-to-cart` buttons,
  sends the CSRF header. The product page gets static (cache-safe) add buttons.
- **Schema (V4):** `cart(id, token UQ, user_id NULL, created_at, updated_at)`,
  `cart_item(id, cart_id FK, variant_id FK, quantity>0, added_at, UQ(cart,variant))`.

## Tests (TDD)

- CartService: add creates cart+token; same variant increments; non-orderable add
  rejected; quantity update + remove; line/total use the effective (sale) price.
- API (MockMvc): modifying call without CSRF → 403; add with CSRF → cookie set +
  item in view; GET /api/cart without cookie → empty, no cart row; /api/session
  without cookie → zeros + no-store; with cookie → real count; flow: API add →
  /kosar page HTML contains the product (acceptance); product page still has no
  Set-Cookie with Security on the classpath (existing test).

## Out of scope

Login + cart merge (T13), checkout + price locking + hard stock reservation (T9),
the A-track shared island bundle (K1 — cart.js is the seed/contract).
