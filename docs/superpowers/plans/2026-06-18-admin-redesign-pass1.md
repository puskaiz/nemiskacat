# Admin Redesign — Pass 1 (whole new look everywhere) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the admin-ui indigo design with the nemiskacat handoff look applied across the entire app — new theme/font/shape, custom chrome (grouped sidebar + top bar), bilingual HU/EN, and a route for every section (backed modules live, the rest with the design's static example data).

**Architecture:** Keep Refine + Ant Design + react-router. Re-tokenize antd to the handoff palette (light+dark) + a global CSS layer for squared-containers/pill-buttons, Hanken Grotesk. Build a custom `AdminLayout` (Sidebar + Topbar) replacing `ThemedLayoutV2`. Add react-i18next via Refine's `i18nProvider`. Register all ~12 sections as resources/routes; backed pages (orders/workshops/bookings) keep real-API wiring re-skinned, the rest render co-located static fixtures.

**Tech Stack:** TypeScript, React 18, Refine 4, Ant Design 5, react-router 6, Vite 6, Vitest 2. New deps: `i18next`, `react-i18next`, `@fontsource/hanken-grotesk`, `lucide-react`. Design source of truth: `design_handoff_nemiskacat_admin/Admin Mid-fi HU.dc.html` (+ States & Flows, Type & Components). Spec: `docs/superpowers/specs/2026-06-18-admin-redesign-design.md`.

**Test philosophy:** Unit-test PURE helpers in the node env (like the existing `mode.ts`/`providers.test.ts`) — no jsdom. Components are verified by the type-check + build gate (`npm run build`) and manual check. Every task ends green on `npm run build`; the backend is untouched.

**Working dir:** all paths under `admin-ui/` unless noted. Run npm commands from `admin-ui/`.

---

## File Structure (Pass 1)

```
admin-ui/
  public/                 logo.png, logo-small.png (copied from the handoff uploads/)
  src/
    theme/
      tokens.ts           re-tokenized antd theme (handoff palette, squared+pills)  [MODIFY]
      palette.ts          raw token maps {light,dark} + pure resolvers              [CREATE]
      themeVars.css       CSS variables + global shape/font rules                   [CREATE]
      mode.ts             kept as-is                                                [KEEP]
      ThemeProvider.tsx   inject themeVars + data-theme attr                        [MODIFY]
    i18n/
      index.ts            i18next init + Refine i18nProvider adapter                [CREATE]
      resources.ts        typed resource bundles import                            [CREATE]
      hu/*.json en/*.json namespaced strings                                        [CREATE]
    components/
      layout/
        nav.config.ts     grouped nav model (pure data)                            [CREATE]
        navState.ts       pure active-section/accordion helpers (+ tested)          [CREATE]
        Sidebar.tsx       grouped nav, collapse rail, drawer, badges               [CREATE]
        Topbar.tsx        search, lang switch, dark toggle, bell, profile menu     [CREATE]
        AdminLayout.tsx   composes Sidebar + Topbar + content                      [CREATE]
        Header.tsx Title.tsx   removed (folded into AdminLayout)                   [DELETE]
      ui/
        StatusPill.tsx + tone.ts(+tested)  status → {label,tone}                   [CREATE]
        DataState.tsx + dataState.ts(+tested)  loading/empty/error selector        [CREATE]
        PillButton.tsx    primary/secondary/danger pill buttons                    [CREATE]
        PageHeader.tsx    title + actions + back-link                              [CREATE]
        InlinePageScaffold.tsx  inline create/edit shell                           [CREATE]
    data/                 static fixtures for backend-less modules                  [CREATE]
    pages/
      <existing>          orders/, workshops/, dashboard/, login   re-skinned       [MODIFY]
      products/ coupons/ customers/ shipping/ reports/ pages/ settings/ + workshops/instructors, products/categories, auth/   placeholder screens  [CREATE]
    App.tsx               all resources + routes; AdminLayout                       [MODIFY]
```

---

## Task 1: Dependencies, font, and the token palette

**Files:**
- Modify: `admin-ui/package.json` (via npm install)
- Create: `admin-ui/src/theme/palette.ts`
- Create: `admin-ui/src/theme/palette.test.ts`

- [ ] **Step 1: Install dependencies**

Run from `admin-ui/`:
```bash
npm install i18next@^23 react-i18next@^15 @fontsource/hanken-grotesk@^5 lucide-react@^0.460
```
Expected: deps added, `node_modules` updated, no peer-dep errors that fail the install.

- [ ] **Step 2: Write the failing test for the palette resolver**

Create `admin-ui/src/theme/palette.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { paletteFor, SEMANTIC } from "./palette";

describe("paletteFor", () => {
  it("returns the near-black accent in light mode", () => {
    expect(paletteFor("light").accent).toBe("#16181D");
    expect(paletteFor("light").bg).toBe("#F7F8FA");
  });
  it("returns the dark surfaces in dark mode", () => {
    expect(paletteFor("dark").bg).toBe("#0E1525");
    expect(paletteFor("dark").accent).toBe("#3A4658");
  });
  it("exposes theme-independent semantic colors", () => {
    expect(SEMANTIC.secondary).toBe("#C20E1A");
    expect(SEMANTIC.danger).toBe("#C0392B");
  });
});
```

- [ ] **Step 3: Run it — expect failure**

Run: `npm run test -- palette`
Expected: FAIL — `./palette` does not exist.

- [ ] **Step 4: Implement `palette.ts`**

Create `admin-ui/src/theme/palette.ts`:
```ts
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

/** Status pill tones (light); dark variants derived in tone.ts. */
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
```

- [ ] **Step 5: Run — expect pass**

Run: `npm run test -- palette`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add admin-ui/package.json admin-ui/package-lock.json admin-ui/src/theme/palette.ts admin-ui/src/theme/palette.test.ts
git commit -m "feat(admin-ui): handoff palette tokens + deps (i18next, hanken, lucide)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Re-theme antd + global shape/font CSS

