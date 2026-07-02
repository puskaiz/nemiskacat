# Image-Column (figure-row) TipTap Node — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a TipTap `figureRow`/`figureItem` node so authors can create and edit side-by-side image-with-caption columns, and so imported `.nk-figure-row` layouts round-trip losslessly through the editor.

**Architecture:** Two custom TipTap nodes in one extension file. `figureRow` (block, `content: "figureItem+"`) maps `<div class="nk-figure-row">`; `figureItem` (atom, attrs `src/alt/caption`) maps `<figure><img><figcaption>`. A React NodeView gives each figure an image + inline caption input + remove control, and the row an "add column" control. An `insertFigureRow` command backs a new toolbar button. No backend/CSS/sanitizer change (all already allow `.nk-figure-row`).

**Tech Stack:** React + TypeScript, TipTap v3 (`@tiptap/core`, `@tiptap/react`, StarterKit/Image already installed), antd, vitest + jsdom (new devDep) for the headless round-trip test.

## Global Constraints

- Output HTML must stay within the server sanitizer's allowlist: `div.nk-figure-row`, `figure`, `figcaption`, `img` (already allowed) — do not introduce other tags/classes for this feature.
- `editor.getHTML()` for a figure row MUST emit `<div class="nk-figure-row"><figure><img src alt>[<figcaption>…</figcaption>]</figure>…</div>`, with `<figcaption>` present **iff** caption is non-empty. This lossless round-trip is the core acceptance criterion.
- Code/comments/commits English; user-facing UI strings Hungarian.
- TipTap version is v3.27.x (already in `admin-ui/package.json`). Follow existing `admin-ui` patterns (antd, the `HtmlEditor` integration shape).
- Run all admin-ui commands from `admin-ui/`. Binary: `npm`.

---

## File Structure

- Create `admin-ui/src/components/extensions/FigureRow.tsx` — both nodes, the `insertFigureRow` command, and the React NodeViews.
- Create `admin-ui/src/components/extensions/FigureRow.test.ts` — jsdom round-trip + insert tests.
- Modify `admin-ui/src/components/HtmlEditor.tsx` — register the nodes, add the "Képoszlopok" toolbar button.
- Modify `admin-ui/package.json` (+ lockfile) — add `jsdom` devDependency.

---

## Task 1: Test harness — jsdom + headless-editor round-trip smoke

**Files:**
- Modify: `admin-ui/package.json` (add `jsdom` devDependency)
- Create: `admin-ui/src/components/extensions/headlessEditor.smoke.test.ts`

**Interfaces:**
- Produces: a proven pattern for headless-editor tests — a vitest file with `// @vitest-environment jsdom` that does `new Editor({ extensions, content }).getHTML()`. Later tasks reuse this pattern.

- [ ] **Step 1: Install jsdom**

Run (from `admin-ui/`): `npm install -D jsdom`
Expected: jsdom appears under `devDependencies`. (If the private registry returns HTTP 503, retry — it was transient last time.)

- [ ] **Step 2: Write the smoke test**

```ts
// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";

describe("headless editor harness", () => {
  it("round-trips a paragraph under jsdom", () => {
    const editor = new Editor({ extensions: [StarterKit], content: "<p>szia</p>" });
    expect(editor.getHTML()).toContain("<p>szia</p>");
    editor.destroy();
  });
});
```

- [ ] **Step 3: Run it**

Run: `npm run test -- headlessEditor.smoke`
Expected: PASS (proves jsdom + headless `new Editor` works). If it fails with a DOM error, the `// @vitest-environment jsdom` docblock is missing or jsdom didn't install.

- [ ] **Step 4: Commit**

```bash
git add admin-ui/package.json admin-ui/package-lock.json admin-ui/src/components/extensions/headlessEditor.smoke.test.ts
git commit -m "test(admin-ui): jsdom headless-editor test harness"
```

---

## Task 2: `figureRow` + `figureItem` nodes + `insertFigureRow` command (lossless round-trip)

**Files:**
- Create: `admin-ui/src/components/extensions/FigureRow.tsx` (nodes + command only in this task; NodeViews added in Task 3)
- Create: `admin-ui/src/components/extensions/FigureRow.test.ts`

