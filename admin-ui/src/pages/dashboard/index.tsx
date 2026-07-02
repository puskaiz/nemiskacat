import type { CSSProperties } from "react";
import { useList, useGetIdentity } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import type { OrderSummary, OrderStatus } from "../../types";
import { STATUS_LABELS } from "../../api/orders";
import { useThemeMode } from "../../theme/ThemeProvider";
import { PILL_DARK, PILL_LIGHT } from "../../theme/palette";
import { toneFor } from "../../components/ui/tone";

// Faithful port of the prototype dashboard (Admin Mid-fi HU.dc.html lines 78-104).
// KPI cards + upcoming workshops are the design's static example data (no
// aggregation backend yet); "Legutóbbi rendelések" is wired to the real orders API.

const huf = (n: number) => `${n.toLocaleString("hu-HU")} Ft`;
const card: CSSProperties = { flex: 1, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, padding: 16, boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const avatar: CSSProperties = { width: 30, height: 30, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flex: "none" };

const KPIS = [
  { labelKey: "dashboard:kpiRevenue", value: "84 200 Ft", delta: "▲ 8% a tegnapihoz", deltaColor: "#047857" },
  { labelKey: "dashboard:kpiNewOrders", value: "12", delta: "4 fizetésre vár", deltaColor: "var(--muted)" },
  { labelKey: "dashboard:kpiSeats", value: "31", delta: "▲ 22% ezen a héten", deltaColor: "#047857" },
  { labelKey: "dashboard:kpiLowStock", value: "5", delta: "utánrendelés kell", deltaColor: "#B45309" },
];
const UPCOMING = [
  { mon: "JUN", day: "21", name: "Kerámia este", info: "8 / 10 hely · 18:00" },
  { mon: "JUN", day: "24", name: "Makramé workshop", info: "10 / 10 · megtelt" },
  { mon: "JUN", day: "28", name: "Akvarell délután", info: "3 / 12 hely · 15:00" },
];

export const Dashboard = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const { mode } = useThemeMode();
  const { data: identity } = useGetIdentity<{ name?: string }>();
  const recent = useList<OrderSummary>({ resource: "orders", pagination: { current: 1, pageSize: 5 } });
  const rows = recent.data?.data ?? [];

  const pill = (status: OrderStatus): CSSProperties => {
    const c = (mode === "dark" ? PILL_DARK : PILL_LIGHT)[toneFor(status)];
    return { color: c.fg, background: c.bg, borderRadius: 3, padding: "2px 9px", fontSize: 12, fontWeight: 600, whiteSpace: "nowrap" };
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 25, fontWeight: 700, letterSpacing: "-.3px" }}>{t("dashboard:greet", { name: identity?.name ?? "Anna" })}</div>
          <div style={{ fontSize: 14, color: "var(--muted)", marginTop: 3 }}>{t("dashboard:subtitle")}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ border: "1px solid var(--border)", borderRadius: 999, padding: "9px 15px", fontSize: 13, fontWeight: 600, color: "#fff", background: "#C20E1A" }}>{t("dashboard:range")} ▾</div>
      </div>

      <div data-stack style={{ display: "flex", gap: 16 }}>
        {KPIS.map((k) => (
          <div key={k.labelKey} style={card}>
            <div style={{ fontSize: 13, color: "var(--muted)" }}>{t(k.labelKey)}</div>
            <div style={{ fontSize: 27, fontWeight: 700, marginTop: 6, letterSpacing: "-.5px" }}>{k.value}</div>
            <div style={{ fontSize: 12, color: k.deltaColor, marginTop: 4, fontWeight: 600 }}>{k.delta}</div>
          </div>
        ))}
      </div>

      <div data-stack style={{ display: "flex", gap: 16, alignItems: "stretch" }}>
        <div style={{ flex: 1.6, minWidth: 280, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, display: "flex", flexDirection: "column", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" }}>
          <div style={{ padding: "14px 18px", borderBottom: "1px solid var(--border-soft)", fontSize: 15, fontWeight: 700, display: "flex", alignItems: "center" }}>
            {t("dashboard:recentOrders")}<span style={{ flex: 1 }} />
            <span style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }} onClick={() => nav("/orders")}>{t("dashboard:all")} →</span>
          </div>
          {rows.map((r) => (
            <div key={r.id} onClick={() => nav(`/orders/show/${r.id}`)} style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 18px", borderBottom: "1px solid var(--border-soft)", fontSize: 14, cursor: "pointer" }}>
              <span style={avatar}>{(r.customerName?.[0] ?? "?").toUpperCase()}</span>
              <div style={{ fontWeight: 600, width: 56 }}>{r.orderNumber}</div>
              <div style={{ flex: 1 }}>{r.customerName}</div>
              <div style={{ fontWeight: 600, width: 84, textAlign: "right" }}>{huf(r.totalGrossHuf)}</div>
              <div style={{ width: 90, display: "flex", justifyContent: "flex-end" }}><span style={pill(r.status)}>{STATUS_LABELS[r.status]}</span></div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, display: "flex", flexDirection: "column", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" }}>
          <div style={{ padding: "14px 18px", borderBottom: "1px solid var(--border-soft)", fontSize: 15, fontWeight: 700 }}>{t("dashboard:upcoming")}</div>
          {UPCOMING.map((w, i) => (
            <div key={i} style={{ padding: "13px 18px", borderBottom: i < UPCOMING.length - 1 ? "1px solid var(--border-soft)" : "none", display: "flex", gap: 12, alignItems: "center" }}>
              <div style={{ width: 44, height: 44, borderRadius: 3, background: "var(--accent-soft)", color: "var(--accent-fg)", textAlign: "center", flex: "none", display: "flex", flexDirection: "column", justifyContent: "center", fontSize: 11, fontWeight: 600, lineHeight: 1.05 }}>{w.mon}<span style={{ fontSize: 16, fontWeight: 700 }}>{w.day}</span></div>
              <div style={{ flex: 1 }}><div style={{ fontSize: 14, fontWeight: 600 }}>{w.name}</div><div style={{ fontSize: 12, color: "var(--muted)" }}>{w.info}</div></div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
