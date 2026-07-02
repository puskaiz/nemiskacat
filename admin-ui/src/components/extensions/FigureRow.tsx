import { Node, mergeAttributes } from "@tiptap/core";
import type { DOMOutputSpec } from "@tiptap/pm/model";
import { ReactNodeViewRenderer, NodeViewWrapper, NodeViewContent } from "@tiptap/react";
import type { NodeViewProps } from "@tiptap/react";
import { Button, Input } from "antd";
import { DeleteOutlined, PictureOutlined, PlusOutlined } from "@ant-design/icons";

// NodeView component for a single figureItem — renders image preview, set/swap button, caption input, delete button.
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

// NodeView component for the figureRow container — renders a flex row with a NodeViewContent hole for figureItems,
// plus "add column" and "delete block" buttons.
function FigureRowView({ editor, getPos, deleteNode }: NodeViewProps) {
  const addColumn = () => {
    const pos = getPos();
    if (typeof pos !== "number") return;
    // Insert a new empty figureItem at the end of this row's content.
    const row = editor.state.doc.nodeAt(pos);
    if (!row) return;
    const end = pos + row.nodeSize - 1;
    editor.chain().focus().insertContentAt(end, { type: "figureItem" }).run();
  };
  return (
    <NodeViewWrapper
      as="div"
      className="nk-figure-row"
      style={{ display: "flex", gap: 16, alignItems: "flex-start", border: "1px dashed #d9d9d9", borderRadius: 6, padding: 8, position: "relative" }}
    >
      {/* content hole: TipTap renders figureItem NodeViews inside this element */}
      <NodeViewContent as="div" style={{ display: "flex", gap: 16, flex: 1, minWidth: 0 }} />
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addColumn} title="Oszlop hozzáadása" />
        <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => deleteNode()} title="Képblokk törlése" />
      </div>
    </NodeViewWrapper>
  );
}

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
    const inner: DOMOutputSpec[] = [["img", { src, alt }]];
    if (caption) inner.push(["figcaption", {}, caption]);
    return ["figure", {}, ...inner] as DOMOutputSpec;
  },

  addNodeView() {
    return ReactNodeViewRenderer(FigureItemView);
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

  addNodeView() {
    return ReactNodeViewRenderer(FigureRowView);
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
