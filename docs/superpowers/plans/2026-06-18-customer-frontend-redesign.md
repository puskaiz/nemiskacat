# Customer Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the `design_handoff_nemiskacat` brand design system to the customer-facing Spring Boot + Thymeleaf webshop — porting the handoff verbatim (tokens, fonts, PNG assets, marketing components) and restyling every page.

**Architecture:** A single verbatim CSS token layer (`colors_and_type.css`, `--nk-*`) + brand fonts/assets feed a rebuilt `site.css`. Global chrome (nav/footer/cart-drawer) lives in `fragments/layout.html`; the cart drawer is an empty shell hydrated by `cart.js` from a new read-only `GET /api/cart`. The marketing homepage and all functional pages are recreated as Thymeleaf, wired to existing `CatalogQueryService`/`CartService` view-models. No business logic changes.

**Tech Stack:** Spring Boot 3 / Thymeleaf, plain CSS (no build step for the customer site), vanilla-JS session island, JUnit + Testcontainers.

## Global Constraints

- Cacheable HTML (home, product, category, workshop) carries **no session/cart/user data**. Cart count, auth state, and cart-drawer lines come only from the island (`/api/session`, `/api/cart`, both `Cache-Control: no-store`).
- **No business-logic changes.** Order placement, payment callback, cart mutation, stock derivation, and their tests are untouched.
- **No new framework/library.** Plain CSS + existing vanilla-JS island only. Do not add htmx.
- **Slugs/URLs preserved 1:1.** Product `Product` JSON-LD and blog `Article` JSON-LD preserved where present.
- **Money:** HUF minor units; view-models already expose pre-formatted strings (`*Formatted`) via `Money.huf(amount).formatted()` → `"3 700 Ft"` (NBSP separator). Templates never format money themselves.
- **Hungarian copy, informal capitalised "Te", "Mi" voice, sentence case. NO emoji and NO decorative Unicode glyphs** (★ ✓ → 📍 etc.) — use the hand-drawn PNG icon set; carets/arrows/checks are CSS-drawn.
- Code/comments/commit messages in English.
- Verification gate per task: `mvn -q compile` succeeds; touched backend gets `mvn -q test` green; existing tests stay green; app starts.

## Source files to port (in `design_handoff_nemiskacat/`)

- Tokens/fonts: `colors_and_type.css`, `fonts/*.ttf`.
- Assets: `assets/logos/` (6), `assets/brushes/` (10), `assets/icons/` (11).
- Components: `ui_kits/marketing-site/{Nav,Hero,CategoryGrid,FeaturedProducts,StoryRow,Testimonial,WorkshopCard,Footer,CartDrawer}.jsx` + `ui_kits/marketing-site/styles.css`.
- Voice/visual rules: `SYSTEM.md`, `README.md`.

## Existing backend contracts (do not re-derive)

- `CartController` (`api/CartController.java`): `POST/PATCH/DELETE /api/cart/items*` return `CartView(List<CartItemView> items, int count, String totalFormatted)`. `CartItemView(long id, String name, String variantLabel, String sku, int quantity, String unitPriceFormatted, String lineTotalFormatted, String productUrl, String imageUrl)`. `CartService.view(String token)` returns `CartView`. Cart cookie const `CartController.CART_COOKIE`. No-store via `response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")`.
- `CatalogQueryService` (`application/catalog/CatalogQueryService.java`): `Optional<ProductPageView> productPage(slug)`, `Optional<CategoryPageView> categoryPage(slug, page)`, `List<WorkshopListItemView> workshopList()`. Card view-model `CategoryItemView(String name, String url, String imageUrl, String priceFromFormatted, boolean available, boolean addable, String sku)`. `WorkshopListItemView(String name, String url, String imageUrl, String nextDateLabel, String priceFromFormatted, boolean hasUpcoming)`.
- `HomeController` (`web/HomeController.java`): currently sets only `appName`.
- `Money` (`domain/catalog/Money.java`): `Money.huf(long).formatted()`.
- Blog: **no Java backend exists** — StoryRow is static this pass.

