import type { CrudFilters, DataProvider } from "@refinedev/core";
import { apiFetch, API_BASE } from "../api/http";

// Refine's DataProvider methods are generic over the record type; this provider
// works untyped at the HTTP boundary, so results are cast through `any` (the
// resource pages restore strong typing via their own generics).
/* eslint-disable @typescript-eslint/no-explicit-any */

// Maps Refine's list params to our API: page (0-based) + size, and pass-through
// filter fields (orders: status, q, from, to). Reads the X-Total-Count header.
function buildListQuery(
  pagination: { current?: number; pageSize?: number } | undefined,
  filters: CrudFilters | undefined,
): string {
  const params = new URLSearchParams();
  const current = pagination?.current ?? 1;
  const pageSize = pagination?.pageSize ?? 20;
  params.set("page", String(Math.max(0, current - 1)));
  params.set("size", String(pageSize));
  for (const filter of filters ?? []) {
    if ("field" in filter && filter.value != null && filter.value !== "") {
      // Array values (e.g. the "in" operator) become repeated query params:
      // ?category=a&category=b — the backend binds them to a List.
      if (Array.isArray(filter.value)) {
        for (const v of filter.value) {
          if (v != null && v !== "") params.append(filter.field, String(v));
        }
      } else {
        params.set(filter.field, String(filter.value));
      }
    }
  }
  return params.toString();
}

async function asJson(res: Response): Promise<unknown> {
  if (res.status === 204) {
    return {};
  }
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.text();
      if (body) message = body;
    } catch {
      /* ignore */
    }
    throw { message, statusCode: res.status };
  }
  return res.json();
}

export const dataProvider: DataProvider = {
  getApiUrl: () => API_BASE,

  getList: async ({ resource, pagination, filters }) => {
    const query = buildListQuery(pagination, filters);
    const res = await apiFetch(`${API_BASE}/${resource}?${query}`);
    const data = (await asJson(res)) as any[];
    const headerTotal = res.headers.get("X-Total-Count");
    const total = headerTotal != null ? Number(headerTotal) : data.length;
    return { data, total };
  },

  getOne: async ({ resource, id }) => {
    const res = await apiFetch(`${API_BASE}/${resource}/${id}`);
    return { data: (await asJson(res)) as any };
  },

  create: async ({ resource, variables }) => {
    const res = await apiFetch(`${API_BASE}/${resource}`, {
      method: "POST",
      body: JSON.stringify(variables),
    });
    return { data: (await asJson(res)) as any };
  },

  update: async ({ resource, id, variables }) => {
    const res = await apiFetch(`${API_BASE}/${resource}/${id}`, {
      method: "PUT",
      body: JSON.stringify(variables),
    });
    return { data: (await asJson(res)) as any };
  },

  deleteOne: async ({ resource, id }) => {
    const res = await apiFetch(`${API_BASE}/${resource}/${id}`, { method: "DELETE" });
    return { data: (await asJson(res)) as any };
  },
};
