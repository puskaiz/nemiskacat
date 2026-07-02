import type { AuthProvider } from "@refinedev/core";
import { apiFetch, API_BASE, primeCsrf } from "../api/http";
import type { AdminIdentity } from "../types";

/** Cookie-session auth against /api/admin/auth/* (no tokens in the browser). */
export const authProvider: AuthProvider = {
  login: async ({ email, password }) => {
    await primeCsrf(); // make sure the XSRF-TOKEN cookie is set before the POST
    const res = await apiFetch(`${API_BASE}/auth/login`, {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (res.ok) {
      return { success: true, redirectTo: "/" };
    }
    const message =
      res.status === 403
        ? "Ehhez a fiókhoz nincs admin jogosultság."
        : "Hibás email-cím vagy jelszó.";
    return { success: false, error: { name: "Bejelentkezés sikertelen", message } };
  },

  logout: async () => {
    await apiFetch(`${API_BASE}/auth/logout`, { method: "POST" }).catch(() => undefined);
    return { success: true, redirectTo: "/login" };
  },

  check: async () => {
    const res = await apiFetch(`${API_BASE}/auth/me`);
    if (res.ok) {
      return { authenticated: true };
    }
    return { authenticated: false, redirectTo: "/login", logout: true };
  },

  getIdentity: async () => {
    const res = await apiFetch(`${API_BASE}/auth/me`);
    if (!res.ok) {
      return null;
    }
    const me = (await res.json()) as AdminIdentity;
    return { id: me.email, name: me.email, productEditorEnabled: me.productEditorEnabled };
  },

  onError: async (error) => {
    if (error?.status === 401) {
      return { logout: true, redirectTo: "/login", error };
    }
    return { error };
  },
};