---

## Phase A — Brand foundation

### Task A1: Vendor brand fonts, assets, and verbatim token layer

**Files:**
- Create: `src/main/resources/static/fonts/` (12 `.ttf` copied from handoff)
- Create: `src/main/resources/static/design/assets/{logos,brushes,icons}/` (PNGs copied from handoff)
- Create: `src/main/resources/static/design/colors_and_type.css` (verbatim port)
- Modify: `src/main/resources/static/design/styles.css` (import the token layer)
- Commit: the untracked `design_handoff_nemiskacat/` folder into the repo

**Interfaces:**
- Produces: `--nk-*` CSS custom properties + `.nk-h1…h5`, `.nk-body*`, `.nk-caption`, `.nk-eyebrow`, `.nk-script`, `.nk-slogan` classes; `@font-face` for Lora/Caveat resolving to `/fonts/*.ttf`; asset URLs under `/design/assets/...`.

- [ ] **Step 1: Copy fonts and assets into static**

```bash
cd /Users/zolika/Work/Claude/website
mkdir -p src/main/resources/static/fonts src/main/resources/static/design/assets
cp design_handoff_nemiskacat/fonts/*.ttf src/main/resources/static/fonts/
cp -R design_handoff_nemiskacat/assets/logos src/main/resources/static/design/assets/
cp -R design_handoff_nemiskacat/assets/brushes src/main/resources/static/design/assets/
cp -R design_handoff_nemiskacat/assets/icons src/main/resources/static/design/assets/
ls src/main/resources/static/fonts && ls src/main/resources/static/design/assets/*/
```
Expected: 12 ttf files; logos (6), brushes (10), icons (11) PNGs present.

- [ ] **Step 2: Port `colors_and_type.css` verbatim**

Read `design_handoff_nemiskacat/colors_and_type.css` and copy it to `src/main/resources/static/design/colors_and_type.css` **byte-for-byte EXCEPT** rewrite each `@font-face` `src: url(...)` to point at `/fonts/<File>.ttf` (the static path). Do not rename tokens, change values, or drop classes.

- [ ] **Step 3: Wire the token layer into `styles.css`**

Read the current `src/main/resources/static/design/styles.css`. Make its first line `@import "/design/colors_and_type.css";` and remove `@import`s of the old `--ns-*` token files (`tokens/colors.css`, `tokens/typography.css`, `tokens/fonts.css`, `tokens/spacing.css`, `tokens/motifs.css`). Leave the old `tokens/*.css` files on disk for now (Task A2 removes them once `site.css` no longer references `--ns-*`).

- [ ] **Step 4: Favicon → n-mark**

In `src/main/resources/templates/fragments/layout.html`, inside `head(title)`, add: `<link rel="icon" href="/design/assets/logos/n-mark-yellow.png">`.

- [ ] **Step 5: Verify the app serves the assets**

Run: `mvn -q compile`
Expected: success. (Static assets need no compile; this confirms nothing broke.)

