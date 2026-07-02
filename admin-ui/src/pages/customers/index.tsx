import { useEffect, useState, type CSSProperties } from "react";
import { useTable } from "@refinedev/core";
import { useTranslation } from "react-i18next";
import { Tag } from "antd";
import type { CustomerRole, CustomerSummary } from "../../types";
import { Pagination } from "../../components/ui/Pagination";

// Customers list, backed by the real admin API (GET /api/admin/customers) via
// Refine's useTable. Mirrors orders/list.tsx for pagination + the debounced
// product-name search pattern from products/index.tsx, wired to the backend `q`
// filter. Role/status render as Ant Tags. Header layout follows the prototype
// (Admin Mid-fi HU.dc.html, sec.customers block); the VIP/tier chips were dropped.

const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const headCell: CSSProperties = { fontSize: 12, color: "var(--faint)", fontWeight: 600 };
const avatar: CSSProperties = { width: 30, height: 30, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flex: "none" };
const searchField: CSSProperties = { minWidth: 280, border: "1px solid var(--border)", borderRadius: 999, padding: "9px 16px", fontSize: 13, color: "var(--text)", background: "var(--surface)", outline: "none" };

const initialOf = (name: string) => (name?.trim()?.[0] ?? "?").toUpperCase();
// Date stored UTC; render in Europe/Budapest like the rest of the app.
const shortDate = (iso: string) =>
  new Date(iso).toLocaleDateString("hu-HU", { year: "numeric", month: "short", day: "numeric", timeZone: "Europe/Budapest" });

const ROLE_TONE: Record<CustomerRole, { color: string; labelKey: string }> = {
  ADMIN: { color: "gold", labelKey: "customers:roleAdmin" },
  SUBSCRIBER: { color: "blue", labelKey: "customers:roleSubscriber" },
  CUSTOMER: { color: "default", labelKey: "customers:roleCustomer" },
};

export function Customers() {
  const { t } = useTranslation();
  const { tableQueryResult, current, setCurrent, pageSize, setFilters } = useTable<CustomerSummary>({ syncWithLocation: true, pagination: { pageSize: 10 } });

  const [search, setSearch] = useState("");

  // Debounce the search so we don't refetch on every keystroke; field `q` maps
  // straight to the ?q= query param in the dataProvider.
  useEffect(() => {
    const id = setTimeout(() => {
      setFilters([{ field: "q", operator: "contains", value: search.trim() }], "replace");
      setCurrent(1);
    }, 300);
    return () => clearTimeout(id);
  }, [search, setFilters, setCurrent]);

  const rows = (tableQueryResult.data?.data ?? []) as CustomerSummary[];
  const total = tableQueryResult.data?.total ?? 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("customers:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("customers:subtitle", { total: total.toLocaleString("hu-HU") })}</div>
        </div>
        <div style={{ flex: 1 }} />
        <input
          value={search}
          placeholder={t("customers:search")}
          onChange={(e) => setSearch(e.target.value)}
          style={searchField}
        />
      </div>

      {/* table */}
      <div data-table style={card}>
        <div style={{ display: "flex", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", background: "var(--th)", ...headCell }}>
          <div style={{ flex: 1.2 }}>{t("customers:name")}</div>
          <div style={{ flex: 1.4 }}>{t("customers:email")}</div>
          <div style={{ width: 110 }}>{t("customers:role")}</div>
          <div style={{ width: 100 }}>{t("customers:status")}</div>
          <div style={{ width: 130 }}>{t("customers:registered")}</div>
        </div>
        {rows.map((c) => {
          const role = ROLE_TONE[c.role] ?? ROLE_TONE.CUSTOMER;
          return (
            <div key={c.id} style={{ display: "flex", fontSize: 14, padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center" }}>
              <div style={{ flex: 1.2, display: "flex", alignItems: "center", gap: 9 }}><span style={avatar}>{initialOf(c.name)}</span>{c.name}</div>
              <div style={{ flex: 1.4, color: "var(--muted)" }}>{c.email}</div>
              <div style={{ width: 110 }}><Tag color={role.color}>{t(role.labelKey)}</Tag></div>
              <div style={{ width: 100 }}>
                <Tag color={c.enabled ? "green" : "red"}>{t(c.enabled ? "customers:statusActive" : "customers:statusDisabled")}</Tag>
              </div>
              <div style={{ width: 130, color: "var(--muted)" }}>{shortDate(c.createdAt)}</div>
            </div>
          );
        })}
        {rows.length === 0 && !tableQueryResult.isLoading && (
          <div style={{ padding: "48px 24px", textAlign: "center" }}>
            <div style={{ fontSize: 16, fontWeight: 700 }}>{t("customers:emptyTitle")}</div>
            <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 4 }}>{t("customers:emptyBody")}</div>
          </div>
        )}
        <Pagination current={current} pageSize={pageSize} total={total} onChange={setCurrent} />
      </div>
    </div>
  );
}
