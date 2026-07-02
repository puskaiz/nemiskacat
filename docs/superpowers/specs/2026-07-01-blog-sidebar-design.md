# Blog Sidebar — Design

**Goal:** Give the public blog a populated right sidebar on both the list pages
(`/blog`, `/blog/kategoria/{slug}`) and the article page (`/{slug}`),
recreating the spirit of the production WordPress sidebar within our
architecture (server-rendered, session-independent, cacheable — CLAUDE.md #2),
with the sidebar content **managed in the admin SPA**.

## Background

- `/blog` (`blog/list.html`) already has the layout grid `.nk-blog-layout`
  (content `2fr` + `<aside class="nk-blog-sidebar">` `1fr`) but the aside is
  empty. The article page (`blog/post.html`) does not use that grid at all.
- The production sidebar (Elementor) had: author bio, a tag cloud, a live
  Instagram feed, a "paint this?" CTA, and contact/social. We recreate the
  on-architecture subset and make it admin-managed.

## Block set

Five block types, all server-rendered, no per-user data:

1. **AUTHOR** — photo + name + short bio.
2. **CATEGORIES** — managed blog categories, alphabetical, linking to
   `/blog/kategoria/{slug}`; honors a per-category hide flag. Stands in for the
   production "Címkék" (we have categories, not tags).
3. **CTA** — heading + image + button linking to the webshop.
4. **CONTACT** — phone / email / address.
5. **SOCIAL** — list of social links (Facebook, Instagram, YouTube).

Out of scope (named, deferred): **Instagram feed** (Basic Display API
deprecated Dec 2024; a live feed needs a third-party script or a full Graph API
integration — separate slice), **per-block image upload** (images are URL
fields the developer sets; existing image storage not wired in), and a
**related/recent-posts** block.

## Architecture — generic block CMS

A single managed table drives the sidebar; the public side renders enabled
blocks in order, and the admin SPA edits them. Chosen over bespoke per-block
tables (5× the endpoints/screens) and key-value settings (can't do
reorder/repeating items) because it delivers edit + enable/disable + reorder
with one table, one REST resource, one admin screen.

```
domain/sidebar/      SidebarBlock entity (+ BlockType enum) + repository
                     BlogCategory gains `sidebarHidden` (per-category visibility)
application/sidebar/ SidebarQueryService.sidebar() -> SidebarView (parses
                     content JSON; attaches the live category list to the
                     CATEGORIES block)
web/                 BlogController @ModelAttribute injects SidebarView for ALL
                     its endpoints; post.html wrapped in .nk-blog-layout; shared
                     fragment fragments/blog.html :: sidebar
api/admin/           (Phase 2) SidebarBlockController — REST CRUD + reorder + toggle
admin-ui/            (Phase 2) Refine resource: sortable list + per-type edit form
db/migration/        V24__sidebar_blocks.sql (+ blog_category.sidebar_hidden)
static/css/          sidebar block styles in site.css
```

### Data model

**`sidebar_block`:**
- `id` bigserial PK
- `block_type` varchar NOT NULL — one of `AUTHOR | CATEGORIES | CTA | CONTACT | SOCIAL`
- `display_order` int NOT NULL — ascending render order
- `enabled` boolean NOT NULL DEFAULT true
- `content` TEXT NOT NULL — type-specific JSON payload (below), parsed with the
  already-used `ObjectMapper` (the codebase has no `jsonb`/Hibernate-JSON
  mapping; content is never queried inside the DB, only loaded and parsed)

**`blog_category`** gains `sidebar_hidden` boolean NOT NULL DEFAULT false
(per-category visibility within the CATEGORIES block; `uncategorized` seeded
hidden).

**Content JSON per block type:**
- AUTHOR: `{ "name": string, "bio": string, "photoUrl": string }`
- CATEGORIES: `{ "title": string }` — items come live from `blog_category`
  (not hidden, alphabetical); the block stores only its heading.
- CTA: `{ "title": string, "buttonLabel": string, "url": string, "imageUrl": string }`
- CONTACT: `{ "title": string, "phone": string, "email": string, "address": string }`
- SOCIAL: `{ "title": string, "links": [ { "network": "facebook|instagram|youtube", "url": string } ] }`

The CONTACT block carries its own copy of the contact details (admin-managed);
the footer keeps its existing static contact list (no shared-snippet dedup — it
would couple admin-managed sidebar content to the footer).

### Rendering model (read side)

`SidebarQueryService.sidebar()` returns `SidebarView(List<SidebarBlockView>
blocks)`, ordered by `display_order`, `enabled = true` only. Each
`SidebarBlockView` carries `type` (String) and the parsed typed payload for
that type (other payloads null); the CATEGORIES payload additionally carries
the live `List<CategoryRef>`. The Thymeleaf fragment `th:switch`es on `type`.

Business logic (ordering, enabled filter, JSON parse, category list, hidden
filter) lives in the service (rule #1); the controller and templates are thin.

**Cacheability (CLAUDE.md #2):** every block is identical for all visitors — no
user-/cart-dependent data. The reads are small and cache-friendly. Compliant.

## Phasing

Two independently-shippable plans:

### Phase 1 — public sidebar from seeded blocks (ships the visible feature)

- `sidebar_block` table + `blog_category.sidebar_hidden` (migration V24),
  **seeded** with today's five blocks and their content.
- `SidebarBlock` entity + `BlockType` enum + repository.
- `SidebarQueryService.sidebar()` + view records; `BlogQueryService` gains the
  category lookup used by the CATEGORIES block (not-hidden, alphabetical).
- `BlogController` `@ModelAttribute("sidebar")` injects the view for all blog
  endpoints.
- Templates: `post.html` wrapped in `.nk-blog-layout`; `list.html` aside fed by
  the shared fragment; new `fragments/blog.html :: sidebar` with the `th:switch`
  render of each block type (inline-SVG social icons).
- CSS in `site.css`; author placeholder asset.
- No admin UI; blocks are edited via the seed/migration by the developer.

### Phase 2 — admin management

- `SidebarBlockController` under `/api/admin/sidebar-blocks` (ADMIN role):
  list, update content, toggle `enabled`, reorder (`display_order`). Validates
  content against the block type.
- Admin SPA (Refine) resource: a sortable list with enable toggles and a
  per-type edit form (AUTHOR/CTA/CONTACT/SOCIAL fields; CATEGORIES = title +
  a sub-list of categories with visibility toggles writing
  `blog_category.sidebar_hidden`).
- Behind the existing admin auth; no public surface change.

## Testing

- **Phase 1:** `SidebarQueryServiceIT` (Testcontainers) — ordering by
  `display_order`, `enabled` filter, JSON parse per type, CATEGORIES attaches
  not-hidden alphabetical categories (excludes `uncategorized`).
  `BlogControllerIT` — sidebar fragment renders on `/blog` and an article URL;
  a visible category appears, a hidden one does not; a disabled block is absent.
- **Phase 2:** `SidebarBlockControllerIT` — CRUD/reorder/toggle require ADMIN
  (401 unauth), content validated per type, reorder persists. Admin-UI:
  follow existing Refine test patterns if present.

## Open items (owner input, non-blocking)

- Full author bio text + author photo asset (Phase 1 seeds the one-line opener
  "Szendrődi Enikő vagyok, a Nemiskacat tulajdonosa, hobbibútorfestő." + a
  placeholder photo).
- Final block copy/headings (defaults proposed).

## Confirmed decisions

- Generic block CMS (one `sidebar_block` table), two phases.
- Admin can edit content, enable/disable, and reorder blocks; per-category
  visibility via `sidebar_hidden`. No image upload (URL fields).
- CTA links to the webshop (`/termekkategoria/kretafestek-annie-sloan-chalk-paint/`,
  same as the header "Webshop" link).
- Categories alphabetical; `uncategorized` seeded hidden.
- Instagram deferred.