**Interfaces:**
- Produces:
  - `export const FigureRow: Node` — TipTap node, name `"figureRow"`.
  - `export const FigureItem: Node` — TipTap node, name `"figureItem"`, attrs `{ src: string; alt: string; caption: string }`.
  - Command `insertFigureRow(count?: number)` (default 2), registered on `FigureRow`, callable as `editor.chain().focus().insertFigureRow(2).run()`.
- Consumes: nothing (StarterKit + Image used only in tests to form a valid schema).

- [ ] **Step 1: Write the failing round-trip + insert tests**

```ts
// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import { FigureRow, FigureItem } from "./FigureRow";

function makeEditor(content: string) {
  return new Editor({ extensions: [StarterKit, Image, FigureRow, FigureItem], content });
}

describe("FigureRow round-trip", () => {
  it("parses an imported .nk-figure-row and re-serializes it equivalently", () => {
    const html =
      '<div class="nk-figure-row">' +
      '<figure><img src="/media/a" alt="A"><figcaption>Cap A</figcaption></figure>' +
      '<figure><img src="/media/b" alt="B"></figure>' +
      "</div>";
    const editor = makeEditor(html);
    const out = editor.getHTML();
    editor.destroy();
    expect(out).toContain('<div class="nk-figure-row">');
    expect(out).toContain('<img src="/media/a" alt="A">');
    expect(out).toContain("<figcaption>Cap A</figcaption>");
    expect(out).toContain('<img src="/media/b" alt="B">');
    // figure B had no caption -> no empty figcaption emitted
    const figureB = out.slice(out.indexOf("/media/b"));
    expect(figureB).not.toContain("<figcaption></figcaption>");
  });

  it("insertFigureRow(2) creates a row with two empty figures", () => {
    const editor = makeEditor("<p></p>");
    editor.chain().focus().insertFigureRow(2).run();
    const out = editor.getHTML();
    editor.destroy();
    expect(out).toContain('<div class="nk-figure-row">');
    expect((out.match(/<figure>/g) || []).length).toBe(2);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test -- FigureRow`
Expected: FAIL — `./FigureRow` has no exports yet (import error).

- [ ] **Step 3: Implement the nodes + command**

```tsx
import { Node, mergeAttributes } from "@tiptap/core";

declare module "@tiptap/core" {
  interface Commands<ReturnType> {
    figureRow: {
      /** Insert a figure-row with `count` empty image columns (default 2). */
      insertFigureRow: (count?: number) => ReturnType;
    };
  }
}

export const FigureItem = Node.create({
  name: "figureItem",
  // only valid inside figureRow (referenced by name in FigureRow.content)
  selectable: true,
  draggable: false,
  atom: true,

  addAttributes() {
    return {
      src: {
        default: "",
        parseHTML: (el) => el.querySelector("img")?.getAttribute("src") ?? "",
      },
      alt: {
        default: "",
        parseHTML: (el) => el.querySelector("img")?.getAttribute("alt") ?? "",
      },
      caption: {
        default: "",
        parseHTML: (el) => el.querySelector("figcaption")?.textContent ?? "",
      },
    };
  },

  parseHTML() {
    return [{ tag: "figure" }];
  },

  renderHTML({ node }) {
    const { src, alt, caption } = node.attrs as {
      src: string;
      alt: string;
      caption: string;
    };
    const out: any[] = ["figure", {}, ["img", mergeAttributes({ src, alt })]];
    if (caption) out.push(["figcaption", {}, caption]);
    return out;
  },
});

export const FigureRow = Node.create({
  name: "figureRow",
  group: "block",
  content: "figureItem+",
  draggable: false,

  parseHTML() {
    return [{ tag: "div.nk-figure-row" }];
  },

  renderHTML({ HTMLAttributes }) {
    return ["div", mergeAttributes(HTMLAttributes, { class: "nk-figure-row" }), 0];
  },

  addCommands() {
    return {
      insertFigureRow:
        (count = 2) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            content: Array.from({ length: Math.max(1, count) }, () => ({
              type: "figureItem",
            })),
          }),
    };
  },
});
```

- [ ] **Step 4: Run to verify it passes**

