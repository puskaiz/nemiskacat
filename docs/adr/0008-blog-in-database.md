# 8. Blog posts stored in Postgres (DB-backed CMS)

Date: 2026-06-29

## Status

Accepted

## Context

CLAUDE.md #9 and the Stack section originally specified file-based blog content:
Markdown files under `content/blog/` with front matter (title, slug, date, meta,
recommended products), published via PR merge → deploy → cache purge. This
approach matches static-site conventions but couples publishing to the deployment
pipeline.

The owner chose a DB-backed approach instead: posts edited directly in the admin
SPA for a unified admin experience and the ability to publish without triggering
a deploy.

## Decision

Store blog posts and managed categories in Postgres (`V22__blog.sql`: tables
`blog_post`, `blog_category`, `blog_post_category`, `blog_post_product`).
Introduce flexmark as the Markdown renderer (shared between the public render
path and the admin live-preview endpoint, ensuring render parity).

Edit posts via the admin SPA (Refine): Markdown editor with live preview, managed
category picker, recommended-SKU picker, cover image upload (reuses StorageService
hashed key), Draft/Published toggle. Admin operations go through
`BlogAdminService` with an audit log on every mutation.

Render public blog pages in the `web` layer via `BlogQueryService` (session-
independent, cacheable HTML; `Article` JSON-LD; recommended products fetched by
SKU via `CatalogQueryService`, out-of-stock and discontinued products omitted).

`content/blog/` is retired as a content source; a `.gitkeep` remains so the
directory is not deleted from the repository.

## Consequences

- Posts can be published and unpublished without a deploy.
- Slugs remain immutable and unique (enforced at the DB level); existing SEO URLs
  are unaffected.
- The public blog pages remain session-independent and cacheable (rule #2 upheld).
- Cache purge on publish/unpublish, reverse-reference invalidation on product
  status change, and Meilisearch blog indexing are deferred to separate later
  slices.
- Scheduled publishing and AI article generation are likewise deferred.
- CLAUDE.md rule #9 and the Stack section are updated to reflect this design.
