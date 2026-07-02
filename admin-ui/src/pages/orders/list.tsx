import { useState, type CSSProperties } from "react";
import { useTable } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { App } from "antd";
import { useTranslation } from "react-i18next";
import type { OrderDetail, OrderStatus, OrderSummary } from "../../types";
import { STATUS_LABELS, refundOrder, transitionOrder } from "../../api/orders";
import { API_BASE, apiFetch } from "../../api/http";
import { useThemeMode } from "../../theme/ThemeProvider";
import { PILL_DARK, PILL_LIGHT } from "../../theme/palette";
import { toneFor } from "../../components/ui/tone";
import { Pagination } from "../../components/ui/Pagination";

const huf = (n: number) => `${n.toLocaleString("hu-HU")} Ft`;
const shortDate = (iso: string) => new Date(iso).toLocaleDateString("hu-HU", { month: "short", day: "numeric" });
const initialOf = (name: string) => (name?.trim()?.[0] ?? "?").toUpperCase();

const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)", cursor: "pointer" };
const redPill: CSSProperties = { border: "1px solid var(--border)", borderRadius: 999, padding: "9px 16px", fontSize: 13, fontWeight: 600, color: "#fff", background: "#C20E1A", cursor: "pointer" };
const chip: CSSProperties = { border: "1px solid var(--border)", borderRadius: 3, padding: "7px 13px", color: "var(--muted)", background: "var(--surface)", fontSize: 13 };
const bulkBtn: CSSProperties = { border: "1px solid var(--border)", background: "var(--surface)", borderRadius: 999, padding: "6px 13px", fontWeight: 600, cursor: "pointer" };
const avatar: CSSProperties = { width: 28, height: 28, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flex: "none" };

const TABS: { key: string; labelKey: string; status?: OrderStatus }[] = [
  { key: "all", labelKey: "orders:tabAll" },
  { key: "pending", labelKey: "orders:tabPending", status: "NEW" },
  { key: "paid", labelKey: "orders:tabPaid", status: "PAID" },
  { key: "shipped", labelKey: "orders:tabShipped", status: "SHIPPED" },
  { key: "refunded", labelKey: "orders:tabRefunded", status: "REFUNDED" },
];
// Statuses an admin can move an order to from the per-row dropdown.
const ROW_TARGETS: OrderStatus[] = ["PACKING", "SHIPPED", "COMPLETED", "CANCELLED"];
const DEMO: { key: string; labelKey: string }[] = [
  { key: "data", labelKey: "orders:demoData" }, { key: "loading", labelKey: "orders:demoLoading" },
  { key: "empty", labelKey: "orders:demoEmpty" }, { key: "error", labelKey: "orders:demoError" },
];

