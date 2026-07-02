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

  it("normalizes the real imported shape (img wrapped in <p> inside <figure>)", () => {
    // The Markdown->HTML backfill emits images as paragraphs, so imported posts
    // store <figure><p><img></p><figcaption></figcaption></figure>. figureItem is
    // an atom keyed on src/alt/caption, so re-serialization intentionally drops the
    // redundant <p> wrapper while preserving image, alt, caption and column structure.
    // This documents that the first save of an imported figure-row post rewrites the
    // markup to the canonical <figure><img><figcaption></figcaption></figure> form.
    const imported =
      '<div class="nk-figure-row">' +
      '<figure><p><img src="/media/a" alt="A"></p><figcaption>Cap A</figcaption></figure>' +
      '<figure><p><img src="/media/b" alt="B"></p></figure>' +
      "</div>";
    const editor = makeEditor(imported);
    const out = editor.getHTML();
    editor.destroy();
    expect(out).toContain('<div class="nk-figure-row">');
    // canonical form: img is a direct child of figure, no wrapping <p>
    expect(out).toContain('<figure><img src="/media/a" alt="A"><figcaption>Cap A</figcaption></figure>');
    expect(out).not.toContain("<p>");
    // both columns survive; figure B (no caption) emits no empty figcaption
    expect((out.match(/<figure>/g) || []).length).toBe(2);
    expect(out).toContain('<img src="/media/b" alt="B">');
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
