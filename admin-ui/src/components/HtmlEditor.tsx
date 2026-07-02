import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Link from "@tiptap/extension-link";
import Image from "@tiptap/extension-image";
import { Table, TableRow, TableCell, TableHeader } from "@tiptap/extension-table";
import { FigureRow, FigureItem } from "./extensions/FigureRow";
import { Button, Space, Divider } from "antd";
import {
  BoldOutlined,
  ItalicOutlined,
  OrderedListOutlined,
  UnorderedListOutlined,
  LinkOutlined,
  PictureOutlined,
  TableOutlined,
  AppstoreOutlined,
} from "@ant-design/icons";
import { useEffect } from "react";

// ---------------------------------------------------------------------------
// Pure helper — exported so it can be unit-tested without a DOM
// ---------------------------------------------------------------------------

/**
 * Builds a `<div class="nk-figure-row">` block containing one `<figure>` per
 * item.  Caption is omitted when falsy.
 */
export function figureRowHtml(
  items: { src: string; alt: string; caption?: string }[]
): string {
  const figures = items
    .map(({ src, alt, caption }) => {
      const img = `<img src="${src}" alt="${alt}">`;
      const cap = caption ? `<figcaption>${caption}</figcaption>` : "";
      return `<figure>${img}${cap}</figure>`;
    })
    .join("");
  return `<div class="nk-figure-row">${figures}</div>`;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

interface Props {
  value?: string;
  onChange?: (html: string) => void;
}

export function HtmlEditor({ value, onChange }: Props) {
  const editor = useEditor({
    extensions: [
      StarterKit,
      Link.configure({ openOnClick: false }),
      Image,
      Table.configure({ resizable: false }),
      TableRow,
      TableHeader,
      TableCell,
      FigureRow,
      FigureItem,
    ],
    content: value ?? "",
    onUpdate: ({ editor: ed }) => {
      onChange?.(ed.getHTML());
    },
  });

  // Sync external value into editor (e.g. after form reset or initial load)
  useEffect(() => {
    if (editor && value !== undefined && value !== editor.getHTML()) {
      editor.commands.setContent(value ?? "");
    }
  }, [editor, value]);

  const insertLink = () => {
    const url = window.prompt("URL:");
    if (!url) return;
    editor?.chain().focus().setLink({ href: url }).run();
  };

  const insertImage = () => {
    const url = window.prompt("Kép URL (/media/…):");
    if (!url) return;
    editor?.chain().focus().setImage({ src: url }).run();
  };

  const insertTable = () => {
    editor
      ?.chain()
      .focus()
      .insertTable({ rows: 3, cols: 3, withHeaderRow: true })
      .run();
  };

  const insertFigureRow = () => {
    editor?.chain().focus().insertFigureRow(2).run();
  };

  return (
    <div
      style={{
        border: "1px solid #d9d9d9",
        borderRadius: 6,
        overflow: "hidden",
      }}
    >
      {/* Toolbar */}
      <div
        style={{
          padding: "6px 8px",
          background: "#fafafa",
          borderBottom: "1px solid #d9d9d9",
        }}
      >
        <Space size={2} wrap>
          <Button
            size="small"
            title="Félkövér"
            icon={<BoldOutlined />}
            type={editor?.isActive("bold") ? "primary" : "text"}
            onClick={() => editor?.chain().focus().toggleBold().run()}
          />
          <Button
            size="small"
            title="Dőlt"
            icon={<ItalicOutlined />}
            type={editor?.isActive("italic") ? "primary" : "text"}
            onClick={() => editor?.chain().focus().toggleItalic().run()}
          />

          <Divider type="vertical" style={{ margin: "0 4px" }} />

          <Button
            size="small"
            title="H2"
            type={
              editor?.isActive("heading", { level: 2 }) ? "primary" : "text"
            }
            onClick={() =>
              editor?.chain().focus().toggleHeading({ level: 2 }).run()
            }
          >
            H2
          </Button>
          <Button
            size="small"
            title="H3"
            type={
              editor?.isActive("heading", { level: 3 }) ? "primary" : "text"
            }
            onClick={() =>
              editor?.chain().focus().toggleHeading({ level: 3 }).run()
            }
          >
            H3
          </Button>

          <Divider type="vertical" style={{ margin: "0 4px" }} />

          <Button
            size="small"
            title="Számozott lista"
            icon={<OrderedListOutlined />}
            type={editor?.isActive("orderedList") ? "primary" : "text"}
            onClick={() => editor?.chain().focus().toggleOrderedList().run()}
          />
          <Button
            size="small"
            title="Felsorolás"
            icon={<UnorderedListOutlined />}
            type={editor?.isActive("bulletList") ? "primary" : "text"}
            onClick={() => editor?.chain().focus().toggleBulletList().run()}
          />

          <Divider type="vertical" style={{ margin: "0 4px" }} />

          <Button
            size="small"
            title="Hivatkozás"
            icon={<LinkOutlined />}
            type={editor?.isActive("link") ? "primary" : "text"}
            onClick={insertLink}
          />
          <Button
            size="small"
            title="Kép"
            icon={<PictureOutlined />}
            type="text"
            onClick={insertImage}
          />
          <Button
            size="small"
            title="Táblázat"
            icon={<TableOutlined />}
            type="text"
            onClick={insertTable}
          />
          <Button
            size="small"
            title="Képoszlopok"
            icon={<AppstoreOutlined />}
            type="text"
            onClick={insertFigureRow}
          />
        </Space>
      </div>

      {/* Editor area — capped height with its own scrollbar so a long post
          doesn't stretch the whole page. */}
      <EditorContent
        editor={editor}
        className="nk-html-editor"
        style={{
          padding: "10px 12px",
          minHeight: 240,
          maxHeight: 480,
          overflowY: "auto",
          cursor: "text",
        }}
      />
    </div>
  );
}
