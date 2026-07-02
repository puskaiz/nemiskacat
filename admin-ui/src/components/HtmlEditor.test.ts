import { describe, expect, it } from "vitest";
import { figureRowHtml } from "./HtmlEditor";

describe("figureRowHtml", () => {
  it("wraps items in nk-figure-row", () => {
    const html = figureRowHtml([{ src: "/media/a.jpg", alt: "A" }]);
    expect(html).toBe(
      '<div class="nk-figure-row"><figure><img src="/media/a.jpg" alt="A"></figure></div>'
    );
  });

  it("includes figcaption when caption provided", () => {
    const html = figureRowHtml([
      { src: "/media/b.jpg", alt: "B", caption: "Felirat" },
    ]);
    expect(html).toContain("<figcaption>Felirat</figcaption>");
  });

  it("omits figcaption when caption is absent", () => {
    const html = figureRowHtml([{ src: "/media/c.jpg", alt: "C" }]);
    expect(html).not.toContain("figcaption");
  });

  it("handles multiple items", () => {
    const html = figureRowHtml([
      { src: "/media/x.jpg", alt: "X", caption: "X cap" },
      { src: "/media/y.jpg", alt: "Y" },
    ]);
    expect(html).toContain('<img src="/media/x.jpg" alt="X">');
    expect(html).toContain('<img src="/media/y.jpg" alt="Y">');
    expect(html).toContain("<figcaption>X cap</figcaption>");
    // Y has no caption
    const yFigure = html.slice(html.indexOf('/media/y.jpg'));
    expect(yFigure).not.toContain("figcaption");
  });

  it("returns empty nk-figure-row for empty input", () => {
    const html = figureRowHtml([]);
    expect(html).toBe('<div class="nk-figure-row"></div>');
  });
});
