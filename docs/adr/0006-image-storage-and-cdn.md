# 6. Image storage (Storage port + Railway Volume) and delivery (TransformImgs CDN)

Date: 2026-06-18

## Status

Accepted

## Context

Admin product editing (P2) needs to store uploaded product image **originals**. Today there is no
object storage — `WebshopProperties.imageUrl` maps interim `wp/<path>` keys to the WP uploads URL
"until object storage exists". The app is hosted on **Railway**, which provides persistent
**Volumes** (disk) and managed databases, but **no managed object/blob store**. CLAUDE.md #8
requires: upload to storage with **hashed, immutable keys**, and the app performs **no image
processing** (resize = CDN optimizer URL parameter).

## Decision

- **Originals storage** is abstracted behind a `StorageService` **port** (`put(bytes,
  contentType) -> content-hashed key`, `delete(key)`). The initial implementation is
  **filesystem-backed**, writing under a configured directory that is a **mounted Railway Volume**
  in production (content-hash = the immutable key). An **S3-compatible** implementation
  (Cloudflare R2 / AWS S3) can replace it later behind the same port without touching the editor.
- **Delivery — what P2c shipped:** uploaded originals (`up/<hash>.<ext>` keys) are served
  **directly by the app** at `GET /media/{key}` (public read; content-hashed → `Cache-Control`
  `max-age=365d, public, immutable`). `WebshopProperties.imageUrl` returns `/media/<key>` for
  uploaded keys (legacy `wp/<path>` keys still resolve to the configured `imagesBaseUrl`). The app
  does **no** resizing/transcoding (CLAUDE.md #8 satisfied — it only stores and serves bytes).
- **Delivery — deferred (TransformImgs):** the planned resize/format-negotiation layer is
  **TransformImgs** (open-source image CDN, `pixboost/transformimgs`), to be deployed as a
  **separate Railway service** (Docker). It is stateless — it would fetch the original from our
  `/media` origin URL embedded in the path (`/img/<origin>/{optimise|resize|fit|asis}`),
  auto-negotiate WebP/AVIF/JPEG-XL via `Accept`, and cache transformed output (~30d). When that
  slice lands, `imageUrl` returns the TransformImgs URL wrapping the `/media` origin — **with no
  change to any caller**, since the indirection already lives behind `imageUrl`. This was
  intentionally **not** wired in P2c (originals serve fine from `/media` for now).

## Consequences

- **P2c shipped:** the `StorageService` port + filesystem impl (content-hashed `up/` keys),
  the `GET /media/{key}` serving endpoint, the flag-gated multipart upload + gallery ops
  (`/api/admin/products/{id}/images`: upload/delete/set-cover/reorder), and the gallery editor in
  the admin SPA. Deploy adds a **Railway Volume** mount (`STORAGE_DIR`). The **TransformImgs**
  service is **not** part of P2c — it's a later slice (see deferred decision above). Spec:
  `docs/superpowers/specs/2026-06-18-admin-products-p2-editor-design.md`.
- The filesystem impl is single-node (tied to the Volume's service); acceptable to start. Moving to
  R2/S3 is a config + one-class change behind the port — do it when multi-node/CDN scale or
  redundancy is needed.
- Imported Woo images keep their existing keys/URLs; only newly uploaded originals use the
  hashed-key storage served from `/media`. The interim `imagesBaseUrl` mapping stays for `wp/`
  keys; it (and `/media`) are superseded by the TransformImgs URL builder when that slice lands.
