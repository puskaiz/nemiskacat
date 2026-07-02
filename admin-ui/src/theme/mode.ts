// Pure theme-mode helpers. Kept free of React/DOM so the resolution logic is
// unit-testable in the default (node) Vitest environment — no jsdom needed.

export type ThemeMode = "light" | "dark";

export const STORAGE_KEY = "admin-theme";

/** First-load mode: an explicitly stored choice wins, otherwise follow the OS. */
export function resolveInitialMode(stored: string | null, prefersDark: boolean): ThemeMode {
  if (stored === "light" || stored === "dark") {
    return stored;
  }
  return prefersDark ? "dark" : "light";
}