Run: `npm run test -- FigureRow`
Expected: PASS (both tests). If `getHTML()` shows attribute-order or whitespace differences, adjust the assertions to use `.toContain` on the meaningful fragments (already the case) — do NOT loosen the "no empty figcaption" check.

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/components/extensions/FigureRow.tsx admin-ui/src/components/extensions/FigureRow.test.ts
git commit -m "feat(admin-ui): figureRow/figureItem TipTap nodes (lossless .nk-figure-row round-trip)"
```

---

## Task 3: React NodeViews (image + caption editing, add/remove column)

**Files:**
- Modify: `admin-ui/src/components/extensions/FigureRow.tsx` (add NodeViews + `addNodeView` to both nodes)

**Interfaces:**
- Consumes: `FigureRow`, `FigureItem` from Task 2.
- Produces: editing UI; no new exports. After this task, a figure row renders in the editor as a flex row of figures; each figure shows its image (or a "set image" button when `src` empty), an inline caption `<input>`, and a remove control; the row shows an "add column" control.

- [ ] **Step 1: Add NodeViews** (append imports + NodeView components, and wire `addNodeView` on each node)

Add to the top of `FigureRow.tsx`:

```tsx
import { ReactNodeViewRenderer, NodeViewWrapper } from "@tiptap/react";
import type { NodeViewProps } from "@tiptap/react";
import { Button, Input } from "antd";
import { DeleteOutlined, PictureOutlined, PlusOutlined } from "@ant-design/icons";
```

Add the two view components (above the node definitions):

```tsx
function FigureItemView({ node, updateAttributes, deleteNode }: NodeViewProps) {
  const { src, alt, caption } = node.attrs as { src: string; alt: string; caption: string };
  const setImage = () => {
    const url = window.prompt("Kép URL (/media/…):", src || "");
    if (url !== null) updateAttributes({ src: url });
  };
  return (
    <NodeViewWrapper
      as="figure"
      style={{ flex: "1 1 0", minWidth: 0, margin: 0, display: "flex", flexDirection: "column", gap: 6 }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <Button size="small" type="text" icon={<PictureOutlined />} onClick={setImage} title="Kép beállítása">
          {src ? "Kép csere" : "Kép"}
        </Button>
        <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => deleteNode()} title="Oszlop törlése" />
      </div>
      {src ? (
        <img src={src} alt={alt} style={{ width: "100%", height: "auto", borderRadius: 4, display: "block" }} />
      ) : (
        <div style={{ aspectRatio: "1 / 1", background: "#f0f0f0", borderRadius: 4, display: "grid", placeItems: "center", color: "#999" }}>
          nincs kép
        </div>
      )}
      <Input
        size="small"
        placeholder="Felirat (opcionális)"
        value={caption}
        onChange={(e) => updateAttributes({ caption: e.target.value })}
      />
    </NodeViewWrapper>
  );
}

function FigureRowView({ editor, getPos }: NodeViewProps) {
  const addColumn = () => {
    const pos = getPos();
    if (typeof pos !== "number") return;
    // insert a new empty figureItem at the end of this row's content
    const end = pos + (editor.state.doc.nodeAt(pos)?.nodeSize ?? 2) - 1;
    editor.chain().focus().insertContentAt(end, { type: "figureItem" }).run();
  };
  return (
    <NodeViewWrapper
      as="div"
      className="nk-figure-row"
      style={{ display: "flex", gap: 16, alignItems: "flex-start", border: "1px dashed #d9d9d9", borderRadius: 6, padding: 8, position: "relative" }}
    >
      {/* contentDOM hole for figureItems */}
      <div data-figure-row-content style={{ display: "flex", gap: 16, flex: 1, minWidth: 0 }} contentEditable={false} />
      <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addColumn} title="Oszlop hozzáadása" />
    </NodeViewWrapper>
  );
}
```

NOTE on contentDOM: `NodeViewWrapper` provides the wrapper; for `figureRow` the figureItems must render into a content hole. Use TipTap's `NodeViewContent` instead of a hand-rolled div:

```tsx
import { NodeViewContent } from "@tiptap/react";
```

and in `FigureRowView` replace the `data-figure-row-content` div with:

```tsx
<NodeViewContent as="div" style={{ display: "flex", gap: 16, flex: 1, minWidth: 0 }} />
```

(Drop the `contentEditable={false}` and the placeholder div.)

- [ ] **Step 2: Wire `addNodeView` on the nodes**

In `FigureItem` add:
```tsx
  addNodeView() {
    return ReactNodeViewRenderer(FigureItemView);
  },
