// Same-origin fetch wrapper for the admin API. Sends the session cookie
// (credentials) and, on mutations, echoes the XSRF-TOKEN cookie as the
// X-XSRF-TOKEN header (Spring's CookieCsrfTokenRepository + plain handler).

export const API_BASE = "/api/admin";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export function readCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp("(?:^|; )" + name.replace(/([.$?*|{}()[\]\\/+^])/g, "\\$1") + "=([^;]*)"),
  );
  return match ? decodeURIComponent(match[1]) : null;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (!SAFE_METHODS.has(method)) {
    const token = readCookie("XSRF-TOKEN");
    if (token) {
      headers.set("X-XSRF-TOKEN", token);
    }
    if (init.body && !headers.has("Content-Type") && !(init.body instanceof FormData)) {
      headers.set("Content-Type", "application/json");
    }
  }
  return fetch(path, { ...init, method, headers, credentials: "include" });
}

/** Ensures the XSRF-TOKEN cookie exists before a mutation by making one safe call. */
export async function primeCsrf(): Promise<void> {
  if (readCookie("XSRF-TOKEN")) {
    return;
  }
  await apiFetch(`${API_BASE}/auth/me`).catch(() => undefined);
}
