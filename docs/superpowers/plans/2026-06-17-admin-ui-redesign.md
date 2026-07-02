# Admin UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the `admin-ui/` Refine + Ant Design SPA to a clean, modern, refined-neutral SaaS look with a persisted, OS-aware light/dark mode toggle.

**Architecture:** A small theme layer (Ant Design design tokens + a mode-owning React context) feeds `ConfigProvider`, so every existing Refine/AntD component inherits the new look and dark mode for free. Custom layout chrome (Title + Header with toggle and user menu) plugs into `ThemedLayoutV2`'s slots. A new dashboard home and a branded login page replace the bare redirect and stock `AuthPage`. No backend or business-logic changes.

**Tech Stack:** React 18, TypeScript, Refine (`@refinedev/core`, `@refinedev/antd`), Ant Design 5, Vite, Vitest. No new dependencies.

## Global Constraints

- No new dependencies/frameworks — work only within the installed Refine + Ant Design stack (CLAUDE.md). Do not import `@ant-design/icons` directly (not a declared dependency).
- No new backend / `/api/admin` endpoints — derive dashboard numbers from existing list endpoints via `X-Total-Count` + status filters.
- No business-logic changes — order transitions, bulk transitions, refund, and workshop CRUD keep current behavior.
- All UI copy in Hungarian; code, comments, commits in English (CLAUDE.md §10).
- Money in HUF minor unit semantics unchanged; display helper `huf(n)` stays as-is.
- Build gate: `cd admin-ui && npm run build` (`tsc --noEmit && vite build`) must pass; existing `src/providers/providers.test.ts` must stay green.

---

### Task 1: Theme foundation (tokens, mode resolver, provider) wired into App

**Files:**
- Create: `admin-ui/src/theme/mode.ts`
- Create: `admin-ui/src/theme/tokens.ts`
- Create: `admin-ui/src/theme/ThemeProvider.tsx`
- Test: `admin-ui/src/theme/mode.test.ts`
- Modify: `admin-ui/src/App.tsx`

**Interfaces:**
- Produces:
  - `mode.ts`: `type ThemeMode = "light" | "dark"`; `const STORAGE_KEY = "admin-theme"`; `function resolveInitialMode(stored: string | null, prefersDark: boolean): ThemeMode`.
  - `tokens.ts`: `function buildTheme(mode: ThemeMode): ThemeConfig`; `function surfaceBg(mode: ThemeMode): string`.
  - `ThemeProvider.tsx`: `const ThemeProvider: React.FC<{ children: ReactNode }>`; `function useThemeMode(): { mode: ThemeMode; toggle: () => void; setMode: (m: ThemeMode) => void }`.

- [ ] **Step 1: Write the failing test for the mode resolver**

Create `admin-ui/src/theme/mode.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { resolveInitialMode } from "./mode";

describe("resolveInitialMode", () => {
  it("uses the stored value when it is a valid mode", () => {
    expect(resolveInitialMode("dark", false)).toBe("dark");
    expect(resolveInitialMode("light", true)).toBe("light");
  });

  it("falls back to the OS preference when nothing is stored", () => {
    expect(resolveInitialMode(null, true)).toBe("dark");
    expect(resolveInitialMode(null, false)).toBe("light");
  });

  it("ignores an unrecognized stored value and uses the OS preference", () => {
    expect(resolveInitialMode("purple", true)).toBe("dark");
    expect(resolveInitialMode("", false)).toBe("light");
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd admin-ui && npx vitest run src/theme/mode.test.ts`
Expected: FAIL — cannot resolve `./mode` / `resolveInitialMode is not a function`.

- [ ] **Step 3: Implement the mode resolver**

Create `admin-ui/src/theme/mode.ts`:

```ts
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd admin-ui && npx vitest run src/theme/mode.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Implement the design tokens**

Create `admin-ui/src/theme/tokens.ts`:

```ts
import { theme as antdTheme, type ThemeConfig } from "antd";
import type { ThemeMode } from "./mode";

// Refined-neutral SaaS look: grayscale surfaces, one calm indigo accent,
// rounded corners, soft borders. Light/dark share these; the algorithm and a
// few surface colors differ per mode.
const sharedToken = {
  colorPrimary: "#4f46e5",
  borderRadius: 8,
  controlHeight: 36,
  fontFamily:
    '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
};

export function surfaceBg(mode: ThemeMode): string {
  return mode === "dark" ? "#0e1116" : "#f6f7f9";
}

