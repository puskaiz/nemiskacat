import { describe, expect, it } from "vitest";
import { resolveInitialMode } from "./mode";

describe("resolveInitialMode", () => {
  it("uses the stored value when it is a valid mode", () => {
    expect(resolveInitialMode("dark", false)).toBe("dark");
    expect(resolveInitialMode("light", true)).toBe("light");
  });

  it("falls back to the OS preference when nothing is stored", () => {
    expect(resolveInitialMode(null, true)).toBe("dark");
    expect(resolveInitialMode(null, false)).toBe("light");
  });

  it("ignores an unrecognized stored value and uses the OS preference", () => {
    expect(resolveInitialMode("purple", true)).toBe("dark");
    expect(resolveInitialMode("", false)).toBe("light");
  });
});
