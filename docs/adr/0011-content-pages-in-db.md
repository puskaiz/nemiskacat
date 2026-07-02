# 11. Content pages stored in Postgres (DB-backed CMS)

Date: 2026-07-02

## Status

Accepted

## Context

The original system plan (TERV.md) envisaged a separate static-site generator (SSG)
and CDN layer handling ~50 WordPress content pages (legal, info, guides, etc.),
while the public site would remain a functional application layer. However, the
blog was pivoted into the database (ADR 0008, 0009) to enable publish-without-deploy
and unified admin UI, deferring the CDN.

The current objective is to make the entire public site functional from Spring Boot
now, with a separate CDN layer deferred. This slice migrates ~10 curated content
pages (legal docs, FAQs, guides — all Elementor-built in WordPress) into the same
DB-backed CMS approach that proved successful for blog posts.

## Decision

Store selected content pages in Postgres (`content_page` table): a minimal data model
with slug (immutable, unique), title, body (sanitized HTML, no Markdown), cover
image key, draft/published state, and created/updated timestamps.

**Import from WordPress** via the same export→import pattern as blog posts and
workshops:
- Python `export_pages.py` extracts Elementor HTML from WordPress, normalizes
  image URLs, and outputs a JSON snapshot.
- `PageImporter` (server-side, keyed on WordPress page ID) is idempotent: re-runs
  do not duplicate; edited WP title refreshes; slug remains locked (rule #7).
  Inline images are re-hosted to `/media/<key>` via `StorageService`.
- All write paths (import and admin save) pass HTML through jsoup sanitizer
  (`PageHtmlSanitizer`, reusing the allowlist and tag vocabulary from the blog).

**Render** content pages at the root slug namespace (`/{slug}`) via a new unified
`RootSlugController`:
- Resolution order: content page → blog post → 404 (content pages win on slug
  collision).
- Pages are session-independent, cacheable, rendered as sanitized HTML via
  `PageQueryService`.
- JSON-LD `WebPage` schema applied (a content page; a blogposztok ezzel szemben `Article` sémát kapnak).

**Edit** via the shared TipTap admin UI (`/api/admin/pages`), with the same
Draft/Published toggle and cover-image upload as blog posts. All writes are
re-sanitized server-side. Publish without deploy.

## Consequences

- The root-slug namespace (`/`) is now owned by a single resolution order
  (`RootSlugController`), preventing nav-menu and functional pages from
  colliding with content page slugs.
- Content pages can be published and unpublished without a deploy, consistent
  with blog posts (ADR 0008).
- Slugs remain immutable and unique (enforced at the DB level); SEO is
  preserved for migrated content.
- Public pages remain session-independent and cacheable (rule #2 upheld).
- The WordPress→DB pattern is now established across catalog, orders,
  workshops, blog posts, and content pages.
- Out of scope (deferred to later slices): header/footer nav-menu wiring,
  campaign/funnel pages with embedded forms, functional pages (cart, checkout,
  account — handled natively by Spring routes), CDN optimisation, page
  hierarchy/tree editing, and reverse-reference invalidation on product
  status change.
- CLAUDE.md rule #9 and the Stack section are updated: content pages are
  stored in Postgres, edited with TipTap, and published without deploy.
