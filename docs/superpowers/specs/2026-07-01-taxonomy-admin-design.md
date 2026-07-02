# Taxonomy Admin (blog categories, blog tags, product tags) — Design

**Goal:** Admin CRUD screens for three taxonomies — blog categories, blog tags,
product tags — full create / rename / delete, following the existing admin
conventions.

## Decisions (confirmed)

- **Full CRUD** for all three (create + rename + delete).
- **Rename = display name only; slug is immutable** (CLAUDE.md #7 — slugs are
  public URLs `/blog/kategoria/{slug}` and Woo-derived import keys). Create sets
  a new slug (validated + unique).
- **Product tags allow manual create:** make `product_tag.external_id`
  **nullable**; admin-created tags have `external_id = null` + a user-supplied
  slug; imported tags keep their Woo term id. Postgres `UNIQUE` allows multiple
  NULLs, so they coexist; the importer's `findByExternalId` only matches Woo ids.
- **Shared `TaxonomyAdmin` SPA component** reused by all three screens (DRY).
- Nav: blog categories + blog tags under **Blog**; product tags under **Products**.
- Out of scope: product *categories* (already have a screen); blog category
  `sidebar_hidden` visibility (stays in the sidebar CATEGORIES block editor).

## Backend

Conventions (all three): `@RestController @RequiredArgsConstructor`, full
per-method paths, NO `@PreAuthorize` (path-based ADMIN via SecurityConfig),
`X-Total-Count` on list, plain-String errors via `AdminExceptionHandler`
(service-nested `NotFoundException → 404`; validation `IllegalArgumentException
→ 400`, already global), audit every write via `AuditService.record(action,
entityType, entityId, summary)`.

- **Blog categories** — reuse the existing `/api/admin/blog/categories`
  (`BlogAdminService`: `listCategories`, `createCategory`, `updateCategory`,
  `delete`; DTOs `CategoryUpsert(name, slug)`, `CategoryView(id, name, slug)`).
  Tweak: `updateCategory(id, cmd)` sets **name only** — the slug is not changed
  (ignore `cmd.slug()` on update, or add a name-only path). Create already
  guards slug conflict. Add `X-Total-Count` to `list` if missing.
- **Blog tags** — new `BlogTagAdminService` + `BlogTagController` at
  `/api/admin/blog/tags`. Records `TagView(id, name, slug)`, `TagUpsert(name,
  slug)`, `NotFoundException`. `list()` (readOnly), `create` (slug validated via
  `BlogPost.isValidSlug` + unique via `BlogTagRepository.existsBySlug` → else
  `SlugConflictException`/`IllegalArgumentException`), `rename(id, name)`,
  `delete(id)`. Keyed by slug.
- **Product tags** — new `ProductTagAdminService` + `ProductTagController` at
  `/api/admin/products/tags`. Migration **V33** `ALTER TABLE product_tag ALTER
  COLUMN external_id DROP NOT NULL`; `ProductTag` entity `external_id` becomes
  nullable (`@Column(name="external_id")` without `nullable=false`), add a
  `createManual(slug, name)` factory (external_id null). Records `TagView(id,
  name, slug)`, `TagUpsert(name, slug)`. `list()`, `create` (external_id null,
  slug unique via a new `ProductTagRepository.existsBySlug`), `rename(id, name)`,
  `delete(id)`. Keyed by slug for admin purposes; imported tags still keyed by
  Woo id for the importer.

Slug conflict on create returns 409 (blog category already does via
`SlugConflictException`); blog tag / product tag reuse a 409-mapped conflict
exception or a 400 `IllegalArgumentException` — mirror `BlogAdminService`'s
`SlugConflictException → 409` handler pattern.

## Frontend (admin-ui)

- **Shared component** `components/TaxonomyAdmin.tsx` — a table with a `name`
  column (editable inline), a read-only `slug` column, add-row (name + slug),
  rename (name), delete. Props: `resource` (Refine resource name → REST path),
  i18n namespace/labels. Uses Refine `useTable`/`useCreate`/`useUpdate`/`useDelete`.
  Create sends `{name, slug}`; rename sends `{name}` (slug omitted / unchanged);
  delete by id. Error → `message.error`.
- Three thin screen wrappers: `pages/blog/categories.tsx` (resource
  `blog/categories`), `pages/blog/tags.tsx` (`blog/tags`), `pages/products/tags.tsx`
  (`products/tags`) — each renders `<TaxonomyAdmin resource=… ns=…/>`.
- Types in `types.ts`: `Taxonomy { id: number; name: string; slug: string }`.
- Register resources + routes in `App.tsx` (`/blog/categories`, `/blog/tags`,
  `/products/tags`); nav entries (blog two under a Blog parent, product tags
  under Products); i18n namespaces (hu/en) — one shared `taxonomy` namespace
  with generic labels (name/slug/add/rename/delete/saved/…), plus per-screen
  titles.

## Testing

- Backend ITs per service: create (name+slug), rename (name changes, **slug
  unchanged**), delete, duplicate-slug rejected, product-tag create yields null
  `external_id`; controller ITs: 401 unauth, list+X-Total-Count, create/rename/
  delete, 400 invalid, 404 unknown, 409 duplicate slug. Blog category: an IT
  asserting `update` no longer changes the slug.
- admin-ui: build green; a pure helper test only if the shared component has
  extractable logic; screens gated by build.

## Constraints honored

- Slug immutable on rename (#7); admin auth path-based; plain-String errors;
  audit on writes; public category/tag read paths unaffected (still read the
  same tables). Migration V33 additive (loosen NOT NULL). Code/comments English;
  UI Hungarian.

## Sequencing

One plan. Order: (1) product_tag external_id nullable migration + entity +
`existsBySlug`; (2) blog category `update` name-only tweak (+X-Total-Count);
(3) BlogTagAdminService + controller + IT; (4) ProductTagAdminService +
controller + IT; (5) shared `TaxonomyAdmin` component + the three screens +
resources/routes/nav/i18n.

## Self-review

- Placeholders: concrete services/endpoints/migration; the shared component's
  props are specified. No TODOs.
- Consistency: `TagUpsert(name, slug)` / `TagView(id, name, slug)` uniform across
  blog tag + product tag; rename = name-only everywhere; product-tag create =
  null external_id.
- Scope: three taxonomies; product categories + sidebar visibility excluded.
- Ambiguity: slug immutability + product-tag id handling pinned; V33 with the
  cross-branch caveat.
