import { useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { StatusPill } from "../../components/ui/StatusPill";
import { Pagination } from "../../components/ui/Pagination";
import { CMS_PAGES } from "../../data/pages";

// Faithful 1:1 port of the prototype Pages/CMS list (Admin Mid-fi HU.dc.html,
// sec.cms block, lines 575-588): header (title + subtitle + "+ Új oldal") +
// table (Cím / URL / Nyelvek / Frissítve / Állapot) + pagination. Static example
// data; real pages arrive in a later pass. The block-editor detail sub-view
// (sc-if detail, lines 590-610) is intentionally out of scope.

const TOTAL = CMS_PAGES.length;

const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)", cursor: "pointer" };
const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };

export function Pages() {
  const { t } = useTranslation();
  const [current, setCurrent] = useState(1);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("cms:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("cms:subtitle")}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={accentPill}>+ {t("cms:new")}</div>
      </div>

      {/* table */}
      <div data-table style={card}>
        <div style={{ display: "flex", fontSize: 12, color: "var(--faint)", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", fontWeight: 600, background: "var(--th)" }}>
          <div style={{ flex: 1.2 }}>{t("cms:colTitle")}</div>
          <div style={{ flex: 1 }}>{t("cms:colUrl")}</div>
          <div style={{ width: 130 }}>{t("cms:colLangs")}</div>
          <div style={{ width: 90 }}>{t("cms:colUpdated")}</div>
          <div style={{ width: 84 }}>{t("cms:colStatus")}</div>
        </div>
        {CMS_PAGES.map((p) => (
          <div key={p.slug} style={{ display: "flex", fontSize: 14, padding: "13px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center", cursor: "pointer" }}>
            <div style={{ flex: 1.2, fontWeight: 600 }}>{p.title}</div>
            <div style={{ flex: 1, color: "var(--muted)", fontFamily: "monospace", fontSize: 13 }}>{p.slug}</div>
            <div style={{ width: 130, color: "var(--muted)" }}>{p.langs}</div>
            <div style={{ width: 90, color: "var(--muted)" }}>{p.updated}</div>
            <div style={{ width: 84 }}><StatusPill status={p.status} label={t(`cms:status_${p.status}`)} /></div>
          </div>
        ))}
        <Pagination current={current} pageSize={TOTAL} total={TOTAL} onChange={setCurrent} />
      </div>
    </div>
  );
}
