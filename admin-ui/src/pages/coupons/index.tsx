import { useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { StatusPill } from "../../components/ui/StatusPill";
import { Pagination } from "../../components/ui/Pagination";
import { COUPONS } from "../../data/coupons";

// Faithful 1:1 port of the prototype Coupons list (Admin Mid-fi HU.dc.html,
// sec.coupons block, lines 473-506): header (title + "+ Új kupon") + table
// (monospace code / type / value / usage / expiry / status pill) + pagination.
// Static example data; the create/detail sub-view is intentionally out of scope.

const TOTAL = 6;

const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)", cursor: "pointer" };
const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };

export function Coupons() {
  const { t } = useTranslation();
  const [current, setCurrent] = useState(1);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("coupons:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("coupons:subtitle")}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={accentPill}>+ {t("coupons:new")}</div>
      </div>

      {/* table */}
      <div data-table style={card}>
        <div style={{ display: "flex", fontSize: 12, color: "var(--faint)", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", fontWeight: 600, background: "var(--th)" }}>
          <div style={{ width: 130 }}>{t("coupons:code")}</div>
          <div style={{ flex: 1 }}>{t("coupons:type")}</div>
          <div style={{ width: 90 }}>{t("coupons:value")}</div>
          <div style={{ width: 110 }}>{t("coupons:usage")}</div>
          <div style={{ width: 100 }}>{t("coupons:expires")}</div>
          <div style={{ width: 84 }}>{t("coupons:status")}</div>
        </div>
        {COUPONS.map((c) => (
          <div key={c.code} style={{ display: "flex", fontSize: 14, padding: "13px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center" }}>
            <div style={{ width: 130, fontWeight: 700, letterSpacing: ".4px", fontFamily: "monospace", fontSize: 13 }}>{c.code}</div>
            <div style={{ flex: 1, color: "var(--muted)" }}>{c.type}</div>
            <div style={{ width: 90, fontWeight: 600 }}>{c.value}</div>
            <div style={{ width: 110, color: "var(--muted)" }}>{c.usage}</div>
            <div style={{ width: 100, color: "var(--muted)" }}>{c.expires}</div>
            <div style={{ width: 84 }}><StatusPill status={c.status} label={c.statusLabel} /></div>
          </div>
        ))}
        <Pagination current={current} pageSize={10} total={TOTAL} onChange={setCurrent} />
      </div>
    </div>
  );
}