**Files:**
- Modify: `admin-ui/src/theme/tokens.ts`
- Create: `admin-ui/src/theme/themeVars.css`
- Modify: `admin-ui/src/theme/ThemeProvider.tsx`

- [ ] **Step 1: Rewrite `tokens.ts` to the handoff kit**

Replace `admin-ui/src/theme/tokens.ts` with:
```ts
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
      borderRadius: 3,              // squared containers/inputs
      controlHeight: 36,
      fontFamily: FONT,
    },
    components: {
      Layout: { siderBg: p.surface, headerBg: p.surface, bodyBg: p.bg },
      Menu: { itemBg: "transparent", itemSelectedBg: p.accentSoft, itemBorderRadius: 2 },
      Card: { borderRadiusLG: 3 },
      Button: { borderRadius: 999, borderRadiusLG: 999, borderRadiusSM: 999 }, // pills
      Table: { headerBg: p.th, borderColor: p.borderSoft },
      Input: { borderRadius: 3, colorBgContainer: p.input },
    },
  };
}
```

- [ ] **Step 2: Create the CSS-variable + global rules layer**

Create `admin-ui/src/theme/themeVars.css`:
```css
@import "@fontsource/hanken-grotesk/400.css";
@import "@fontsource/hanken-grotesk/500.css";
@import "@fontsource/hanken-grotesk/600.css";
@import "@fontsource/hanken-grotesk/700.css";
@import "@fontsource/hanken-grotesk/800.css";

:root,[data-theme="light"]{
  --bg:#F7F8FA;--surface:#fff;--input:#F8FAFC;--th:#FBFCFD;--fill:#F1F5F9;
  --border:#E5E8EC;--border-soft:#EEF0F3;--text:#0F172A;--muted:#475569;--faint:#94a3b8;
  --accent:#16181D;--accent-fg:#16181D;--accent-soft:#ECECEE;
  --secondary:#C20E1A;--danger:#C0392B;--signout:#DC4040;
}
[data-theme="dark"]{
  --bg:#0E1525;--surface:#161E2E;--input:#1B2436;--th:#1B2436;--fill:#222C40;
  --border:#2A3650;--border-soft:#222C40;--text:#E7ECF3;--muted:#9AA7BD;--faint:#6B7A93;
  --accent:#3A4658;--accent-fg:#C9D3E2;--accent-soft:#232C3C;
}
body{font-family:"Hanken Grotesk",-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif;}
/* Squared containers, pill buttons (belt-and-suspenders over antd tokens). */
.ant-card,.ant-table-wrapper .ant-table{border-radius:3px;}
.ant-btn{border-radius:999px;}
.ant-card{box-shadow:0 1px 2px rgba(16,24,40,.04);}
/* Brand logo auto-invert in dark. */
[data-theme="dark"] [data-brandlogo]{filter:invert(1) brightness(2);}
h1,h2,.page-title{letter-spacing:-0.3px;}
```

- [ ] **Step 3: Wire CSS + `data-theme` attribute in `ThemeProvider.tsx`**

In `admin-ui/src/theme/ThemeProvider.tsx`: add `import "./themeVars.css";` at the top, and set the attribute so CSS vars switch with the mode. Replace the existing background `useEffect` with:
```tsx
  // Drive the CSS-variable theme + keep the page bg in sync (no flash behind layout).
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", mode);
    document.body.style.backgroundColor = surfaceBg(mode);
  }, [mode]);
```
(Keep everything else; `buildTheme`/`surfaceBg` now come from the re-themed `tokens.ts`.)

- [ ] **Step 4: Type-check + build**

Run: `npm run build`
Expected: `tsc --noEmit` passes and `vite build` succeeds (the `@fontsource` CSS imports resolve).

- [ ] **Step 5: Commit**

```bash
git add admin-ui/src/theme/tokens.ts admin-ui/src/theme/themeVars.css admin-ui/src/theme/ThemeProvider.tsx
git commit -m "feat(admin-ui): re-theme antd to handoff kit (near-black/red, hanken, squared+pills)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: i18n (HU default + EN)

**Files:**
- Create: `admin-ui/src/i18n/hu/common.json`, `admin-ui/src/i18n/hu/nav.json`
- Create: `admin-ui/src/i18n/en/common.json`, `admin-ui/src/i18n/en/nav.json`
- Create: `admin-ui/src/i18n/index.ts`
- Create: `admin-ui/src/i18n/i18n.test.ts`

- [ ] **Step 1: Create the string bundles**

Create `admin-ui/src/i18n/hu/nav.json`:
```json
{
  "dashboard": "Irányítópult",
  "sell": "Értékesítés", "orders": "Rendelések", "products": "Termékek",
  "allProducts": "Összes termék", "categories": "Kategóriák", "coupons": "Kuponok",
  "customers": "Vásárlók", "shipping": "Szállítás",
  "workshopsGroup": "Workshopok", "workshops": "Workshopok", "allWorkshops": "Összes workshop",
  "instructors": "Oktatók", "bookings": "Foglalások",
  "analytics": "Elemzések", "reports": "Riportok",
  "content": "Tartalom", "pages": "Oldalak", "settings": "Beállítások"
}
```
Create `admin-ui/src/i18n/en/nav.json`:
```json
{
  "dashboard": "Dashboard",
  "sell": "Sell", "orders": "Orders", "products": "Products",
  "allProducts": "All products", "categories": "Categories", "coupons": "Coupons",
  "customers": "Customers", "shipping": "Shipping",
  "workshopsGroup": "Workshops", "workshops": "Workshops", "allWorkshops": "All workshops",
  "instructors": "Instructors", "bookings": "Bookings",
  "analytics": "Analytics", "reports": "Reports",
  "content": "Content", "pages": "Pages", "settings": "Settings"
}
```
Create `admin-ui/src/i18n/hu/common.json`:
```json
{ "search": "Keresés…", "signOut": "Kijelentkezés", "profileSettings": "Profilbeállítások",
  "changePassword": "Jelszó módosítása", "addPasskey": "Passkey hozzáadása",
  "notifications": "Értesítések", "loading": "Betöltés…", "empty": "Nincs megjeleníthető elem",
  "errorTitle": "Hiba történt", "retry": "Újra", "save": "Mentés", "cancel": "Mégse" }
