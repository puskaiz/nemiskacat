import { describe, expect, it } from "vitest";
import { activeKey, expandedParent } from "./navState";

describe("navState", () => {
  it("matches the longest nav key that prefixes the path", () => {
    expect(activeKey("/orders/show/5")).toBe("/orders");
    expect(activeKey("/products/categories")).toBe("/products/categories");
    expect(activeKey("/")).toBe("/");
    expect(activeKey("/blog")).toBe("/blog");
    expect(activeKey("/blog/create")).toBe("/blog");
    expect(activeKey("/blog/edit/1")).toBe("/blog");
  });
  it("finds the parent submenu to auto-expand (one open at a time)", () => {
    expect(expandedParent("/products/categories")).toBe("/products");
    expect(expandedParent("/workshops/instructors")).toBe("/workshops");
    expect(expandedParent("/orders")).toBeNull();
  });
});
