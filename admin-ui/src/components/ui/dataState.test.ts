import { describe, expect, it } from "vitest";
import { selectDataState } from "./dataStateSelect";

describe("selectDataState", () => {
  it("loading wins", () => {
    expect(selectDataState({ isLoading: true, isError: false, count: 0 })).toBe("loading");
  });
  it("error before empty", () => {
    expect(selectDataState({ isLoading: false, isError: true, count: 0 })).toBe("error");
  });
  it("empty when no rows", () => {
    expect(selectDataState({ isLoading: false, isError: false, count: 0 })).toBe("empty");
  });
  it("data when rows exist", () => {
    expect(selectDataState({ isLoading: false, isError: false, count: 3 })).toBe("data");
  });
});
