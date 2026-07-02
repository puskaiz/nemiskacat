import { useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { SHIPMENTS } from "../../data/shipping";

// Faithful 1:1 port of the prototype Fulfilment / Shipping screen
// (Admin Mid-fi HU.dc.html, sec.shipping block, lines 509-535): header
// (title + carrier filter chip), tab row (To pack / Ready / Shipped / Pickup),
// the "3 selected" bulk bar, the table and the integrations footnote. Static
// example data; the prototype has no detail sub-view here.

const carrierChip: CSSProperties = { border: "1px solid var(--border)", borderRadius: 999, padding: "7px 14px", fontSize: 13, color: "var(--muted)", background: "var(--surface)" };
const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const avatar: CSSProperties = { width: 28, height: 28, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flex: "none" };
const bulkBar: CSSProperties = { background: "var(--accent-soft)", border: "1px solid var(--accent-soft)", borderRadius: 2, padding: "11px 16px", display: "flex", alignItems: "center", gap: 14, fontSize: 13 };
const bulkBtn: CSSProperties = { border: "1px solid var(--accent-soft)", borderRadius: 3, padding: "6px 13px", background: "var(--surface)", fontWeight: 600, color: "var(--accent-fg)" };
const printPill: CSSProperties = { border: "1px solid var(--accent-soft)", color: "var(--accent-fg)", background: "var(--accent-soft)", borderRadius: 2, padding: "3px 11px", fontSize: 12, fontWeight: 600 };

const TABS: { key: string; labelKey: string; count: number }[] = [
  { key: "toPack", labelKey: "shipping:toPack", count: 9 },
  { key: "ready", labelKey: "shipping:ready", count: 4 },
  { key: "shipped", labelKey: "shipping:shipped", count: 63 },
  { key: "pickup", labelKey: "shipping:pickup", count: 2 },
];

export function Shipping() {
  const { t } = useTranslation();
  const [tab, setTab] = useState("toPack");

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("shipping:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("shipping:subtitle")}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={carrierChip}>{t("shipping:carrierFilter")} ▾</div>
      </div>

      {/* tab filters */}
      <div style={{ display: "flex", gap: 24, fontSize: 14, borderBottom: "1px solid var(--border)" }}>
        {TABS.map((tb) => {
          const isActive = tab === tb.key;
          return (
            <div key={tb.key} onClick={() => setTab(tb.key)} style={{ paddingBottom: 11, cursor: "pointer", borderBottom: isActive ? "2px solid var(--accent)" : "2px solid transparent", fontWeight: isActive ? 600 : 400, color: isActive ? "var(--text)" : "var(--muted)" }}>
              {t(tb.labelKey)} <span style={{ color: "var(--faint)", fontWeight: isActive ? 500 : 400 }}>{tb.count}</span>
            </div>
          );
        })}
      </div>

      {/* selection / bulk bar */}
      <div style={bulkBar}>
        <span style={{ display: "inline-block", width: 15, height: 15, border: "1.6px solid var(--accent)", borderRadius: 4 }} />
        <span style={{ color: "var(--accent-fg)", fontWeight: 600 }}>{t("shipping:selected", { count: 3 })}</span>
        <div style={bulkBtn}>{t("shipping:printLabels")}</div>
        <div style={bulkBtn}>{t("shipping:printDelivery")}</div>
        <div style={{ borderRadius: 3, padding: "6px 13px", background: "var(--accent)", color: "#fff", fontWeight: 600 }}>{t("shipping:markShipped")}</div>
      </div>

      {/* table */}
      <div data-table style={card}>
        <div style={{ display: "flex", fontSize: 12, color: "var(--faint)", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", fontWeight: 600, background: "var(--th)" }}>
          <div style={{ width: 16 }} />
          <div style={{ width: 56 }}>{t("shipping:order")}</div>
          <div style={{ flex: 1 }}>{t("shipping:customer")}</div>
          <div style={{ width: 48, textAlign: "center" }}>{t("shipping:items")}</div>
          <div style={{ flex: 1 }}>{t("shipping:destination")}</div>
          <div style={{ width: 120 }}>{t("shipping:method")}</div>
          <div style={{ width: 84 }}>{t("shipping:label")}</div>
        </div>
        {SHIPMENTS.map((s) => (
          <div key={s.id} style={{ display: "flex", fontSize: 14, padding: "13px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center" }}>
            <div style={{ width: 16 }}><span style={{ display: "inline-block", width: 15, height: 15, border: "1.6px solid #cbd2da", borderRadius: 4 }} /></div>
            <div style={{ width: 56, fontWeight: 600 }}>{s.id}</div>
            <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 9 }}><span style={avatar}>{s.initial}</span>{s.name}</div>
            <div style={{ width: 48, textAlign: "center", color: "var(--muted)" }}>{s.items}</div>
            <div style={{ flex: 1, color: "var(--muted)" }}>{s.city}</div>
            <div style={{ width: 120, color: "var(--muted)" }}>{s.method}</div>
            <div style={{ width: 84 }}><span style={printPill}>{t("shipping:print")}</span></div>
          </div>
        ))}
      </div>

      <div style={{ fontSize: 13, color: "var(--faint)" }}>{t("shipping:footnote")}</div>
    </div>
  );
}