```
Create `admin-ui/src/i18n/en/common.json`:
```json
{ "search": "Search…", "signOut": "Sign out", "profileSettings": "Profile settings",
  "changePassword": "Change password", "addPasskey": "Add passkey",
  "notifications": "Notifications", "loading": "Loading…", "empty": "Nothing to show",
  "errorTitle": "Something went wrong", "retry": "Retry", "save": "Save", "cancel": "Cancel" }
```

- [ ] **Step 2: Write the failing test**

Create `admin-ui/src/i18n/i18n.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { i18n } from "./index";

describe("i18n", () => {
  it("defaults to Hungarian", () => {
    expect(i18n.language).toBe("hu");
    expect(i18n.t("nav:orders")).toBe("Rendelések");
  });
  it("switches to English", async () => {
    await i18n.changeLanguage("en");
    expect(i18n.t("nav:orders")).toBe("Orders");
    await i18n.changeLanguage("hu");
  });
});
```

- [ ] **Step 3: Run — expect failure**

Run: `npm run test -- i18n`
Expected: FAIL — `./index` does not exist.

- [ ] **Step 4: Implement `i18n/index.ts`**

Create `admin-ui/src/i18n/index.ts`:
```ts
import i18next from "i18next";
import { initReactI18next } from "react-i18next";
import type { I18nProvider } from "@refinedev/core";
import huNav from "./hu/nav.json";
import huCommon from "./hu/common.json";
import enNav from "./en/nav.json";
import enCommon from "./en/common.json";

export const LANG_KEY = "admin-lang";
const stored = typeof localStorage !== "undefined" ? localStorage.getItem(LANG_KEY) : null;

export const i18n = i18next.createInstance();
i18n.use(initReactI18next).init({
  resources: {
    hu: { nav: huNav, common: huCommon },
    en: { nav: enNav, common: enCommon },
  },
  lng: stored === "en" || stored === "hu" ? stored : "hu",
  fallbackLng: "hu",
  ns: ["common", "nav"],
  defaultNS: "common",
  interpolation: { escapeValue: false },
});

/** Refine i18nProvider backed by the shared i18next instance. */
export const i18nProvider: I18nProvider = {
  translate: (key, options) => i18n.t(key, options as Record<string, unknown>) as string,
  changeLocale: async (lang) => {
    localStorage.setItem(LANG_KEY, lang);
    await i18n.changeLanguage(lang);
  },
  getLocale: () => i18n.language,
};
```

- [ ] **Step 5: Run — expect pass**

Run: `npm run test -- i18n`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add admin-ui/src/i18n
git commit -m "feat(admin-ui): i18n scaffolding (HU default + EN) via react-i18next + Refine provider

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Nav model + pure nav-state helpers

**Files:**
- Create: `admin-ui/src/components/layout/nav.config.ts`
- Create: `admin-ui/src/components/layout/navState.ts`
- Create: `admin-ui/src/components/layout/navState.test.ts`

- [ ] **Step 1: Create the nav model**

Create `admin-ui/src/components/layout/nav.config.ts`:
```ts
// Grouped sidebar model. `labelKey` resolves against the i18n `nav` namespace.
// `badge` names a count source wired in Sidebar (orders/products/shipping/bookings).
export interface NavItem {
  key: string;            // matches the route path, e.g. "/orders"
  labelKey: string;       // e.g. "nav:orders"
  icon: string;           // lucide-react icon name
  badge?: "orders" | "products" | "shipping" | "bookings";
  children?: { key: string; labelKey: string }[];
}
export interface NavGroup { headerKey?: string; items: NavItem[]; }

export const NAV: NavGroup[] = [
  { items: [{ key: "/", labelKey: "nav:dashboard", icon: "LayoutDashboard" }] },
  { headerKey: "nav:sell", items: [
    { key: "/orders", labelKey: "nav:orders", icon: "ShoppingCart", badge: "orders" },
    { key: "/products", labelKey: "nav:products", icon: "Package", badge: "products", children: [
      { key: "/products", labelKey: "nav:allProducts" },
      { key: "/products/categories", labelKey: "nav:categories" } ] },
    { key: "/coupons", labelKey: "nav:coupons", icon: "Ticket" },
    { key: "/customers", labelKey: "nav:customers", icon: "Users" },
    { key: "/shipping", labelKey: "nav:shipping", icon: "Truck", badge: "shipping" } ] },
  { headerKey: "nav:workshopsGroup", items: [
    { key: "/workshops", labelKey: "nav:workshops", icon: "GraduationCap", children: [
      { key: "/workshops", labelKey: "nav:allWorkshops" },
      { key: "/workshops/instructors", labelKey: "nav:instructors" } ] },
    { key: "/bookings", labelKey: "nav:bookings", icon: "CalendarCheck", badge: "bookings" } ] },
  { headerKey: "nav:analytics", items: [
    { key: "/reports", labelKey: "nav:reports", icon: "BarChart3" } ] },
  { headerKey: "nav:content", items: [
    { key: "/pages", labelKey: "nav:pages", icon: "FileText" } ] },
];

export const SETTINGS_ITEM: NavItem = { key: "/settings", labelKey: "nav:settings", icon: "Settings" };
```

- [ ] **Step 2: Write the failing test for nav-state helpers**

Create `admin-ui/src/components/layout/navState.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { activeKey, expandedParent } from "./navState";