export function buildTheme(mode: ThemeMode): ThemeConfig {
  const isDark = mode === "dark";
  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      ...sharedToken,
      colorBgLayout: surfaceBg(mode),
      ...(isDark
        ? { colorBgContainer: "#171a21", colorBorderSecondary: "#262b36" }
        : { colorBgContainer: "#ffffff", colorBorderSecondary: "#eceef1" }),
    },
    components: {
      Layout: {
        siderBg: isDark ? "#12151b" : "#ffffff",
        headerBg: isDark ? "#12151b" : "#ffffff",
        bodyBg: surfaceBg(mode),
      },
      Menu: {
        itemBg: "transparent",
        itemSelectedBg: isDark ? "#1e2330" : "#eef0fb",
        itemBorderRadius: 8,
      },
      Card: {
        borderRadiusLG: 12,
      },
    },
  };
}
```

- [ ] **Step 6: Implement the ThemeProvider**

Create `admin-ui/src/theme/ThemeProvider.tsx`:

```tsx
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { ConfigProvider } from "antd";
import { buildTheme, surfaceBg } from "./tokens";
import { STORAGE_KEY, resolveInitialMode, type ThemeMode } from "./mode";

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

  // Keep the page background in sync so there is no flash behind the layout.
  useEffect(() => {
    document.body.style.backgroundColor = surfaceBg(mode);
  }, [mode]);

  return (
    <ThemeModeContext.Provider value={value}>
      <ConfigProvider theme={buildTheme(mode)}>{children}</ConfigProvider>
    </ThemeModeContext.Provider>
  );
};
```

- [ ] **Step 7: Wire the provider into App.tsx**

In `admin-ui/src/App.tsx`:

Replace the imports block. Remove `RefineThemes` from the `@refinedev/antd` import and remove `ConfigProvider` from the `antd` import, then add the ThemeProvider import.

Change:
```tsx
import {
  AuthPage,
  ErrorComponent,
  RefineThemes,
  ThemedLayoutV2,
  useNotificationProvider,
} from "@refinedev/antd";
```
to:
```tsx
import {
  AuthPage,
  ErrorComponent,
  ThemedLayoutV2,
  useNotificationProvider,
} from "@refinedev/antd";
```

Change:
```tsx
import { App as AntdApp, ConfigProvider } from "antd";
```
to:
```tsx
import { App as AntdApp } from "antd";
import { ThemeProvider } from "./theme/ThemeProvider";
```

Then change the wrapper. Replace:
```tsx
  <BrowserRouter basename="/admin">
    <ConfigProvider theme={RefineThemes.Blue}>
      <AntdApp>
```
with:
```tsx
  <BrowserRouter basename="/admin">
    <ThemeProvider>
      <AntdApp>
```

And the matching closing tags. Replace:
```tsx
      </AntdApp>
    </ConfigProvider>
  </BrowserRouter>
```
with:
```tsx
      </AntdApp>
    </ThemeProvider>
  </BrowserRouter>
```

- [ ] **Step 8: Verify the build passes**

Run: `cd admin-ui && npm run build`
Expected: `tsc --noEmit` clean, `vite build` succeeds, no errors.

- [ ] **Step 9: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add admin-ui/src/theme admin-ui/src/App.tsx
git commit -m "feat(admin-ui): indigo design-token theme + OS-aware dark mode provider

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Custom layout chrome (Title + Header with theme toggle and user menu)

**Files:**
- Create: `admin-ui/src/components/layout/Title.tsx`
- Create: `admin-ui/src/components/layout/Header.tsx`
- Modify: `admin-ui/src/App.tsx`

**Interfaces:**
- Consumes: `useThemeMode()` from `src/theme/ThemeProvider` (Task 1); `authProvider.logout` via Refine's `useLogout`; identity via Refine's `useGetIdentity` (returns `{ name }`, where `name` is the admin email — see `authProvider.getIdentity`).
- Produces: `const Title: React.FC<{ collapsed: boolean }>`; `const Header: React.FC` — both passed to `ThemedLayoutV2` as the `Title` and `Header` props.

- [ ] **Step 1: Implement the brand Title**

Create `admin-ui/src/components/layout/Title.tsx`:

```tsx
import { Link } from "react-router-dom";