export const OrderList = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const { message } = App.useApp();
  const { mode } = useThemeMode();
  const { tableQueryResult, current, setCurrent, pageSize, setFilters } = useTable<OrderSummary>({ syncWithLocation: true, pagination: { pageSize: 10 } });

  const [tab, setTab] = useState("all");
  const [selected, setSelected] = useState<number[]>([]);
  const [statusMenu, setStatusMenu] = useState<number | null>(null);
  const [demo, setDemo] = useState("data");
  const [busy, setBusy] = useState(false);
  const [prep, setPrep] = useState<{ id: string; name: string; phone: string; lines: { n: string; q: number }[] }[] | null>(null);

  const rows = (tableQueryResult.data?.data ?? []) as OrderSummary[];
  const total = tableQueryResult.data?.total ?? 0;
  const refresh = () => tableQueryResult.refetch();

  const pillStyle = (status: OrderStatus): CSSProperties => {
    const c = (mode === "dark" ? PILL_DARK : PILL_LIGHT)[toneFor(status)];
    return { color: c.fg, background: c.bg, borderRadius: 3, padding: "2px 9px", fontSize: 12, fontWeight: 600, cursor: "pointer", whiteSpace: "nowrap" };
  };

  const selectTab = (tk: string, status?: OrderStatus) => {
    setTab(tk);
    setSelected([]);
    setFilters([{ field: "status", operator: "eq", value: status }], "replace");
  };

  const toggleSel = (id: number) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const allSelected = rows.length > 0 && rows.every((r) => selected.includes(r.id));
  const toggleAll = () => setSelected(allSelected ? [] : rows.map((r) => r.id));

  const transitionOne = async (id: number, target: OrderStatus) => {
    setStatusMenu(null);
    setBusy(true);
    try {
      const res = await transitionOrder(id, target);
      res.ok ? message.success(t("orders:statusUpdated")) : message.error((await res.text().catch(() => "")) || t("orders:failed"));
      await refresh();
    } catch {
      message.error(t("orders:networkError"));
    } finally {
      setBusy(false);
    }
  };

  const bulkRun = async (fn: (id: number) => Promise<Response>, okMsgKey: string) => {
    setBusy(true);
    let ok = 0, skip = 0;
    for (const id of selected) {
      try { (await fn(id)).ok ? ok++ : skip++; } catch { skip++; }
    }
    message.info(t(okMsgKey, { ok, skip }));
    setSelected([]);
    await refresh();
    setBusy(false);
  };

  const openPrep = async () => {
    setBusy(true);
    try {
      const details = await Promise.all(
        selected.map((id) => apiFetch(`${API_BASE}/orders/${id}`).then((r) => r.json() as Promise<OrderDetail>)),
      );
      setPrep(details.map((d) => ({
        id: d.orderNumber,
        name: d.customerName,
        phone: d.phone ?? "—",
        lines: d.lines.map((l) => ({ n: l.variantLabel ? `${l.productName} – ${l.variantLabel}` : l.productName, q: l.quantity })),
      })));
    } catch {
      message.error(t("orders:networkError"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("nav:orders")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("orders:subtitle", { total })}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={redPill}>{t("orders:export")}</div>
        <div style={accentPill} onClick={() => message.info(t("orders:newOrderSoon"))}>+ {t("orders:newOrder")}</div>
      </div>

      {/* tab filters */}
      <div style={{ display: "flex", gap: 24, fontSize: 14, borderBottom: "1px solid var(--border)" }}>
        {TABS.map((tb) => {
          const isActive = tab === tb.key;
          return (
            <div key={tb.key} onClick={() => selectTab(tb.key, tb.status)} style={{ paddingBottom: 11, cursor: "pointer", borderBottom: isActive ? "2px solid var(--accent)" : "2px solid transparent", fontWeight: isActive ? 600 : 400, color: isActive ? "var(--text)" : "var(--muted)" }}>
              {t(tb.labelKey)}
            </div>
          );
        })}
      </div>

      {/* secondary filter chips (visual; date/channel filtering is a later pass) */}
      <div style={{ display: "flex", gap: 10, fontSize: 13 }}>
        <div style={chip}>{t("orders:filterChannel")} ▾</div>
        <div style={chip}>{t("orders:filterDate")} ▾</div>
        <div style={chip}>{t("orders:filterFulfilment")} ▾</div>
      </div>

      {/* bulk bar */}
      {selected.length > 0 && (
        <div style={{ display: "flex", alignItems: "center", gap: 10, background: "var(--accent-soft)", border: "1px solid var(--accent)", borderRadius: 3, padding: "10px 14px", fontSize: 13 }}>
          <span style={{ fontWeight: 700, color: "var(--accent-fg)" }}>{t("orders:selected", { count: selected.length })}</span>
          <div style={{ flex: 1 }} />
          <div onClick={openPrep} style={{ borderRadius: 999, padding: "6px 15px", fontWeight: 600, cursor: "pointer", color: "#fff", background: "var(--accent)" }}>{t("orders:prepPackaging")}</div>
          <div onClick={() => bulkRun((id) => transitionOrder(id, "PAID"), "orders:bulkResult")} style={bulkBtn}>{t("orders:markPaid")}</div>
          <div onClick={() => bulkRun((id) => transitionOrder(id, "SHIPPED"), "orders:bulkResult")} style={bulkBtn}>{t("orders:markShipped")}</div>
          <div onClick={() => bulkRun((id) => refundOrder(id), "orders:bulkResult")} style={bulkBtn}>{t("orders:refund")}</div>
          <div onClick={() => setSelected([])} style={{ color: "var(--muted)", cursor: "pointer", padding: "6px 8px" }}>{t("orders:clear")}</div>
        </div>
      )}

      {/* demo state switcher */}
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: -4 }}>
        <span style={{ fontSize: 11, color: "var(--faint)", fontWeight: 600, textTransform: "uppercase", letterSpacing: ".5px" }}>{t("orders:viewDemo")}</span>
        <div style={{ display: "flex", gap: 2, background: "var(--fill)", borderRadius: 999, padding: 3 }}>
          {DEMO.map((d) => (
            <div key={d.key} onClick={() => setDemo(d.key)} style={{ padding: "4px 10px", borderRadius: 999, cursor: "pointer", fontSize: 12, fontWeight: 600, background: demo === d.key ? "var(--accent)" : "transparent", color: demo === d.key ? "#fff" : "var(--muted)" }}>{t(d.labelKey)}</div>
          ))}
        </div>
      </div>

      <OrdersTableArea
        demo={demo}
        loading={tableQueryResult.isLoading}
        error={tableQueryResult.isError}
        rows={rows}
        total={total}
        current={current}
        pageSize={pageSize}
        setCurrent={setCurrent}
        selected={selected}
        allSelected={allSelected}
        toggleAll={toggleAll}
        toggleSel={toggleSel}
        statusMenu={statusMenu}
        setStatusMenu={setStatusMenu}
        pillStyle={pillStyle}
        onRow={(id) => nav(`/orders/show/${id}`)}
        onPick={transitionOne}
        busy={busy}
        onRetry={refresh}
        t={t}
      />

      {prep && <PrepModal prep={prep} onClose={() => setPrep(null)} t={t} />}
    </div>
  );
};