```
In `FigureRow` add:
```tsx
  addNodeView() {
    return ReactNodeViewRenderer(FigureRowView);
  },
```

- [ ] **Step 3: Verify the round-trip test still passes (NodeViews must not change serialization)**

Run: `npm run test -- FigureRow`
Expected: PASS — NodeViews affect only in-editor rendering, not `renderHTML`/`getHTML`. If it fails, the node `renderHTML` was altered by mistake; revert that.

- [ ] **Step 4: Verify the build compiles**

Run: `npm run build`
Expected: `tsc --noEmit` + vite build succeed. Fix any TipTap v3 type mismatches (e.g. `NodeViewProps` import path, `getPos` typing) until green.

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/components/extensions/FigureRow.tsx
git commit -m "feat(admin-ui): React NodeViews for figure columns (image, caption, add/remove)"
```

---

## Task 4: Register in `HtmlEditor` + toolbar button

**Files:**
- Modify: `admin-ui/src/components/HtmlEditor.tsx`

**Interfaces:**
- Consumes: `FigureRow`, `FigureItem`, the `insertFigureRow` command.

- [ ] **Step 1: Register the nodes**

Add import:
```tsx
import { FigureRow, FigureItem } from "./extensions/FigureRow";
```
Add to the `extensions` array (after `TableCell`):
```tsx
      FigureRow,
      FigureItem,
```

- [ ] **Step 2: Add the toolbar button**

Add the icon import alongside the others:
```tsx
  AppstoreOutlined,
```
Add an insert handler near `insertTable`:
```tsx
  const insertFigureRow = () => {
    editor?.chain().focus().insertFigureRow(2).run();
  };
```
Add the button after the table button in the toolbar `<Space>`:
```tsx
          <Button
            size="small"
            title="Képoszlopok"
            icon={<AppstoreOutlined />}
            type="text"
            onClick={insertFigureRow}
          />
```

- [ ] **Step 3: Build**

Run: `npm run build`
Expected: succeeds, no type errors, no unused-import errors.

- [ ] **Step 4: Run the full admin-ui test suite**

Run: `npm run test`
Expected: all green (FigureRow round-trip + insert, harness smoke, existing suites).

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/components/HtmlEditor.tsx
git commit -m "feat(admin-ui): Képoszlopok toolbar button + register figure-row nodes"
```

---

## Manual verification (after Task 4; human or browser-driven)

1. Build the admin SPA into the app (`mvn -pl . -Pfrontend` or the normal Maven build) and open `/admin/blog/edit/270`.
2. Confirm the imported image row appears as **two editable columns** (images + captions), not stacked/lost.
3. Edit a caption, click Save, reload the editor → caption persists; view the public page `/blog/ombre-butorfestes` → layout intact.
4. In a draft, click **Képoszlopok**, set two `/media` images + captions, Save → public page shows side-by-side images with captions.

---

## Self-Review

- **Spec coverage:** nodes + attrs (Task 2), parseHTML/renderHTML lossless round-trip incl. caption-iff-non-empty (Task 2 test), NodeView editing with add/remove + caption input + image set (Task 3), toolbar insert of 2 columns (Task 4), jsdom round-trip test (Task 1+2), no backend/CSS/sanitizer change (none in any task), manual verification (section above). All spec sections covered.
- **Placeholder scan:** code blocks are concrete; the one "NOTE on contentDOM" is a concrete correction (use `NodeViewContent`), not a placeholder.
- **Type consistency:** `figureRow`/`figureItem` names, attrs `src/alt/caption`, and `insertFigureRow(count?)` are used identically across Tasks 2–4; test helper `makeEditor` consistent across test files; `NodeViewProps`/`ReactNodeViewRenderer`/`NodeViewContent` from `@tiptap/react`.
- **Known implementer latitude:** exact TipTap v3 type names and `getPos`/`insertContentAt` signatures are verified against the installed version via the build (Task 3 step 4); the round-trip assertions use `.toContain` to tolerate attribute-order/whitespace while still proving losslessness and the no-empty-figcaption rule.
