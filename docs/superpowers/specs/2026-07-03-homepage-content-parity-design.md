# Homepage content parity with nemiskacat.hu — design

**Date:** 2026-07-03
**Status:** approved (full content parity; 4 defaults accepted)

## Goal

Rebuild the app homepage (`templates/index.html`) so its content and section
order mirror the live nemiskacat.hu homepage, keeping the A-track design system.
Real data where available (products, blog, categories, workshop); live copy for
static sections. Deploy locally + Railway.

## Section plan (top → bottom)

1. **Hero** — keep; slogan already matches. Expand to the live's 3 CTAs:
   Webshop (→ chalk-paint category), "Megyek olvasni, inspirálódni" (→ `/blog`),
   "Megnézem a workshopokat" (→ `/workshopok`). [static]
2. **Three pillars** — new 3-column intro (Festékek webshop · Bútorfelújítás
   tudásanyagok · Workshopok), each title+desc+CTA. [static]
3. **Category grid** — keep existing (`home.categories`). [real]
4. **Legnépszerűbb termékeink** — existing featured section, re-headed to the live
   copy ("Legnépszerűbb termékeink" / "TOP termékeink, amivel kezdőként is
   kiemelkedőt alkothatsz"), featured count raised to 8. [real] `home.featured`
5. **Miért krétafestékkel, miért velünk?** — new; 3 sub-blocks (könnyű / nincs
   rumli / színválasztás) with copy + contextual CTAs. [static]
6. **Kezdőknek: ingyenes kiadvány** — new; quote + "Érdekel a kiadvány" CTA →
   `/butorfestes-kezdoknek`. [static]
7. **Bolt / tanácsadás** ("Kérj tanácsot, szívesen segítünk!") — new; showroom
   block, CTA "Megnézem a boltot!" → `/latvanymuhely`. [static]
8. **Workshop** — keep real next-workshop card; add the live 5-point benefits list
   + CTA "Tanulni akarok!" → `/workshopok`. [real + static]
9. **Friss blogcikkek** — new; 3 latest published posts as cards + "Megnézem a
   többi cikket is!" → `/blog`. [real] new model attr `latestPosts`
10. **Záró CTA-sáv** ("Festeni akarok!") — new; full-width CTA → webshop. [static]
11. **GYIK** — new; native `<details>` accordion, curated 6–8 questions. [static]

The current "story row" folds into pillar #2 / blog #9; the Annie Sloan founder
quote is kept as an on-brand block (stands in for the live testimonials section).

## Decisions (accepted)

1. Asset-heavy sections **omitted**: press-logo strip, customer-photo testimonials,
   embedded customer-journey video (no assets). Founder quote stays.
2. The live's two product blocks ("Legnépszerűbb" + "TOP4") are **merged** into one
   real featured section (no separate curated TOP4 source).
3. **GYIK**: curated 6–8 questions on the homepage; the full list stays at
   `/gyakran-ismetelt-kerdesek`.
4. "Ingyenes kiadvány" CTA → `/butorfestes-kezdoknek` (no separate download page).

## Backend

- `HomeController`: raise `featuredProducts(4)` → `featuredProducts(8)`; add
  `model.addAttribute("latestPosts", blogQueryService.publishedList(1).items())`
  (template takes first 3). Inject `BlogQueryService`. Keep the model
  session-independent (CLAUDE.md #2).
- No new query methods required (`BlogQueryService.publishedList` + `BlogListItem`
  already provide slug/title/excerpt/coverImageUrl/categories/publishedDate; blog
  posts are served at `/{slug}`).

## Frontend

- `templates/index.html`: rewrite `<main>` with the sections above.
- `templates/fragments/components.html`: add `blogCard(item)` fragment (mirrors the
  existing `.nk-blog-card` markup from `blog/list.html`) for the latest-posts row.
- `static/css/site.css`: styles for the new sections (three-pillars, why-us,
  beginner-download, in-store, workshop-benefits, latest-posts grid, cta-banner,
  faq accordion), using existing design tokens.

## Verification

Template/data change — verify by rendering locally:
- Homepage renders fully (HTTP 200, no Thymeleaf error), all new sections present.
- Real data populates: featured products (8), category tiles, latest 3 blog cards
  (with cover images), next-workshop card.
- Every CTA href resolves (webshop category, `/blog`, `/workshopok`,
  `/butorfestes-kezdoknek`, `/latvanymuhely`).
- FAQ `<details>` expand/collapse without JS.
Then push to Railway and confirm the live homepage.

## Non-goals

Pixel-perfect visual match; press logos / testimonial photos / video; a separate
publication-download page; admin-editable homepage content.
