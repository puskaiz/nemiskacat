import { describe, expect, it } from "vitest";
import { paletteFor, SEMANTIC } from "./palette";

describe("paletteFor", () => {
  it("returns the near-black accent in light mode", () => {
    expect(paletteFor("light").accent).toBe("#16181D");
    expect(paletteFor("light").bg).toBe("#F7F8FA");
  });
  it("returns the dark surfaces in dark mode", () => {
    expect(paletteFor("dark").bg).toBe("#0E1525");
    expect(paletteFor("dark").accent).toBe("#3A4658");
  });
  it("exposes theme-independent semantic colors", () => {
    expect(SEMANTIC.secondary).toBe("#C20E1A");
    expect(SEMANTIC.danger).toBe("#C0392B");
  });
});
