import { useEffect, useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { useList } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { StatusPill } from "../../components/ui/StatusPill";
import { Pagination } from "../../components/ui/Pagination";
import { CategorySelect } from "../../components/CategorySelect";
import type { ProductCategoryRef, ProductStatus, ProductSummary } from "../../types";

// Faithful 1:1 port of the prototype Products list (Admin Mid-fi HU.dc.html, pList
// block, lines 214-242): header + view toggle + filter chips + grid/list views +
// pagination. The static fixtures are now backed by the real catalog API
// (GET /api/admin/products + /api/admin/categories) via Refine's useList. The
// markup/styling is unchanged — only the data source was swapped.

const PAGE_SIZE = 10;

const huf = (n: number | null) => (n == null ? "—" : `${n.toLocaleString("hu-HU")} Ft`);
// Map the catalog status to the prototype's tone keys so StatusPill keeps its
// green (published) / gray (draft) colours.
const tone = (s: ProductStatus) => (s === "PUBLISHED" ? "active" : "neutral");

const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)", cursor: "pointer" };
const card: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, background: "var(--surface)", overflow: "hidden", boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const catBadge: CSSProperties = { flex: "none", borderRadius: 999, padding: "1px 8px", fontSize: 11, fontWeight: 600, color: "var(--muted)", background: "var(--fill)", border: "1px solid var(--border)", cursor: "pointer" };
// Rounded product-name search field.
const searchField: CSSProperties = { minWidth: 240, border: "1px solid var(--border)", borderRadius: 999, padding: "7px 14px", fontSize: 13, color: "var(--text)", background: "var(--surface)", outline: "none" };
export function Products() {
  const { t } = useTranslation();
  const nav = useNavigate();
  const [view, setView] = useState<"grid" | "list">("list");
  const [selectedCats, setSelectedCats] = useState<string[]>([]);
  const [search, setSearch] = useState("");
  const [searchApplied, setSearchApplied] = useState("");
  const [catPopupId, setCatPopupId] = useState<number | null>(null);
  const [current, setCurrent] = useState(1);

  // Debounce the product-name search so we don't refetch on every keystroke.
  useEffect(() => {
    const id = setTimeout(() => {
      setSearchApplied(search.trim());
      setCurrent(1);
    }, 300);
    return () => clearTimeout(id);
  }, [search]);

  const filters: { field: string; operator: "in" | "contains"; value: string[] | string }[] = [];
  if (selectedCats.length) filters.push({ field: "category", operator: "in", value: selectedCats });
  if (searchApplied) filters.push({ field: "q", operator: "contains", value: searchApplied });

  const { data } = useList<ProductSummary>({
    resource: "products",
    pagination: { current, pageSize: PAGE_SIZE },
    filters,
  });
  const { data: catData } = useList<ProductCategoryRef>({
    resource: "categories",
    pagination: { mode: "off" },
  });

  const rows = data?.data ?? [];
  const total = data?.total ?? 0;
  const categories = catData?.data ?? [];

  const tab = (on: boolean): CSSProperties => ({
    padding: "9px 18px", cursor: "pointer",
    background: on ? "var(--accent)" : "transparent",
    color: on ? "#fff" : "var(--muted)",
  });

  const selectCategories = (slugs: string[]) => {
    setSelectedCats(slugs);
    setCurrent(1);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("products:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("products:subtitle", { total, low: 0 })}</div>
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ display: "flex", border: "1px solid var(--border)", borderRadius: 3, overflow: "hidden", fontSize: 13, fontWeight: 600, background: "var(--surface)" }}>
          <div style={tab(view === "grid")} onClick={() => setView("grid")}>{t("products:grid")}</div>
          <div style={tab(view === "list")} onClick={() => setView("list")}>{t("products:list")}</div>
        </div>
        <div style={accentPill}>+ {t("products:new")}</div>
      </div>

      {/* filter bar: product-name search + category selector */}
      <div style={{ display: "flex", flexDirection: "column", gap: 10, fontSize: 13 }}>
        <div style={{ display: "flex", gap: 9, alignItems: "center" }}>
          {/* product-name search (debounced → backend q) */}
          <input
            value={search}
            placeholder={t("products:searchProducts")}
            onChange={(e) => setSearch(e.target.value)}
            style={searchField}
          />
          {/* category selector: autocomplete dropdown, selections shown as chips below */}
          <CategorySelect categories={categories} value={selectedCats} onChange={selectCategories} />
        </div>
      </div>

      {/* grid view */}
      {view === "grid" && (
        <>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5,1fr)", gap: 16 }}>
          {rows.map((p) => (
            <div key={p.id} onClick={() => nav(`/products/show/${p.id}`)} style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, overflow: "hidden", cursor: "pointer", boxShadow: "0 1px 2px rgba(16,24,40,.04)" }}>
              {p.coverImageUrl ? (
                <img src={p.coverImageUrl} alt={p.name} style={{ width: "100%", aspectRatio: "1 / 1", objectFit: "cover", borderBottom: "1px solid var(--border-soft)", display: "block" }} />
              ) : (
                <div style={{ width: "100%", aspectRatio: "1 / 1", background: "var(--fill)", borderBottom: "1px solid var(--border-soft)" }} />
              )}
              <div style={{ padding: "12px 13px" }}>
                <div style={{ fontSize: 14, fontWeight: 600 }}>{p.name}</div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 6 }}>
                  <span style={{ fontSize: 14, fontWeight: 700 }}>{huf(p.priceGrossHuf)}</span>
                  <span style={{ fontSize: 12, color: "var(--muted)" }}>{p.stockQty}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
        <div style={card}><Pagination current={current} pageSize={PAGE_SIZE} total={total} onChange={setCurrent} /></div>
        </>
      )}

      {/* list view */}
      {view === "list" && (
        <div data-table style={card}>
          <div style={{ display: "flex", fontSize: 12, color: "var(--faint)", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", fontWeight: 600, background: "var(--th)" }}>
            <div style={{ width: 44 }} />
            <div style={{ flex: 1 }}>{t("products:colProduct")}</div>
            <div style={{ width: 240 }}>{t("products:colCategory")}</div>
            <div style={{ width: 90, textAlign: "right" }}>{t("products:colPrice")}</div>
            <div style={{ width: 80 }}>{t("products:colStock")}</div>
            <div style={{ width: 120 }}>{t("products:colStatus")}</div>
          </div>
          {rows.map((p) => (
            <div key={p.id} onClick={() => nav(`/products/show/${p.id}`)} style={{ display: "flex", fontSize: 14, padding: "11px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center", cursor: "pointer" }}>
              {p.coverImageUrl ? (
                <img src={p.coverImageUrl} alt={p.name} style={{ width: 44, height: 44, borderRadius: 3, objectFit: "cover", border: "1px solid var(--border)", flex: "none" }} />
              ) : (
                <div style={{ width: 44, height: 44, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)", flex: "none" }} />
              )}
              <div style={{ flex: 1, fontWeight: 600 }}>{p.name}</div>
              <div style={{ width: 240, color: "var(--muted)", display: "flex", alignItems: "center", gap: 8, position: "relative" }}>
                <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={p.primaryCategory}>{p.primaryCategory}</span>
                {p.categories.length > 1 && (
                  <span
                    style={catBadge}
                    title={t("products:categoriesAll")}
                    onClick={(e) => { e.stopPropagation(); setCatPopupId((id) => (id === p.id ? null : p.id)); }}
                  >
                    +{p.categories.length - 1}
                  </span>
                )}
                {catPopupId === p.id && (
                  <>
                    <div onClick={(e) => { e.stopPropagation(); setCatPopupId(null); }} style={{ position: "fixed", inset: 0, zIndex: 40 }} />
                    <div onClick={(e) => e.stopPropagation()} style={{ position: "absolute", top: 26, left: 0, zIndex: 41, minWidth: 200, maxWidth: 320, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, boxShadow: "0 8px 24px rgba(16,24,40,.18)", overflow: "hidden" }}>
                      <div style={{ padding: "8px 12px", fontSize: 11, fontWeight: 600, color: "var(--faint)", borderBottom: "1px solid var(--border-soft)", background: "var(--th)" }}>{t("products:categoriesAll")}</div>
                      {p.categories.map((c) => (
                        <div key={c.slug} style={{ padding: "7px 12px", fontSize: 13, color: "var(--text)", borderBottom: "1px solid var(--border-soft)" }}>{c.name}</div>
                      ))}
                    </div>
                  </>
                )}
              </div>
              <div style={{ width: 90, textAlign: "right", fontWeight: 600 }}>{huf(p.priceGrossHuf)}</div>
              <div style={{ width: 80, color: "var(--muted)" }}>{p.stockQty}</div>
              <div style={{ width: 120 }}><StatusPill status={tone(p.status)} label={t(`products:status_${p.status}`)} /></div>
            </div>
          ))}
          <Pagination current={current} pageSize={PAGE_SIZE} total={total} onChange={setCurrent} />
        </div>
      )}
    </div>
  );
}
