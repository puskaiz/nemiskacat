import { useState, type CSSProperties } from "react";
import { useShow } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { App } from "antd";
import { useTranslation } from "react-i18next";
import type { OrderDetail, OrderStatus } from "../../types";
import { STATUS_LABELS, refundOrder, transitionOrder } from "../../api/orders";
import { useThemeMode } from "../../theme/ThemeProvider";
import { PILL_DARK, PILL_LIGHT } from "../../theme/palette";
import { toneFor } from "../../components/ui/tone";

const huf = (n: number) => `${n.toLocaleString("hu-HU")} Ft`;
const card: CSSProperties = { background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const cardPad: CSSProperties = { ...card, padding: 16 };
const cardTitle: CSSProperties = { fontSize: 13, fontWeight: 700, marginBottom: 8 };
const outlineBtn: CSSProperties = { border: "1px solid var(--border)", borderRadius: 999, padding: "9px 16px", fontSize: 13, fontWeight: 600, background: "var(--surface)", cursor: "pointer" };
const accentBtn: CSSProperties = { borderRadius: 999, padding: "9px 16px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", cursor: "pointer" };
const field: CSSProperties = { border: "1px solid var(--border)", borderRadius: 3, padding: "11px 13px", fontSize: 14, background: "var(--input)" };

export const OrderShow = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const { message } = App.useApp();
  const { mode } = useThemeMode();
  const { queryResult } = useShow<OrderDetail>();
  const order = queryResult?.data?.data;
  const [busy, setBusy] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);

  if (!order) {
    return <div style={{ color: "var(--muted)" }}>{t("loading")}</div>;
  }

  const tone = (mode === "dark" ? PILL_DARK : PILL_LIGHT)[toneFor(order.status)];
  const refundable = order.status === "PAID" || order.status === "PACKING";

  const run = async (fn: () => Promise<Response>, okKey: string) => {
    setBusy(true);
    try {
      const res = await fn();
      res.ok ? message.success(t(okKey)) : message.error((await res.text().catch(() => "")) || t("orders:failed"));
      await queryResult?.refetch();
    } catch {
      message.error(t("orders:networkError"));
    } finally {
      setBusy(false);
    }
  };

  const doRefund = async () => {
    setRefundOpen(false);
    await run(() => refundOrder(order.id), "orders:refunded");
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div onClick={() => nav("/orders")} style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}>← {t("orders:back")}</div>

      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ fontSize: 23, fontWeight: 700 }}>{t("nav:orders")} #{order.orderNumber}</div>
        <span style={{ color: tone.fg, background: tone.bg, borderRadius: 3, padding: "3px 11px", fontSize: 12, fontWeight: 600 }}>{STATUS_LABELS[order.status]}</span>
        <div style={{ flex: 1 }} />
        {refundable && (
          <div onClick={() => setRefundOpen(true)} style={{ ...outlineBtn, color: "#C0392B" }}>{t("orders:refundTitle")}</div>
        )}
        <div onClick={() => window.print()} style={outlineBtn}>{t("orders:printInvoice")}</div>
        <div onClick={() => !busy && run(() => transitionOrder(order.id, "COMPLETED" as OrderStatus), "orders:statusUpdated")} style={accentBtn}>{t("orders:markFulfilled")}</div>
      </div>

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
        {/* items + totals */}
        <div style={{ flex: 1.5, ...card, overflow: "hidden" }}>
          <div style={{ padding: "14px 18px", borderBottom: "1px solid var(--border-soft)", fontSize: 14, fontWeight: 700 }}>{t("orders:detailItems")}</div>
          {order.lines.map((l, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 12, padding: "13px 18px", borderBottom: "1px solid var(--border-soft)" }}>
              <div style={{ width: 46, height: 46, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)", flex: "none" }} />
              <div style={{ flex: 1, fontSize: 14 }}>
                {l.variantLabel ? `${l.productName} – ${l.variantLabel}` : l.productName}
                <div style={{ fontSize: 12, color: "var(--muted)" }}>{l.sku ? `SKU ${l.sku} · ` : ""}×{l.quantity}</div>
              </div>
              <div style={{ fontWeight: 600 }}>{huf(l.lineGrossHuf)}</div>
            </div>
          ))}
          <div style={{ padding: "14px 18px", display: "flex", flexDirection: "column", gap: 7, fontSize: 14 }}>
            <div style={{ display: "flex", justifyContent: "space-between", color: "var(--muted)" }}><span>{t("orders:subtotal")}</span><span>{huf(order.itemsGrossHuf)}</span></div>
            <div style={{ display: "flex", justifyContent: "space-between", color: "var(--muted)" }}><span>{t("orders:shipping")} ({order.shipMethodName})</span><span>{huf(order.shipGrossHuf)}</span></div>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 16, fontWeight: 700, borderTop: "1px solid var(--border-soft)", paddingTop: 8 }}><span>{t("orders:totalLabel")}</span><span>{huf(order.totalGrossHuf)}</span></div>
          </div>
        </div>

        {/* customer / address / timeline */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 16 }}>
          <div style={cardPad}>
            <div style={cardTitle}>{t("orders:customer")}</div>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span style={{ width: 34, height: 34, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 13, fontWeight: 700 }}>{(order.customerName?.[0] ?? "?").toUpperCase()}</span>
              <div style={{ fontSize: 14 }}>{order.customerName}<div style={{ fontSize: 12, color: "var(--muted)" }}>{order.email}</div></div>
            </div>
          </div>
          <div style={cardPad}>
            <div style={cardTitle}>{t("orders:shippingAddress")}</div>
            <div style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.5 }}>{order.postcode} {order.city}<br />{order.addressLine}</div>
          </div>
          <div style={cardPad}>
            <div style={cardTitle}>{t("orders:timeline")}</div>
            <div style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.7 }}>
              {new Date(order.createdAt).toLocaleDateString("hu-HU", { month: "short", day: "numeric" })} · {t("orders:placed")}
            </div>
          </div>
        </div>
      </div>

      {refundOpen && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,.42)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 90, padding: 24 }} onClick={() => setRefundOpen(false)}>
          <div onClick={(e) => e.stopPropagation()} style={{ width: 440, background: "var(--surface)", borderRadius: 3, boxShadow: "0 24px 60px rgba(16,24,40,.3)", overflow: "hidden" }}>
            <div style={{ padding: "18px 22px", borderBottom: "1px solid var(--border-soft)", display: "flex", alignItems: "center" }}>
              <div style={{ fontSize: 17, fontWeight: 700 }}>{t("orders:refundTitle")} — #{order.orderNumber}</div>
              <div style={{ flex: 1 }} />
              <div onClick={() => setRefundOpen(false)} style={{ width: 30, height: 30, borderRadius: 3, background: "var(--fill)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--muted)", cursor: "pointer" }}>✕</div>
            </div>
            <div style={{ padding: "20px 22px", display: "flex", flexDirection: "column", gap: 16 }}>
              <div>
                <div style={{ fontSize: 12, color: "var(--muted)", fontWeight: 600, marginBottom: 6 }}>{t("orders:refundAmount")}</div>
                <div style={field}>{order.totalGrossHuf.toLocaleString("hu-HU")}</div>
                <div style={{ fontSize: 12, color: "var(--faint)", marginTop: 5 }}>{t("orders:refundWhole")}</div>
              </div>
              <div>
                <div style={{ fontSize: 12, color: "var(--muted)", fontWeight: 600, marginBottom: 6 }}>{t("orders:refundReason")}</div>
                <div style={field}>{t("orders:refundReasonValue")} ▾</div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 42, height: 24, borderRadius: 999, background: "var(--accent)", padding: 2, display: "flex", justifyContent: "flex-end" }}><div style={{ width: 20, height: 20, borderRadius: "50%", background: "#fff" }} /></div>
                <span style={{ fontSize: 13, color: "var(--muted)" }}>{t("orders:restock")}</span>
              </div>
            </div>
            <div style={{ padding: "16px 22px", borderTop: "1px solid var(--border-soft)", display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <div onClick={() => setRefundOpen(false)} style={{ border: "1px solid var(--border)", borderRadius: 999, padding: "10px 18px", fontSize: 13, fontWeight: 600, color: "var(--muted)", cursor: "pointer" }}>{t("orders:cancel")}</div>
              <div onClick={doRefund} style={{ borderRadius: 999, padding: "10px 20px", fontSize: 13, fontWeight: 600, color: "#fff", background: "#C0392B", cursor: busy ? "wait" : "pointer" }}>{t("orders:refundTitle")}</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