describe("navState", () => {
  it("matches the longest nav key that prefixes the path", () => {
    expect(activeKey("/orders/show/5")).toBe("/orders");
    expect(activeKey("/products/categories")).toBe("/products/categories");
    expect(activeKey("/")).toBe("/");
  });
  it("finds the parent submenu to auto-expand (one open at a time)", () => {
    expect(expandedParent("/products/categories")).toBe("/products");
    expect(expandedParent("/workshops/instructors")).toBe("/workshops");
    expect(expandedParent("/orders")).toBeNull();
  });
});
```

- [ ] **Step 3: Run — expect failure**

Run: `npm run test -- navState`
Expected: FAIL — `./navState` does not exist.

- [ ] **Step 4: Implement `navState.ts`**

Create `admin-ui/src/components/layout/navState.ts`:
```ts
import { NAV } from "./nav.config";

const ALL_KEYS: string[] = NAV.flatMap((g) =>
  g.items.flatMap((i) => [i.key, ...(i.children?.map((c) => c.key) ?? [])]),
).concat("/settings");

/** The nav key that best matches the current path (longest prefix wins). */
export function activeKey(path: string): string {
  const matches = ALL_KEYS.filter((k) => k === path || (k !== "/" && path.startsWith(k + "/")) || (k !== "/" && path === k));
  if (path === "/") return "/";
  return matches.sort((a, b) => b.length - a.length)[0] ?? "/";
}

/** Parent item key whose submenu should be open for this path, or null. */
export function expandedParent(path: string): string | null {
  for (const g of NAV) {
    for (const i of g.items) {
      if (!i.children) continue;
      if (i.children.some((c) => c.key !== i.key && (path === c.key || path.startsWith(c.key + "/")))) {
        return i.key;
      }
      // a child sharing the parent key (e.g. /products) still expands the parent
      if (path === i.key || path.startsWith(i.key + "/")) return i.key;
    }
  }
  return null;
}
```

- [ ] **Step 5: Run — expect pass**

Run: `npm run test -- navState`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add admin-ui/src/components/layout/nav.config.ts admin-ui/src/components/layout/navState.ts admin-ui/src/components/layout/navState.test.ts
git commit -m "feat(admin-ui): grouped nav model + pure active/expand helpers

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Shared UI primitives

**Files:**
- Create: `admin-ui/src/components/ui/tone.ts` + `tone.test.ts`
- Create: `admin-ui/src/components/ui/StatusPill.tsx`
- Create: `admin-ui/src/components/ui/dataState.ts` + `dataState.test.ts`
- Create: `admin-ui/src/components/ui/DataState.tsx`
- Create: `admin-ui/src/components/ui/PillButton.tsx`, `PageHeader.tsx`, `InlinePageScaffold.tsx`

- [ ] **Step 1: Write failing tests for the pure helpers**

Create `admin-ui/src/components/ui/tone.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { toneFor } from "./tone";

describe("toneFor", () => {
  it("maps order statuses to pill tones", () => {
    expect(toneFor("paid")).toBe("green");
    expect(toneFor("pending")).toBe("amber");
    expect(toneFor("shipped")).toBe("blue");
    expect(toneFor("refunded")).toBe("gray");
  });
  it("falls back to gray for unknown", () => {
    expect(toneFor("whatever")).toBe("gray");
  });
});
```
Create `admin-ui/src/components/ui/dataState.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { selectDataState } from "./dataState";

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
```

- [ ] **Step 2: Run — expect failure**

Run: `npm run test -- tone dataState`
Expected: FAIL — modules do not exist.

- [ ] **Step 3: Implement the pure helpers**

Create `admin-ui/src/components/ui/tone.ts`:
```ts
export type Tone = "green" | "amber" | "blue" | "gray";

const MAP: Record<string, Tone> = {
  paid: "green", active: "green", completed: "green",
  pending: "amber", packing: "amber", waitlist: "amber",
  shipped: "blue", new: "blue",
  refunded: "gray", cancelled: "gray", neutral: "gray",
};
export function toneFor(status: string): Tone {
  return MAP[status.toLowerCase()] ?? "gray";
}
```
Create `admin-ui/src/components/ui/dataState.ts`:
```ts
export type DataStateKind = "loading" | "error" | "empty" | "data";
export function selectDataState(q: { isLoading: boolean; isError: boolean; count: number }): DataStateKind {
  if (q.isLoading) return "loading";
  if (q.isError) return "error";
  return q.count === 0 ? "empty" : "data";
}
```

- [ ] **Step 4: Run — expect pass**

Run: `npm run test -- tone dataState`
Expected: PASS (tone 2, dataState 4).

- [ ] **Step 5: Implement the presentational components**

Create `admin-ui/src/components/ui/StatusPill.tsx`:
```tsx
import { useThemeMode } from "../../theme/ThemeProvider";
import { PILL_DARK, PILL_LIGHT } from "../../theme/palette";
import { toneFor } from "./tone";

export function StatusPill({ status, label }: { status: string; label?: string }) {
  const { mode } = useThemeMode();
  const tone = toneFor(status);
  const c = (mode === "dark" ? PILL_DARK : PILL_LIGHT)[tone];
  return (
    <span style={{
      display: "inline-flex", alignItems: "center", gap: 6, padding: "2px 10px",
      borderRadius: 999, fontSize: 12, fontWeight: 600, color: c.fg, background: c.bg,
    }}>
      <span style={{ width: 6, height: 6, borderRadius: 999, background: c.fg }} />
      {label ?? status}
    </span>
  );
}
```
Create `admin-ui/src/components/ui/DataState.tsx`:
```tsx
import type { ReactNode } from "react";
import { Button, Skeleton, Empty } from "antd";
import { useTranslation } from "react-i18next";
import { selectDataState } from "./dataState";