// Sider brand block. ThemedLayoutV2 passes `collapsed`; hide the wordmark when
// the sider is collapsed.
export const Title = ({ collapsed }: { collapsed: boolean }) => (
  <Link
    to="/"
    style={{
      display: "flex",
      alignItems: "center",
      gap: 10,
      padding: "0 8px",
      textDecoration: "none",
      color: "inherit",
    }}
  >
    <span style={{ fontSize: 22, lineHeight: 1 }}>🐱</span>
    {!collapsed && <span style={{ fontWeight: 700, fontSize: 16 }}>Nemiskacat</span>}
  </Link>
);
```

- [ ] **Step 2: Implement the Header**

Create `admin-ui/src/components/layout/Header.tsx`:

```tsx
import { useGetIdentity, useLogout } from "@refinedev/core";
import { Breadcrumb } from "@refinedev/antd";
import { Avatar, Dropdown, Layout, Space, Switch, theme } from "antd";
import { useThemeMode } from "../../theme/ThemeProvider";

const { Header: AntHeader } = Layout;

export const Header = () => {
  const { mode, toggle } = useThemeMode();
  const { data: identity } = useGetIdentity<{ name?: string }>();
  const { mutate: logout } = useLogout();
  const { token } = theme.useToken();

  const name = identity?.name ?? "—";

  return (
    <AntHeader
      style={{
        position: "sticky",
        top: 0,
        zIndex: 10,
        height: 56,
        padding: "0 24px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        background: token.colorBgContainer,
        borderBottom: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <Breadcrumb />
      <Space size="middle">
        <Switch
          checked={mode === "dark"}
          onChange={toggle}
          checkedChildren="🌙"
          unCheckedChildren="☀️"
          aria-label="Sötét mód"
        />
        <Dropdown
          trigger={["click"]}
          menu={{
            items: [
              { key: "user", label: name, disabled: true },
              { type: "divider" },
              { key: "logout", label: "Kijelentkezés", onClick: () => logout() },
            ],
          }}
        >
          <Space style={{ cursor: "pointer" }}>
            <Avatar size="small" style={{ backgroundColor: token.colorPrimary }}>
              {name.charAt(0).toUpperCase()}
            </Avatar>
          </Space>
        </Dropdown>
      </Space>
    </AntHeader>
  );
};
```

- [ ] **Step 3: Wire Title + Header into ThemedLayoutV2**

In `admin-ui/src/App.tsx`, add imports near the other local imports:
```tsx
import { Header } from "./components/layout/Header";
import { Title } from "./components/layout/Title";
```

Change:
```tsx
                  <ThemedLayoutV2>
                    <Outlet />
                  </ThemedLayoutV2>
```
to:
```tsx
                  <ThemedLayoutV2 Header={Header} Title={Title}>
                    <Outlet />
                  </ThemedLayoutV2>
```

- [ ] **Step 4: Verify the build passes**

Run: `cd admin-ui && npm run build`
Expected: clean type-check and successful build.

- [ ] **Step 5: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add admin-ui/src/components/layout admin-ui/src/App.tsx
git commit -m "feat(admin-ui): custom sider title + header with dark-mode toggle and user menu

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Dashboard home

**Files:**
- Create: `admin-ui/src/pages/dashboard/index.tsx`
- Modify: `admin-ui/src/App.tsx`

**Interfaces:**
- Consumes: Refine `useList` against `orders` and `workshops` resources (existing dataProvider returns `{ data, total }`, total from `X-Total-Count`); `STATUS_COLORS`, `STATUS_LABELS` from `src/api/orders`; `OrderStatus`, `OrderSummary` from `src/types`.
- Produces: `const Dashboard: React.FC` rendered at the index route; a `dashboard` Refine resource with `list: "/"` for the sider entry.

- [ ] **Step 1: Implement the Dashboard page**

Create `admin-ui/src/pages/dashboard/index.tsx`:

```tsx
import { useList } from "@refinedev/core";
import { Card, Col, Row, Statistic, Table, Tag, Typography } from "antd";
import { Link } from "react-router-dom";
import type { OrderStatus, OrderSummary } from "../../types";
import { STATUS_COLORS, STATUS_LABELS } from "../../api/orders";

const huf = (n: number) => `${n.toLocaleString("hu-HU")} Ft`;

export const Dashboard = () => {
  const recent = useList<OrderSummary>({
    resource: "orders",
    pagination: { current: 1, pageSize: 6 },
  });
  const newOrders = useList<OrderSummary>({
    resource: "orders",
    pagination: { current: 1, pageSize: 1 },
    filters: [{ field: "status", operator: "eq", value: "NEW" }],
  });
  const paidOrders = useList<OrderSummary>({
    resource: "orders",
    pagination: { current: 1, pageSize: 1 },
    filters: [{ field: "status", operator: "eq", value: "PAID" }],
  });
  const workshops = useList({
    resource: "workshops",
    pagination: { current: 1, pageSize: 1 },
  });

  const totalOrders = recent.data?.total ?? 0;
  const needsAction = (newOrders.data?.total ?? 0) + (paidOrders.data?.total ?? 0);
  const workshopCount = workshops.data?.total ?? 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Áttekintés
      </Typography.Title>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={8}>
          <Card>
            <Statistic title="Összes rendelés" value={totalOrders} loading={recent.isLoading} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card>
            <Statistic
              title="Teendő (új + fizetve)"
              value={needsAction}
              loading={newOrders.isLoading || paidOrders.isLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card>
            <Statistic title="Workshopok" value={workshopCount} loading={workshops.isLoading} />
          </Card>
        </Col>
      </Row>

      <Card title="Legutóbbi rendelések">
        <Table
          dataSource={recent.data?.data}
          loading={recent.isLoading}
          rowKey="id"
          pagination={false}
          locale={{ emptyText: "Nincs rendelés" }}
        >
          <Table.Column<OrderSummary>
            dataIndex="orderNumber"
            title="Rendelésszám"
            render={(v: string, record) => <Link to={`/orders/show/${record.id}`}>{v}</Link>}
          />
          <Table.Column<OrderSummary>
            dataIndex="status"
            title="Státusz"
            render={(s: OrderStatus) => <Tag color={STATUS_COLORS[s]}>{STATUS_LABELS[s]}</Tag>}
          />
          <Table.Column dataIndex="customerName" title="Vásárló" />
          <Table.Column dataIndex="totalGrossHuf" title="Összeg" render={(n: number) => huf(n)} />
          <Table.Column
            dataIndex="createdAt"
            title="Létrehozva"
            render={(v: string) => new Date(v).toLocaleString("hu-HU")}
          />
        </Table>
      </Card>
    </div>
  );
};
```

- [ ] **Step 2: Add the dashboard resource and index route**

In `admin-ui/src/App.tsx`, add the import:
```tsx
import { Dashboard } from "./pages/dashboard";
```

Add a `dashboard` entry as the FIRST item in the `resources` array:
```tsx
          resources={[
            {
              name: "dashboard",
              list: "/",
              meta: { label: "Áttekintés" },
            },
            {
              name: "workshops",
```

Change the protected index route. Replace:
```tsx
              <Route index element={<NavigateToResource resource="workshops" />} />
```
with:
```tsx
              <Route index element={<Dashboard />} />
```

(Leave the public-route `<NavigateToResource resource="workshops" />` and its import unchanged.)

- [ ] **Step 3: Verify the build passes**

Run: `cd admin-ui && npm run build`
Expected: clean type-check and successful build.

- [ ] **Step 4: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add admin-ui/src/pages/dashboard admin-ui/src/App.tsx
git commit -m "feat(admin-ui): dashboard home with KPI cards and recent orders

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Branded login page

**Files:**
- Create: `admin-ui/src/pages/login.tsx`
- Modify: `admin-ui/src/App.tsx`

**Interfaces:**
- Consumes: Refine `useLogin` (drives `authProvider.login`, which returns `{ success, redirectTo }` or an error surfaced via the notification provider).
- Produces: `const Login: React.FC` rendered at `/login`.

- [ ] **Step 1: Implement the login page**

Create `admin-ui/src/pages/login.tsx`:

```tsx
import { useLogin } from "@refinedev/core";
import { Button, Card, Form, Input, Layout, Typography } from "antd";

interface LoginVars {
  email: string;
  password: string;
}

export const Login = () => {
  const { mutate: login, isLoading } = useLogin<LoginVars>();

  return (
    <Layout style={{ minHeight: "100vh", display: "grid", placeItems: "center", padding: 24 }}>
      <Card style={{ width: 380, maxWidth: "100%" }}>
        <div style={{ textAlign: "center", marginBottom: 24 }}>
          <div style={{ fontSize: 32, lineHeight: 1 }}>🐱</div>
          <Typography.Title level={4} style={{ marginBottom: 0 }}>
            Nemiskacat admin
          </Typography.Title>
          <Typography.Text type="secondary">Jelentkezz be a folytatáshoz</Typography.Text>
        </div>
        <Form layout="vertical" requiredMark={false} onFinish={(values: LoginVars) => login(values)}>
          <Form.Item label="Email" name="email" rules={[{ required: true, type: "email" }]}>
            <Input autoComplete="username" size="large" />
          </Form.Item>
          <Form.Item label="Jelszó" name="password" rules={[{ required: true }]}>
            <Input.Password autoComplete="current-password" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={isLoading}>
            Bejelentkezés
          </Button>
        </Form>
      </Card>
    </Layout>
  );
};
```

- [ ] **Step 2: Wire the login route**

In `admin-ui/src/App.tsx`:

Remove `AuthPage` from the `@refinedev/antd` import (so it becomes `ErrorComponent, ThemedLayoutV2, useNotificationProvider`).

Add the import:
```tsx
import { Login } from "./pages/login";
```

Replace the `/login` route element. Change:
```tsx
              <Route
                path="/login"
                element={
                  <AuthPage
                    type="login"
                    title="Nemiskacat admin"
                    registerLink={false}
                    forgotPasswordLink={false}
                    rememberMe={false}
                  />
                }
              />
```
to:
```tsx
              <Route path="/login" element={<Login />} />
```

- [ ] **Step 3: Verify the build passes**

Run: `cd admin-ui && npm run build`
Expected: clean type-check (no unused `AuthPage`), successful build.

- [ ] **Step 4: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add admin-ui/src/pages/login.tsx admin-ui/src/App.tsx
git commit -m "feat(admin-ui): branded login page replacing stock AuthPage

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Table empty states and density polish

**Files:**
- Modify: `admin-ui/src/pages/orders/list.tsx`
- Modify: `admin-ui/src/pages/workshops/list.tsx`
- Modify: `admin-ui/src/pages/orders/show.tsx`

**Interfaces:**
- Consumes: existing page components only. No new exports. Pure presentation tweaks — no logic touched.

- [ ] **Step 1: Add an empty state to the orders list table**

In `admin-ui/src/pages/orders/list.tsx`, change:
```tsx
      <Table
        {...tableProps}
        rowKey="id"
        rowSelection={{ selectedRowKeys: selected, onChange: setSelected }}
      >
```
to:
```tsx
      <Table
        {...tableProps}
        rowKey="id"
        rowSelection={{ selectedRowKeys: selected, onChange: setSelected }}
        locale={{ emptyText: "Nincs a szűrőnek megfelelő rendelés" }}
      >
```

- [ ] **Step 2: Add an empty state to the workshops list table**

In `admin-ui/src/pages/workshops/list.tsx`, change:
```tsx
      <Table {...tableProps} rowKey="id">
```
to:
```tsx
      <Table {...tableProps} rowKey="id" locale={{ emptyText: "Még nincs workshop" }}>
```

- [ ] **Step 3: Add an empty state to the order detail line-items table**

In `admin-ui/src/pages/orders/show.tsx`, change:
```tsx
          <Table dataSource={order.lines} rowKey={(_, i) => String(i)} pagination={false} size="small">
```
to:
```tsx
          <Table
            dataSource={order.lines}
            rowKey={(_, i) => String(i)}
            pagination={false}
            size="small"
            locale={{ emptyText: "Nincs tétel" }}
          >
```

- [ ] **Step 4: Verify the build passes**

Run: `cd admin-ui && npm run build`
Expected: clean type-check and successful build.

- [ ] **Step 5: Run the full admin-ui test suite**

Run: `cd admin-ui && npm test`
Expected: all tests pass (existing `providers.test.ts` + `theme/mode.test.ts` from Task 1).

- [ ] **Step 6: Commit**

```bash
cd /Users/zolika/Work/Claude/website
git add admin-ui/src/pages/orders/list.tsx admin-ui/src/pages/workshops/list.tsx admin-ui/src/pages/orders/show.tsx
git commit -m "polish(admin-ui): friendly empty states for order and workshop tables

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Notes for the implementer

- The new theme cascades automatically: once Task 1 lands, the orders/workshops tables, forms, and `Descriptions` adopt the indigo light theme and react to dark mode with no per-component edits. Tasks 2–5 layer chrome and surfaces on top.
- Status `Tag` colors (`STATUS_COLORS`) are Ant Design preset names, which auto-adapt to dark mode — leave them as-is.
- Do not introduce `@ant-design/icons`; the dark-mode toggle uses emoji glyphs on an AntD `Switch`, and the avatar uses an initial, to stay within declared dependencies.
- Dev preview (optional, manual): `cd admin-ui && npm run dev` proxies `/api` to `http://localhost:8085`.
