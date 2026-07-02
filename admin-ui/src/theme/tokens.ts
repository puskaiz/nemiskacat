import { theme as antdTheme, type ThemeConfig } from "antd";
import type { ThemeMode } from "./mode";
import { paletteFor } from "./palette";

const FONT =
  '"Hanken Grotesk", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif';

export function surfaceBg(mode: ThemeMode): string {
  return paletteFor(mode).bg;
}

export function buildTheme(mode: ThemeMode): ThemeConfig {
  const p = paletteFor(mode);
  const isDark = mode === "dark";
  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: p.accent,
      colorBgLayout: p.bg,
      colorBgContainer: p.surface,
      colorBorderSecondary: p.borderSoft,
      colorText: p.text,
      colorTextSecondary: p.muted,
      borderRadius: 3,
      controlHeight: 36,
      fontFamily: FONT,
    },
    components: {
      Layout: { siderBg: p.surface, headerBg: p.surface, bodyBg: p.bg },
      Menu: { itemBg: "transparent", itemSelectedBg: p.accentSoft, itemBorderRadius: 2 },
      Card: { borderRadiusLG: 3 },
      Button: { borderRadius: 999, borderRadiusLG: 999, borderRadiusSM: 999 },
      Table: { headerBg: p.th, borderColor: p.borderSoft },
      Input: { borderRadius: 3, colorBgContainer: p.input },
    },
  };
}
