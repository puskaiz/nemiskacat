# Blog Editor — Image-Column (figure-row) TipTap Node — Design

Date: 2026-06-30
Status: Approved (brainstorming)
Related: ADR 0009 (blog HTML + TipTap), `.nk-figure-row` CSS (site.css), `BlogHtmlSanitizer`.

## Problem

The blog WYSIWYG (`admin-ui/src/components/HtmlEditor.tsx`, TipTap StarterKit + Link
+ Image + Table) has no way to create the side-by-side image-with-caption layout
(`.nk-figure-row`). Worse, TipTap drops elements it doesn't recognize on load, so
opening any post that contains a `.nk-figure-row` (all 271 imported posts, e.g. post
270) and re-saving **silently strips the layout and captions**. We need a TipTap node
that (1) round-trips `.nk-figure-row` losslessly and (2) lets authors create/edit it.

## Scope

In scope: image columns with optional per-image captions — the existing
`.nk-figure-row` vocabulary only. Out of scope (deferred): generic/text multi-column
content; rich-text (formatted) captions; drag-reorder of columns.

## Design

### Nodes (TipTap / ProseMirror schema)

Two custom nodes in a single extension file `admin-ui/src/components/extensions/FigureRow.ts(x)`:

- **`figureRow`** — `group: "block"`, `content: "figureItem+"`, `draggable: false`.
  - `parseHTML`: `div.nk-figure-row`.
  - `renderHTML`: `<div class="nk-figure-row">` + hole for content.
- **`figureItem`** — `group` (only valid inside figureRow), `atom: true`,
  `selectable: true`, `draggable: false`, with attributes:
  - `src: string` (image URL, e.g. `/media/<key>` or absolute), default `""`.
  - `alt: string`, default `""`.
  - `caption: string` (plain text), default `""`.
  - `parseHTML`: `figure` — read `img@src` → `src`, `img@alt` → `alt`,
    `figcaption` textContent → `caption`.
  - `renderHTML`: `<figure><img src alt>` + (`<figcaption>caption</figcaption>` only
    when `caption` is non-empty).
  - `addNodeView`: a React NodeView (`@tiptap/react` `ReactNodeViewRenderer`).

Captions are plain text (an attribute), not editable rich content — sufficient for
captions and keeps the schema simple and the round-trip robust.

### NodeView (editing UX)

`FigureItemView` (React) renders, per figure: the `<img>` (or a placeholder + "set
image" control if `src` empty), an inline `<input>` bound to the `caption` attribute
(updates via `updateAttributes`), and per-item controls: **✕ remove this column**.
The image URL is set via the existing prompt pattern (`/media/…`). The `figureRow`
itself renders its items in a flex/grid row with inline styles (the admin SPA does
not load `site.css`, so the editor styles columns itself for fidelity); a **＋ add
column** control appends an empty `figureItem`.

### Toolbar

Add a button **"Képoszlopok"** (image columns) to `HtmlEditor`'s toolbar that inserts
a `figureRow` containing **2** empty `figureItem`s (the author then sets each image
and caption). A command `insertFigureRow(count = 2)` on the extension.

### Round-trip / data safety (primary requirement)

Because `figureRow.parseHTML` matches `div.nk-figure-row` and `figureItem.parseHTML`
matches `figure`, loading an imported post preserves the layout and makes it editable;
`editor.getHTML()` must emit markup equivalent to the stored `.nk-figure-row` (same
tag/class/attribute shape; `figcaption` present iff caption non-empty). This is the
core acceptance criterion.

### Registration

`HtmlEditor` adds `FigureRow` + `FigureItem` to its `extensions` array. Image URLs in
figures stay as-is (no rewriting in the editor). No change to `BlogHtmlSanitizer`
(`nk-figure-row`, `figure`, `figcaption`, `img` already allowed) and no change to
`site.css` (`.nk-figure-row` already styled for the public page).

## Testing

- **jsdom devDependency** added to `admin-ui` (approved) to enable DOM-based tests.
- **Round-trip test** (the key gate): using TipTap headless `generateJSON` (HTML →
  doc) and `generateHTML` (doc → HTML) with the two nodes registered, assert that a
  representative `.nk-figure-row` (two figures, one with caption, one without) parses
  and re-serializes to equivalent markup — proving no data loss. Include a figure with
  a missing caption (asserts no empty `<figcaption>` emitted).
- Keep/extend the existing pure `figureRowHtml` unit test.
- **Build gate:** `npm run build` (tsc --noEmit && vite build) passes.
- **Manual verification:** open `/admin/blog/edit/270`, confirm the imported image row
  appears as editable columns, edit a caption, save, reload — layout intact; insert a
  new 2-column row and verify it renders on the public page.

## Risks / notes

- ProseMirror atom NodeView for `figureItem` keeps captions as an attribute; if rich
  captions are ever needed, `figureItem` would change to `content: "inline*"` — out of
  scope now.
- The editor's column styling is duplicated inline (NodeView) vs `site.css`
  (public) — acceptable; they are independent render targets.
- No backend/migration work; imported posts are converted lazily (figure-row stays
  figure-row; only re-save through the editor normalizes attribute formatting).
