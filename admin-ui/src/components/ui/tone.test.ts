import { describe, expect, it } from "vitest";
import { toneFor } from "./tone";

describe("toneFor", () => {
  it("maps order statuses to pill tones", () => {
    expect(toneFor("paid")).toBe("green");
    expect(toneFor("pending")).toBe("amber");
    expect(toneFor("shipped")).toBe("blue");
    expect(toneFor("refunded")).toBe("gray");
  });
  it("falls back to gray for unknown", () => {
    expect(toneFor("whatever")).toBe("gray");
  });
});
