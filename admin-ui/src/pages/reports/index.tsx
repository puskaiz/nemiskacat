import type { CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { REPORT_KPIS, CHART_BARS, TOP_PRODUCTS, SALES_BY_CHANNEL } from "../../data/reports";

// Faithful 1:1 port of the prototype Reports screen (Admin Mid-fi HU.dc.html,
// sec.reports block, lines 538-572): header (title "Riportok" + date-range
// control) + 4 KPI cards + the div-bar "Bevétel az időben" chart + top products
// list + sales-by-channel bars. Static example data; the real reporting backend
// arrives in a later pass.

const redPill: CSSProperties = { border: "1px solid var(--border)", borderRadius: 999, padding: "9px 15px", fontSize: 13, fontWeight: 600, color: "#fff", background: "#C20E1A" };
const kpiCard: CSSProperties = { flex: 1, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, padding: 16, boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const panel: CSSProperties = { background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)", display: "flex", flexDirection: "column" };

export function Reports() {
  const { t } = useTranslation();

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("reports:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("reports:subtitle")}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={redPill}>{t("reports:range")} ▾</div>
        <div style={redPill}>{t("reports:export")}</div>
      </div>

      {/* KPI cards */}
      <div style={{ display: "flex", gap: 16 }}>
        {REPORT_KPIS.map((k) => (
          <div key={k.labelKey} style={kpiCard}>
            <div style={{ fontSize: 13, color: "var(--muted)" }}>{t(k.labelKey)}</div>
            <div style={{ fontSize: 26, fontWeight: 700, marginTop: 5, letterSpacing: "-.5px" }}>{k.value}</div>
            <div style={{ fontSize: 12, color: "#047857", fontWeight: 600, marginTop: 3 }}>{k.delta}</div>
          </div>
        ))}
      </div>

      {/* chart + side panel */}
      <div style={{ display: "flex", gap: 16, alignItems: "stretch" }}>
        {/* sales over time — bar chart drawn with divs */}
        <div style={{ ...panel, flex: 1.7 }}>
          <div style={{ padding: "14px 18px", borderBottom: "1px solid var(--border-soft)", fontSize: 15, fontWeight: 700 }}>{t("reports:salesOverTime")}</div>
          <div style={{ flex: 1, display: "flex", alignItems: "flex-end", gap: 9, padding: 18, minHeight: 230 }}>
            {CHART_BARS.map((h, i) => (
              <div key={i} style={{ flex: 1, background: "var(--accent)", borderRadius: "5px 5px 0 0", height: h, opacity: 0.85 }} />
            ))}
          </div>
        </div>

        {/* top products + sales by channel */}
        <div style={{ ...panel, flex: 1 }}>
          <div style={{ padding: "14px 18px", borderBottom: "1px solid var(--border-soft)", fontSize: 15, fontWeight: 700 }}>{t("reports:topProducts")}</div>
          {TOP_PRODUCTS.map((p, i) => (
            <div
              key={p.name}
              style={{ padding: "11px 18px", borderBottom: i < TOP_PRODUCTS.length - 1 ? "1px solid var(--border-soft)" : undefined, fontSize: 14, display: "flex", justifyContent: "space-between" }}
            >
              <span>{p.name}</span>
              <span style={{ color: "var(--muted)" }}>{p.sold}</span>
            </div>
          ))}
          <div style={{ padding: "13px 18px", borderTop: "1px solid var(--border-soft)", fontSize: 14, fontWeight: 700 }}>{t("reports:byChannel")}</div>
          {SALES_BY_CHANNEL.map((c, i) => (
            <div
              key={c.labelKey}
              style={{ padding: i < SALES_BY_CHANNEL.length - 1 ? "0 18px 9px" : "0 18px 16px", display: "flex", alignItems: "center", gap: 10 }}
            >
              <div style={{ flex: 1, height: 9, background: "#EEF0F3", borderRadius: 5, overflow: "hidden" }}>
                <div style={{ width: `${c.pct}%`, height: "100%", background: "var(--accent)" }} />
              </div>
              <span style={{ fontSize: 12, color: "var(--muted)", width: 90 }}>{t(c.labelKey)} · {c.pct}%</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
