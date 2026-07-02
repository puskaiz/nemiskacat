import type { ThemeMode } from "./mode";

export interface Palette {
  bg: string; surface: string; input: string; th: string; fill: string;
  border: string; borderSoft: string; text: string; muted: string; faint: string;
  accent: string; accentFg: string; accentSoft: string;
}

const LIGHT: Palette = {
  bg: "#F7F8FA", surface: "#ffffff", input: "#F8FAFC", th: "#FBFCFD", fill: "#F1F5F9",
  border: "#E5E8EC", borderSoft: "#EEF0F3", text: "#0F172A", muted: "#475569", faint: "#94a3b8",
  accent: "#16181D", accentFg: "#16181D", accentSoft: "#ECECEE",
};

const DARK: Palette = {
  bg: "#0E1525", surface: "#161E2E", input: "#1B2436", th: "#1B2436", fill: "#222C40",
  border: "#2A3650", borderSoft: "#222C40", text: "#E7ECF3", muted: "#9AA7BD", faint: "#6B7A93",
  accent: "#3A4658", accentFg: "#C9D3E2", accentSoft: "#232C3C",
};

/** Theme-independent brand/semantic colors. */
export const SEMANTIC = {
  secondary: "#C20E1A", // Export, date filters
  danger: "#C0392B",    // refund/destructive
  signout: "#DC4040",   // sign-out, validation, error icon
} as const;

export function paletteFor(mode: ThemeMode): Palette {
  return mode === "dark" ? DARK : LIGHT;
}

/** Status pill tones (light); dark variants in PILL_DARK. */
export const PILL_LIGHT = {
  green: { fg: "#047857", bg: "#ECFDF3" },
  amber: { fg: "#B45309", bg: "#FEF6E7" },
  blue:  { fg: "#1D4ED8", bg: "#EAF0FE" },
  gray:  { fg: "#64748B", bg: "#F1F5F9" },
} as const;
export const PILL_DARK = {
  green: { fg: "#34D399", bg: "#0C2A22" },
  amber: { fg: "#FBBF77", bg: "#2A2310" },
  blue:  { fg: "#93B4FF", bg: "#16223E" },
  gray:  { fg: "#9AA7BD", bg: "#222C40" },
} as const;
