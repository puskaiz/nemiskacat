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