interface Props {
  isLoading: boolean; isError: boolean; count: number;
  onRetry?: () => void; emptyAction?: ReactNode; children: ReactNode;
}
export function DataState({ isLoading, isError, count, onRetry, emptyAction, children }: Props) {
  const { t } = useTranslation();
  const kind = selectDataState({ isLoading, isError, count });
  if (kind === "loading") return <Skeleton active paragraph={{ rows: 6 }} />;
  if (kind === "error")
    return (
      <div style={{ textAlign: "center", padding: 48, color: "var(--muted)" }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>{t("errorTitle")}</div>
        {onRetry && <Button onClick={onRetry}>{t("retry")}</Button>}
      </div>
    );
  if (kind === "empty")
    return <Empty description={t("empty")} style={{ padding: 48 }}>{emptyAction}</Empty>;
  return <>{children}</>;
}
```
Create `admin-ui/src/components/ui/PillButton.tsx`:
```tsx
import { Button } from "antd";
import type { ButtonProps } from "antd";
import { SEMANTIC } from "../../theme/palette";

type Variant = "primary" | "secondary" | "danger";
export function PillButton({ variant = "primary", style, ...rest }: ButtonProps & { variant?: Variant }) {
  const tint =
    variant === "secondary" ? { background: SEMANTIC.secondary, borderColor: SEMANTIC.secondary, color: "#fff" }
    : variant === "danger" ? { background: SEMANTIC.danger, borderColor: SEMANTIC.danger, color: "#fff" }
    : undefined;
  return <Button type={variant === "primary" ? "primary" : "default"} style={{ borderRadius: 999, ...tint, ...style }} {...rest} />;
}
```
Create `admin-ui/src/components/ui/PageHeader.tsx`:
```tsx
import type { ReactNode } from "react";
export function PageHeader({ title, actions, back }: { title: string; actions?: ReactNode; back?: ReactNode }) {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 18 }}>
      <div>
        {back}
        <h1 className="page-title" style={{ fontSize: 24, fontWeight: 700, margin: 0 }}>{title}</h1>
      </div>
      <div style={{ display: "flex", gap: 8 }}>{actions}</div>
    </div>
  );
}
```
Create `admin-ui/src/components/ui/InlinePageScaffold.tsx`:
```tsx
import type { ReactNode } from "react";
import { PageHeader } from "./PageHeader";
export function InlinePageScaffold({ title, back, actions, children }: { title: string; back?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <div style={{ maxWidth: 880 }}>
      <PageHeader title={title} back={back} actions={actions} />
      <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, padding: 18 }}>
        {children}
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Build + commit**

