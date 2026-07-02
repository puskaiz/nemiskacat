import { describe, expect, it } from "vitest";
import { parseContent, serializeContent } from "./content";

describe("sidebar block content helper", () => {
  it("round-trips AUTHOR content", () => {
    const json = '{"name":"A","bio":"b","photoUrl":"/x.svg"}';
    const parsed = parseContent("AUTHOR", json);
    expect(parsed).toMatchObject({ name: "A", bio: "b", photoUrl: "/x.svg" });
    expect(JSON.parse(serializeContent("AUTHOR", parsed))).toMatchObject({ name: "A" });
  });

  it("returns {} for empty or invalid json", () => {
    expect(parseContent("CTA", "")).toEqual({});
    expect(parseContent("CTA", "{not json")).toEqual({});
    expect(parseContent("CTA", "[1,2]")).toEqual({});
  });

  it("serializes SOCIAL links array", () => {
    const out = serializeContent("SOCIAL", { title: "K", links: [{ network: "facebook", url: "u" }] });
    expect(JSON.parse(out).links[0].network).toBe("facebook");
  });

  it("round-trips INSTAGRAM content with a count", () => {
    const json = '{"title":"Kövess minket","count":6}';
    const parsed = parseContent("INSTAGRAM", json);
    expect(parsed).toMatchObject({ title: "Kövess minket", count: 6 });
    expect(JSON.parse(serializeContent("INSTAGRAM", parsed))).toMatchObject({ title: "Kövess minket", count: 6 });
  });

  it("drops an absent INSTAGRAM count (backend defaults it)", () => {
    const out = serializeContent("INSTAGRAM", { title: "X", count: undefined });
    expect(JSON.parse(out)).toEqual({ title: "X" });
  });

  it("keeps a cleared INSTAGRAM count as null (antd InputNumber → backend nullable Integer)", () => {
    const out = serializeContent("INSTAGRAM", { title: "X", count: null });
    expect(JSON.parse(out)).toEqual({ title: "X", count: null });
  });
});
