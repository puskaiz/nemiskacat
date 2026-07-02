# 9. Blog body stored as sanitized HTML, edited with a WYSIWYG (TipTap)

Date: 2026-06-30

## Status

Accepted

Supersedes the *authoring format* portion of [0008](0008-blog-in-database.md)
(Markdown editor + flexmark render). The DB-backed decision in 0008 stands —
posts and managed categories remain in Postgres.

## Context

0008 stored each post body as Markdown (`blog_post.body_markdown`), edited in a
Markdown textarea with a flexmark live-preview, and the importer converted
WordPress post HTML to Markdown via `HtmlToMarkdown` (flexmark-html2md).

Two problems surfaced in practice:

1. **Lossy import.** Blog content is *born as HTML* (WordPress Gutenberg) and is
   re-imported repeatedly (rule #4). The HTML→Markdown step is lossy: Gutenberg
   `wp:columns` image layouts degraded into a broken pipe-pseudo-table, and
   recovering layout meant hand-built HTML islands embedded in the Markdown
   (`.nk-figure-row`) plus enabling the flexmark GFM-tables extension. These are
   symptoms of forcing HTML content through a Markdown pipeline.
2. **Authoring mismatch.** The blog's authors are non-technical and most posts
   are imported-then-tweaked. A Markdown textarea that exposes raw `<div>` blocks
   is the wrong tool for them. Meanwhile the rest of the admin (products,
   categories, workshops) already standardises on a WYSIWYG (`react-quill-new`)
   that stores HTML and renders it via `th:utext`. The blog was the lone
   Markdown holdout.

A separate, pre-existing gap: the blog render path has **no HTML sanitizer**
between content and `th:utext`. Acceptable while content was trusted-admin-only,
but it must be closed before the body can contain arbitrary stored HTML.

## Decision

Store the blog body as **sanitized HTML** in a new column `blog_post.body_html`
and edit it with a **TipTap** WYSIWYG in the admin SPA.

- **Sanitization is the single source of truth for safety.** A server-side
  `BlogHtmlSanitizer` (jsoup `Safelist`, jsoup already on the classpath via
  flexmark-html2md) runs on *every* write path — admin save and import — with an
  explicit allowlist of tags/attributes/classes (headings, paragraphs, lists,
  `a`, `img`, `figure`/`figcaption`, `table` family, and the `nk-figure-row`
  layout container). `script`, `style`, event handlers and unknown embeds are
  stripped. Nothing is rendered that was not sanitized on write.
- **Editor: TipTap** (`@tiptap/react` + StarterKit + Link, Image, Table
  extensions, and a custom `figureRow` node for side-by-side captioned images).
  Chosen over reusing Quill because the blog needs column/figure/table layouts
  that Quill cannot express without dropping to raw HTML. New dependency,
  approved by the owner.
- **Importer becomes sanitize-and-passthrough.** It stores the WordPress HTML
  directly (no Markdown conversion), reusing the jsoup `wp:columns` →
  `.nk-figure-row` normalisation (ported from `HtmlToMarkdown`) and rewriting
  `wp-content/uploads` image URLs to `/media/<key>` in the DOM before sanitizing.
- **Public render path returns stored HTML directly.** `BlogQueryService` no
  longer runs flexmark; `BlogPostView.bodyHtml` is the already-sanitized
  `body_html`. Public pages stay session-independent and cacheable (rule #2),
  `Article` JSON-LD and the recommended-products box are unchanged.
- **Migration is non-destructive.** `V23` adds `body_html`; a one-time backfill
  renders existing `body_markdown` through flexmark + the sanitizer into
  `body_html`, preserving any manual edits. `body_markdown` is kept (deprecated)
  for one release, then dropped in a later migration.

## Consequences

- Non-technical authors edit posts visually; columns, tables and captioned
  images are authored without touching HTML source.
- The lossy import class of bugs disappears; the Markdown-pipeline workarounds
  (flexmark GFM-tables extension, the `wp:columns`→figure-row *Markdown*
  converter) become blog-unused and are removed once the Markdown path is retired.
- New dependency: TipTap in `admin-ui`. No new server dependency (jsoup is
  already present).
- The sanitizer is now load-bearing and security-critical: it gets dedicated
  tests (XSS vectors, allowlist coverage) and a security review.
- The `.nk-figure-row` CSS (committed earlier) is retained as the canonical
  layout vocabulary; the figure-row Markdown converter is superseded by emitting
  the same HTML at import time.
- The admin Markdown live-preview endpoint (`POST /api/admin/blog/preview`) is
  removed — the WYSIWYG *is* the preview.
- Future AI article generation (deferred in 0008) ingests Markdown by converting
  it to HTML once on save, so a single retained Markdown→HTML helper stays
  server-side.
- CLAUDE.md rule #9 and the Stack section are updated: blog authoring format is
  sanitized HTML via TipTap, not Markdown.
