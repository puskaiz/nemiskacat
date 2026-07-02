import { afterEach, describe, expect, it, vi } from "vitest";
import { apiFetch, readCookie } from "../api/http";
import { dataProvider } from "./dataProvider";

function stubCookie(value: string) {
  vi.stubGlobal("document", { cookie: value });
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("readCookie", () => {
  it("extracts a named cookie value", () => {
    stubCookie("foo=1; XSRF-TOKEN=abc%2D123; bar=2");
    expect(readCookie("XSRF-TOKEN")).toBe("abc-123");
    expect(readCookie("missing")).toBeNull();
  });
});

describe("apiFetch CSRF + credentials", () => {
  it("adds X-XSRF-TOKEN on mutations and always sends credentials", async () => {
    stubCookie("XSRF-TOKEN=tok-9");
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}"));
    vi.stubGlobal("fetch", fetchMock);

    await apiFetch("/api/admin/workshops", { method: "POST", body: "{}" });

    const [, init] = fetchMock.mock.calls[0];
    const headers = init.headers as Headers;
    expect(headers.get("X-XSRF-TOKEN")).toBe("tok-9");
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(init.credentials).toBe("include");
  });

  it("does not add the CSRF header on GET", async () => {
    stubCookie("XSRF-TOKEN=tok-9");
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}"));
    vi.stubGlobal("fetch", fetchMock);

    await apiFetch("/api/admin/workshops");

    const [, init] = fetchMock.mock.calls[0];
    expect((init.headers as Headers).get("X-XSRF-TOKEN")).toBeNull();
    expect(init.credentials).toBe("include");
  });
});

describe("dataProvider.getList", () => {
  it("maps pagination + filters to query params and reads X-Total-Count", async () => {
    stubCookie("");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: 1 }, { id: 2 }]), {
        headers: { "X-Total-Count": "42", "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await dataProvider.getList({
      resource: "orders",
      pagination: { current: 3, pageSize: 10 },
      filters: [{ field: "q", operator: "contains", value: "kovacs" }],
    } as never);

    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).toContain("/api/admin/orders?");
    expect(url).toContain("page=2"); // current 3 (1-based) -> page 2 (0-based)
    expect(url).toContain("size=10");
    expect(url).toContain("q=kovacs");
    expect(result.total).toBe(42);
    expect(result.data).toHaveLength(2);
  });
});
