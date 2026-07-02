import { useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { StatusPill } from "../../components/ui/StatusPill";
import { Pagination } from "../../components/ui/Pagination";
import { INSTRUCTORS } from "../../data/instructors";

// Faithful 1:1 port of the prototype Instructors list (Admin Mid-fi HU.dc.html,
// wInstr block inside the workshops section, lines 319-332): header
// ("Oktatók" + "+ Új oktató") + table (Oktató / Szakterület / Workshopok /
// Állapot pill) + pagination. Static example data; detail/create sub-views are
// intentionally out of scope.

const TOTAL = 4;

const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)", cursor: "pointer" };
const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const avatar: CSSProperties = { width: 28, height: 28, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flex: "none" };

export function Instructors() {
  const { t } = useTranslation();
  const [current, setCurrent] = useState(1);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("instructors:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("instructors:subtitle")}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={accentPill}>+ {t("instructors:new")}</div>
      </div>

      {/* table */}
      <div data-table style={card}>
        <div style={{ display: "flex", fontSize: 12, color: "var(--faint)", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", fontWeight: 600, background: "var(--th)" }}>
          <div style={{ flex: 1 }}>{t("instructors:name")}</div>
          <div style={{ flex: 1 }}>{t("instructors:specialty")}</div>
          <div style={{ width: 90, textAlign: "center" }}>{t("instructors:workshopCount")}</div>
          <div style={{ width: 100 }}>{t("instructors:status")}</div>
        </div>
        {INSTRUCTORS.map((i) => (
          <div key={i.email} style={{ display: "flex", fontSize: 14, padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center", cursor: "pointer" }}>
            <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 9 }}>
              <span style={avatar}>{i.name[0]}</span>
              <div>
                <div style={{ fontWeight: 600 }}>{i.name}</div>
                <div style={{ fontSize: 12, color: "var(--muted)" }}>{i.email}</div>
              </div>
            </div>
            <div style={{ flex: 1, color: "var(--muted)" }}>{i.specialty}</div>
            <div style={{ width: 90, textAlign: "center", color: "var(--muted)" }}>{i.count}</div>
            <div style={{ width: 100 }}><StatusPill status={i.status} label={t(`instructors:status_${i.status}`)} /></div>
          </div>
        ))}
        <Pagination current={current} pageSize={10} total={TOTAL} onChange={setCurrent} />
      </div>
    </div>
  );
}