Run: `npm run build` → expect success.
```bash
git add admin-ui/src/components/ui
git commit -m "feat(admin-ui): shared UI primitives (StatusPill, DataState, PillButton, PageHeader, scaffold)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: AdminLayout chrome (Sidebar + Topbar)

**Files:**
- Create: `admin-ui/public/logo.png`, `admin-ui/public/logo-small.png` (copy from handoff)
- Create: `admin-ui/src/components/layout/Sidebar.tsx`
- Create: `admin-ui/src/components/layout/Topbar.tsx`
- Create: `admin-ui/src/components/layout/AdminLayout.tsx`

- [ ] **Step 1: Copy the logos**

Run from repo root:
```bash
cp design_handoff_nemiskacat_admin/uploads/logo.png admin-ui/public/logo.png
cp design_handoff_nemiskacat_admin/uploads/logo-small.png admin-ui/public/logo-small.png
```

- [ ] **Step 2: Implement `Sidebar.tsx`**

Create `admin-ui/src/components/layout/Sidebar.tsx`:
```tsx
import { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import * as Icons from "lucide-react";
import { NAV, SETTINGS_ITEM, type NavItem } from "./nav.config";
import { activeKey, expandedParent } from "./navState";

function Icon({ name, size = 18 }: { name: string; size?: number }) {
  const C = (Icons as Record<string, React.ComponentType<{ size?: number }>>)[name] ?? Icons.Circle;
  return <C size={size} />;
}

export function Sidebar({ collapsed, badges }: { collapsed: boolean; badges: Record<string, number> }) {
  const nav = useNavigate();
  const { pathname } = useLocation();
  const { t } = useTranslation();
  const active = activeKey(pathname);
  const [open, setOpen] = useState<string | null>(expandedParent(pathname));

  const renderItem = (it: NavItem) => {
    const isActive = active === it.key || it.children?.some((c) => c.key === active);
    const hasChildren = !!it.children;
    return (
      <div key={it.key}>
        <button
          onClick={() => (hasChildren ? setOpen(open === it.key ? null : it.key) : nav(it.key))}
          style={{
            display: "flex", alignItems: "center", gap: 10, width: "100%", border: 0,
            cursor: "pointer", padding: "9px 12px", textAlign: "left",
            background: isActive ? "var(--accent-soft)" : "transparent", color: "var(--text)",
            borderLeft: isActive ? "3px solid var(--accent)" : "3px solid transparent",
            fontSize: 13, fontWeight: isActive ? 700 : 500,
          }}
        >
          <Icon name={it.icon} />
          {!collapsed && <span style={{ flex: 1 }}>{t(it.labelKey)}</span>}
          {!collapsed && it.badge && badges[it.badge] ? (
            <span style={{ fontSize: 11, fontWeight: 700, background: "var(--fill)", borderRadius: 999, padding: "0 7px" }}>
              {badges[it.badge]}
            </span>
          ) : null}
        </button>
        {!collapsed && hasChildren && open === it.key && (
          <div style={{ paddingLeft: 34 }}>
            {it.children!.map((c) => (
              <button key={c.key} onClick={() => nav(c.key)} style={{
                display: "block", width: "100%", textAlign: "left", border: 0, cursor: "pointer",
                background: "transparent", padding: "7px 12px", fontSize: 13,
                color: active === c.key ? "var(--text)" : "var(--muted)",
                fontWeight: active === c.key ? 700 : 500,
              }}>{t(c.labelKey)}</button>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <nav style={{ height: "100%", display: "flex", flexDirection: "column", background: "var(--surface)", borderRight: "1px solid var(--border)" }}>
      <div style={{ padding: "16px 12px", display: "flex", alignItems: "center", gap: 8 }}>
        <img data-brandlogo src={collapsed ? "/admin/logo-small.png" : "/admin/logo.png"} alt="nemiskacat" style={{ height: 24 }} />
        {!collapsed && <span style={{ color: "var(--muted)", fontSize: 12 }}>admin</span>}
      </div>
      <div style={{ flex: 1, overflowY: "auto" }}>
        {NAV.map((g, i) => (
          <div key={i} style={{ padding: "8px 0" }}>
            {!collapsed && g.headerKey && (
              <div style={{ padding: "6px 14px", fontSize: 11, fontWeight: 700, color: "var(--faint)", textTransform: "uppercase" }}>{t(g.headerKey)}</div>
            )}
            {g.items.map(renderItem)}
          </div>
        ))}
      </div>
      <div style={{ borderTop: "1px solid var(--border-soft)", padding: "8px 0" }}>{renderItem(SETTINGS_ITEM)}</div>
    </nav>
  );
}
```

- [ ] **Step 3: Implement `Topbar.tsx`**

Create `admin-ui/src/components/layout/Topbar.tsx`:
```tsx
import { Input, Dropdown, Avatar, Badge } from "antd";
import { Menu as MenuIcon, Moon, Sun, Bell, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useThemeMode } from "../../theme/ThemeProvider";
import { i18n } from "../../i18n";
import { useGetIdentity, useLogout } from "@refinedev/core";

export function Topbar({ onHamburger, onToggleCollapse }: { onHamburger: () => void; onToggleCollapse: () => void }) {
  const { t } = useTranslation();
  const { mode, toggle } = useThemeMode();
  const { mutate: logout } = useLogout();
  const { data: identity } = useGetIdentity<{ name?: string }>();
  const otherLang = i18n.language === "hu" ? "en" : "hu";
  return (
    <div style={{ height: 62, display: "flex", alignItems: "center", gap: 12, padding: "0 18px", background: "var(--surface)", borderBottom: "1px solid var(--border)" }}>
      <button aria-label="menu" onClick={onHamburger} className="admin-hamburger" style={{ border: 0, background: "transparent", cursor: "pointer" }}><MenuIcon size={20} /></button>
      <button aria-label="collapse" onClick={onToggleCollapse} className="admin-collapse" style={{ border: 0, background: "transparent", cursor: "pointer" }}>«</button>
      <Input prefix={<Search size={16} />} placeholder={t("search")} style={{ maxWidth: 360 }} />
      <div style={{ flex: 1 }} />
      <button onClick={() => i18n.changeLanguage(otherLang)} style={{ border: 0, background: "transparent", cursor: "pointer", fontWeight: 700, color: "var(--muted)" }}>{otherLang.toUpperCase()}</button>
      <button aria-label="theme" onClick={toggle} style={{ border: 0, background: "transparent", cursor: "pointer" }}>{mode === "dark" ? <Sun size={18} /> : <Moon size={18} />}</button>
      <Badge dot><Bell size={18} /></Badge>
      <Dropdown
        menu={{ items: [
          { key: "profile", label: t("profileSettings") },
          { key: "password", label: t("changePassword") },
          { key: "passkey", label: t("addPasskey") },
          { key: "notifications", label: t("notifications") },
          { type: "divider" },
          { key: "logout", label: <span style={{ color: "var(--signout)" }}>{t("signOut")}</span>, onClick: () => logout() },
        ] }}
      >
        <span style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
          <Avatar size={28} style={{ background: "var(--accent-soft)", color: "var(--accent-fg)" }}>{(identity?.name ?? "A").charAt(0)}</Avatar>
          <span style={{ fontSize: 13, fontWeight: 600 }}>{identity?.name ?? "Admin"}</span>
        </span>
      </Dropdown>
    </div>
  );
}
```

- [ ] **Step 4: Implement `AdminLayout.tsx`**

Create `admin-ui/src/components/layout/AdminLayout.tsx`:
```tsx
import { useEffect, useState, type ReactNode } from "react";
import { Drawer } from "antd";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

const MOBILE = 880;
// Pass 1: badge counts are static placeholders; wired to real queries in depth passes.
const BADGES = { orders: 3, products: 0, shipping: 2, bookings: 1 };

export function AdminLayout({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false);
  const [drawer, setDrawer] = useState(false);
  const [mobile, setMobile] = useState(typeof window !== "undefined" && window.innerWidth < MOBILE);

  useEffect(() => {
    const onResize = () => setMobile(window.innerWidth < MOBILE);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  return (
    <div style={{ display: "flex", minHeight: "100vh", background: "var(--bg)" }}>
      {!mobile && (
        <div style={{ width: collapsed ? 68 : 234, flexShrink: 0, transition: "width .15s" }}>
          <div style={{ position: "sticky", top: 0, height: "100vh" }}>
            <Sidebar collapsed={collapsed} badges={BADGES} />
          </div>
        </div>
      )}
      {mobile && (
        <Drawer open={drawer} onClose={() => setDrawer(false)} placement="left" width={234} styles={{ body: { padding: 0 } }}>
          <Sidebar collapsed={false} badges={BADGES} />
        </Drawer>
      )}
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <Topbar onHamburger={() => setDrawer(true)} onToggleCollapse={() => setCollapsed((c) => !c)} />
        <main style={{ padding: 24, maxWidth: 1760, width: "100%", margin: "0 auto" }}>{children}</main>
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Build + commit**

Run: `npm run build` → expect success.
```bash
git add admin-ui/public/logo.png admin-ui/public/logo-small.png admin-ui/src/components/layout/Sidebar.tsx admin-ui/src/components/layout/Topbar.tsx admin-ui/src/components/layout/AdminLayout.tsx
git commit -m "feat(admin-ui): AdminLayout chrome — grouped sidebar (collapse/drawer) + top bar

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Static fixtures + placeholder screens for the backend-less sections

Each backend-less section is a themed page rendering the design's example data. Build ONE fully (Reports, below) as the pattern, then create the rest the same way, taking each screen's exact content/columns from `Admin Mid-fi HU.dc.html`. Keep every screen a single focused file under `pages/<section>/`.

**Files:**
- Create: `admin-ui/src/data/reports.ts` (+ siblings: `products.ts`, `categories.ts`, `coupons.ts`, `customers.ts`, `shipping.ts`, `instructors.ts`, `pages.ts`)
- Create: `admin-ui/src/pages/reports/index.tsx` (+ the other section pages)

- [ ] **Step 1: Reports fixture (the pattern for static data)**

Create `admin-ui/src/data/reports.ts`:
```ts
// Static example data mirroring the design's Reports screen. Replaced by a real
// reporting backend in a later pass; shape kept simple and swappable.
export const REPORT_KPIS = [
  { key: "sales", labelKey: "reports:salesRange", value: "1 240 000 Ft" },
  { key: "orders", labelKey: "reports:orders", value: "184" },
  { key: "aov", labelKey: "reports:aov", value: "6 740 Ft" },
  { key: "refunds", labelKey: "reports:refunds", value: "3" },
];
export const SALES_OVER_TIME = [12, 18, 9, 22, 30, 17, 25, 28, 14, 20, 26, 31];
export const TOP_PRODUCTS = [
  { name: "Annie Sloan Chalk Paint – Athenian Black", sold: 42, revenue: "176 400 Ft" },
  { name: "Fusion Mineral Paint – Coal Black", sold: 31, revenue: "120 900 Ft" },
  { name: "Polyvine Wax Finish – Dead Flat", sold: 27, revenue: "94 500 Ft" },
];
```

- [ ] **Step 2: Reports page (the pattern for a static screen)**

Create `admin-ui/src/pages/reports/index.tsx`:
```tsx
import { Card, Col, Row, Table } from "antd";
import { useTranslation } from "react-i18next";
import { PageHeader } from "../../components/ui/PageHeader";
import { PillButton } from "../../components/ui/PillButton";
import { REPORT_KPIS, SALES_OVER_TIME, TOP_PRODUCTS } from "../../data/reports";

export function Reports() {
  const { t } = useTranslation();
  const max = Math.max(...SALES_OVER_TIME);
  return (
    <div>
      <PageHeader title={t("nav:reports")} actions={<PillButton variant="secondary">{t("reports:export")}</PillButton>} />
      <Row gutter={16}>
        {REPORT_KPIS.map((k) => (
          <Col key={k.key} xs={12} md={6}>
            <Card><div style={{ color: "var(--muted)", fontSize: 12 }}>{t(k.labelKey)}</div>
              <div style={{ fontSize: 22, fontWeight: 700 }}>{k.value}</div></Card>
          </Col>
        ))}
      </Row>
      <Card style={{ marginTop: 16 }} title={t("reports:salesOverTime")}>
        <div style={{ display: "flex", alignItems: "flex-end", gap: 6, height: 160 }}>
          {SALES_OVER_TIME.map((v, i) => (
            <div key={i} style={{ flex: 1, height: `${(v / max) * 100}%`, background: "var(--accent)", borderRadius: 2 }} />
          ))}
        </div>
      </Card>
      <Card style={{ marginTop: 16 }} title={t("reports:topProducts")}>
        <Table dataSource={TOP_PRODUCTS} rowKey="name" pagination={false}
          columns={[
            { title: t("reports:product"), dataIndex: "name" },
            { title: t("reports:sold"), dataIndex: "sold", width: 100 },
            { title: t("reports:revenue"), dataIndex: "revenue", width: 140 },
          ]} />
      </Card>
    </div>
  );
}
```
Add a `reports` namespace: create `admin-ui/src/i18n/hu/reports.json` and `en/reports.json` with keys `salesRange, orders, aov, refunds, export, salesOverTime, topProducts, product, sold, revenue` (HU + EN values), and register the namespace in `i18n/index.ts` (`reports: reportsJson` under each language, add `"reports"` to `ns`).

- [ ] **Step 3: Build the remaining static screens the same way**

For each of the following, create a `data/<x>.ts` fixture (content taken from `Admin Mid-fi HU.dc.html`) and a `pages/<x>/index.tsx` that renders a `PageHeader` + the screen's table/cards using antd `Table`/`Card`, `StatusPill` for status columns, and a `<section>` i18n namespace. Take exact columns from the design:
  - **Products** (`/products`): grid/list of products (name, category, price, stock, status pill). Grid/List toggle may be a static toggle in Pass 1.
  - **Categories** (`/products/categories`): name, slug, product-count, status.
  - **Coupons** (`/coupons`): code, type, value, usage, expires, status pill.
  - **Customers** (`/customers`): name, email, orders, spent, last, group.
  - **Shipping** (`/shipping`): queue rows (order, customer, items, destination, carrier) with tabs To pack/Ready/Shipped/Pickup (static tab state).
  - **Instructors** (`/workshops/instructors`): name, specialty, workshop-count, status.
  - **Pages** (`/pages`): title, url, updated, status.
  - **Settings** (`/settings`): left sub-nav + a General tab form (shop name, contact, address, currency HUF, timezone) rendered with `InlinePageScaffold`; other tabs render their static content (see design). Sub-routes `/settings/:tab`.
Each screen: one file, `PageHeader` title from its nav key, antd table/cards, static fixture. No data-provider wiring.

- [ ] **Step 4: Build + commit**

Run: `npm run build` → expect success (and `npm run test` still green).
```bash
git add admin-ui/src/data admin-ui/src/pages/reports admin-ui/src/pages/products admin-ui/src/pages/coupons admin-ui/src/pages/customers admin-ui/src/pages/shipping admin-ui/src/pages/pages admin-ui/src/pages/settings admin-ui/src/i18n
git commit -m "feat(admin-ui): static placeholder screens for backend-less sections (design example data)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Wire it all in App.tsx + re-skin backed pages + auth

**Files:**
- Modify: `admin-ui/src/App.tsx`
- Modify: existing `pages/orders/*`, `pages/workshops/*`, `pages/dashboard/index.tsx`, `pages/login.tsx`
- Delete: `admin-ui/src/components/layout/Header.tsx`, `admin-ui/src/components/layout/Title.tsx`

- [ ] **Step 1: Replace the layout + register all resources/routes in `App.tsx`**

Rewrite `admin-ui/src/App.tsx` so that: (a) the i18nProvider is passed to `<Refine>`; (b) `ThemedLayoutV2` is replaced by `<AdminLayout>`; (c) every section is a resource + route. Concretely:
- Add imports: `import { i18nProvider } from "./i18n";`, `import { AdminLayout } from "./components/layout/AdminLayout";`, and the new page components (Reports, Products, Categories, Coupons, Customers, Shipping, Instructors, Pages, Settings).
- Remove imports of `ThemedLayoutV2`, `Header`, `Title`.
- Pass `i18nProvider={i18nProvider}` on `<Refine>`.
- Replace the protected layout element:
```tsx
  <Authenticated key="protected" fallback={<CatchAllNavigate to="/login" />}>
    <AdminLayout>
      <Outlet />
    </AdminLayout>
  </Authenticated>
```
- Extend `resources={[...]}` to include all sections (dashboard, orders, products [+ meta parent], coupons, customers, shipping, workshops [+ instructors], bookings, reports, pages, settings) with their `list` routes and i18n `meta.label` keys.
- Add the routes inside the protected `<Route>`:
```tsx
  <Route index element={<Dashboard />} />
  <Route path="/orders"><Route index element={<OrderList />} /><Route path="show/:id" element={<OrderShow />} /></Route>
  <Route path="/products"><Route index element={<Products />} /><Route path="categories" element={<Categories />} /></Route>
  <Route path="/coupons" element={<Coupons />} />
  <Route path="/customers" element={<Customers />} />
  <Route path="/shipping" element={<Shipping />} />
  <Route path="/workshops"><Route index element={<WorkshopList />} /><Route path="create" element={<WorkshopCreate />} /><Route path="edit/:id" element={<WorkshopEdit />} /><Route path="instructors" element={<Instructors />} /></Route>
  <Route path="/bookings" element={<Bookings />} />
  <Route path="/reports" element={<Reports />} />
  <Route path="/pages" element={<Pages />} />
  <Route path="/settings"><Route index element={<Settings />} /><Route path=":tab" element={<Settings />} /></Route>
  <Route path="*" element={<ErrorComponent />} />
```
  (`Bookings` is a static placeholder page in Pass 1 even though a backend exists — its depth pass wires the real list; create a minimal `pages/bookings/index.tsx` placeholder now.)

- [ ] **Step 2: Delete the obsolete chrome files**

```bash
git rm admin-ui/src/components/layout/Header.tsx admin-ui/src/components/layout/Title.tsx
```
Ensure no remaining imports reference them (App.tsx updated in Step 1).

- [ ] **Step 3: Re-skin the backed pages to the new primitives**

In `pages/orders/list.tsx`, `pages/orders/show.tsx`, `pages/workshops/list.tsx`, `pages/dashboard/index.tsx`: replace ad-hoc headers with `<PageHeader>`, status columns with `<StatusPill>`, and wrap list bodies in `<DataState>` driven by the Refine query (`isLoading`/`isError`/row count). Keep their existing real-data wiring (Refine hooks / dataProvider) intact — this step is visual only. Dashboard greeting uses `t()` and the 25px title.

- [ ] **Step 4: Re-skin login to the branded full-screen auth**

In `pages/login.tsx`: render a centered branded card (logo, email, password, forgot link, a disabled "Passkey" button per the design), using `PillButton` and the theme vars. Keep the existing `authProvider.login` call. (Password reset / add-passkey screens are deferred to the Auth depth pass.)

- [ ] **Step 5: Full build + tests**

Run: `npm run build` → expect `tsc --noEmit` + `vite build` success.
Run: `npm run test` → expect all unit tests green (palette, i18n, navState, tone, dataState, plus existing mode/providers).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(admin-ui): AdminLayout + all section routes; re-skin orders/workshops/dashboard/login

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage:** stack mapping → T1/T2; tokens → T1/T2; i18n → T3; chrome/nav → T4/T6; shared primitives → T5; routing & full section inventory → T7/T8; backed pages re-skin → T8; auth login → T8 (reset/passkey deferred per spec). The three popups, Orders bulk/filters, products variants, workshop calendar, etc. are explicitly **depth-pass** scope (spec §8), not Pass 1.
- **Placeholder scan:** Task 7 Step 3 intentionally specifies a repeatable screen pattern (one full worked example in Steps 1–2) rather than hand-coding 8 near-identical static pages — the design file is the per-screen visual source of truth, as the spec mandates. All framework-level code (theme, i18n, layout, primitives, routing) is given in full. No TBD/TODO left in infra tasks.
- **Type consistency:** `paletteFor`/`Palette`/`SEMANTIC`/`PILL_LIGHT`/`PILL_DARK` (T1) are consumed by `tokens.ts` (T2), `StatusPill`/`PillButton` (T5). `toneFor`/`Tone` (T5) used by `StatusPill`. `selectDataState`/`DataStateKind` (T5) used by `DataState`. `NAV`/`NavItem`/`SETTINGS_ITEM` (T4) used by `navState.ts` + `Sidebar` (T6). `i18n`/`i18nProvider`/`LANG_KEY` (T3) used by `Topbar` (T6) + `App.tsx` (T8). Logo paths `/admin/logo*.png` match the `basename="/admin"` in App.tsx and `public/`.

## Notes for the executor
- After Pass 1, manually verify in the browser: light/dark toggle, sidebar collapse + <880px drawer, HU/EN switch flips nav labels, every nav entry routes without error, backed pages still show real data.
- The backend is not touched in Pass 1; do not run `mvn` here. The gate is `npm run build` + `npm run test` in `admin-ui/`.
- Depth passes (Orders, Workshops/Bookings, Products, mocked-module polish, Auth) are separate plans per the spec.