- [ ] **Step 6: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add design_handoff_nemiskacat src/main/resources/static/fonts src/main/resources/static/design src/main/resources/templates/fragments/layout.html
git commit -m "feat(customer-ui): vendor nemiskacat brand fonts, assets, and verbatim design tokens"
```

### Task A2: Rebuild `site.css` base layer on `--nk-*` tokens

**Files:**
- Modify: `src/main/resources/static/css/site.css` (rebuild on `--nk-*`)
- Delete: `src/main/resources/static/design/tokens/*.css` (old `--ns-*` files, once unreferenced)

**Interfaces:**
- Consumes: `--nk-*` tokens + `.nk-*` type classes from A1.
- Produces: base resets, `.wrap` container (max-width 1200px content / 1400px hero, token gutters), and the global element treatments every later task relies on: primary **pill** button (`yellow-50`→`yellow-60` hover, `translateY(-1px)`, `shadow-sm`), secondary outline button (inverts to black fill on hover), ghost/link (2px `yellow-50` underline), card (1px `gray-30`, `radius-md`, `shadow-sm`; interactive cards lift `translateY(-2px)`→`shadow-md`), form fields (`input` fill, 1px border, focus ring), and the **focus ring** (3px `yellow-50`, 2px offset) on all interactive elements.

- [ ] **Step 1: Rebuild `site.css`**

Read the current `src/main/resources/static/css/site.css` and `design_handoff_nemiskacat/ui_kits/marketing-site/styles.css`. Rewrite `site.css` so every color/size/radius/shadow/motion value comes from a `--nk-*` token (or the handoff's exact values), implementing the element treatments listed in Interfaces and the state spec in `SYSTEM.md` §"Animation & interaction states". Keep the existing CSS class names that templates already use (`.topbar`, `.main`, `.nav-inner`, `.nav-left`, `.logo`, `.chip`, `.cart`, `.cart-badge`, `.foot-grid`, `.wrap`, product grid/detail/cart/form/account classes) so current templates keep working — only their *appearance* changes here. Replace any `--ns-*` reference with the `--nk-*` equivalent.

- [ ] **Step 2: Confirm no `--ns-*` references remain**

Run: `grep -rn -- "--ns-" src/main/resources/static src/main/resources/templates`
Expected: no matches. If any remain, fix them, then re-run.

- [ ] **Step 3: Delete the orphaned old token files**

```bash
cd /Users/zolika/Work/Claude/website
rm -f src/main/resources/static/design/tokens/colors.css src/main/resources/static/design/tokens/typography.css src/main/resources/static/design/tokens/fonts.css src/main/resources/static/design/tokens/spacing.css src/main/resources/static/design/tokens/motifs.css
rmdir src/main/resources/static/design/tokens 2>/dev/null || true
grep -rn "tokens/" src/main/resources/static/design/styles.css || echo "no token-file imports remain"
```
Expected: "no token-file imports remain".

- [ ] **Step 4: Verify**

Run: `mvn -q compile`
Expected: success.

- [ ] **Step 5: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/resources/static/css/site.css src/main/resources/static/design
git commit -m "feat(customer-ui): rebuild site.css base + states on nemiskacat tokens"
```

---

## Phase B — Global chrome + cart island

### Task B1: Read-only `GET /api/cart` for the drawer

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/api/CartController.java`
- Test: `src/test/java/hu/deposoft/webshop/api/CartControllerTest.java` (add cases; create if absent — follow the existing cart/web test style)

**Interfaces:**
- Produces: `GET /api/cart` → `CartView` (same record the other cart endpoints return), `Cache-Control: no-store`. Consumed by `cart.js` in Task B4.

- [ ] **Step 1: Write the failing test**

Add to the cart controller test (MockMvc, mirroring how the existing POST/PATCH tests are written). Empty cart returns `count: 0` and `no-store`; a populated cart returns its items.

```java
@Test
void getCart_emptyCart_returnsZeroAndNoStore() throws Exception {
    mockMvc.perform(get("/api/cart"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.count").value(0))
        .andExpect(jsonPath("$.items").isArray());
}
```
(Add a populated-cart case matching how existing tests seed a cart via `CartService`/the add endpoint, asserting `$.items[0].name` and `$.count`.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=CartControllerTest test`
Expected: FAIL — no handler for `GET /api/cart`.

- [ ] **Step 3: Implement the endpoint**

Add to `CartController`, following the no-store pattern used by the existing methods:

```java
@GetMapping
public CartView cart(@CookieValue(name = CART_COOKIE, required = false) String token,
                     HttpServletResponse response) {
    response.setHeader(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store");
    return cartService.view(token);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=CartControllerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/java/hu/deposoft/webshop/api/CartController.java src/test/java/hu/deposoft/webshop/api/CartControllerTest.java
git commit -m "feat(customer-ui): read-only GET /api/cart (no-store) for the cart drawer"
```

### Task B2: Nav / header chrome

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html` (`header` fragment)
- Modify: `src/main/resources/static/css/site.css` (nav/topbar styles)

**Interfaces:**
- Consumes: tokens/classes (A1/A2); logo `nemiskacat-logo-black.png`; cart badge `[data-cart-count]` filled by the island.
- Produces: restyled `header` fragment; markup stable enough that `cart.js`'s `.nav-toggle`/`#navMenu`/`[data-cart-count]` hooks still resolve.

- [ ] **Step 1: Port the Nav**

Read `design_handoff_nemiskacat/ui_kits/marketing-site/Nav.jsx` + the nav rules in the handoff README ("Global: Nav"). Recreate the `header` fragment in `layout.html` faithfully: sticky bar, wordmark `<img src="/design/assets/logos/nemiskacat-logo-black.png" height="30">`, nav links (Webshop, Workshopok, Tudástár, Színek — keep existing hrefs), 38px circular search + cart icon-buttons, yellow cart-count badge. Keep the `.nav-toggle`, `#navMenu`, `.cart-badge[data-cart-count]` hooks. **Replace the `📍` emoji** in the topbar with the hand-drawn `house.png` icon (`<img src="/design/assets/icons/house.png" ...>`) + text, or drop the marker; keep the free-shipping line. Style the nav (white 96% + 6px blur on scroll handled in B4/cart.js or pure CSS sticky; at minimum 1px `gray-30` bottom border, yellow hover underline on links) in `site.css`.

- [ ] **Step 2: Verify**

Run: `mvn -q compile`
Expected: success. Note for reviewer: visual check that the nav renders with wordmark + icons and no emoji.

- [ ] **Step 3: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/resources/templates/fragments/layout.html src/main/resources/static/css/site.css
git commit -m "feat(customer-ui): port nemiskacat nav/header chrome"
```

### Task B3: Footer chrome

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html` (`footer` fragment)
- Modify: `src/main/resources/static/css/site.css` (footer styles)

**Interfaces:**
- Consumes: tokens; hand-drawn icons `phone/email/house/delivery`.
- Produces: restyled `footer` fragment.

- [ ] **Step 1: Port the Footer**

Read `design_handoff_nemiskacat/ui_kits/marketing-site/Footer.jsx` + handoff README ("Footer"). Recreate the `footer` fragment: warm-gray (`--nk-gray-20`) background, 4-col grid `1.4fr/1fr/1fr/1fr`, brand column (wordmark + 15px description), three link columns (keep existing footer links/sections), contact column with inline hand-drawn `phone`/`email`/`house`/`delivery` PNG icons (22px) + label, bottom divider + copyright. Keep Hungarian copy. Style in `site.css`.

- [ ] **Step 2: Verify**

Run: `mvn -q compile`
Expected: success.

- [ ] **Step 3: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/resources/templates/fragments/layout.html src/main/resources/static/css/site.css
git commit -m "feat(customer-ui): port nemiskacat warm-gray footer"
```

### Task B4: Cart drawer shell + `cart.js` hydration

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html` (add cart-drawer shell to `header` or a new `cart-drawer` block included in the layout body)
- Modify: `src/main/resources/static/js/cart.js`
- Modify: `src/main/resources/static/css/site.css` (drawer + backdrop styles)

**Interfaces:**
- Consumes: `GET /api/cart` (B1) → `CartView`; cart icon button (B2).
- Produces: a right-side drawer that opens on cart-icon click and on add-to-cart, hydrated client-side from `/api/cart`; no cart data in server HTML.

- [ ] **Step 1: Add the empty drawer shell**

Read `design_handoff_nemiskacat/ui_kits/marketing-site/CartDrawer.jsx`. Add to `layout.html` (as part of the `header` fragment, after `</nav>`) an **empty** drawer + backdrop: `<div class="cart-drawer" data-cart-drawer hidden>` with a header (22px title "Kosár" + a CSS-drawn ✕ close button `[data-cart-close]`), an empty `<div data-cart-lines>` body, and a footer with `<span data-cart-subtotal>` + a full-width primary checkout button linking to `/kosar`. Add `<div class="cart-backdrop" data-cart-backdrop hidden>`. No line data in the HTML.

- [ ] **Step 2: Style the drawer**

In `site.css`: fixed right, 420px, full height, `--nk-shadow-lg`, `transform: translateX(100%)` → `0` on `.open`, 360ms `--nk-ease`; backdrop `rgba(0,0,0,.3)` fade. Cart line row: 64px thumb + name/variant + Caveat price.

- [ ] **Step 3: Extend `cart.js`**

Add to `cart.js` (vanilla, same IIFE style): a `renderDrawer(view)` that builds line rows from a `CartView` (`view.items[].name/variantLabel/imageUrl/lineTotalFormatted`, `view.totalFormatted`) into `[data-cart-lines]`/`[data-cart-subtotal]`; an `openDrawer()` that `fetch('/api/cart')` → `renderDrawer` → adds `.open` and unhides backdrop; close handlers on `[data-cart-close]` and `[data-cart-backdrop]`. Wire the cart icon (`.chip.cart`) click to `openDrawer()` (preventDefault so it no longer navigates to `/kosar`). After a successful `add(...)`, call `openDrawer()`. **Remove the `'Kosárban ✓'` Unicode check** — use `'Kosárban'` or re-open the drawer as the confirmation.

- [ ] **Step 4: Verify**

Run: `mvn -q compile`
Expected: success. Reviewer visual check: clicking the cart icon opens the drawer with current lines; backdrop/✕ closes it.

- [ ] **Step 5: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/resources/templates/fragments/layout.html src/main/resources/static/js/cart.js src/main/resources/static/css/site.css
git commit -m "feat(customer-ui): cart drawer shell + client hydration from /api/cart"
```

### Task B5: Shared product-card + section fragments

**Files:**
- Create: `src/main/resources/templates/fragments/components.html`
- Modify: `src/main/resources/static/css/site.css` (product-card + section styles)

**Interfaces:**
- Consumes: `CategoryItemView(name, url, imageUrl, priceFromFormatted, available, addable, sku)`.
- Produces: a Thymeleaf fragment `productCard(item)` rendering the FeaturedProducts-style card (square image area, optional swatch, brand eyebrow, Lora name, **Caveat price**, add-to-cart button `[data-add-to-cart="${item.sku}"]` when `addable`, else a "Megnézem" link to `item.url`); plus a `sectionHeader(eyebrow, title, sub)` fragment. Reused by C2, D2.

- [ ] **Step 1: Build the fragments**

Read `design_handoff_nemiskacat/ui_kits/marketing-site/FeaturedProducts.jsx`. Create `components.html` with `th:fragment="productCard(item)"` recreating that card markup against `CategoryItemView`, and `th:fragment="sectionHeader(eyebrow,title,sub)"` (eyebrow + 48px title + sub). Card uses existing add-to-cart hook (`data-add-to-cart` → `cart.js`). Style `.nk-product-card`, `.nk-section-header` in `site.css`.

- [ ] **Step 2: Verify**

Run: `mvn -q compile`
Expected: success.

- [ ] **Step 3: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/resources/templates/fragments/components.html src/main/resources/static/css/site.css
git commit -m "feat(customer-ui): shared product-card + section header fragments"
```

---

## Phase C — Marketing homepage

### Task C1: Homepage backend (real categories, featured products, next workshop)

**Files:**
- Modify: `src/main/java/hu/deposoft/webshop/application/catalog/CatalogQueryService.java`
- Modify: the relevant repository (`CategoryRepository` and/or `ProductRepository`) under `application/catalog/` or `domain/catalog/`
- Create: `HomePageView` (record, in `application/catalog/` next to the other views)
- Modify: `src/main/java/hu/deposoft/webshop/web/HomeController.java`
- Test: `src/test/java/.../catalog/CatalogQueryServiceTest.java` (add cases; follow existing catalog test style with Testcontainers)

**Interfaces:**
- Produces:
  - `record HomeCategoryView(String name, String url, long productCount, String iconKey)` — `iconKey` is one of the handoff icon filenames without extension (default `"paint-can"`).
  - `List<HomeCategoryView> CatalogQueryService.topLevelCategories()` — published top-level categories (`CategoryRepository.findByParentIsNull(...)`), with product counts.
  - `List<CategoryItemView> CatalogQueryService.featuredProducts(int limit)` — first `limit` published, available simple/variable products (reuses the existing `CategoryItemView` card shape).
  - `Optional<WorkshopListItemView> CatalogQueryService.nextWorkshop()` — the soonest `hasUpcoming` item from `workshopList()` (or empty).
  - `record HomePageView(List<HomeCategoryView> categories, List<CategoryItemView> featured, WorkshopListItemView nextWorkshop)` (nextWorkshop nullable).
  - `HomeController` sets model attribute `home` → `HomePageView` (keep `appName`).

- [ ] **Step 1: Write failing tests**

In the catalog service test, add cases (seeding via the existing fixtures):
- `topLevelCategories()` returns only parent-less published categories with correct `productCount`.
- `featuredProducts(4)` returns at most 4 available products as `CategoryItemView`.
- `nextWorkshop()` returns the soonest upcoming workshop, or empty when none.

Write concrete assertions matching the seed data already used by the existing catalog tests (read that test to reuse its fixture builders).

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q -Dtest=CatalogQueryServiceTest test`
Expected: FAIL — methods don't exist.

- [ ] **Step 3: Implement repository finders + service methods + view-models**

Add `findByParentIsNull` (with a name `Sort`) to the category repository; implement the three `CatalogQueryService` methods and the `HomeCategoryView`/`HomePageView` records. Map category→`iconKey` with a sensible default (`"paint-can"`); featured products reuse the existing internal mapper that builds `CategoryItemView`. Reuse `Money.huf(...).formatted()` indirectly via the existing view builders — do not format in the controller.

- [ ] **Step 4: Wire `HomeController`**

```java
@GetMapping("/")
public String home(Model model) {
    model.addAttribute("appName", appInfoService.currentInfo().name());
    model.addAttribute("home", new HomePageView(
        catalogQueryService.topLevelCategories(),
        catalogQueryService.featuredProducts(4),
        catalogQueryService.nextWorkshop().orElse(null)));
    return "index";
}
```
(Inject `CatalogQueryService` via the constructor — match the existing `@RequiredArgsConstructor` pattern.)

- [ ] **Step 5: Run tests**

Run: `mvn -q -Dtest=CatalogQueryServiceTest test`
Expected: PASS. Then `mvn -q compile` for the controller.

- [ ] **Step 6: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/java/hu/deposoft/webshop/application/catalog src/main/java/hu/deposoft/webshop/web/HomeController.java src/test/java
git commit -m "feat(customer-ui): homepage query methods + HomePageView wiring"
```

### Task C2: Marketing homepage template

**Files:**
- Modify: `src/main/resources/templates/index.html` (full rebuild)
- Modify: `src/main/resources/static/css/site.css` (home section styles)

**Interfaces:**
- Consumes: `home` (`HomePageView`) from C1; `components.html` fragments (B5); chrome (B2/B3/B4); brushes/icons assets.
- Produces: the full marketing homepage, all cacheable (no session data).

- [ ] **Step 1: Build the homepage**

Read `design_handoff_nemiskacat/ui_kits/marketing-site/{Hero,CategoryGrid,FeaturedProducts,StoryRow,Testimonial,WorkshopCard}.jsx` and `App.jsx` for composition order. Rebuild `index.html` including the layout head/header/footer + island-script, with sections in order:
- **Hero** (static): eyebrow → Caveat slogan on `brush-short-yellow.png` (slogan from `SYSTEM.md`, e.g. *"Az ecsetet Te fogod, a többit mi adjuk!"*) → Lora lead → primary pill CTA *"Megyek a webshopba"* (→ the Webshop category URL) + ghost link → right lifestyle photo placeholder with circular *"Újdonság!"* `brush-circle-yellow.png` badge.
- **CategoryGrid** (real): tone background; `th:each` over `${home.categories}`, each tile = `/design/assets/icons/${cat.iconKey}.png` + `cat.name` + `cat.productCount` "termék", link `cat.url`.
- **FeaturedProducts** (real): `th:each` over `${home.featured}` rendering `~{fragments/components :: productCard(${item})}`.
- **WorkshopCard** (real): `th:if="${home.nextWorkshop != null}"` render the next workshop (name, `nextDateLabel`, `priceFromFormatted`, CTA → `url`); else a "Hamarosan" fallback.
- **StoryRow** (static): editorial teaser (photo placeholder + headline + two paragraphs + secondary CTA) linking to `/workshopok` (no blog backend yet — keep copy generic/on-brand).
- **Testimonial** (static): soft-yellow card, Annie Sloan founder quote (Lora italic 24px) from the handoff.

Style the home sections in `site.css` per the handoff layout specs (paddings ≥64px, grids, tone backgrounds).

- [ ] **Step 2: Confirm no session data leaks**

Run: `grep -n "cart\|auth\|fiok\|session" src/main/resources/templates/index.html | grep -iv "kosar.*link\|nav"` — confirm no per-user/cart data is server-rendered into the homepage (cart badge stays the island hook only).

- [ ] **Step 3: Verify**

Run: `mvn -q compile`
Expected: success. Reviewer visual check: homepage renders all sections with real categories/products/workshop.

- [ ] **Step 4: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add src/main/resources/templates/index.html src/main/resources/static/css/site.css
git commit -m "feat(customer-ui): marketing homepage (hero, categories, featured, workshop, story, testimonial)"
```

---

## Phase D — Functional pages restyle

> All Phase D tasks: **markup + CSS only**, logic untouched. Reuse `components.html` fragments and existing data hooks (`data-add-to-cart`, `.cart-grid .qinput`/`.remove`, form field names). Preserve JSON-LD blocks verbatim. Each task ends with `mvn -q compile` + a reviewer visual note + commit.

### Task D1: Product detail page

**Files:**
- Modify: `src/main/resources/templates/product.html`
- Modify: `src/main/resources/static/css/site.css`

- [ ] **Step 1: Restyle product.html**

Read `product.html` and `design_handoff_nemiskacat/ui_kits/marketing-site/styles.css` (card/button/type idioms). Restyle the gallery (cover + thumbs + prev/next), breadcrumb, title, variant/size picker, qty stepper, add-to-cart button (primary pill), price (Caveat for the price figure, strikethrough regular when on sale), stock status, perks grid, tabbed description — all on `--nk-*` tokens. **Keep** every `th:*` binding, the `data-add-to-cart`/gallery JS hooks, and the `<script type="application/ld+json">` JSON-LD block exactly. Replace any emoji/Unicode glyph with a hand-drawn icon or CSS shape.

- [ ] **Step 2: Verify + commit**

Run: `mvn -q compile` → success.
```bash
git add src/main/resources/templates/product.html src/main/resources/static/css/site.css
git commit -m "style(customer-ui): restyle product detail to nemiskacat system"
```

### Task D2: Category + workshops listings

**Files:**
- Modify: `src/main/resources/templates/category.html`, `src/main/resources/templates/workshops.html`
- Modify: `src/main/resources/static/css/site.css`

- [ ] **Step 1: Restyle category.html**

Restyle the listing: breadcrumb/title, optional category description, toolbar (count), the product grid using `~{fragments/components :: productCard(${item})}` over `${category.items}` (the `CategoryItemView` list), and pagination (CSS-drawn carets, no Unicode). Keep all bindings/hooks.

- [ ] **Step 2: Restyle workshops.html**

Restyle the workshop grid against `${workshops}` (`WorkshopListItemView`): card image, name, `nextDateLabel` or "Hamarosan" (`hasUpcoming`), `priceFromFormatted`, link to `url`. Match the card system.

- [ ] **Step 3: Verify + commit**

Run: `mvn -q compile` → success.
```bash
git add src/main/resources/templates/category.html src/main/resources/templates/workshops.html src/main/resources/static/css/site.css
git commit -m "style(customer-ui): restyle category + workshops listings"
```

### Task D3: Cart, checkout, order-confirmation

**Files:**
- Modify: `src/main/resources/templates/cart.html`, `checkout.html`, `order-confirmation.html`
- Modify: `src/main/resources/static/css/site.css`

- [ ] **Step 1: Restyle the three pages**

Restyle (no-store pages, logic untouched): `cart.html` table + summary sidebar (keep `.cart-grid .qinput`/`.remove` hooks and `clientKey`/item bindings); `checkout.html` shipping form (token-styled inputs, **required `*` in red**, invalid = red border + helper text per the state spec), shipping-method radios, ÁSZF checkbox, order summary, hidden `clientKey`; `order-confirmation.html` order number, status (success/pending/failed using token colors, not emoji), items table, totals/VAT breakdown, retry button. Keep all `th:*` bindings and form field names.

- [ ] **Step 2: Verify + commit**

Run: `mvn -q compile` → success.
```bash
git add src/main/resources/templates/cart.html src/main/resources/templates/checkout.html src/main/resources/templates/order-confirmation.html src/main/resources/static/css/site.css
git commit -m "style(customer-ui): restyle cart, checkout, order confirmation"
```

### Task D4: Account + login

**Files:**
- Modify: `src/main/resources/templates/account-login.html`, `account.html`
- Modify: `src/main/resources/static/css/site.css`

- [ ] **Step 1: Restyle auth + account**

Restyle `account-login.html` (two-column login + registration, branded card, error/success notices, validation states) and `account.html` (greeting, logout, customer info, order-history table). Keep all form actions, field names, and `th:*` bindings.

- [ ] **Step 2: Verify + commit**

Run: `mvn -q compile` → success.
```bash
git add src/main/resources/templates/account-login.html src/main/resources/templates/account.html src/main/resources/static/css/site.css
git commit -m "style(customer-ui): restyle account + login"
```

---

## Final verification (after all tasks)

- [ ] `mvn -q test` — full suite green (new cart + catalog tests pass; existing order/payment/stock/cart tests unchanged and green).
- [ ] App starts (`mvn -q spring-boot:run` or the project's run skill); manual pass over homepage, chrome, drawer, and each restyled page; confirm focus rings, no emoji/Unicode glyphs, and no session data in cacheable HTML.
- [ ] Existing Playwright checkout-path E2E still passes (extend only if a selector changed).

## Self-review notes (gaps vs. spec)

- StoryRow is **static** (no blog Java backend exists) — spec's "real data where cheap" excludes blog. Recorded as a known follow-up: wire StoryRow to the blog once a `BlogQueryService` exists.
- "Featured products" has no domain flag; `featuredProducts(limit)` = first N published/available products (documented choice; revisit if the team wants a curated flag).
- Nav blur-on-scroll may need a small scroll listener in `cart.js`; acceptable as progressive enhancement (sticky + border works without it).