// ---- table area (data / skeleton / empty / error) ----
type AreaProps = {
  demo: string; loading: boolean; error: boolean; rows: OrderSummary[]; total: number;
  current: number; pageSize: number; setCurrent: (p: number) => void;
  selected: number[]; allSelected: boolean; toggleAll: () => void; toggleSel: (id: number) => void;
  statusMenu: number | null; setStatusMenu: (id: number | null) => void;
  pillStyle: (s: OrderStatus) => CSSProperties; onRow: (id: number) => void;
  onPick: (id: number, target: OrderStatus) => void; busy: boolean; onRetry: () => void;
  t: ReturnType<typeof useTranslation>["t"];
};

const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const check = (on: boolean): CSSProperties => ({ width: 15, height: 15, borderRadius: 3, border: on ? "none" : "1.6px solid var(--border)", background: on ? "var(--accent)" : "transparent", display: "inline-flex", alignItems: "center", justifyContent: "center", color: "#fff", fontSize: 11, cursor: "pointer" });
const headCell: CSSProperties = { fontSize: 12, color: "var(--faint)", fontWeight: 600 };

function OrdersTableArea(p: AreaProps) {
  const showState = p.demo === "data" ? (p.loading ? "loading" : p.error ? "error" : p.rows.length === 0 ? "empty" : "data") : p.demo;

  if (showState === "loading") {
    return (
      <div style={{ ...card, overflow: "hidden" }}>
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center", gap: 16, padding: "14px 18px", borderBottom: "1px solid var(--border-soft)" }}>
            <div style={{ width: 15, height: 15, borderRadius: 3, background: "var(--fill)" }} />
            <div style={{ width: 54, height: 11, borderRadius: 5, background: "var(--fill)" }} />
            <div style={{ width: 50, height: 11, borderRadius: 5, background: "var(--fill)" }} />
            <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 10 }}><div style={{ width: 28, height: 28, borderRadius: "50%", background: "var(--fill)" }} /><div style={{ width: 140, height: 11, borderRadius: 5, background: "var(--fill)" }} /></div>
            <div style={{ width: 64, height: 11, borderRadius: 5, background: "var(--fill)" }} />
            <div style={{ width: 72, height: 20, borderRadius: 999, background: "var(--fill)" }} />
          </div>
        ))}
      </div>
    );
  }
  if (showState === "empty") {
    return (
      <div style={{ ...card, padding: "64px 24px", display: "flex", flexDirection: "column", alignItems: "center", gap: 12, textAlign: "center" }}>
        <div style={{ width: 64, height: 64, borderRadius: 16, background: "var(--accent-soft)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--accent-fg)" }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M8 4h8a1 1 0 011 1v15l-3-2-2 2-2-2-2 2-2-2V5a1 1 0 011-1z M9 9h6 M9 13h4" /></svg>
        </div>
        <div style={{ fontSize: 18, fontWeight: 700 }}>{p.t("orders:emptyTitle")}</div>
        <div style={{ fontSize: 14, color: "var(--muted)", maxWidth: 340, lineHeight: 1.5 }}>{p.t("orders:emptyBody")}</div>
      </div>
    );
  }
  if (showState === "error") {
    return (
      <div style={{ ...card, padding: "64px 24px", display: "flex", flexDirection: "column", alignItems: "center", gap: 12, textAlign: "center" }}>
        <div style={{ width: 64, height: 64, borderRadius: 16, background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center", color: "#DC4040" }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M12 9v4 M12 17h.01 M10.3 3.9l-8 14A1 1 0 003 19h18a1 1 0 00.9-1.5l-8-14a1 1 0 00-1.7 0z" /></svg>
        </div>
        <div style={{ fontSize: 18, fontWeight: 700 }}>{p.t("orders:errorTitle")}</div>
        <div style={{ fontSize: 14, color: "var(--muted)", maxWidth: 340, lineHeight: 1.5 }}>{p.t("orders:errorBody")}</div>
        <div style={accentPill} onClick={p.onRetry}>{p.t("orders:retry")}</div>
      </div>
    );
  }

  return (
    <div data-table style={{ ...card }}>
      <div style={{ display: "flex", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", background: "var(--th)", ...headCell }}>
        <div style={{ width: 16 }}><span style={check(p.allSelected)} onClick={p.toggleAll}>{p.allSelected ? "✓" : ""}</span></div>
        <div style={{ width: 64 }}>{p.t("orders:colOrder")}</div>
        <div style={{ width: 70 }}>{p.t("orders:colDate")}</div>
        <div style={{ flex: 1 }}>{p.t("orders:colCustomer")}</div>
        <div style={{ width: 84 }}>{p.t("orders:colChannel")}</div>
        <div style={{ width: 48, textAlign: "center" }}>{p.t("orders:colItems")}</div>
        <div style={{ width: 90, textAlign: "right" }}>{p.t("orders:colTotal")}</div>
        <div style={{ width: 96 }}>{p.t("orders:colStatus")}</div>
        <div style={{ width: 20 }} />
      </div>
      {p.rows.map((r) => {
        const on = p.selected.includes(r.id);
        return (
          <div key={r.id} onClick={() => p.onRow(r.id)} style={{ display: "flex", fontSize: 14, padding: "13px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center", cursor: "pointer" }}>
            <div style={{ width: 16 }}><span style={check(on)} onClick={(e) => { e.stopPropagation(); p.toggleSel(r.id); }}>{on ? "✓" : ""}</span></div>
            <div style={{ width: 64, fontWeight: 600 }}>{r.orderNumber}</div>
            <div style={{ width: 70, color: "var(--muted)", fontSize: 13 }}>{shortDate(r.createdAt)}</div>
            <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 9 }}><span style={avatar}>{initialOf(r.customerName)}</span>{r.customerName}</div>
            <div style={{ width: 84 }}><span style={{ fontSize: 12, color: "var(--muted)", background: "var(--fill)", borderRadius: 3, padding: "2px 8px" }}>Web</span></div>
            <div style={{ width: 48, textAlign: "center", color: "var(--muted)" }}>—</div>
            <div style={{ width: 90, textAlign: "right", fontWeight: 600 }}>{huf(r.totalGrossHuf)}</div>
            <div style={{ width: 96, position: "relative" }}>
              <span style={p.pillStyle(r.status)} onClick={(e) => { e.stopPropagation(); p.setStatusMenu(p.statusMenu === r.id ? null : r.id); }}>{STATUS_LABELS[r.status]} ▾</span>
              {p.statusMenu === r.id && (
                <div style={{ position: "absolute", top: 28, left: 0, zIndex: 40, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, boxShadow: "0 8px 24px rgba(16,24,40,.18)", width: 150, overflow: "hidden" }} onClick={(e) => e.stopPropagation()}>
                  {ROW_TARGETS.map((s) => (
                    <div key={s} onClick={() => p.onPick(r.id, s)} style={{ padding: "8px 12px", fontSize: 13, cursor: p.busy ? "wait" : "pointer", color: "var(--text)" }}>{STATUS_LABELS[s]}</div>
                  ))}
                </div>
              )}
            </div>
            <div style={{ width: 20, color: "var(--faint)" }}>⋯</div>
          </div>
        );
      })}
      <Pagination current={p.current} pageSize={p.pageSize} total={p.total} onChange={p.setCurrent} />
    </div>
  );
}

// ---- prepare-for-packaging modal ----
function PrepModal({ prep, onClose, t }: { prep: { id: string; name: string; phone: string; lines: { n: string; q: number }[] }[]; onClose: () => void; t: ReturnType<typeof useTranslation>["t"] }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,.42)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 80, padding: 24 }} onClick={onClose}>
      <div onClick={(e) => e.stopPropagation()} style={{ width: 560, maxHeight: "86vh", background: "var(--surface)", borderRadius: 3, boxShadow: "0 24px 60px rgba(16,24,40,.3)", overflow: "hidden", display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "18px 22px", borderBottom: "1px solid var(--border-soft)", display: "flex", alignItems: "center" }}>
          <div><div style={{ fontSize: 17, fontWeight: 700 }}>{t("orders:prepPackaging")}</div><div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("orders:prepCount", { count: prep.length })}</div></div>
          <div style={{ flex: 1 }} />
          <div onClick={onClose} style={{ width: 30, height: 30, borderRadius: 3, background: "var(--fill)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--muted)", cursor: "pointer" }}>✕</div>
        </div>
        <div style={{ padding: "16px 22px", display: "flex", flexDirection: "column", gap: 12, overflow: "auto" }}>
          {prep.map((o) => (
            <div key={o.id} style={{ border: "1px solid var(--border)", borderRadius: 3, overflow: "hidden" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "11px 14px", background: "var(--th)", borderBottom: "1px solid var(--border-soft)" }}>
                <span style={avatar}>{initialOf(o.name)}</span>
                <div style={{ flex: 1 }}><div style={{ fontSize: 14, fontWeight: 600 }}>{o.name}</div><div style={{ fontSize: 12, color: "var(--muted)" }}>{o.phone}</div></div>
                <div style={{ fontSize: 12, color: "var(--muted)", fontFamily: "monospace" }}>{o.id}</div>
              </div>
              {o.lines.map((l, i) => (
                <div key={i} style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 14px", borderBottom: "1px solid var(--border-soft)", fontSize: 14 }}>
                  <span style={{ minWidth: 30, height: 24, borderRadius: 3, background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, padding: "0 6px" }}>{l.q}×</span>
                  <span style={{ flex: 1 }}>{l.n}</span>
                  <span style={{ width: 18, height: 18, border: "1.6px solid var(--border)", borderRadius: 3, flex: "none" }} />
                </div>
              ))}
            </div>
          ))}
        </div>
        <div style={{ padding: "16px 22px", borderTop: "1px solid var(--border-soft)", display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <div onClick={onClose} style={{ border: "1px solid var(--border)", borderRadius: 999, padding: "10px 18px", fontSize: 13, fontWeight: 600, color: "var(--muted)", cursor: "pointer" }}>{t("orders:close")}</div>
          <div onClick={() => window.print()} style={{ borderRadius: 999, padding: "10px 20px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", cursor: "pointer" }}>{t("orders:print")}</div>
        </div>
      </div>
    </div>
  );
}
