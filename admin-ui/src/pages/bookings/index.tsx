import { useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { StatusPill } from "../../components/ui/StatusPill";
import { Pagination } from "../../components/ui/Pagination";
import { BOOKINGS } from "../../data/bookings";

// Faithful 1:1 port of the prototype Bookings list (Admin Mid-fi HU.dc.html,
// sec.bookings block, lines 382-405): header (title + Type/Event filter chips
// + export + add), tab row (Upcoming / Waitlist / Attended / Cancelled), the
// table and pagination. Static example data; the detail sub-view is out of
// scope (list views only, per the established pattern).

const TOTAL = 24;

const filterChip: CSSProperties = { border: "1px solid var(--border)", borderRadius: 999, padding: "7px 14px", fontSize: 13, color: "var(--muted)", background: "var(--surface)", cursor: "pointer" };
const exportPill: CSSProperties = { border: "1px solid var(--border)", borderRadius: 999, padding: "9px 16px", fontSize: 13, fontWeight: 600, color: "#fff", background: "#C20E1A" };
const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)" };
const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const avatar: CSSProperties = { width: 28, height: 28, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flex: "none" };

const TABS: { key: string; labelKey: string; count: number }[] = [
  { key: "upcoming", labelKey: "bookings:tabUpcoming", count: 24 },
  { key: "waitlist", labelKey: "bookings:tabWaitlist", count: 3 },
  { key: "attended", labelKey: "bookings:tabAttended", count: 112 },
  { key: "cancelled", labelKey: "bookings:tabCancelled", count: 6 },
];

export function Bookings() {
  const { t } = useTranslation();
  const [tab, setTab] = useState("upcoming");
  const [current, setCurrent] = useState(1);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("bookings:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("bookings:subtitle")}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={filterChip}>{t("bookings:filterType")} ▾</div>
        <div style={filterChip}>{t("bookings:filterEvent")} ▾</div>
        <div style={exportPill}>{t("bookings:export")}</div>
        <div style={accentPill}>+ {t("bookings:new")}</div>
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

      {/* table */}
      <div data-table style={card}>
        <div style={{ display: "flex", fontSize: 12, color: "var(--faint)", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", fontWeight: 600, background: "var(--th)" }}>
          <div style={{ width: 64 }}>{t("bookings:ref")}</div>
          <div style={{ flex: 1 }}>{t("bookings:attendee")}</div>
          <div style={{ flex: 1.3 }}>{t("bookings:workshop")}</div>
          <div style={{ width: 50, textAlign: "center" }}>{t("bookings:seats")}</div>
          <div style={{ width: 84, textAlign: "right" }}>{t("bookings:paid")}</div>
          <div style={{ width: 96 }}>{t("bookings:status")}</div>
        </div>
        {BOOKINGS.map((b) => (
          <div key={b.ref} style={{ display: "flex", fontSize: 14, padding: "13px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center", cursor: "pointer" }}>
            <div style={{ width: 64, color: "var(--muted)", fontSize: 13 }}>{b.ref}</div>
            <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 9 }}><span style={avatar}>{b.initial}</span>{b.name}</div>
            <div style={{ flex: 1.3, color: "var(--muted)" }}>{b.ws}</div>
            <div style={{ width: 50, textAlign: "center" }}>{b.seats}</div>
            <div style={{ width: 84, textAlign: "right", fontWeight: 600 }}>{b.paid}</div>
            <div style={{ width: 96 }}><StatusPill status={b.status} label={t(`bookings:status_${b.status}`)} /></div>
          </div>
        ))}
        <Pagination current={current} pageSize={10} total={TOTAL} onChange={setCurrent} />
      </div>
    </div>
  );
}
