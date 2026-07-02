# Admin products P2 — editor (umbrella + P2a content) — design

## Context

P1 shipped read-only admin product list + detail over the real catalog. P2 adds **editing**,
gated behind a feature flag, writing only to this app's DB (never to live Woo) — per ADR 0005
(this app becomes the product master post-cutover). Catalog entities are mutable (Lombok
`@Setter`); `VatCalculator` + `Money` (minor units) exist for net/gross; the SPA already fetches
`GET /api/admin/auth/me` at startup.

## Decisions (confirmed)

- **Feature flag** `webshop.admin.product-editor-enabled` (default `false`, env-overridable —
  matches the `khpos.enabled` / `webshop.invoicing.billingo-enabled` pattern). Gates the **write
  endpoints** (server-side, authoritative) **and** the edit UI. Delivered to the SPA by extending
  `GET /api/admin/auth/me` with the flag (no new endpoint).
- **Editable** (writes to our DB): name, short/long description, SEO title + meta, status,
  categories (P2a); per-variant **price** with net-or-gross entry + variant add/remove (P2b);
  images (P2c). **Slug stays locked** (Woo 1:1, CLAUDE.md). The Woo importer stays read-only.
- **Price** stored **gross** (`regularPriceHuf`, minor units); the editor accepts net OR gross and
  computes the counterpart from the product `vatRatePercent` via `VatCalculator`; VAT math
  unit-tested (CLAUDE.md #6).
- **Images** stored as **originals** via a `StorageService` port (filesystem impl on a Railway
  Volume now, content-hashed immutable keys; R2/S3-swappable behind the port). Delivery/resize via
  **TransformImgs** (open-source image CDN, `pixboost/transformimgs`, deployed as a separate
  Railway service) — `WebshopProperties.imageUrl` emits a TransformImgs URL. The app never
  processes images (CLAUDE.md #8). See ADR `docs/adr/0006-image-storage-and-cdn.md`.

## Decomposition (build order: P2a → P2b → P2c; each its own plan)

- **P2a — Content editor** *(this spec, detailed)*: flag + `/me` delivery + a write endpoint for
  name/descriptions/SEO/status/categories; the P1 detail becomes an editable form when the flag is on.
- **P2b — Price + variant CRUD**: per-variant net/gross price (VAT, tested) + add/remove variants.
- **P2c — Images**: `StorageService` port + filesystem/Volume, multipart upload, gallery editor,
  TransformImgs delivery.

---

## P2a — Content editor (this slice)

### Backend

- **Flag config:** new `@ConfigurationProperties(prefix = "webshop.admin")` record
  `AdminProperties(@DefaultValue("false") boolean productEditorEnabled)`; registered like the other
  webshop properties. `application.yml`: `webshop.admin.product-editor-enabled: ${ADMIN_PRODUCT_EDITOR_ENABLED:false}`
  (relaxed-binding `product-editor-enabled` → `productEditorEnabled`).
- **`/me` flag delivery:** extend `MeResponse` (in `AdminAuthController`) from `(name, role)` to
  `(name, role, boolean productEditorEnabled)`, value from `AdminProperties`. The SPA reads it.
- **Update service:** new `ProductAdminEditService` (`@Transactional`) with
  `ProductDetailView updateContent(Long id, ContentUpdate cmd)`:
  - guard: if `!productEditorEnabled` → throw `EditorDisabledException` (→ 403).
  - load product (404 via shared NotFoundException); set name, shortDescription, description,
    seoTitle, metaDescription, status (via setters); replace categories from `categorySlugs`
    (resolve each slug via `CategoryRepository`; unknown slug → 400). **Slug ignored** (never set).
  - return the P1 `ProductAdminQueryService.ProductDetailView` (reuse it for the response).
  - `ContentUpdate(String name, String shortDescription, String description, String seoTitle,
    String metaDescription, ProductStatus status, List<String> categorySlugs)`; `@NotBlank name`,
    `@NotNull status`.
- **Endpoint:** `PUT /api/admin/products/{id}` (add to `ProductAdminController`) → `ProductDetailView`.
  Validation errors and `EditorDisabledException` flow through the existing `AdminExceptionHandler`
  (RFC 9457 problem+json; 400 / 403).

### Frontend

- `authProvider`/identity: surface `productEditorEnabled` from `/me` (extend the identity type used
  by `useGetIdentity`).
- `pages/products/show.tsx`: when `productEditorEnabled`, render the content fields (name,
  short/long description, SEO title + meta, status select, category multi-select) as **inputs**
  with a **Mentés** (Save) action calling `PUT /api/admin/products/{id}`; success toast + refetch;
  validation errors surfaced (red field + helper, matching the prototype). When the flag is off,
  the view stays exactly the P1 read-only layout. Price/variants/images remain read-only in P2a.
  Categories come from `GET /api/admin/categories` (P1).

### Testing (P2a)

- Backend (Testcontainers, seed via importer): `ProductAdminEditServiceTest` — updates fields +
  replaces categories (flag on); `EditorDisabledException` when flag off; unknown category slug →
  error; name blank → validation. Flag toggled via `@TestPropertySource`/`@SpringBootTest(properties=...)`.
  `ProductAdminControllerTest` (extend): `PUT` 200 when flag on + ADMIN; 403 when flag off; 400 on
  blank name; 403 for non-admin. `/me` returns `productEditorEnabled`.
- Frontend: `npm run build`; the editable form compiles; flag-off renders read-only. No new test
  framework; a pure helper test if any non-trivial transform is added.
- `mvn verify` green incl. ArchUnit. The importer + storefront untouched.

## P2b — Price + variant CRUD — outline (own spec/plan)

- Per-variant price editor: net-or-gross input → `VatCalculator` computes the counterpart from
  `vatRatePercent`; store gross `regularPriceHuf` (minor units). VAT/rounding **unit-tested**.
- Add variant (SKU unique, attribute values, default-variant rule) / remove variant. Flag-gated.
- Endpoints under `/api/admin/products/{id}/variants` (or a richer product PUT). Money rule tests
  not skipped (CLAUDE.md).

## P2c — Images — outline (own spec/plan + ADR 0006)

- `StorageService` port: `String put(bytes, contentType)` → content-hashed key; `delete(key)`.
  Filesystem impl writing under a configured dir (Railway Volume in prod). R2/S3 impl later behind
  the port.
- `POST /api/admin/products/{id}/images` (multipart) → stores original, creates `ProductImage`
  (key/alt/position/featured). Gallery editor: upload/replace/reorder/set-cover; flag-gated.
- `WebshopProperties.imageUrl` rebuilt to emit a TransformImgs URL (`/img/<originUrl>/optimise|resize`).
  TransformImgs deployed as a separate Railway service (Docker `pixboost/transformimgs`). App does
  no image processing.

## Out of scope / unchanged

- Woo importer stays read-only; no writes to live Woo, ever.
- Storefront stock = derived availability (CLAUDE.md #5); admin numeric view unchanged.
- Adding/removing categories as entities (category CRUD) — separate from product editing.
