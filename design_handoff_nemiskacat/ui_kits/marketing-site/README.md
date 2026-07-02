# Marketing Site UI Kit

A click-thru recreation of the nemiskacat.hu marketing experience. Built from the brand book (no production codebase or Figma was attached), so this is a *faithful interpretation* — not a 1:1 component copy.

## Files

| File | What it is |
| --- | --- |
| `index.html` | Mounts the whole experience. Open this. |
| `styles.css` | Site-specific CSS (imports `colors_and_type.css` from the root). |
| `App.jsx` | Top-level composition + cart state. |
| `Nav.jsx` | Sticky top nav with wordmark, links, search, cart. |
| `Hero.jsx` | Hero band — eyebrow + Caveat slogan on a brushstroke + sub + CTA + lifestyle photo + "Újdonság" badge. |
| `CategoryGrid.jsx` | 4-up category tiles on warm-gray tone background, using the hand-drawn icons. |
| `FeaturedProducts.jsx` | 4-up product grid. Clicking a card adds it to the cart drawer. |
| `StoryRow.jsx` | Editorial split — photo + headline + body + secondary CTA. |
| `Testimonial.jsx` | Soft-yellow pull-quote card (Annie Sloan founder quote from the brand book). |
| `WorkshopCard.jsx` | "Karácsonyi Alkotónap" card pulled from the real e-mail header. |
| `Footer.jsx` | Warm-gray footer with contact rows using the hand-drawn icons. |
| `CartDrawer.jsx` | Right-side cart drawer; opens on cart-icon click and on add-to-cart. |

## Interactions you can click

- **Top-right cart icon** → opens the drawer (which is pre-seeded with two items).
- **Any product card** → adds that product to the cart and opens the drawer.
- **Drawer backdrop / ✕** → closes the drawer.
- **Hovers** are tuned on the nav links (yellow underline), category tiles (lift), product cards (lift + soft shadow).

## What's faithful, what's invented

- ✅ Wordmark, brushstrokes, hand-drawn icons — all from official assets.
- ✅ Lora + Caveat type system, exact px values from the brand book.
- ✅ The single-yellow / warm-gray / black / white palette.
- ✅ Caveat-script CTA labels (`Megyek a webshopba`, `Regisztrálj`).
- ⚠️ Product imagery is stand-in (a circular paint-swatch on a tinted card). Drop real product photography into `assets/photos/` to replace.
- ⚠️ Lifestyle hero photo is a soft gradient placeholder. Same — swap with real imagery.
- ⚠️ Workshop / story photo is a placeholder.
- ❌ No real e-commerce wiring — clicking checkout does nothing.
