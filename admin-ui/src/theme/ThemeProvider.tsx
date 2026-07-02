import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { ConfigProvider } from "antd";
import { buildTheme, surfaceBg } from "./tokens";
import { STORAGE_KEY, resolveInitialMode, type ThemeMode } from "./mode";
import "./themeVars.css";

interface ThemeModeContextValue {
  mode: ThemeMode;
  toggle: () => void;
  setMode: (mode: ThemeMode) => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue | undefined>(undefined);

export function useThemeMode(): ThemeModeContextValue {
  const ctx = useContext(ThemeModeContext);
  if (!ctx) {
    throw new Error("useThemeMode must be used within <ThemeProvider>");
  }
  return ctx;
}

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  const [mode, setModeState] = useState<ThemeMode>(() =>
    resolveInitialMode(
      localStorage.getItem(STORAGE_KEY),
      window.matchMedia("(prefers-color-scheme: dark)").matches,
    ),
  );

  const value = useMemo<ThemeModeContextValue>(() => {
    const setMode = (next: ThemeMode) => {
      localStorage.setItem(STORAGE_KEY, next);
      setModeState(next);
    };
    return { mode, setMode, toggle: () => setMode(mode === "dark" ? "light" : "dark") };
  }, [mode]);

  // Drive the CSS-variable theme + keep the page bg in sync (no flash behind layout).
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", mode);
    document.body.style.backgroundColor = surfaceBg(mode);
  }, [mode]);

  return (
    <ThemeModeContext.Provider value={value}>
      <ConfigProvider theme={buildTheme(mode)}>{children}</ConfigProvider>
    </ThemeModeContext.Provider>
  );
};
